package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.List;
import java.util.Objects;

/** C++ source closure expression. Capture storage is assigned during lexical binding. */
public record CppLambdaExpr(CaptureDefault captureDefault,List<Capture> captures,
                            List<Declaration.Parameter> parameters,boolean variadic,boolean mutable,
                            MiniType returnType,Statement.BlockStmt body,SourceRange range,
                            MiniType.ExceptionSpecification exceptionSpecification,boolean constexprSpecifier) implements Expression {
    public CppLambdaExpr(CaptureDefault captureDefault,List<Capture> captures,List<Declaration.Parameter> parameters,boolean variadic,boolean mutable,MiniType returnType,Statement.BlockStmt body,SourceRange range,MiniType.ExceptionSpecification exceptionSpecification){this(captureDefault,captures,parameters,variadic,mutable,returnType,body,range,exceptionSpecification,false);}
    public CppLambdaExpr(CaptureDefault captureDefault,List<Capture> captures,List<Declaration.Parameter> parameters,
                         boolean variadic,boolean mutable,MiniType returnType,Statement.BlockStmt body,SourceRange range) {
        this(captureDefault,captures,parameters,variadic,mutable,returnType,body,range,MiniType.ExceptionSpecification.UNSPECIFIED);
    }
    public enum CaptureDefault { NONE, COPY, REFERENCE }
    public enum CaptureKind { COPY, REFERENCE, THIS, THIS_COPY }
    public record Capture(String name,CaptureKind kind,CppInitializer initializer,SourceRange range) implements AstNode {
        public Capture {Objects.requireNonNull(name);Objects.requireNonNull(kind);Objects.requireNonNull(range);}
    }
    public CppLambdaExpr {
        Objects.requireNonNull(exceptionSpecification);Objects.requireNonNull(captureDefault);captures=List.copyOf(captures);parameters=List.copyOf(parameters);
        Objects.requireNonNull(returnType);Objects.requireNonNull(body);Objects.requireNonNull(range);
    }
}
