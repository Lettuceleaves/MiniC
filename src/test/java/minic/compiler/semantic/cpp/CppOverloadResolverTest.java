package minic.compiler.semantic.cpp;

import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static minic.compiler.semantic.cpp.CppOverloadResolver.*;
import static minic.compiler.semantic.cpp.CppValueCategory.*;
import static minic.compiler.type.MiniType.*;
import static minic.compiler.type.MiniType.TypeQualifier.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppOverloadResolverTest {
    private static MiniType c(MiniType t) { return qualified(t, Set.of(CONST)); }
    private static MiniType v(MiniType t) { return qualified(t, Set.of(VOLATILE)); }
    private static Argument l(MiniType t) { return new Argument(t, LVALUE, false); }
    private static Argument r(MiniType t) { return new Argument(t, PRVALUE, false); }
    private static Candidate<String> candidate(String id, MiniType... types) {
        return new Candidate<>(id, List.of(types), false);
    }
    private static final MiniType IP = INT.pointerTo(), CP = c(INT).pointerTo();
    private static final MiniType FN = function(INT, List.of(INT));

    static Stream<Arguments> oneArgumentCases() {
        return Stream.of(
            ok("identity", r(INT), INT, DOUBLE),
            ok("integral promotion", l(CHAR), INT, LONG),
            ok("unsigned short promotion", r(UNSIGNED_SHORT), INT, UNSIGNED_INT),
            ok("bool promotion", r(BOOL), INT, SHORT),
            ok("floating promotion", r(FLOAT), DOUBLE, INT),
            ambiguous("no width preference", r(INT), LONG, LONG_LONG),
            ambiguous("no signedness preference", r(INT), UNSIGNED_INT, LONG),
            ok("value top cv removed", l(c(INT)), INT, DOUBLE),
            ok("reference cv preference", l(INT), INT.referenceTo(), c(INT).referenceTo()),
            ambiguous("value and reference", l(INT), INT, c(INT).referenceTo()),
            ambiguous("prvalue value and reference", r(INT), INT, c(INT).referenceTo()),
            reject("mutable ref temporary", r(INT), INT.referenceTo()),
            reject("mutable ref conversion", l(SHORT), INT.referenceTo()),
            ok("const ref scalar temporary", l(SHORT), c(INT).referenceTo(), c(LONG).referenceTo()),
            reject("volatile cannot fall back to copy", l(v(INT)), c(INT).referenceTo()),
            reject("const volatile ref temporary", r(INT), c(v(INT)).referenceTo()),
            ok("volatile ref direct", l(v(INT)), v(INT).referenceTo(), c(v(INT)).referenceTo()),
            ok("pointer qualification", r(IP), CP, VOID.pointerTo()),
            ok("pointer cv subset", r(IP), CP, c(v(INT)).pointerTo()),
            ambiguous("pointer incomparable cv", r(IP), CP, v(INT).pointerTo()),
            reject("unsafe nested qualification", r(IP.pointerTo()), CP.pointerTo()),
            ok("safe nested qualification", r(IP.pointerTo()), c(CP).pointerTo()),
            reject("pointer const removal", r(CP), IP),
            ok("object pointer void", r(IP), VOID.pointerTo(), BOOL),
            ok("const object pointer void", r(CP), c(VOID).pointerTo(), BOOL),
            reject("void cannot object", r(VOID.pointerTo()), IP),
            reject("void cannot remove pointee cv", r(CP), VOID.pointerTo()),
            reject("function cannot void", l(FN), VOID.pointerTo()),
            ok("function decay", l(FN), FN.pointerTo(), BOOL),
            ok("function reference", l(FN), FN.referenceTo()),
            ambiguous("function reference decay tie", l(FN), FN.referenceTo(), FN.pointerTo()),
            ok("array value decay", l(INT.arrayOf(3)), IP, BOOL),
            ok("array reference cv", l(INT.arrayOf(3)), INT.arrayOf(3).referenceTo(), c(INT).arrayOf(3).referenceTo()),
            ambiguous("array reference decay tie", l(INT.arrayOf(3)), INT.arrayOf(3).referenceTo(), IP),
            reject("array reference bound mismatch", l(INT.arrayOf(3)), INT.arrayOf(4).referenceTo()),
            reject("array reference const removal", l(c(INT).arrayOf(3)), INT.arrayOf(3).referenceTo()),
            ok("array outer cv propagates", l(c(INT.arrayOf(3))), CP),
            ok("pointer temporary binds const pointer ref", l(IP), c(CP).referenceTo()),
            reject("pointer temporary cannot bind mutable pointer ref", l(IP), CP.referenceTo()),
            ok("pointer ref identity beats qualification", l(IP), c(IP).referenceTo(), CP),
            ok("deep versus top cv reference", l(IP), c(v(IP)).referenceTo(), c(CP).referenceTo()),
            ambiguous("cross pointer ref value cv", l(IP), c(CP).referenceTo(), v(INT).pointerTo()),
            ambiguous("same qualification ref value", l(IP), c(CP).referenceTo(), CP),
            ok("null pointer", r(NULL), IP),
            reject("nullptr cannot implicit bool", r(NULL), BOOL),
            ambiguous("null pointer targets tied", r(NULL), IP, CP),
            ambiguous("null pointer void targets tied", r(NULL), IP, VOID.pointerTo()),
            ambiguous("null reference value tied", r(NULL), c(IP).referenceTo(), CP),
            ambiguous("null references tied", r(NULL), c(IP).referenceTo(), c(CP).referenceTo()),
            ok("zero literal prefers int", new Argument(INT, PRVALUE, true), INT, IP),
            ambiguous("zero pointer targets tied", new Argument(INT, PRVALUE, true), IP, CP),
            reject("runtime integer is not null", l(INT), IP),
            reject("nonzero literal is not null", r(INT), IP),
            ok("reference expression keeps referent", l(INT.referenceTo()), INT.referenceTo(), c(INT).referenceTo()),
            reject("unrelated record", l(struct("::A")), struct("::B")),
            ok("same record copy", l(struct("::A")), struct("::A")),
            reject("void expression", r(VOID), VOID)
        );
    }
    private static Arguments ok(String name, Argument argument, MiniType... parameters) {
        return Arguments.of(name, argument, List.of(parameters), Status.SELECTED);
    }
    private static Arguments reject(String name, Argument argument, MiniType... parameters) {
        return Arguments.of(name, argument, List.of(parameters), Status.NO_VIABLE);
    }
    private static Arguments ambiguous(String name, Argument argument, MiniType... parameters) {
        return Arguments.of(name, argument, List.of(parameters), Status.AMBIGUOUS);
    }
    @ParameterizedTest(name="{0}") @MethodSource("oneArgumentCases")
    void standardConversionDecision(String name, Argument argument, List<MiniType> parameters, Status expected) {
        var candidates = new ArrayList<Candidate<String>>();
        for (int i=0; i<parameters.size(); i++) candidates.add(candidate("candidate"+i, parameters.get(i)));
        var result = resolve(candidates, List.of(argument));
        assertEquals(expected, result.status(), name);
        if (expected == Status.SELECTED) assertEquals("candidate0", result.winner().identity(), name);
        else assertNull(result.winner());
        Collections.reverse(candidates);
        var reversed = resolve(candidates, List.of(argument));
        assertEquals(result.status(), reversed.status());
        assertEquals(result.winner(), reversed.winner());
    }
    @Test void comparesEveryArgumentInsteadOfSummingRanks() {
        var candidates = List.of(candidate("a", INT, INT, LONG), candidate("b", LONG, LONG, INT));
        assertEquals(Status.AMBIGUOUS, resolve(candidates, List.of(r(INT), r(INT), r(INT))).status());
        var winner = candidate("all", INT, INT, INT);
        assertEquals(winner, resolve(List.of(candidates.get(0), winner, candidates.get(1)),
                List.of(r(INT), r(INT), r(INT))).winner());
    }
    @Test void arityAndEllipsisParticipateOnlyWhenUsed() {
        var ellipsis = new Candidate<>("ellipsis", List.<MiniType>of(), true);
        assertEquals("fixed", resolve(List.of(ellipsis, candidate("fixed", DOUBLE)), List.of(r(INT))).winner().identity());
        assertEquals(Status.AMBIGUOUS, resolve(List.of(ellipsis, candidate("zero")), List.of()).status());
        assertEquals("ellipsis", resolve(List.of(ellipsis, candidate("two", INT, INT)), List.of(r(INT))).winner().identity());
        assertEquals(Status.NO_VIABLE, resolve(List.of(candidate("zero")), List.of(r(INT))).status());
        assertEquals(Status.NO_VIABLE, resolve(List.of(candidate("one", INT)), List.of()).status());
    }
    @Test void receiverCvAndArgumentConversionsHaveEqualWeight() {
        var type = struct("::C");
        var mutable = new Candidate<>("mutable", List.of(LONG), false, type);
        var constant = new Candidate<>("constant", List.of(INT), false, c(type));
        assertEquals(Status.AMBIGUOUS, resolve(List.of(mutable, constant), List.of(r(INT)), l(type)).status());
        assertEquals(constant, resolve(List.of(mutable, constant), List.of(r(INT)), l(c(type))).winner());
        assertEquals(Status.NO_VIABLE, resolve(List.of(mutable), List.of(r(INT)), l(c(type))).status());
        assertEquals(Status.NO_VIABLE, resolve(List.of(mutable), List.of(r(INT)), l(struct("::Other"))).status());
        assertEquals(Status.NO_VIABLE, resolve(List.of(mutable), List.of(r(INT))).status());
    }
    @Test void unqualifiedMethodsAcceptSameClassPrvalueReceiver() {
        var type = struct("::C");
        var mutable = new Candidate<>("mutable", List.<MiniType>of(), false, type);
        var constant = new Candidate<>("constant", List.<MiniType>of(), false, c(type));
        assertEquals(mutable, resolve(List.of(constant, mutable), List.of(), r(type)).winner());
        assertEquals(constant, resolve(List.of(mutable, constant), List.of(), r(c(type))).winner());
    }
    @Test void viableIncludesDominatedCandidatesAndIsImmutable() {
        var parameters = new ArrayList<>(List.of(INT));
        var first = new Candidate<>("first", parameters, false);
        parameters.clear();
        var second = candidate("second", DOUBLE);
        var result = resolve(List.of(first, second, candidate("bad", INT.pointerTo())), List.of(r(INT)));
        assertEquals(List.of(first, second), result.viable());
        assertThrows(UnsupportedOperationException.class, () -> result.viable().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.parameterTypes().clear());
        assertEquals(Status.NO_VIABLE, resolve(List.of(), List.of(r(INT))).status());
    }
    @Test void functionParameterAdjustmentPreservesReferenceAndReturnTypes() {
        var adjusted = function(INT, List.of(c(INT), INT.arrayOf(3)));
        var canonicalFunction = function(INT, List.of(INT, IP));
        assertEquals("function", resolve(List.of(candidate("function", canonicalFunction.pointerTo()),
                candidate("bool", BOOL)), List.of(l(adjusted))).winner().identity());
        assertEquals(Status.NO_VIABLE, resolve(List.of(candidate("function", FN.pointerTo())),
                List.of(l(function(c(INT), List.of(INT))))).status());
        assertEquals(Status.NO_VIABLE, resolve(List.of(candidate("function", FN.pointerTo())),
                List.of(l(function(INT, List.of(c(INT).referenceTo()))))).status());
    }
    @Test void pointerObjectCvIsDistinctFromItsPointeeCv() {
        assertEquals("identity", resolve(List.of(candidate("identity", IP),candidate("qualification", CP)),
                List.of(l(c(IP)))).winner().identity());
        assertEquals(Status.NO_VIABLE, resolve(List.of(candidate("mutable", IP.referenceTo())),
                List.of(l(c(IP)))).status());
        assertEquals(Status.SELECTED, resolve(List.of(candidate("constant", c(IP).referenceTo())),
                List.of(l(c(IP)))).status());
    }
    @Test void rejectsInvalidMetadataWithoutGuessingMissingInformation() {
        assertThrows(IllegalArgumentException.class, () -> new Argument(BOOL, PRVALUE, true));
        assertThrows(IllegalArgumentException.class, () -> new Argument(INT, LVALUE, true));
        assertThrows(IllegalArgumentException.class, () -> new Candidate<>("bad",List.of(),false,INT));
        assertThrows(NullPointerException.class, () -> new Argument(null, LVALUE, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Resolution<>(Status.SELECTED,null,List.of(candidate("one",INT))));
        assertEquals(Status.NO_VIABLE, resolve(List.of(candidate("ordinary",INT)),
                List.of(r(INT)), l(struct("::C"))).status());
    }
}
