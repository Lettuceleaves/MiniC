package minic.compiler.semantic.manager;

import minic.SourceRange;
import minic.compiler.Diagnostic;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Expression.InitializerSyntax;
import minic.compiler.parser.node.Declaration.ConstructorMember;
import minic.compiler.parser.node.Declaration.FieldMember;
import minic.compiler.parser.node.Declaration.MemberInitializer;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Declaration.StructField;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Selects one source initializer per direct member, in layout declaration order.
 * Name/type binding, constructor selection and conversion checks belong to the caller.
 * In particular the caller must resolve type aliases before identifying delegation/base targets.
 */
public final class MemberInitializationPlan {
    private MemberInitializationPlan() {}

    public enum Origin { EXPLICIT, DEFAULT_MEMBER, DEFAULT }

    public record Entry(StructField field, InitializerSyntax initializer, Origin origin, AstNode source) {
        public Entry {
            Objects.requireNonNull(field,"field");
            Objects.requireNonNull(initializer,"initializer");
            Objects.requireNonNull(origin,"origin");
            Objects.requireNonNull(source,"source");
        }
    }

    /** An invalid plan has no executable entries; source objects retain their identities. */
    public record Result(List<Entry> entries,List<Diagnostic> diagnostics) {
        public Result {
            entries=List.copyOf(entries);
            diagnostics=List.copyOf(diagnostics);
            if (!diagnostics.isEmpty() && !entries.isEmpty()) {
                throw new IllegalArgumentException("An invalid member initialization plan cannot contain executable entries");
            }
        }
    }

    public static Result plan(StructDecl record,ConstructorMember constructor) {
        Objects.requireNonNull(record,"record");
        Objects.requireNonNull(constructor,"constructor");
        var diagnostics=new ArrayList<Diagnostic>();
        if (!record.definition()) error(diagnostics,"SEM003","Member initialization requires a complete class definition",record.range());
        if (constructor.body()==null) error(diagnostics,"SEM003","A constructor declaration has no member initialization execution plan",constructor.range());
        if (record.union()) error(diagnostics,"SEM004","Union construction is not supported yet",record.range());

        var fields=new LinkedHashMap<String,StructField>();
        for (var field:record.fields()) {
            if (field.anonymous()) {
                error(diagnostics,"SEM004","Construction of anonymous aggregate members is not supported yet",field.range());
            } else if (fields.putIfAbsent(field.name(),field)!=null) {
                error(diagnostics,"SEM003","Duplicate data member '"+field.name()+"'",field.range());
            }
        }
        Map<StructField,FieldMember> defaultMembers=new IdentityHashMap<>();
        if (record.recordInfo()!=null) {
            for (var member:record.recordInfo().members()) {
                if (member instanceof FieldMember field && field.defaultInitializer()!=null) {
                    defaultMembers.put(field.field(),field);
                }
            }
        }

        var explicit=new LinkedHashMap<String,MemberInitializer>();
        for (var initializer:constructor.initializers()) {
            var target=initializer.target();
            if (target.global() || target.segments().size()!=1) {
                error(diagnostics,"SEM004","Qualified/base member initialization is not supported yet",target.range());
                continue;
            }
            String name=target.segments().getFirst();
            if (name.equals(constructor.name())) {
                error(diagnostics,"SEM004","Delegating constructors are not supported yet",target.range());
            } else if (!fields.containsKey(name)) {
                error(diagnostics,"SEM003","Unknown direct data member '"+name+"'",target.range());
            } else if (explicit.putIfAbsent(name,initializer)!=null) {
                error(diagnostics,"SEM003","Data member '"+name+"' is initialized more than once",target.range());
            }
        }
        if (!diagnostics.isEmpty()) return new Result(List.of(),diagnostics);

        var entries=new ArrayList<Entry>();
        for (var field:record.fields()) {
            var written=explicit.get(field.name());
            var defaultMember=defaultMembers.get(field);
            if (written!=null) {
                entries.add(new Entry(field,written.initializer(),Origin.EXPLICIT,written));
            } else if (defaultMember!=null) {
                entries.add(new Entry(field,defaultMember.defaultInitializer(),Origin.DEFAULT_MEMBER,defaultMember));
            } else {
                entries.add(new Entry(field,new InitializerSyntax(InitializerSyntax.Kind.DEFAULT,List.of(),field.range()),Origin.DEFAULT,field));
            }
        }
        return new Result(entries,List.of());
    }

    private static void error(List<Diagnostic> diagnostics,String code,String message,SourceRange range) {
        diagnostics.add(new Diagnostic(code,Diagnostic.Severity.ERROR,message,range));
    }
}
