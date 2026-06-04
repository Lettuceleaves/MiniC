package minic.runtime.debug.dataflow;

import minic.compiler.ast.decl.Program;
import minic.compiler.ir.lowering.IrLowerer;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.runtime.debug.DebugExecutionState;
import minic.runtime.debug.DebugSession;
import minic.runtime.debug.IrDebugInterpreter;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataFlowEventTest {
    @Test
    void recordsLocalWritesWithOldAndNewValues() {
        DebugSession session = run("""
                int main() {
                    int x = 1;
                    x = 2;
                    return x;
                }
                """);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(events(session, DataFlowEventType.WRITE_LOCAL, "x"))
                .anySatisfy(event -> {
                    assertThat(event.oldValue()).isEqualTo("<uninitialized>");
                    assertThat(event.newValue()).isEqualTo("1");
                    assertThat(event.cExpression()).contains("x");
                })
                .anySatisfy(event -> {
                    assertThat(event.oldValue()).isEqualTo("1");
                    assertThat(event.newValue()).isEqualTo("2");
                    assertThat(event.cExpression()).contains("x");
                });
    }

    @Test
    void recordsPointerRetargetsWithTargetAddress() {
        DebugSession session = run("""
                int main() {
                    int x = 1;
                    int y = 2;
                    int *p = &x;
                    p = &y;
                    return *p;
                }
                """);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(events(session, DataFlowEventType.POINTER_RETARGET, "p"))
                .hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(event -> {
                    assertThat(event.newValue()).startsWith("stack:0x");
                    assertThat(event.address()).startsWith("stack:0x");
                    assertThat(event.pointerTarget()).isEqualTo(event.newValue());
                    assertThat(event.cExpression()).contains("p");
                })
                .anySatisfy(event -> assertThat(event.oldValue()).isEqualTo("<uninitialized>"))
                .anySatisfy(event -> assertThat(event.oldValue()).startsWith("stack:0x"));
    }

    @Test
    void recordsFieldWritesIncludingStructArrayElements() {
        DebugSession session = run("""
                struct Point {
                    int value;
                    int x;
                };

                int main() {
                    struct Point a;
                    struct Point arr[2];
                    a.value = 1;
                    arr[1].x = 30;
                    return a.value + arr[1].x;
                }
                """);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(events(session, DataFlowEventType.FIELD_WRITE, "a.value"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.oldValue()).isEqualTo("<uninitialized>");
                    assertThat(event.newValue()).isEqualTo("1");
                    assertThat(event.cExpression()).contains("value");
                });
        assertThat(events(session, DataFlowEventType.FIELD_WRITE, "arr[1].x"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.oldValue()).isEqualTo("<uninitialized>");
                    assertThat(event.newValue()).isEqualTo("30");
                    assertThat(event.address()).startsWith("stack:0x");
                });
    }

    @Test
    void recordsArrayElementWrites() {
        DebugSession session = run("""
                int main() {
                    int arr[3];
                    arr[1] = 20;
                    return arr[1];
                }
                """);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(events(session, DataFlowEventType.ARRAY_ELEMENT_WRITE, "arr[1]"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.oldValue()).isEqualTo("<uninitialized>");
                    assertThat(event.newValue()).isEqualTo("20");
                    assertThat(event.cExpression()).contains("arr");
                });
    }

    @Test
    void recordsAddressCalculationAndPointerLoadEvents() {
        DebugSession session = run("""
                struct Point {
                    int x;
                };

                int main() {
                    int x = 1;
                    int *p = &x;
                    struct Point point;
                    int arr[2];
                    point.x = *p;
                    arr[1] = point.x;
                    return arr[1];
                }
                """);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.dataFlowEvents()).extracting(DataFlowEvent::type)
                .contains(
                        DataFlowEventType.DECLARE_LOCAL,
                        DataFlowEventType.ADDRESS_OF_LOCAL,
                        DataFlowEventType.FIELD_ADDRESS,
                        DataFlowEventType.ELEMENT_ADDRESS,
                        DataFlowEventType.LOAD_POINTER
                );
        assertThat(session.dataFlowEvents())
                .filteredOn(event -> event.type() == DataFlowEventType.FIELD_ADDRESS)
                .anySatisfy(event -> {
                    assertThat(event.lvaluePath()).isEqualTo("point.x");
                    assertThat(event.newValue()).startsWith("stack:0x");
                    assertThat(event.pointerTarget()).isEqualTo(event.newValue());
                });
        assertThat(session.dataFlowEvents())
                .filteredOn(event -> event.type() == DataFlowEventType.ELEMENT_ADDRESS)
                .anySatisfy(event -> {
                    assertThat(event.lvaluePath()).isEqualTo("arr[1]");
                    assertThat(event.newValue()).startsWith("stack:0x");
                });
        assertThat(session.dataFlowEvents())
                .filteredOn(event -> event.type() == DataFlowEventType.LOAD_POINTER)
                .anySatisfy(event -> {
                    assertThat(event.lvaluePath()).contains("x");
                    assertThat(event.newValue()).isEqualTo("1");
                });
    }

    @Test
    void recordsStructCopyMemoryWrites() {
        DebugSession session = run("""
                struct Point {
                    int x;
                    int y;
                };

                int main() {
                    struct Point point;
                    struct Point copy;
                    point.x = 1;
                    point.y = 2;
                    copy.x = 0;
                    copy.y = 0;
                    copy = point;
                    return copy.x + copy.y;
                }
                """);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(events(session, DataFlowEventType.STORE_POINTER, "copy.x"))
                .anySatisfy(event -> {
                    assertThat(event.oldValue()).isEqualTo("0");
                    assertThat(event.newValue()).isEqualTo("1");
                });
        assertThat(events(session, DataFlowEventType.STORE_POINTER, "copy.y"))
                .anySatisfy(event -> {
                    assertThat(event.oldValue()).isEqualTo("0");
                    assertThat(event.newValue()).isEqualTo("2");
                });
    }

    @Test
    void recordsArrowFieldWritesWithPathFallback() {
        DebugSession session = run("""
                struct Point {
                    int x;
                };

                int main() {
                    struct Point point;
                    struct Point *pp = &point;
                    pp->x = 20;
                    return point.x;
                }
                """);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.dataFlowEvents())
                .filteredOn(event -> event.type() == DataFlowEventType.FIELD_WRITE)
                .anySatisfy(event -> {
                    assertThat(event.lvaluePath()).contains("x");
                    assertThat(event.oldValue()).isEqualTo("<uninitialized>");
                    assertThat(event.newValue()).isEqualTo("20");
                    assertThat(event.address()).startsWith("stack:0x");
                });
    }

    @Test
    void returnsDefensiveCopyOfDataFlowEvents() {
        DebugSession session = run("""
                int main() {
                    int x = 1;
                    return x;
                }
                """);

        assertThatThrownBy(() -> session.dataFlowEvents().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(session.dataFlowEvents()).isNotEmpty();
    }

    private java.util.List<DataFlowEvent> events(
            DebugSession session,
            DataFlowEventType type,
            String lvaluePath
    ) {
        return session.dataFlowEvents().stream()
                .filter(event -> event.type() == type)
                .filter(event -> event.lvaluePath().equals(lvaluePath))
                .toList();
    }

    private DebugSession run(String source) {
        SourceFile sourceFile = new SourceFile("data-flow.mc", source);
        return new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
    }

    private IrModule lower(SourceFile sourceFile) {
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        Program program = parseResult.program();
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).as(semanticResult.diagnostics().toString()).isEmpty();
        return new IrLowerer().lower(program, semanticResult);
    }
}
