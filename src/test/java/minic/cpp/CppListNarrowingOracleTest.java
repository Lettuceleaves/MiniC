package minic.cpp;

import minic.compiler.semantic.cpp.CppListNarrowing;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import static minic.compiler.semantic.cpp.CppListNarrowing.Result.*;
import static minic.compiler.type.MiniType.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the independent rules, not a claim that ordinary source list initialization is implemented. */
@Timeout(30)
final class CppListNarrowingOracleTest {
    @TempDir Path temporary;
    record Probe(MiniType source, MiniType target, String value, boolean constant, boolean accepted) {}
    static Stream<Probe> probes() {
        return Stream.of(
            new Probe(INT,LONG,"5",false,true),
            new Probe(UNSIGNED_INT,LONG_LONG,"5",false,true),
            new Probe(BOOL,CHAR,"true",false,true),
            new Probe(UNSIGNED_CHAR,SHORT,"255",false,true),
            // GCC 8 PR 65043 accepts narrowing to bool. Negative bool cases are
            // asserted from [dcl.init.list]/7 in CppListNarrowingTest, not this oracle.
            new Probe(BOOL,FLOAT,"true",false,false),
            new Probe(UNSIGNED_CHAR,DOUBLE,"5",false,false),
            new Probe(DOUBLE,FLOAT,"0.1",false,false),
            new Probe(FLOAT,DOUBLE,"0.1f",false,true),
            new Probe(DOUBLE,INT,"1.0",true,false),
            new Probe(INT,CHAR,"127",true,true),
            new Probe(INT,CHAR,"128",true,false),
            new Probe(INT,CHAR,"-128",true,true),
            new Probe(INT,CHAR,"-129",true,false),
            new Probe(INT,UNSIGNED_CHAR,"255",true,true),
            new Probe(INT,UNSIGNED_CHAR,"-1",true,false),
            new Probe(INT,BOOL,"0",true,true),
            new Probe(INT,BOOL,"1",true,true),
            new Probe(UNSIGNED_LONG_LONG,LONG_LONG,"9223372036854775807",true,true),
            new Probe(UNSIGNED_LONG_LONG,LONG_LONG,"9223372036854775808",true,false),
            new Probe(UNSIGNED_LONG_LONG,DOUBLE,"18446744073709551615",true,false),
            new Probe(LONG_LONG,DOUBLE,"1152921504606846976",true,true),
            new Probe(LONG_LONG,DOUBLE,"9007199254740993",true,false),
            new Probe(INT,FLOAT,"16777216",true,true),
            new Probe(INT,FLOAT,"16777217",true,false),
            new Probe(DOUBLE,FLOAT,"0.1",true,true),
            new Probe(DOUBLE,FLOAT,"1e100",true,false),
            new Probe(DOUBLE,FLOAT,"1e-100",true,true));
    }
    @ParameterizedTest @MethodSource("probes")
    void agreesWithCpp17ForConstantsAndRuntimeValues(Probe probe) throws Exception {
        String value = probe.value;
        if (probe.source.equals(UNSIGNED_LONG_LONG)) value += "ULL";
        else if (probe.source.equals(LONG_LONG)) value += "LL";
        String source = "int main(){" + (probe.constant ? "constexpr " : "") + probe.source + " input=" + value
                + ";" + probe.target + " output{input};return 0;}";
        Path file = temporary.resolve("probe.cpp"); Files.writeString(file, source);
        var compilation = BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(15),16384);
        assertFalse(compilation.timedOut()); assertFalse(compilation.outputExceeded());
        assertEquals(probe.accepted, compilation.exitCode() == 0, source + "\n" + compilation.stderr());
        if (!probe.accepted) assertTrue(compilation.stderr().contains("narrowing"), compilation.stderr());
        BigDecimal constant = probe.constant ? probe.source.isIntegerScalar()
                ? new BigDecimal(probe.value) : new BigDecimal(Double.parseDouble(probe.value)) : null;
        var result = CppListNarrowing.check(probe.source, probe.target, constant);
        assertNotEquals(OUTSIDE_SUBSET,result, source);
        assertEquals(probe.accepted, result == SAFE, source);
    }
}
