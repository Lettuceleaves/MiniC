package minic.cpp;

import minic.cpp.support.CppDifferentialHarness;

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
import java.util.stream.Stream;

/** F04b1: C++ lvalue results and sequencing, independent of temporary materialization. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppReferenceValueCategoryTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("conditional-selects-one-lvalue-and-preserves-alias", """
                        #include <stdio.h>
                        int calls = 0;
                        int &pick(int &value) { ++calls; return value; }
                        int main() {
                            int first = 1;
                            int second = 2;
                            int &chosen = 0 ? pick(first) : pick(second);
                            chosen = 9;
                            (1 ? first : second) = 7;
                            printf("%d %d %d %d\\n", first, second, calls, &chosen == &second);
                            return 0;
                        }
                        """, "7 9 1 1\n"),
                Arguments.of("assignment-result-is-the-left-object", """
                        #include <stdio.h>
                        int main() {
                            int values[2] = {1, 2};
                            int index = 0;
                            int &alias = (values[index++] = 4);
                            alias += 3;
                            (values[1] = 6) = 8;
                            printf("%d %d %d %d\\n", values[0], values[1], index, &alias == &values[0]);
                            return 0;
                        }
                        """, "7 8 1 1\n"),
                Arguments.of("prefix-and-compound-assignment-results-are-lvalues", """
                        #include <stdio.h>
                        int main() {
                            int values[2] = {1, 2};
                            int index = 0;
                            int &prefix = ++values[index++];
                            int &compound = (values[1] += 3);
                            prefix = 7;
                            compound = 8;
                            printf("%d %d %d %d\\n", values[0], values[1], index, &prefix == &values[0]);
                            return 0;
                        }
                        """, "7 8 1 1\n"),
                Arguments.of("comma-and-reference-return-remain-addressable", """
                        #include <stdio.h>
                        int &identity(int &value) { return value; }
                        int main() {
                            int first = 1;
                            int second = 2;
                            int &alias = (++first, second);
                            identity(alias) = 8;
                            int *address = &(first = 4);
                            ++*address;
                            printf("%d %d %d\\n", first, second, &identity(alias) == &second);
                            return 0;
                        }
                        """, "5 8 1\n"),
                Arguments.of("sizeof-reference-expressions-does-not-evaluate", """
                        #include <stdio.h>
                        int count = 0;
                        int &touch(int &value) { ++count; return value; }
                        int main() {
                            int value = 1;
                            int &alias = value;
                            int size = sizeof(++alias) + sizeof(touch(alias)) + sizeof(alias = 7);
                            printf("%d %d %d\\n", size, value, count);
                            return 0;
                        }
                        """, "12 1 0\n"),
                Arguments.of("assignment-right-side-is-sequenced-before-left-address", """
                        #include <stdio.h>
                        int trace=0;
                        int value=0;
                        int &left(){trace=trace*10+2;return value;}
                        int right(){trace=trace*10+1;return 4;}
                        int main(){
                            int &alias=(left()=right());
                            printf("%d %d %d\\n", trace, value, &alias==&value);
                            return 0;
                        }
                        """, "12 4 1\n"),
                Arguments.of("compound-right-side-before-left-with-one-address-evaluation", """
                        #include <stdio.h>
                        int trace=0;
                        int value=3;
                        int &left(){trace=trace*10+2;return value;}
                        int right(){trace=trace*10+1;return 4;}
                        int main(){
                            int result=(left()+=right());
                            printf("%d %d %d\\n", trace, value, result);
                            return 0;
                        }
                        """, "12 7 7\n"),
                Arguments.of("lvalue-normalization-remains-inside-short-circuit-paths", """
                        #include <stdio.h>
                        int main(){
                            int values[2]={1,2};
                            int index=0;
                            int unchanged=0 && (values[index++]+=4);
                            int &selected=1 ? values[0] : ++values[index++];
                            selected=7;
                            printf("%d %d %d %d\\n", values[0], values[1], index, unchanged);
                            return 0;
                        }
                        """, "7 2 0 0\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void lvalueResultsRetainIdentityAndEvaluationOrder(String name, String source, String expected) throws Exception {
        CppReferenceTest.agree(temporary, name, source, expected);
    }

    static Stream<Arguments> extendedPrograms() {
        return Stream.of(
                Arguments.of("scalar-assignment-results-use-the-stored-type", """
                        #include <stdio.h>
                        int main(){
                            unsigned char small=0;
                            int simple=(small=257);
                            int compound=(small+=257);
                            int whole=1;
                            int fraction=(whole+=1.75);
                            printf("%d %d %d %d %d\\n", simple,compound,small,whole,fraction);
                            return 0;
                        }
                        """, "1 2 2 2 2\n"),
                Arguments.of("assignment-captures-parameter-value-before-left-effects", """
                        #include <stdio.h>
                        int value=0;
                        int &target(int &parameter){parameter=9;return value;}
                        int run(int parameter){return target(parameter)=parameter;}
                        int main(){int result=run(4);printf("%d %d\\n",value,result);return 0;}
                        """, "4 4\n"),
                Arguments.of("array-and-function-lvalues-through-comma-and-conditional", """
                        #include <stdio.h>
                        int calls=0;
                        int bump(int &x){return ++x;}
                        int main(){
                            int a[3]={1,2,3}; int b[3]={4,5,6};
                            int (&row)[3]=(++calls,1?a:b);
                            int (&function)(int&)=(++calls,1?bump:bump);
                            row[1]=function(row[0]);
                            printf("%d %d %d %d %d\\n", a[0],a[1],calls,(int)sizeof((calls,a)),(int)sizeof(1?a:b));
                            return 0;
                        }
                        """, "2 2 2 12 12\n"),
                Arguments.of("aggregate-assignment-keeps-rhs-object-until-left-address", """
                        #include <stdio.h>
                        struct Item {int value;};
                        Item source={1}; Item destination={0};
                        Item &left(){source.value=7;return destination;}
                        int main(){Item &alias=(left()=source);printf("%d %d\\n",alias.value,&alias==&destination);return 0;}
                        """, "7 1\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("extendedPrograms")
    void lvalueConversionsAndIndirectCapturesRetainTheirSourceMeaning(String name, String source, String expected) throws Exception {
        CppReferenceTest.agree(temporary, name, source, expected);
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                Arguments.of("postfix-is-prvalue", "int main(){int x=1;int &r=x++;return 0;} // bad"),
                Arguments.of("mixed-conditional-is-prvalue", "int main(){int x=1;int &r=1?x:2;return 0;} // bad"),
                Arguments.of("const-conditional-cannot-be-mutated", "int main(){int x=1;const int y=2;(1?x:y)=3;return 0;} // bad"),
                Arguments.of("assignment-to-call-prvalue", "int get(){return 1;} int main(){get()=2;return 0;} // bad"),
                Arguments.of("comma-final-prvalue", "int main(){int x=1;(x,2)=3;return 0;} // bad"),
                Arguments.of("compound-array-rejected", "int main(){int a[2]={1,2};a+=1;return 0;} // bad"),
                Arguments.of("compound-result-cannot-implicitly-convert-pointer-to-int", "int main(){int a[2]={};int n=0;int*p=a;n+=p;return 0;} // bad"),
                Arguments.of("reference-to-invalid-compound-result", "int main(){int*p=0;int n=1;int&r=(n+=p);return r;} // bad"),
                Arguments.of("function-comma-has-no-object-layout", "int helper(){return 0;}int main(){return sizeof((0,helper));} // bad"),
                Arguments.of("assignment-does-not-enable-int-to-pointer", "int main(){int *p=0;p=7;return 0;} // bad"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void invalidResultsCannotAcquireLvalueIdentity(String name, String source) throws Exception {
        CppReferenceTest.reject(temporary, name, source);
    }

    @Test void conditionalArrayLvaluesCombineElementQualifiersWithoutDecay() throws Exception {
        CppReferenceTest.agree(temporary, "cv-array-conditional", """
                #include <stdio.h>
                int main(){
                    int a[2]={1,2};const int b[2]={4,5};
                    const int (&row)[2]=1?a:b;
                    a[0]=7;
                    printf("%d %d %d\\n",row[0],(int)sizeof(row),(void*)&row==(void*)&a);
                    return 0;
                }
                """, "7 8 1\n");
    }

    @Test void cCompoundAssignmentRetainsUsualArithmeticTypeBeforeStoreConversion() throws Exception {
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), minic.compiler.LanguageMode.C).run("c-compound-types", """
                #include <stdio.h>
                int main(){int value=1;int result=(value+=1.75);unsigned char small=255;int narrowed=(small+=3);
                printf("%d %d %d %d\\n",value,result,small,narrowed);return 0;}
                """, "");
        org.junit.jupiter.api.Assertions.assertTrue(report.passed(), report::describe);
        org.junit.jupiter.api.Assertions.assertEquals("2 2 2 2\n", report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n", "\n"));
    }

    static Stream<Arguments> volatileConditionals() {
        return Stream.of(Arguments.of("1?(x=3):(y=4)", 0L), Arguments.of("1?++x:++y", 2L),
                Arguments.of("1?(x+=3):(y+=4)", 2L));
    }

    @ParameterizedTest @MethodSource("volatileConditionals")
    void valueConditionalDoesNotReadTheUpdatedVolatileObjectAgain(String expression, long expectedReads) {
        var ir = CppReferenceTest.compiler("int main(){volatile int x=1;volatile int y=2;return " + expression + ";}").runToIr();
        var instructions = ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream()).toList();
        org.junit.jupiter.api.Assertions.assertEquals(expectedReads, instructions.stream().filter(instruction ->
                instruction instanceof minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction load && load.volatileAccess()
                        || instruction instanceof minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction local && local.volatileAccess()).count());
    }

    @Test void valueConditionalRetainsNarrowLvalueTypeInUnevaluatedQueries() throws Exception {
        CppReferenceTest.agree(temporary, "conditional-lvalue-query-type", """
                #include <stdio.h>
                int main(){char a=1;char b=2;bool x=true;bool y=false;
                printf("%d %d %d\\n",(int)sizeof(1?a:b),(int)sizeof(1?x:y),a);return 0;}
                """, "1 1 1\n");
    }

    @Test void cppConditionalElseArmIncludesAnUnparenthesizedAssignment() throws Exception {
        CppReferenceTest.agree(temporary, "conditional-else-assignment-grammar", """
                #include <stdio.h>
                int main(){int first=1;int second=2;int result=1?first:second=7;
                printf("%d %d %d\\n",first,second,result);return 0;}
                """, "1 2 1\n");
        var c = new minic.compiler.CompilerApi(new minic.compiler.SourceFile("conditional.c",
                "int main(){int first=1;int second=2;int result=1?first:second=7;return result;}"),
                minic.compiler.LanguageMode.C);
        var parser = CppReferenceTest.stage(c, minic.compiler.parser.Parser.class);
        c.runThrough(parser);
        org.junit.jupiter.api.Assertions.assertFalse(parser.succeeded(), "C keeps its conditional-expression else grammar");
    }

    @Test void capturesPreserveSourceRangesAndStayOutOfSourceScopesAndRecordedActions() {
        var api = CppReferenceTest.compiler("""
                int main(){
                    int value=1;
                    int &alias=(value+=2);
                    ++alias;
                    value=7;
                    return value;
                }
                """);
        var semantic = CppReferenceTest.stage(api, minic.compiler.semantic.SemanticAnalyzer.class);
        api.setResultRecording(semantic, true);
        api.runThrough(semantic);
        org.junit.jupiter.api.Assertions.assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var result = semantic.semanticResult();
        var sourceNodes = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<minic.compiler.parser.node.AstNode, Boolean>());
        sourceNodes.addAll(CppReferenceTest.nodes(result.sourceProgram()));
        var assignment = sourceNodes.stream().filter(minic.compiler.parser.node.Expression.AssignmentExpr.class::isInstance)
                .map(minic.compiler.parser.node.Expression.AssignmentExpr.class::cast).findFirst().orElseThrow();
        var normalized = result.sourceToCore().get(assignment);
        org.junit.jupiter.api.Assertions.assertEquals(assignment.range(), normalized.range());
        org.junit.jupiter.api.Assertions.assertEquals(minic.compiler.type.MiniType.INT, result.typeOf(assignment).orElseThrow());
        org.junit.jupiter.api.Assertions.assertTrue(CppReferenceTest.nodes(normalized).stream()
                .anyMatch(minic.compiler.parser.node.Expression.LetExpr.class::isInstance));
        org.junit.jupiter.api.Assertions.assertFalse(result.scopeSnapshot().toString().contains("<expression value>"));
        result.sourceToCore().forEach((original, core) -> {
            if (original instanceof minic.compiler.parser.node.Expression) {
                org.junit.jupiter.api.Assertions.assertEquals(original.range(), core.range(),
                        () -> "Normalization changed the source range of " + original);
            }
        });
        semantic.stepResults().stream().flatMap(step -> step.contextAs(minic.compiler.semantic.SemanticResult.class).stream())
                .flatMap(context -> context.actionOptional().stream())
                .filter(action -> action.astNode() instanceof minic.compiler.parser.node.AstNode)
                .forEach(action -> org.junit.jupiter.api.Assertions.assertTrue(sourceNodes.contains(action.astNode()),
                        () -> "Generated capture node exposed as a source action: " + action.astNode()));
    }

    @Test void capturedLvaluesSupportDebugHistoryWithoutHeapOrSyntheticUserVariables() {
        CppReferenceTest.assertDebugHistory("""
                #include <stdio.h>
                int update(int &value){int &alias=(value+=2);++alias;return alias;}
                int main(){int value=1;printf("%d\\n",update(value));return 0;}
                """, "4\n", "update", "value");
    }

    @Test void prefixReferenceBindingUsesOneVolatileReadAndNoCaptureStackObjects() {
        var ir = CppReferenceTest.compiler("int main(){volatile int value=1; volatile int &alias=++value;return 0;}")
                .runToIr();
        var instructions = ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream()).toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, instructions.stream().filter(instruction ->
                instruction instanceof minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction load
                        && load.volatileAccess()
                || instruction instanceof minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction localLoad
                        && localLoad.volatileAccess()).count());
        org.junit.jupiter.api.Assertions.assertEquals(2, instructions.stream().filter(
                minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction.class::isInstance).count(),
                "Only the source object and its reference slot need stack storage; captures are IR values");
        org.junit.jupiter.api.Assertions.assertTrue(ir.externalFunctionNames().isEmpty());
    }
}
