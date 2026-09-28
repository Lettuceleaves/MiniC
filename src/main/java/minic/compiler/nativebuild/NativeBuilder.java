package minic.compiler.nativebuild;

import minic.compiler.Loop;
import minic.compiler.Stage;
import minic.compiler.asm.AsmResult;
import minic.compiler.asm.Assembler;
import minic.compiler.nativebuild.assembler.WindowsX64MachineAssembler;
import minic.compiler.nativebuild.coff.CoffObjectFile;
import minic.compiler.nativebuild.coff.CoffObjectWriter;
import minic.compiler.nativebuild.machine.MachineModule;
import minic.compiler.nativebuild.pe.PeImage;
import minic.compiler.nativebuild.pe.WindowsPeLinker;
import minic.compiler.nativebuild.runtime.MiniCWindowsRuntime;
import minic.compiler.nativebuild.x64.EncodedMachineModule;
import minic.compiler.nativebuild.x64.X64Encoder;
import minic.diagnostics.Diagnostic;
import minic.compiler.SourceFile;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 完全由 MiniC Java 代码实现、可逐步执行的 Windows x64 本机构建阶段。 */
public final class NativeBuilder extends Stage {
    private static final Map<String, String> SYSTEM_IMPORTS = Map.of(
            "ExitProcess", "KERNEL32.dll",
            "GetStdHandle", "KERNEL32.dll",
            "WriteFile", "KERNEL32.dll"
    );

    private final WindowsX64MachineAssembler machineAssembler;
    private final X64Encoder encoder;
    private final CoffObjectWriter objectWriter;
    private final WindowsPeLinker linker;
    private final MiniCWindowsRuntime runtime;

    private Assembler asmStage;
    private SourceFile sourceFile;
    private AsmResult asmResult;
    private Path outputDirectory;
    private String artifactName;
    private Path assemblyPath;
    private Path objectPath;
    private Path executablePath;
    private MachineModule machineModule;
    private EncodedMachineModule encodedMachineModule;
    private CoffObjectFile objectFile;
    private PeImage peImage;
    private ExecutableArtifact executableArtifact;
    private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();
    private NativeBuildResult result;
    private Phase phase = Phase.PREPARE_OUTPUT;
    private String currentOperation = "";
    private boolean initialized;
    private boolean completed;
    private long stepCount;

    public NativeBuilder() {
        this(
                new WindowsX64MachineAssembler(),
                new X64Encoder(),
                new CoffObjectWriter(),
                new WindowsPeLinker(),
                new MiniCWindowsRuntime()
        );
    }

    NativeBuilder(
            WindowsX64MachineAssembler machineAssembler,
            X64Encoder encoder,
            CoffObjectWriter objectWriter,
            WindowsPeLinker linker,
            MiniCWindowsRuntime runtime
    ) {
        this.machineAssembler = Objects.requireNonNull(machineAssembler, "machineAssembler");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.objectWriter = Objects.requireNonNull(objectWriter, "objectWriter");
        this.linker = Objects.requireNonNull(linker, "linker");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /** 创建由 Asm 阶段提供输入的本机构建阶段。 */
    public NativeBuilder(
            SourceFile sourceFile,
            Assembler asmStage,
            Path outputDirectory,
            String artifactName
    ) {
        this();
        this.asmStage = Objects.requireNonNull(asmStage, "asmStage");
        configure(sourceFile, outputDirectory, artifactName);
    }

    /** 设置本机构建输入并重置阶段。 */
    public void begin(
            SourceFile sourceFile,
            AsmResult asmResult,
            Path outputDirectory,
            String artifactName
    ) {
        asmStage = null;
        this.asmResult = Objects.requireNonNull(asmResult, "asmResult");
        configure(sourceFile, outputDirectory, artifactName);
    }

    /** 使用根 Loop 执行完整本机构建。 */
    public NativeBuildResult buildExecutable(
            SourceFile sourceFile,
            AsmResult asmResult,
            Path outputDirectory,
            String artifactName
    ) {
        begin(sourceFile, asmResult, outputDirectory, artifactName);
        new Loop(List.of(this)).run();
        return result();
    }

    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("native build is already completed");
        }
        ensureInitialized();
        SourceRange sourceRange = phase == Phase.COMPLETE
                ? null
                : sourceFile.range(0, sourceFile.content().length());
        try {
            switch (phase) {
                case PREPARE_OUTPUT -> {
                    currentOperation = "prepare output directory";
                    Files.createDirectories(outputDirectory);
                    phase = Phase.WRITE_ASSEMBLY;
                }
                case WRITE_ASSEMBLY -> {
                    currentOperation = "write assembly";
                    Files.writeString(assemblyPath, asmResult.text(), StandardCharsets.US_ASCII);
                    phase = Phase.ASSEMBLE_MACHINE_MODULE;
                }
                case ASSEMBLE_MACHINE_MODULE -> {
                    currentOperation = "assemble machine module";
                    machineModule = machineAssembler.assemble(asmResult);
                    phase = Phase.ATTACH_RUNTIME;
                }
                case ATTACH_RUNTIME -> {
                    currentOperation = "attach runtime";
                    machineModule = runtime.attach(machineModule);
                    phase = Phase.ENCODE_X64;
                }
                case ENCODE_X64 -> {
                    currentOperation = "encode x64 machine sections";
                    encodedMachineModule = encoder.encode(machineModule);
                    phase = Phase.BUILD_COFF;
                }
                case BUILD_COFF -> {
                    currentOperation = "build COFF object";
                    objectFile = objectWriter.write(encodedMachineModule);
                    phase = Phase.WRITE_OBJECT;
                }
                case WRITE_OBJECT -> {
                    currentOperation = "write COFF object";
                    Files.write(objectPath, objectFile.bytes());
                    phase = Phase.LINK_PE;
                }
                case LINK_PE -> {
                    currentOperation = "link PE32+ image";
                    peImage = linker.link(objectFile, machineModule.entrySymbol(), SYSTEM_IMPORTS);
                    phase = Phase.WRITE_EXECUTABLE;
                }
                case WRITE_EXECUTABLE -> {
                    currentOperation = "write executable";
                    Files.write(executablePath, peImage.bytes());
                    executableArtifact = new ExecutableArtifact(executablePath);
                    phase = Phase.COMPLETE;
                }
                case COMPLETE -> finish();
            }
        } catch (IOException exception) {
            fail("NAT001", "写出本机二进制产物失败：" + exception.getMessage());
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            fail("NAT002", "内置 Windows x64 本机构建失败：" + exception.getMessage());
        }
        stepCount++;
        return sourceRange;
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    @Override
    public boolean succeeded() {
        return completed && diagnostics.isEmpty() && executableArtifact != null;
    }

