package craken.compiler.obj;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.Stage;
import craken.compiler.asm.AsmResult;
import craken.compiler.asm.Assembler;
import craken.compiler.obj.assembler.WindowsX64MachineAssembler;
import craken.compiler.obj.coff.CoffObjectFile;
import craken.compiler.obj.coff.CoffObjectWriter;
import craken.compiler.obj.machine.MachineModule;
import craken.compiler.obj.x64.EncodedMachineModule;
import craken.compiler.obj.x64.X64Encoder;
import craken.compiler.Diagnostic;
import craken.SourceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 将 AsmResult 编码并写出为 Windows x64 COFF obj 的可单步阶段。 */
public final class ObjBuilder extends Stage {
    private final WindowsX64MachineAssembler machineAssembler;
    private final X64Encoder encoder;
    private final CoffObjectWriter objectWriter;

    private Assembler asmStage;
    private SourceFile sourceFile;
    private AsmResult asmResult;
    private Path outputDirectory;
    private Path assemblyPath;
    private Path objectPath;
    private MachineModule machineModule;
    private EncodedMachineModule encodedMachineModule;
    private CoffObjectFile objectFile;
    private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();
    private ObjResult result;
    private Phase phase = Phase.PREPARE_OUTPUT;
    private String currentOperation = "";
    private boolean initialized;
    private boolean completed;
    private long stepCount;

    public ObjBuilder() {
        this(new WindowsX64MachineAssembler(), new X64Encoder(), new CoffObjectWriter());
    }

    ObjBuilder(
            WindowsX64MachineAssembler machineAssembler,
            X64Encoder encoder,
            CoffObjectWriter objectWriter
    ) {
        this.machineAssembler = Objects.requireNonNull(machineAssembler, "machineAssembler");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.objectWriter = Objects.requireNonNull(objectWriter, "objectWriter");
    }

    public ObjBuilder(SourceFile sourceFile, Assembler asmStage, Path outputDirectory, String artifactName) {
        this();
        this.asmStage = Objects.requireNonNull(asmStage, "asmStage");
        configure(sourceFile, outputDirectory, artifactName);
    }

    public void begin(SourceFile sourceFile, AsmResult asmResult, Path outputDirectory, String artifactName) {
        asmStage = null;
        this.asmResult = Objects.requireNonNull(asmResult, "asmResult");
        configure(sourceFile, outputDirectory, artifactName);
    }

    public ObjResult build(SourceFile sourceFile, AsmResult asmResult, Path outputDirectory, String artifactName) {
        begin(sourceFile, asmResult, outputDirectory, artifactName);
        new CompilerApi(List.of(this)).run();
        return result();
    }

    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("obj stage is already completed");
        }
        ensureInitialized();
        SourceRange range = phase == Phase.COMPLETE ? null : sourceFile.range(0, sourceFile.content().length());
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
                    phase = Phase.COMPLETE;
                }
                case COMPLETE -> finish();
            }
        } catch (IOException exception) {
            fail("OBJ001", "写出 obj 产物失败：" + exception.getMessage());
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            fail("OBJ002", "生成 Windows x64 COFF obj 失败：" + exception.getMessage());
        }
        stepCount++;
        return finishStep(range, currentOperation, diagnostics, this::currentResult);
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    public ObjResult result() {
        if (result == null) throw new IllegalStateException("obj result is not ready");
        return result;
    }

    public Phase phase() { return phase; }
    public long stepCount() { return stepCount; }
    public long plannedStepCount() { return Phase.values().length; }
    public Optional<MachineModule> machineModule() { return Optional.ofNullable(machineModule); }
    public Optional<CoffObjectFile> objectFile() { return Optional.ofNullable(objectFile); }
    public Optional<EncodedMachineModule> encodedMachineModule() { return Optional.ofNullable(encodedMachineModule); }

    private void configure(SourceFile sourceFile, Path outputDirectory, String artifactName) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (artifactName == null || artifactName.isBlank()) throw new IllegalArgumentException("artifactName must not be blank");
        assemblyPath = outputDirectory.resolve(artifactName + ".asm");
        objectPath = outputDirectory.resolve(artifactName + ".obj");
        machineModule = null;
        encodedMachineModule = null;
        objectFile = null;
        diagnostics.clear();
        result = null;
        phase = Phase.PREPARE_OUTPUT;
        currentOperation = "";
        stepCount = 0;
        completed = false;
        initialized = true;
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("obj builder has no input");
        if (asmResult != null) return;
        if (asmStage.canNext()) throw new IllegalStateException("Asm stage has not completed");
        if (!asmStage.succeeded()) throw new IllegalStateException("Asm stage did not succeed");
        asmResult = asmStage.result();
    }

    private void finish() {
        currentOperation = "";
        result = new ObjResult(assemblyPath, objectPath, objectFile, machineModule.entrySymbol());
        completed = true;
    }

    private ObjResult currentResult() {
        if (result != null) {
            return result;
        }
        String entrySymbol = machineModule == null ? null : machineModule.entrySymbol();
        return new ObjResult(assemblyPath, objectPath, objectFile, entrySymbol);
    }

    private void fail(String code, String message) {
        diagnostics.add(new Diagnostic(code, Diagnostic.Severity.ERROR, message, sourceFile.range(0, 0)));
        result = new ObjResult(assemblyPath, objectPath, objectFile, machineModule == null ? null : machineModule.entrySymbol());
        completed = true;
    }

    public enum Phase {
        PREPARE_OUTPUT,
        WRITE_ASSEMBLY,
        ASSEMBLE_MACHINE_MODULE,
        ENCODE_X64,
        BUILD_COFF,
        WRITE_OBJECT,
        COMPLETE
    }
}
