package minic.compiler.parser;

import minic.compiler.lexer.Token;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;

final class ParserTrace {
    private final ArrayList<ParserTraceEvent> events = new ArrayList<>();

    void enter(String rule, SourceRange range) {
        events.add(new ParserTraceEvent("enter", "enter " + rule, range, null));
    }

    void exit(String rule, SourceRange range) {
        events.add(new ParserTraceEvent("exit", "exit " + rule, range, null));
    }

    void consume(Token token) {
        events.add(new ParserTraceEvent("consume", "consume " + token.kind() + " " + token.lexeme(), token.range(), null));
    }

    void build(Object node, String label, SourceRange range) {
        events.add(new ParserTraceEvent("build", "build " + label, range, node));
    }

    List<ParserTraceEvent> events() {
        return List.copyOf(events);
    }
}
