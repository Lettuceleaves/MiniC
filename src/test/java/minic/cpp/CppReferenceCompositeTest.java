package minic.cpp;

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

/** Original F04a array/function cases kept separately so earlier lvalue ABI commits can be green. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppReferenceCompositeTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("array-reference-parameter-return-and-size", """
                        #include <stdio.h>
                        int (&identity(int (&values)[3]))[3] { return values; }
                        void change(int (&values)[3]) { values[1] = 9; }
                        int main() {
                            int values[3] = {1, 2, 3};
                            int (&alias)[3] = identity(values);
                            const int (&view)[3] = alias;
                            change(alias);
                            printf("%d %d %d %d\\n", values[1], view[2], (int)sizeof(alias), &alias == &values);
                            return 0;
                        }
                        """, "9 3 12 1\n"),
                Arguments.of("function-reference-local-parameter-return", """
                        #include <stdio.h>
                        int plus(int value) { return value + 2; }
                        int call(int (&operation)(int), int value) { return operation(value); }
                        int (&choose())(int) { return plus; }
                        int main() {
                            int (&operation)(int) = choose();
                            printf("%d %d %d\\n", operation(3), call(operation, 5), &operation == &plus);
                            return 0;
                        }
                        """, "5 7 1\n"),
                Arguments.of("multidimensional-array-reference-cv-and-address", """
                        #include <stdio.h>
                        void change(int (&rows)[2][3]) { rows[1][2] = 9; }
                        int main() {
                            int rows[2][3] = {{1,2,3}, {4,5,6}};
                            const int (&view)[2][3] = rows;
                            volatile int cell[2] = {7,8};
                            const volatile int (&observed)[2] = cell;
                            change(rows);
                            printf("%d %d %d %d\\n", view[1][2], (int)sizeof(view), observed[1], &view == &rows);
                            return 0;
                        }
                        """, "9 24 8 1\n"),
                Arguments.of("function-reference-value-contexts-and-nested-signature", """
                        #include <stdio.h>
                        int &bump(int &value) { value += 2; return value; }
                        typedef int &Function(int &);
                        Function &choose() { return bump; }
                        int main() {
                            int value = 1;
                            Function &operation = choose();
                            Function *pointer = operation;
                            int &alias = (***operation)(value);
                            alias += 4;
                            printf("%d %d %d %d\\n", value, pointer == operation, operation != 0, (int)sizeof(&operation));
                            return 0;
                        }
                        """, "7 1 1 8\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void compositeReferencesAgreeAcrossAllBackends(String name, String source, String expected) throws Exception {
        CppReferenceTest.agree(temporary, name, source, expected);
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                Arguments.of("array-element-const-discard", "int main(){const int values[2]={1,2}; int (&alias)[2]=values; // bad\n return 0;}"),
                Arguments.of("nested-array-bound-mismatch", "int main(){int values[2][3]={{1,2,3},{4,5,6}}; int (&alias)[2][2]=values; // bad\n return 0;}"),
                Arguments.of("function-pointer-is-not-function", "int target(int x){return x;} int main(){int (*pointer)(int)=target; int (&alias)(int)=pointer; // bad\n return 0;}"),
                Arguments.of("function-reference-assignment", "int target(int x){return x;} int other(int x){return x+1;} int main(){int (&alias)(int)=target; alias=other; // bad\n return 0;}"),
                Arguments.of("function-reference-sizeof", "int target(int x){return x;} int main(){int (&alias)(int)=target; return sizeof(alias); // bad\n }"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void invalidCompositeReferencesKeepSourceDiagnostics(String name, String source) throws Exception {
        CppReferenceTest.reject(temporary, name, source);
    }

    @Test void compositeReferenceExpressionsRetainUndecayedSourceTypes() {
        var api = CppReferenceTest.compiler("""
                int identity(int value){return value;}
                int main(){int items[2]={1,2}; int (&array)[2]=items; int (&function)(int)=identity;
                    return sizeof(array) + function(array[0]);}
                """);
        var semantic = CppReferenceTest.stage(api, minic.compiler.semantic.SemanticAnalyzer.class);
        api.runThrough(semantic);
        org.junit.jupiter.api.Assertions.assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var source = semantic.semanticResult().sourceProgram();
        for (var node : CppReferenceTest.nodes(source)) {
            if (node instanceof minic.compiler.parser.node.Expression.NameExpr name) {
                var type = semantic.semanticResult().typeOf(name).orElseThrow();
                if (name.name().equals("array")) org.junit.jupiter.api.Assertions.assertTrue(type.isArray(), type::toString);
                if (name.name().equals("function")) org.junit.jupiter.api.Assertions.assertTrue(type.isFunction(), type::toString);
            }
        }
    }

    @Test void arrayReferenceCvIncludesQualificationsInheritedFromItsOwningObject() throws Exception {
        CppReferenceTest.agree(temporary, "qualified-array-field-reference", """
                #include <stdio.h>
                struct Row { int values[3]; };
                const int (&read(const Row &row))[3] { return row.values; }
                int main(){
                    const Row row={{2,4,6}};
                    const int (&view)[3]=row.values;
                    const int (&returned)[3]=read(row);
                    printf("%d %d %d\\n", view[1], returned[2], (int)sizeof(view));
                    return 0;
                }
                """, "4 6 12\n");
    }
}
