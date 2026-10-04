package craken.compiler.semantic.manager;

import craken.compiler.parser.node.*;
import craken.compiler.type.*;
import craken.compiler.parser.node.Expression.PackExpansionExpr;
import craken.compiler.parser.node.Expression.SizeofPackExpr;
import craken.compiler.parser.node.Expression.TypeQueryExpr;

import java.lang.reflect.*;
import java.util.*;

/** Structural enumeration of unexpanded packs; nested expansions own their own pattern. */
final class TemplatePacks {
    private TemplatePacks() {}
    static Set<CrakenType.TemplateParameterType> parameters(Object pattern) {
        Set<CrakenType.TemplateParameterType> result=new LinkedHashSet<>();
        visit(pattern,result,new LinkedHashSet<>()); return result;
    }
    static Set<String> names(Object pattern) {
        Set<String> result=new LinkedHashSet<>();visit(pattern,new LinkedHashSet<>(),result);return result;
    }
    private static void visit(Object value,Set<CrakenType.TemplateParameterType> types,Set<String> names) {
        if(value==null || value instanceof PackExpansionExpr || value instanceof SizeofPackExpr
                || value instanceof CrakenType.PackExpansionType || value instanceof TemplateArgument.Expansion
                || value instanceof TypeQueryExpr.TypeArgument argument && argument.packExpansion())return;
        if(value instanceof CrakenType.TemplateParameterType parameter){types.add(parameter);return;}
        if(value instanceof Expression.NameExpr name){names.add(name.name());return;}
        if(value instanceof List<?> list){list.forEach(item->visit(item,types,names));return;}
        Class<?> kind=value.getClass();
        if(!(kind.isRecord() || value instanceof AbstractAstNode) || !(kind.getPackageName().equals("craken.compiler.parser.node") || value instanceof CrakenType
                || value instanceof CrakenType.ExceptionSpecification || value instanceof TemplateArgument))return;
        try {for(var component:AstNodeComponents.describe(kind).components())visit(component.accessor().invoke(value),types,names);}
        catch(ReflectiveOperationException error){throw new IllegalArgumentException("Cannot inspect template pattern",error);}
    }
}
