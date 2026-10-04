package craken.visualization.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class AcceptanceReportVerifierTest {
    @TempDir Path directory;
    private void report(String cases) throws Exception {
        Files.writeString(directory.resolve("TEST-junit-jupiter.xml"),
                "<testsuite>" + cases + "</testsuite>");
    }
    @Test void missingOrEmptyReportsCannotPass() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> AcceptanceReportVerifier.verify(directory, Set.of(), false));
        report("");
        assertThrows(IllegalStateException.class,
                () -> AcceptanceReportVerifier.verify(directory, Set.of(), false));
    }
    @Test void requiredClassesMustActuallyRun() throws Exception {
        report("<testcase classname='Present' name='ok'/>");
        assertThrows(IllegalStateException.class,
                () -> AcceptanceReportVerifier.verify(directory, Set.of("Missing"), false));
        var counts = AcceptanceReportVerifier.verify(directory, Set.of("Present"), false);
        assertEquals(1, counts.passed());
    }
    @Test void skippedAbortedAndFailedRequiredExecutionsAreRejected() throws Exception {
        for (String outcome : new String[]{"skipped", "failure", "error"}) {
            report("<testcase classname='Required' name='x'><" + outcome + "/></testcase>");
            assertThrows(IllegalStateException.class,
                    () -> AcceptanceReportVerifier.verify(directory, Set.of("Required"), false));
        }
    }
    @Test void baselineMaySkipUnrelatedTestsButMustExecuteSomething() throws Exception {
        report("<testcase classname='Optional' name='skip'><skipped/></testcase>"
                + "<testcase classname='Active' name='run'/>");
        var counts = AcceptanceReportVerifier.verify(directory, Set.of("Active"), true);
        assertEquals(1, counts.skipped());
        assertEquals(1, counts.passed());
    }
}
