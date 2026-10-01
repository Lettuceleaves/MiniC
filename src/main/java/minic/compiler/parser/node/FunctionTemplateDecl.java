package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.TemplateArgument;
import java.util.List;
import java.util.Objects;

/** A primary function template or an explicit specialization, before core binding. */
public record FunctionTemplateDecl(List<ClassTemplateDecl.Parameter> parameters, Declaration.FunctionDecl function,
        List<TemplateArgument> specializationArguments, QualifiedName qualifiedName, SourceRange range) implements Declaration {
    public FunctionTemplateDecl(List<ClassTemplateDecl.Parameter> parameters,Declaration.FunctionDecl function,SourceRange range) {
        this(parameters,function,null,null,range);
    }
    public FunctionTemplateDecl {
        parameters=List.copyOf(parameters);Objects.requireNonNull(function);Objects.requireNonNull(range);
        specializationArguments=specializationArguments==null?null:List.copyOf(specializationArguments);
    }
    public boolean specialization(){return parameters.isEmpty();}
}
