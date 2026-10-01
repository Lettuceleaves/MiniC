package minic.compiler.semantic.cpp;

import minic.compiler.parser.node.*;
import minic.compiler.type.*;
import java.lang.reflect.*;
import java.util.*;

/** Structural enumeration of unexpanded packs; nested expansions own their own pattern. */
final class CppTemplatePacks {
    private CppTemplatePacks() {}
    static Set<MiniType.TemplateParameterType> parameters(Object pattern) {
        Set<MiniType.TemplateParameterType> result=new LinkedHashSet<>();
        visit(pattern,result,new LinkedHashSet<>()); return result;
    }
    static Set<String> names(Object pattern) {
        Set<String> result=new LinkedHashSet<>();visit(pattern,new LinkedHashSet<>(),result);return result;
    }
    private static void visit(Object value,Set<MiniType.TemplateParameterType> types,Set<String> names) {
        if(value==null || value instanceof CppPackExpansionExpr || value instanceof CppSizeofPackExpr
                || value instanceof MiniType.PackExpansionType || value instanceof TemplateArgument.Expansion
                || value instanceof CppTypeQueryExpr.TypeArgument argument && argument.packExpansion())return;
        if(value instanceof MiniType.TemplateParameterType parameter){types.add(parameter);return;}
        if(value instanceof Expression.NameExpr name){names.add(name.name());return;}
        if(value instanceof List<?> list){list.forEach(item->visit(item,types,names));return;}
        Class<?> kind=value.getClass();
        if(!kind.isRecord() || !(kind.getPackageName().equals("minic.compiler.parser.node") || value instanceof MiniType
                || value instanceof MiniType.ExceptionSpecification || value instanceof TemplateArgument))return;
        try {for(var component:kind.getRecordComponents())visit(component.getAccessor().invoke(value),types,names);}
        catch(ReflectiveOperationException error){throw new IllegalArgumentException("Cannot inspect template pattern",error);}
    }
}