    public NativeBuildResult result() {
        if (result == null) {
            throw new IllegalStateException("native build result is not ready");
        }
        return result;
    }

    public Phase phase() {
        return phase;
    }

    public String currentOperation() {
        return currentOperation;
    }

    public long stepCount() {
        return stepCount;
    }

    public long plannedStepCount() {
        return Phase.values().length;
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public Optional<MachineModule> machineModule() {
        return Optional.ofNullable(machineModule);
    }

    public Optional<CoffObjectFile> objectFile() {
        return Optional.ofNullable(objectFile);
    }

    public Optional<EncodedMachineModule> encodedMachineModule() {
        return Optional.ofNullable(encodedMachineModule);
    }

    public Optional<PeImage> peImage() {
        return Optional.ofNullable(peImage);
    }

    private void configure(SourceFile sourceFile, Path outputDirectory, String artifactName) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("artifactName must not be blank");
        }
        this.artifactName = artifactName;
        assemblyPath = outputDirectory.resolve(artifactName + ".asm");
        objectPath = outputDirectory.resolve(artifactName + ".obj");
        executablePath = outputDirectory.resolve(artifactName + ".exe");
        machineModule = null;
        encodedMachineModule = null;
        objectFile = null;
        peImage = null;
        executableArtifact = null;
        diagnostics.clear();
        result = null;
        phase = Phase.PREPARE_OUTPUT;
        currentOperation = "";
        stepCount = 0;
        completed = false;
        initialized = true;
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("native builder has no input");
        }
        if (asmResult != null) {
            return;
        }
        if (asmStage.canNext()) {
            throw new IllegalStateException("Asm stage has not completed");
        }
        if (!asmStage.succeeded()) {
            throw new IllegalStateException("Asm stage did not succeed");
        }
        asmResult = asmStage.result();
    }

    private void finish() {
        currentOperation = "";
        result = new NativeBuildResult(assemblyPath, objectPath, executableArtifact, diagnostics);
        completed = true;
    }

    private void fail(String code, String message) {
        diagnostics.add(new Diagnostic(
                code,
                Diagnostic.Severity.ERROR,
                message,
                sourceFile.range(0, 0)
        ));
        result = new NativeBuildResult(assemblyPath, objectPath, null, diagnostics);
        completed = true;
    }

    /** NativeBuilder 的宏观构建步骤。 */
    public enum Phase {
        PREPARE_OUTPUT,
        WRITE_ASSEMBLY,
        ASSEMBLE_MACHINE_MODULE,
        ATTACH_RUNTIME,
        ENCODE_X64,
        BUILD_COFF,
        WRITE_OBJECT,
        LINK_PE,
        WRITE_EXECUTABLE,
        COMPLETE
    }
}
