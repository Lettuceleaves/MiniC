package minic.compiler.parser;

import minic.SourceRange;
import minic.compiler.Diagnostic;
import minic.compiler.parser.node.QualifiedName;
import minic.compiler.type.MiniType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Declaration-point C++ name classification for the parser. Namespace scopes persist
 * across reopenings; local scopes (including prototype parameters) are discarded on exit.
 * This environment does not validate value signatures or generate linker names.
 * Lookup never reports diagnostics, making it safe for declaration/expression lookahead.
 */
public final class CppTypeEnvironment {
    public enum Kind { TYPE, VALUE, NAMESPACE, MISSING, AMBIGUOUS, UNSUPPORTED_QUALIFIER }
    public record Lookup(Kind kind, MiniType type, String canonicalName) {}
    private enum Search { ORDINARY, QUALIFIER, ELABORATED }

    private static final class Entry {
        final Kind kind;
        final String canonicalName;
        final MiniType type;
        final Scope owner;
        final Namespace namespace;
        final boolean tag;
        final boolean union;
        boolean defined;

        Entry(Kind kind, String canonicalName, MiniType type, Scope owner, Namespace namespace,
              boolean tag, boolean union, boolean defined) {
            this.kind = kind;
            this.canonicalName = canonicalName;
            this.type = type;
            this.owner = owner;
            this.namespace = namespace;
            this.tag = tag;
            this.union = union;
            this.defined = defined;
        }
    }

    /** C++ permits an aggregate tag and a value with the same name in one scope. */
    private static final class Slot {
        Entry tag;
        Entry ordinary;
    }

    private static class Scope {
        final String canonicalName;
        final Map<String, Slot> names = new LinkedHashMap<>();
        final List<Namespace> directives = new ArrayList<>();
        Scope(String canonicalName) { this.canonicalName = canonicalName; }
        String qualify(String name) { return canonicalName + "::" + name; }
    }

    private static final class Namespace extends Scope {
        final Namespace parent;
        final Map<String, Namespace> children = new LinkedHashMap<>();
        Namespace(Namespace parent, String name) {
            super(parent == null ? "" : parent.qualify(name));
            this.parent = parent;
        }
    }

    private static final class Local extends Scope {
        final Local parent;
        final Namespace namespace;
        final boolean member;
        boolean template;
        Local(Local parent, Namespace namespace, int id, boolean member) {
            super(namespace.qualify((member ? "<member" : "<block") + id + ">"));
            this.parent = parent;
            this.namespace = namespace;
            this.member = member;
        }
    }

    private final Namespace root = new Namespace(null, "");
    private final Deque<Namespace> namespaceStack = new ArrayDeque<>();
    private record DefinitionContext(Namespace namespace, Local local) {}
    private final Deque<DefinitionContext> definitionScopes = new ArrayDeque<>();
    private final Map<String, Namespace> classOwners = new LinkedHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private Namespace namespace = root;
    private Local local;
    private int nextLocalId;

    /** Enters a namespace definition, not a using directive or namespace alias. */
    public void enterNamespace(List<String> path, SourceRange range) {
        Objects.requireNonNull(range, "range");
        if (local != null) throw new IllegalStateException("namespace definition inside a local scope");
        if (path.isEmpty()) throw new IllegalArgumentException("namespace path must not be empty");
        path.forEach(CppTypeEnvironment::requireName);
        namespaceStack.push(namespace);
        for (String name : path) {
            Namespace parent = namespace;
            Slot slot = parent.names.computeIfAbsent(name, ignored -> new Slot());
            Namespace child = parent.children.computeIfAbsent(name, ignored -> new Namespace(parent, name));
            if (slot.tag != null || slot.ordinary != null
                    && (slot.ordinary.kind != Kind.NAMESPACE || slot.ordinary.namespace != child)) {
                conflict(range, "命名空间与已有声明冲突：" + name);
            } else if (slot.ordinary == null) {
                slot.ordinary = new Entry(Kind.NAMESPACE, child.canonicalName, null, parent, child, false, false, true);
            }
            // Keep recovery balanced even when the declaration has already been diagnosed.
            namespace = child;
        }
    }

    public void exitNamespace() {
        if (local != null || namespaceStack.isEmpty()) throw new IllegalStateException("no namespace scope to exit");
        namespace = namespaceStack.pop();
    }

    public void enterLocalScope() { local = new Local(local, namespace, ++nextLocalId, false); }

    /** Template parameters participate in lookup without giving the declared class a block identity. */
    public void enterTemplateScope() {
        local = new Local(local, namespace, ++nextLocalId, false);
        local.template = true;
    }

