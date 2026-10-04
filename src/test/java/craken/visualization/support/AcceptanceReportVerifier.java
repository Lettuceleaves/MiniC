package craken.visualization.support;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Verifies actual XML executions; task success alone is not acceptance evidence. */
public final class AcceptanceReportVerifier {
    private AcceptanceReportVerifier() {}
    public record Counts(int found, int passed, int skipped) {}
    public static Counts verify(Path directory, Set<String> required, boolean allowSkipped) throws Exception {
        if (!Files.isDirectory(directory)) throw new IllegalStateException("Missing reports: " + directory);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var executed = new HashSet<String>();
        int found = 0, passed = 0, skipped = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")).toList()) {
                var document = factory.newDocumentBuilder().parse(file.toFile());
                var cases = document.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    var test = (Element) cases.item(i);
                    String className = test.getAttribute("classname");
                    found++;
                    if (test.getElementsByTagName("failure").getLength() != 0
                            || test.getElementsByTagName("error").getLength() != 0)
                        throw new IllegalStateException("Failed test: " + className + "." + test.getAttribute("name"));
                    if (test.getElementsByTagName("skipped").getLength() != 0) {
                        skipped++;
                        if (!allowSkipped || required.contains(className))
                            throw new IllegalStateException("Required test skipped or aborted: " + className);
                    } else {
                        passed++;
                        executed.add(className);
                    }
                }
            }
        }
        if (passed == 0) throw new IllegalStateException("No tests executed: " + directory);
        if (!executed.containsAll(required)) {
            var missing = new HashSet<>(required);
            missing.removeAll(executed);
            throw new IllegalStateException("Required classes not executed: " + missing);
        }
        return new Counts(found, passed, skipped);
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) {
            String[] spec = args[i].split("\\|", -1);
            Set<String> required = spec[2].isEmpty() ? Set.of() : Set.of(spec[2].split(","));
            System.out.println(spec[0] + ": " + verify(root.resolve(spec[0]), required, Boolean.parseBoolean(spec[1])));
        }
    }
}
