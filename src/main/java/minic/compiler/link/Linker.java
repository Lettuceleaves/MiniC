package minic.compiler.link;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.Stage;
import minic.compiler.link.pe.PeImage;
import minic.compiler.link.pe.WindowsPeLinker;
import minic.compiler.library.SystemLibraryCatalog;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.obj.ObjResult;
import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 将 ObjResult 链接并写出为 PE32+ exe 的可单步阶段。 */
public final class Linker extends Stage {
    private final WindowsPeLinker peLinker;
    private final SystemLibraryCatalog systemLibraries;
    private ObjBuilder objStage;
    private SourceFile sourceFile;
    private ObjResult objResult;
    private Path executablePath;
    private PeImage peImage;
    private ExecutableArtifact executableArtifact;
    private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();
    private LinkResult result;
    private Phase phase = Phase.LINK_PE;
    private String currentOperation = "";
    private boolean initialized;
    private boolean completed;
    private long stepCount;

    public Linker() {
        this(new WindowsPeLinker(), SystemLibraryCatalog.defaults());
    }

    Linker(WindowsPeLinker peLinker, SystemLibraryCatalog systemLibraries) {
        this.peLinker = Objects.requireNonNull(peLinker, "peLinker");
        this.systemLibraries = Objects.requireNonNull(systemLibraries, "systemLibraries");
    }

    public Linker(SourceFile sourceFile, ObjBuilder objStage, Path outputDirectory, String artifactName) {
        this();
        this.objStage = Objects.requireNonNull(objStage, "objStage");
        configure(sourceFile, outputDirectory, artifactName);
    }

    public void begin(SourceFile sourceFile, ObjResult objResult, Path outputDirectory, String artifactName) {
        objStage = null;
        this.objResult = Objects.requireNonNull(objResult, "objResult");
        configure(sourceFile, outputDirectory, artifactName);
    }

    public LinkResult link(SourceFile sourceFile, ObjResult objResult, Path outputDirectory, String artifactName) {
        begin(sourceFile, objResult, outputDirectory, artifactName);
        new CompilerApi(List.of(this)).run();
        return result();
    }

    @Override
    public SourceRange step() {
        if (!canNext()) throw new IllegalStateException("link stage is already completed");
        ensureInitialized();
        SourceRange range = phase == Phase.COMPLETE ? null : sourceFile.range(0, sourceFile.content().length());
        try {
            switch (phase) {
                case LINK_PE -> {
                    currentOperation = "link PE32+ image";
                    peImage = peLinker.link(
                            objResult.objectFile(),
                            objResult.entrySymbol(),
                            systemLibraries.bindings()
                    );
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
            fail("LNK001", "写出 exe 产物失败：" + exception.getMessage());
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            fail("LNK002", "链接 PE32+ 失败：" + exception.getMessage());
        }
        stepCount++;
        return range;
    }

    @Override
    public boolean canNext() { return !completed; }

    @Override
    public boolean succeeded() { return completed && diagnostics.isEmpty() && executableArtifact != null; }

    public LinkResult result() {
        if (result == null) throw new IllegalStateException("link result is not ready");
        return result;
    }

    public Phase phase() { return phase; }
    public String currentOperation() { return currentOperation; }
    public long stepCount() { return stepCount; }
    public long plannedStepCount() { return Phase.values().length; }
    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }
    public Optional<PeImage> peImage() { return Optional.ofNullable(peImage); }

    private void configure(SourceFile sourceFile, Path outputDirectory, String artifactName) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (artifactName == null || artifactName.isBlank()) throw new IllegalArgumentException("artifactName must not be blank");
        executablePath = outputDirectory.resolve(artifactName + ".exe");
        peImage = null;
        executableArtifact = null;
        diagnostics.clear();
        result = null;
        phase = Phase.LINK_PE;
        currentOperation = "";
        stepCount = 0;
        completed = false;
        initialized = true;
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("linker has no input");
        if (objResult != null) return;
        if (objStage.canNext()) throw new IllegalStateException("Obj stage has not completed");
        if (!objStage.succeeded()) throw new IllegalStateException("Obj stage did not succeed");
        objResult = objStage.result();
    }

    private void finish() {
        currentOperation = "";
        result = new LinkResult(objResult.objectPath(), executableArtifact, diagnostics);
        completed = true;
    }

    private void fail(String code, String message) {
        diagnostics.add(new Diagnostic(code, Diagnostic.Severity.ERROR, message, sourceFile.range(0, 0)));
        result = new LinkResult(objResult == null ? null : objResult.objectPath(), null, diagnostics);
        completed = true;
    }

    public enum Phase {
        LINK_PE,
        WRITE_EXECUTABLE,
        COMPLETE
    }
}