    public void exitTemplateScope() {
        if (local == null || !local.template) throw new IllegalStateException("no template scope to exit");
        local = local.parent;
    }

    public boolean isTemplateParameter(String name) {
        for (Local scope = local; scope != null; scope = scope.parent) {
            if (scope.template && scope.names.containsKey(name)) return true;
        }
        return false;
    }

    /** Member names have lexical scope; this slice still hoists anonymous aggregate ASTs. */
    public void enterMemberScope() { enterMemberScope(null); }

    /** The injected class name is a tag, allowing a later value member to hide it. */
    public void enterMemberScope(MiniType selfType) {
        if (selfType != null && !(selfType.unqualified() instanceof MiniType.StructType)) {
            throw new IllegalArgumentException("member scope requires an aggregate self type");
        }
        local = new Local(local, namespace, ++nextLocalId, true);
        if (selfType != null) {
            String identity = ((MiniType.StructType) selfType.unqualified()).name();
            boolean union = identity.startsWith("$union$");
            String canonicalName = union ? identity.substring("$union$".length()) : identity;
            int separator = canonicalName.lastIndexOf("::");
            String simpleName = separator < 0 ? canonicalName : canonicalName.substring(separator + 2);
            Slot slot = new Slot();
            slot.tag = new Entry(Kind.TYPE, canonicalName, selfType.unqualified(), local, null, true, union, true);
            local.names.put(simpleName, slot);
        }
    }

    public void exitMemberScope() {
        if (local == null || !local.member) throw new IllegalStateException("no member scope to exit");
        local = local.parent;
    }

    /** Reenters an existing owner's namespace; no namespace or class is created during lookup. */
    public MiniType enterMemberDefinitionScope(QualifiedName owner) {
        Lookup found = classify(resolve(owner, Search.QUALIFIER));
        MiniType type = found.kind == Kind.TYPE && found.type.unqualified() instanceof MiniType.StructType
                ? found.type.unqualified() : null;
        Namespace target = type instanceof MiniType.StructType record ? classOwners.get(record.name()) : null;
        definitionScopes.push(new DefinitionContext(namespace, local));
        if (target != null) namespace = target;
        local = null;
        enterMemberScope(type);
        return type;
    }

    public void exitMemberDefinitionScope() {
        if (definitionScopes.isEmpty()) throw new IllegalStateException("no member definition scope to exit");
        exitMemberScope();
        DefinitionContext saved = definitionScopes.pop();
        namespace = saved.namespace;
        local = saved.local;
    }

    public void exitLocalScope() {
        if (local == null || local.member) throw new IllegalStateException("no local scope to exit");
        local = local.parent;
    }

    public MiniType declareStruct(String name, boolean union, SourceRange range) {
        return declareStruct(name, union, false, range);
    }

    /** Registers the tag before parsing fields, so self pointers refer to this identity. */
    public MiniType declareStruct(String name, boolean union, boolean definition, SourceRange range) {
        requireName(name);
        Scope scope = local != null && local.template ? namespace : scope();
        Slot slot = scope.names.computeIfAbsent(name, ignored -> new Slot());
        Entry previous = slot.tag;
        if (previous != null) {
            if (previous.union != union || definition && previous.defined) {
                conflict(range, "聚合类型重复定义或种类不一致：" + name);
            }
            previous.defined |= definition;
            return previous.type;
        }
        // Parser-generated anonymous names are unique within a translation unit. Their
        // declarations are hoisted beside the containing aggregate, so preserve that
        // namespace identity while retaining member lookup lifetime here.
        String canonicalName = local != null && local.member && name.startsWith("$anonymous$")
                ? namespace.qualify(name) : scope.qualify(name);
        MiniType type = MiniType.struct((union ? "$union$" : "") + canonicalName);
        if (slot.ordinary != null && slot.ordinary.kind != Kind.VALUE) {
            conflict(range, "聚合类型与已有名称冲突：" + name);
        }
        slot.tag = new Entry(Kind.TYPE, canonicalName, type, scope, null, true, union, definition);
        if (scope instanceof Namespace owner) classOwners.put(((MiniType.StructType) type).name(), owner);
        return type;
    }

