package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** F04b: scalar/trivial-aggregate materialization and C++17 lvalue results; no destructors are claimed. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppReferenceTemporaryTest {
    @TempDir Path temporary;

    private static final String TEMPORARY_SOURCE = """
            #include <stdio.h>
            int read(const int &value) { return value; }
            int main() {
                const int &first = 3 + 4;
                const int &second = 11;
                int result = read(first) + read(5);
                printf("%d %d %d\\n", first, second, result);
                return 0;
            }
            """;

    static Stream<Arguments> temporaryAndCategoryPrograms() {
        return Stream.of(
                Arguments.of("scalar-temporary-local-and-call-lifetimes", TEMPORARY_SOURCE, "7 11 12\n"),
                Arguments.of("conversion-materializes-a-distinct-object", """
                        #include <stdio.h>
                        int main() {
                            int value = 3;
                            const double &converted = value;
                            const int &direct = value;
                            value = 8;
                            printf("%.1f %d %d %d\\n", converted, direct, (int)sizeof(converted), (int)sizeof(const int &));
                            return 0;
                        }
                        """, "3.0 8 8 4\n"),
                Arguments.of("pointer-qualification-conversion-materializes-a-distinct-pointer", """
                        #include <stdio.h>
                        int main(){
                            int first=2;
                            int second=5;
                            int *pointer=&first;
                            const int *const &view=pointer;
                            pointer=&second;
                            printf("%d %d %d\\n", *view, *pointer, (void *)&view != (void *)&pointer);
                            return 0;
                        }
                        """, "2 5 1\n"),
                Arguments.of("each-binding-materializes-once-and-storage-is-distinct", """
                        #include <stdio.h>
                        int count = 0;
                        int next() { return ++count; }
                        int main() {
                            const int &first = next();
                            const int &second = next();
                            int later = next();
                            printf("%d %d %d %d %d\\n", first, second, count, later, &first != &second);
                            return 0;
                        }
                        """, "1 2 3 3 1\n"),
                Arguments.of("aggregate-return-and-subobject-lifetime-extension", """
                        #include <stdio.h>
                        struct Box { int value; int extra; int read() const { return value + extra; } };
                        Box make(int value) { Box result = {value, value + 1}; return result; }
                        int main() {
                            const Box &first = make(2);
                            const int &member = make(6).value;
                            Box later = make(20);
                            printf("%d %d %d\\n", first.read(), member, later.value);
                            return 0;
                        }
                        """, "5 6 20\n"),
                Arguments.of("mixed-conditional-makes-temporary-while-cv-lvalues-alias", """
                        #include <stdio.h>
                        int main() {
                            int value = 2;
                            const int other = 4;
                            const int &mixed = 1 ? value : 7;
                            const int &direct = 1 ? value : other;
                            value = 9;
                            printf("%d %d %d\\n", mixed, direct, &direct == &value);
                            return 0;
                        }
                        """, "2 9 1\n"),
                Arguments.of("materialization-stays-in-selected-branches-and-loop-iterations", """
                        #include <stdio.h>
                        int count = 0;
                        int next() { return ++count; }
                        int main() {
                            int sum = 0;
                            for (int i = 0; i < 3; i++) {
                                const int &value = next();
                                sum += value;
                            }
                            const int &chosen = 0 ? next() : 9;
                            int untouched = 0 && next();
                            printf("%d %d %d %d\\n", sum, chosen, count, untouched);
                            return 0;
                        }
                        """, "6 9 3 0\n"),
                Arguments.of("volatile-conversion-reads-once-into-an-independent-temporary", """
                        #include <stdio.h>
                        int main(){volatile int value=3; const double &view=value; value=8;
                            printf("%.1f %d\\n",view,value);return 0;}
                        """, "3.0 8\n"),
                Arguments.of("null-pointer-reference-conversions", """
                        #include <stdio.h>
                        int main(){int *const &zero=0; const int *const &nil=nullptr;
                            int value=7; int *pointer=&value; const bool &truth=pointer; pointer=0;
                            printf("%d %d %d\\n",zero==0,nil==0,truth);return 0;}
                        """, "1 1 1\n"),
                Arguments.of("non-narrowing-reference-list-temporaries", """
                        #include <stdio.h>
                        int main(){const char &small={17}; const long long &wide={3};
                            const float &fraction={1.5}; const double &exact={4};
                            printf("%d %lld %.1f %.1f\\n",small,wide,fraction,exact);return 0;}
                        """, "17 3 1.5 4.0\n"),
                Arguments.of("unevaluated-reference-call-does-not-materialize", """
                        #include <stdio.h>
                        int count=0;
                        int next(){return ++count;}
                        int read(const int &value){return value;}
                        int main(){int size=sizeof(read(next()));printf("%d %d\\n",size,count);return 0;}
                        """, "4 0\n"),
                Arguments.of("temporary-array-and-conditional-subobjects-keep-the-owner", """
                        #include <stdio.h>
                        struct Box { int values[2]; int value; };
                        int calls=0;
                        Box make(int value){++calls;Box result={{value,value+1},value};return result;}
                        int main(){const int(&array)[2]=make(3).values;
                            const int &conditional=1?make(6).value:make(9).value;
                            const int &comma=(0,make(10).value);
                            printf("%d %d %d %d\\n",array[1],conditional,comma,calls);return 0;}
                        """, "4 6 10 3\n"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("temporaryAndCategoryPrograms")
    void materializationAndValueCategoriesAgreeAcrossAllBackends(String name, String source, String expected) throws Exception {
        CppReferenceTest.agree(temporary, name, source, expected);
    }

    static Stream<Arguments> invalidTemporaryBindings() {
        return Stream.of(
                Arguments.of("mutable-reference-to-literal", "int main(){int &alias=3; // bad\n return 0;}"),
                Arguments.of("mutable-reference-to-conversion", "int main(){int value=3; double &alias=value; // bad\n return 0;}"),
                Arguments.of("postfix-result-is-a-prvalue", "int main(){int value=3; int &alias=value++; // bad\n return 0;}"),
                Arguments.of("volatile-reference-to-temporary", "int main(){const volatile int &alias=3; // bad\n return 0;}"),
                Arguments.of("related-volatile-object-cannot-copy-away-qualification", "int main(){volatile int value=3; const int &alias=value; // bad\n return 0;}"),
                Arguments.of("runtime-zero-cannot-initialize-a-pointer-reference", "int main(){int value=0; int *const &alias=value; // bad\n return 0;}"),
                Arguments.of("reference-list-cannot-narrow-floating-to-integer", "int main(){const int &alias={3.5}; // bad\n return 0;}"),
                Arguments.of("reference-list-cannot-overflow-a-double-to-float", "int main(){const float &alias={1e100}; // bad\n return 0;}"),
                Arguments.of("reference-list-cannot-truncate-an-integer", "int main(){const char &alias={300}; // bad\n return 0;}"),
                Arguments.of("reference-list-respects-unsigned-unary-arithmetic", "int main(){const char &alias={-1u}; // bad\n return 0;}"),
                Arguments.of("reference-list-cannot-narrow-a-runtime-integer-to-floating", "int main(){int value=3; const double &alias={value}; // bad\n return 0;}"),
                Arguments.of("mixed-conditional-is-a-prvalue", "int main(){int value=3; int &alias=(1?value:7); // bad\n return 0;}"),
                Arguments.of("const-array-reference-does-not-enable-writing", "int main(){int values[2]={1,2}; const int (&alias)[2]=values;\n alias[0]=3; // bad\n return 0;}"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("invalidTemporaryBindings")
    void prvaluesAndConstObjectsCannotBecomeMutableAliases(String name, String source) throws Exception {
        CppReferenceTest.reject(temporary, name, source);
    }

    static Stream<Arguments> invalidTemporarySubobjectOperations() {
        return Stream.of("make().value=3", "++make().value", "make().value++", "&make().value",
                        "int &alias=make().value", "int &alias=(0,make().value)",
                        "int &alias=1?make().value:make().value")
                .map(operation -> Arguments.of(operation, "struct Box{int value;int array[1];}; "
                        + "Box make(){Box value={1,{2}};return value;}\nint main(){"
                        + operation + "; // bad\nreturn 0;}"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("invalidTemporarySubobjectOperations")
    void physicalTemporaryStorageDoesNotTurnSourceSubobjectsIntoLvalues(String name, String source) throws Exception {
        CppReferenceTest.reject(temporary, "temporary-category-"+Integer.toUnsignedString(name.hashCode()), source);
    }

    @Test void aTemporaryArraySubscriptRetainsItsNonLvalueCategory() {
        // N4659 [expr.sub]/1 specifies xvalue for a non-lvalue array operand.
        // The available G++ 8 oracle incorrectly accepts this particular operation.
        var api = CppReferenceTest.compiler("struct Box{int values[1];}; Box make(){Box value={{2}};return value;}"
                + "int main(){++make().values[0];return 0;}");
        var semantic = CppReferenceTest.stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(CppReferenceTest.stage(api, Parser.class).succeeded());
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.code().equals("CPP004")));
    }

    @Test void materializedTemporariesAreStackObjectsAndDebugHistoryIsStable() {
        CppReferenceTest.assertDebugHistory(TEMPORARY_SOURCE, "7 11 12\n", "read", "value");
    }

    @Test void aggregateAssignmentFromATemporaryStillReturnsTheExistingTarget() throws Exception {
        CppReferenceTest.agree(temporary, "aggregate-assignment-reference", """
                #include <stdio.h>
                struct Box { int value; };
                int count=0;
                Box make(){++count;Box result={5};return result;}
                int main(){Box target={1};Box &alias=(target=make());alias.value+=2;
                    printf("%d %d %d\\n",target.value,&alias==&target,count);return 0;}
                """, "7 1 1\n");
    }

    @Test void listConversionUsesTheExactBinaryFloatingValueForLargePowersOfTwo() throws Exception {
        CppReferenceTest.agree(temporary, "exact-large-reference-list-conversion", """
                #include <stdio.h>
                int main(){const double &large={1152921504606846976LL};
                    const float &power={16777216};
                    const float &rounded={0.1};
                    printf("%d %d %d\\n",large==(double)1152921504606846976LL,power==16777216.0f,rounded==0.1f);return 0;}
                """, "1 1 1\n");
    }

    @Test void directBindingsExtendTheirObjectsWhileCallArgumentsBelongToTheFullExpression() {
        String source = """
                struct Box { int value; int extra; int array[1]; };
                Box make(){Box result={5,7,{9}};return result;}
                int read(const int &value){return value;}
                const int &identity(const int &value){return value;}
                int main(){
                    const int &direct=3;
                    const int &member=make().value;
                    const int &throughCall=identity(4);
                    const int &computed=make().value+1;
                    const int &viaDereference=*make().array;
                    const int &viaSubscript=make().array[0];
                    read(5)+read(6);
                    if(read(7)){return direct+member;}
                    return 0;
                }
                """;
        var api = CppReferenceTest.compiler(source);
        var parser = CppReferenceTest.stage(api, Parser.class);
        var semantic = CppReferenceTest.stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(), () -> parser.errors().toString());
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var result = semantic.semanticResult();
        var nodes = CppReferenceTest.nodes(parser.result().program());
        var direct = variable(nodes, "direct");
        var member = variable(nodes, "member");
        var throughCall = variable(nodes, "throughCall");
        var computed = variable(nodes, "computed");
        var viaDereference = variable(nodes, "viaDereference");
        var viaSubscript = variable(nodes, "viaSubscript");
        var directTemporary = singleTemporary(result.sourceToCore().get(direct));
        assertEquals(TemporaryLifetime.Kind.REFERENCE_SCOPE, directTemporary.lifetime().kind());
        assertSame(direct, directTemporary.lifetime().sourceOwner());
        assertEquals(MiniType.INT, directTemporary.type().unqualified());
        var memberTemporary = singleTemporary(result.sourceToCore().get(member));
        assertEquals(TemporaryLifetime.Kind.REFERENCE_SCOPE, memberTemporary.lifetime().kind());
        assertSame(member, memberTemporary.lifetime().sourceOwner());
        assertTrue(memberTemporary.type().isStruct(), "A subobject binding extends its entire temporary owner");
        var argumentTemporary = singleTemporary(result.sourceToCore().get(throughCall));
        assertEquals(TemporaryLifetime.Kind.FULL_EXPRESSION, argumentTemporary.lifetime().kind());
        assertSame(throughCall.initializer(), argumentTemporary.lifetime().sourceOwner(),
                "Returning a reference does not extend an argument temporary's lifetime");
        var computedTemporaries = temporaries(result.sourceToCore().get(computed));
        assertEquals(2, computedTemporaries.size());
        var resultTemporary = computedTemporaries.stream().filter(value -> value.type().isScalar()).findFirst().orElseThrow();
        assertSame(computed, resultTemporary.lifetime().sourceOwner());
        var operandTemporary = computedTemporaries.stream().filter(value -> value.type().isStruct()).findFirst().orElseThrow();
        assertEquals(TemporaryLifetime.Kind.FULL_EXPRESSION, operandTemporary.lifetime().kind());
        assertSame(computed.initializer(), operandTemporary.lifetime().sourceOwner());
        var dereferencedTemporary = singleTemporary(result.sourceToCore().get(viaDereference));
        assertEquals(TemporaryLifetime.Kind.FULL_EXPRESSION, dereferencedTemporary.lifetime().kind());
        assertSame(viaDereference.initializer(), dereferencedTemporary.lifetime().sourceOwner(),
                "Unary dereference after array decay does not extend the temporary owner");
        var subscriptTemporary = singleTemporary(result.sourceToCore().get(viaSubscript));
        assertEquals(TemporaryLifetime.Kind.REFERENCE_SCOPE, subscriptTemporary.lifetime().kind());
        assertSame(viaSubscript, subscriptTemporary.lifetime().sourceOwner());
        var addition = nodes.stream().filter(ExprStmt.class::isInstance).map(ExprStmt.class::cast).findFirst().orElseThrow();
        var arguments = temporaries(result.sourceToCore().get(addition));
        assertEquals(2, arguments.size());
        arguments.forEach(value -> {
            assertEquals(TemporaryLifetime.Kind.FULL_EXPRESSION, value.lifetime().kind());
            assertSame(addition.expression(), value.lifetime().sourceOwner());
        });
        var condition = nodes.stream().filter(IfStmt.class::isInstance).map(IfStmt.class::cast).findFirst().orElseThrow();
        var conditionTemporary = singleTemporary(result.sourceToCore().get(condition.condition()));
        assertEquals(TemporaryLifetime.Kind.FULL_EXPRESSION, conditionTemporary.lifetime().kind());
        assertSame(condition.condition(), conditionTemporary.lifetime().sourceOwner());
        assertEquals(direct.range(), result.sourceToCore().get(direct).range());
        assertEquals(member.initializer().range(), result.sourceToCore().get(member.initializer()).range());
    }

    private static VarDeclStmt variable(List<minic.compiler.parser.node.AstNode> nodes, String name) {
        return nodes.stream().filter(VarDeclStmt.class::isInstance).map(VarDeclStmt.class::cast)
                .filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<MaterializeExpr> temporaries(minic.compiler.parser.node.AstNode node) {
        return CppReferenceTest.nodes(node).stream().filter(MaterializeExpr.class::isInstance)
                .map(MaterializeExpr.class::cast).toList();
    }

    private static MaterializeExpr singleTemporary(minic.compiler.parser.node.AstNode node) {
        var values = temporaries(node);
        assertEquals(1, values.size());
        return values.getFirst();
    }
}
