package minic.compiler.semantic.manager;

import minic.compiler.Diagnostic;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.model.*;
import minic.compiler.type.MiniType;
import java.util.*;

/** Read-only, unevaluated core expression validation for frontend immediate contexts. */
public final class ExpressionTypeProbe {
    private ExpressionTypeProbe() {}
    public record Result(MiniType type,List<Diagnostic> diagnostics) {
        public Result { diagnostics=List.copyOf(diagnostics); }
        public boolean valid() { return diagnostics.isEmpty(); }
    }
    /** Names/types and records must already use core representations; no definitions are required or executed. */
    public static Result analyze(Expression expression,Map<String,MiniType> symbols,List<StructDecl> records) {
        Objects.requireNonNull(expression);Objects.requireNonNull(symbols);Objects.requireNonNull(records);
        var diagnostics=new ArrayList<Diagnostic>();var scope=new Scope();
        var structs=new StructRegistry(scope,diagnostics);structs.defineStructs(new Program(records,List.of(),expression.range()));
        var functions=new FunctionRegistry(scope,diagnostics);var declarations=new ArrayList<FunctionDecl>();
        Set<String> names=new LinkedHashSet<>();var pending=new ArrayDeque<AstNode>();pending.add(expression);
        while(!pending.isEmpty()){var node=pending.removeFirst();if(node instanceof Expression.NameExpr name)names.add(name.name());pending.addAll(AstChildren.of(node));}
        for(String name:names) {
            MiniType type=symbols.get(name);if(type==null)continue;
            if(type.unqualified() instanceof MiniType.FunctionType function) {
                var parameters=new ArrayList<Parameter>();for(int i=0;i<function.parameterTypes().size();i++)parameters.add(new Parameter("__probe"+i,function.parameterTypes().get(i),expression.range()));
                declarations.add(new FunctionDecl(name,function.returnType(),parameters,function.variadic(),null,true,expression.range()));
            } else scope.define(new Symbol(name,Symbol.SymbolKind.VARIABLE,expression.range(),type,null));
        }
        functions.defineFunctions(new Program(List.of(),declarations,expression.range()));
        var analyzer=new ExpressionSemanticAnalyzer(functions,structs,diagnostics,new IdentityHashMap<>());
        MiniType type;
        try {type=analyzer.analyzeUnevaluated(expression,scope);}
        catch(IllegalArgumentException error) {
            diagnostics.add(new Diagnostic("SEM001",Diagnostic.Severity.ERROR,"Invalid unevaluated expression: "+error.getMessage(),expression.range()));
            type=MiniType.INT;
        }
        return new Result(type,diagnostics);
    }
}