    /** Aliases store the fully parsed MiniType, including pointer/array/function layers. */
    public void declareTypedef(String name, MiniType type, SourceRange range) {
        requireName(name);
        Objects.requireNonNull(type, "type");
        if (isTemplateParameter(name)) conflict(range, "声明不能遮蔽模板参数：" + name);
        Scope scope = scope();
        Slot slot = scope.names.computeIfAbsent(name, ignored -> new Slot());
        Entry previous = slot.ordinary != null ? slot.ordinary : slot.tag;
        if (previous != null) {
            if (previous.kind != Kind.TYPE || !type.equals(previous.type)) {
                conflict(range, "类型别名与已有声明冲突：" + name);
            }
            return;
        }
        slot.ordinary = new Entry(Kind.TYPE, scope.qualify(name), type, scope, null, false, false, true);
    }

    /** Signatures and repeated value declarations are validated by the semantic binder. */
    public void declareValue(String name, SourceRange range) {
        requireName(name);
        if (isTemplateParameter(name)) conflict(range, "声明不能遮蔽模板参数：" + name);
        Scope scope = scope();
        Slot slot = scope.names.computeIfAbsent(name, ignored -> new Slot());
        if (slot.ordinary != null) {
            if (slot.ordinary.kind != Kind.VALUE || slot.ordinary.owner != scope) {
                conflict(range, "普通标识符与已有声明冲突：" + name);
            }
            return;
        }
        slot.ordinary = new Entry(Kind.VALUE, scope.qualify(name), null, scope, null, false, false, true);
    }

    public void registerUsing(QualifiedName target, boolean directive, SourceRange range) {
        Set<Entry> candidates = resolve(target, directive ? Search.QUALIFIER : Search.ORDINARY);
        Lookup found = classify(candidates);
        if (directive) {
            if (found.kind == Kind.NAMESPACE) scope().directives.add(candidates.iterator().next().namespace);
            else report(found.kind == Kind.TYPE || found.kind == Kind.UNSUPPORTED_QUALIFIER ? "CPP005" : "CPP003",
                    range, "using namespace 需要唯一的命名空间：" + spelling(target));
            return;
        }
        if (found.kind != Kind.TYPE && found.kind != Kind.VALUE) {
            report(found.kind == Kind.UNSUPPORTED_QUALIFIER ? "CPP005" : "CPP003", range,
                    "using 声明需要唯一的类型或值：" + spelling(target));
            return;
        }
        Entry entry = candidates.iterator().next();
        Slot slot = scope().names.computeIfAbsent(target.segments().getLast(), ignored -> new Slot());
        Entry existing = entry.tag ? slot.tag : slot.ordinary;
        Entry other = entry.tag ? slot.ordinary : slot.tag;
        boolean compatibleSame = existing == null || existing == entry || equivalentTypes(existing, entry);
        boolean compatibleOther = other == null || equivalentTypes(other, entry)
                || entry.tag && other.kind == Kind.VALUE || other.tag && entry.kind == Kind.VALUE;
        if (!compatibleSame || !compatibleOther) {
            conflict(range, "using 声明与已有名称冲突：" + spelling(target));
            return;
        }
        if (existing == null) {
            if (entry.tag) slot.tag = entry;
            else slot.ordinary = entry;
        }
    }

    public Lookup lookup(QualifiedName name) { return classify(resolve(name, Search.ORDINARY)); }

    /** Ignores values, but never creates an undeclared tag, especially in another namespace. */
    public Lookup lookupElaborated(QualifiedName name) {
        Set<Entry> candidates = resolve(name, Search.ELABORATED);
        Lookup found = classify(candidates);
        if (found.kind == Kind.TYPE && candidates.stream().anyMatch(entry -> !entry.tag)) {
            return new Lookup(Kind.UNSUPPORTED_QUALIFIER, found.type, found.canonicalName);
        }
        return found;
    }

    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }

    private Scope scope() { return local == null ? namespace : local; }

    private Set<Entry> resolve(QualifiedName name, Search search) {
        Objects.requireNonNull(name, "name");
        List<String> segments = name.segments();
        if (segments.size() == 1) {
            return name.global() ? qualified(root, segments.getFirst(), search, new HashSet<>())
                    : unqualified(segments.getFirst(), search);
        }
        Set<Entry> prefix = name.global()
                ? qualified(root, segments.getFirst(), Search.QUALIFIER, new HashSet<>())
                : unqualified(segments.getFirst(), Search.QUALIFIER);
        for (int index = 1; index < segments.size(); index++) {
            Lookup owner = classify(prefix);
            if (owner.kind == Kind.TYPE) {
                return Set.of(new Entry(Kind.UNSUPPORTED_QUALIFIER, owner.canonicalName, owner.type,
                        null, null, false, false, false));
            }
            if (owner.kind != Kind.NAMESPACE) return prefix;
            Namespace target = prefix.iterator().next().namespace;
            prefix = qualified(target, segments.get(index), index == segments.size() - 1 ? search : Search.QUALIFIER,
                    new HashSet<>());
        }
        return prefix;
    }

    private Set<Entry> unqualified(String name, Search search) {
        for (Local at = local; at != null; at = at.parent) {
            Entry direct = direct(at, name, search);
            if (direct != null) return Set.of(direct);
        }
        Map<Namespace, Set<Namespace>> nominations = nominations();
        for (Namespace at = namespace; at != null; at = at.parent) {
            Set<Entry> candidates = new LinkedHashSet<>();
            add(candidates, direct(at, name, search));
            for (Namespace target : nominations.getOrDefault(at, Set.of())) add(candidates, direct(target, name, search));
            if (!candidates.isEmpty()) return candidates;
        }
        return Set.of();
    }

    private Set<Entry> qualified(Namespace at, String name, Search search, Set<Namespace> visited) {
        if (!visited.add(at)) return Set.of();
        Entry direct = direct(at, name, search);
        if (direct != null) return Set.of(direct);
        Set<Entry> candidates = new LinkedHashSet<>();
        for (Namespace target : at.directives) candidates.addAll(qualified(target, name, search, visited));
        return candidates;
    }

    private Entry direct(Scope at, String name, Search search) {
        Slot slot = at.names.get(name);
        if (slot == null) return null;
        if (slot.ordinary != null) {
            Entry entry = slot.ordinary;
            // Nested-name-specifier lookup considers namespaces and class types, not
            // scalar/pointer/array/function typedefs that merely share their spelling.
            boolean qualifying = entry.kind == Kind.NAMESPACE || entry.kind == Kind.TYPE
                    && entry.type.unqualified() instanceof MiniType.StructType;
            if (search == Search.ORDINARY || search == Search.ELABORATED && entry.kind != Kind.VALUE
                    || search == Search.QUALIFIER && qualifying) return entry;
        }
        return slot.tag;
    }

    private Map<Namespace, Set<Namespace>> nominations() {
        Map<Namespace, Set<Namespace>> result = new IdentityHashMap<>();
        for (Local at = local; at != null; at = at.parent) {
            for (Namespace target : at.directives) nominate(at.namespace, target, result, new HashSet<>());
        }
        for (Namespace at = namespace; at != null; at = at.parent) {
            for (Namespace target : at.directives) nominate(at, target, result, new HashSet<>());
        }
        return result;
    }

    private void nominate(Namespace origin, Namespace target, Map<Namespace, Set<Namespace>> result,
                          Set<Namespace> visited) {
        if (!visited.add(target)) return;
        result.computeIfAbsent(commonAncestor(origin, target), ignored -> new LinkedHashSet<>()).add(target);
        for (Namespace next : target.directives) nominate(origin, next, result, visited);
    }

    private Namespace commonAncestor(Namespace first, Namespace second) {
        Set<Namespace> ancestors = new HashSet<>();
        for (Namespace at = first; at != null; at = at.parent) ancestors.add(at);
        for (Namespace at = second; at != null; at = at.parent) if (ancestors.contains(at)) return at;
        throw new IllegalStateException("namespace trees do not share the root");
    }

    private Lookup classify(Set<Entry> candidates) {
        if (candidates.isEmpty()) return new Lookup(Kind.MISSING, null, null);
        Entry first = candidates.iterator().next();
        if (candidates.size() == 1 || first.kind == Kind.TYPE && candidates.stream()
                .allMatch(entry -> entry.kind == Kind.TYPE && first.type.equals(entry.type))) {
            return new Lookup(first.kind, first.type, first.canonicalName);
        }
        return new Lookup(Kind.AMBIGUOUS, null, null);
    }

    private static void add(Set<Entry> entries, Entry entry) { if (entry != null) entries.add(entry); }
    private static boolean equivalentTypes(Entry first, Entry second) {
        return first.kind == Kind.TYPE && second.kind == Kind.TYPE && first.type.equals(second.type);
    }
    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.contains("::")) throw new IllegalArgumentException("expected an unqualified name");
    }
    private static String spelling(QualifiedName name) {
        return (name.global() ? "::" : "") + String.join("::", name.segments());
    }
    private void conflict(SourceRange range, String message) { report("CPP004", range, message); }
    private void report(String code, SourceRange range, String message) {
        diagnostics.add(new Diagnostic(code, Diagnostic.Severity.ERROR, message, range));
    }
}
