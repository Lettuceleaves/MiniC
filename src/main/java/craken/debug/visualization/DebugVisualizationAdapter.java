package craken.debug.visualization;

import craken.debug.Debugger;
import craken.debug.DebugRuntime.RuntimeState;
import craken.visualization.api.*;
import craken.visualization.snapshot.VisualizationSnapshot;
import craken.visualization.model.*;
import craken.visualization.model.relation.*;
import static craken.debug.visualization.DebugObjectIdentityRegistry.*;
import static craken.visualization.api.VisualizationCommand.*;

import java.util.*;

/** Stop-boundary projection of explicitly registered VM structures. It never mutates or executes the VM. */
public final class DebugVisualizationAdapter implements AutoCloseable {
    public record ProjectionResult(int contextIndex, boolean accepted, RuntimeEventBatch events,
                                   String diagnostic, VisualizationSnapshot snapshot) {}
    private record ManualObject(DebugObjectIdentityRegistry.ObjectKey key, PageRef page, ViewLocation pre) {}
    private final VisualizationSession session;
    private final PageTypeRegistry types;
    private final Map<String, DebugStructureDescriptor> descriptors = new LinkedHashMap<>();
    private final Set<DebugStructureDescriptor.RootAddress> roots = new LinkedHashSet<>();
    private final Map<DebugObjectIdentityRegistry.ObjectKey, ManualObject> manual = new LinkedHashMap<>();
    private DebugObjectIdentityRegistry<ViewLocation> identities = new DebugObjectIdentityRegistry<>();
    private VisualizationSnapshot published;
    private boolean closed;
    private final Map<Integer, ProjectionResult> results = new LinkedHashMap<>();
    private Map<DebugStructureDescriptor.RootAddress, ObjectKey> rootIdentities = new LinkedHashMap<>();
    private record Owner(ObjectKey key, ViewLocation location, String source) {}
    private record Group(ObjectKey key, PageRef page) {}
    private static final class Node {
        final ObjectKey key; final Group group; final ViewNode.Spec spec;
        final Set<Owner> owners = new LinkedHashSet<>();
        Node(ObjectKey key, Group group, ViewNode.Spec spec) { this.key=key; this.group=group; this.spec=spec; }
    }
    private record Work(ObjectKey key, Group group, Set<Owner> owners) {}
    private record Edge(ObjectKey a, ObjectKey b, TopologyEdge.Direction direction) {}
    private record Member(ObjectKey parent, ObjectKey child, int slot) {}
    private record PointerField(ReferenceKey key, int offset, long value) {}

    public DebugVisualizationAdapter(VisualizationSession session, PageTypeRegistry types) {
        this.session = Objects.requireNonNull(session);
        this.types = Objects.requireNonNull(types);
        published = session.snapshot();
    }
    public synchronized void registerDescriptor(DebugStructureDescriptor descriptor) {
        var previous = descriptors.putIfAbsent(descriptor.key(), descriptor);
        if (previous != null && !previous.equals(descriptor)) throw new IllegalArgumentException("Descriptor key already registered");
    }
    public synchronized void registerRoot(DebugStructureDescriptor.RootAddress root) {
        Objects.requireNonNull(root); roots.add(root); rootIdentities.remove(root);
    }
    public synchronized void registerObject(String descriptorKey, DebugMemoryReader.Address address, PageRef page, ViewLocation pre) {
        var key = new DebugObjectIdentityRegistry.ObjectKey(address.allocationId(), address.offset(), descriptorKey);
        manual.put(key, new ManualObject(key, page, pre));
    }
    public synchronized Map<DebugObjectIdentityRegistry.ObjectKey, ViewLocation> locations() { return identities.locations(); }
    public synchronized VisualizationSnapshot publishedSnapshot() { return published; }
    public ProjectionResult consume(Debugger.Context context) { return project(context.index(), context.runtime(), context.events()); }
    public ProjectionResult project(int index, RuntimeState state, RuntimeEventBatch events) {
        return project(index, new DebugMemoryReader(state), events);
    }
    public synchronized ProjectionResult project(int index, DebugMemoryReader memory, RuntimeEventBatch events) {
        if (closed) throw new IllegalStateException("Adapter closed");
        Objects.requireNonNull(memory); Objects.requireNonNull(events);
        if (results.containsKey(index)) return results.get(index);
        if (!events.complete()) return reject(index, events, "Incomplete monitoring interval: " + events.diagnostic());
        VisualizationSnapshot before = session.snapshot();
        ContainerModel old = session.model();
        try {
            var draft = identities.copy();
            var boundRoots = new LinkedHashMap<>(rootIdentities);
            for (var event:events.events()) if(event instanceof RuntimeEvent.Reallocated resize&&resize.previous()!=null) {
                boundRoots.replaceAll((root,key)->key.allocationId()==resize.previous().allocationId()
                        &&key.offset()+descriptor(key.descriptorKey()).minimumSize()<=resize.replacement().size()
                        ?new ObjectKey(resize.replacement().allocationId(),key.offset(),key.descriptorKey()):key);
            }
            var seeds = new LinkedHashSet<ObjectKey>();
            for (var root : roots) {
                descriptor(root.descriptorKey());
                var key = boundRoots.get(root);
                if (key == null) {
                    try { key = key(memory.resolve(root.address()), root.descriptorKey()); boundRoots.put(root, key); }
                    catch (DebugMemoryReader.MemoryReadException e) { if (e.reason()!=DebugMemoryReader.Reason.UNKNOWN_ADDRESS) throw e; }
                }
                if (key != null && live(memory, key)) seeds.add(key);
            }
            manual.keySet().stream().filter(key -> live(memory,key)).forEach(seeds::add);
            // First enumerate declared pointer fields. Their generation is then captured in event order.
            List<PointerField> pointers = pointerFields(memory, seeds);
            draft.beginInterval(memory, events.events());
            for (var event : events.events()) {
                draft.observe(event);
                RuntimeEvent.MemoryRange changed = writeRange(event);
                if (changed != null) for (var field : pointers) {
                    if (overlaps(memory,field.key().source(),field.offset(),8,changed)) {
                        draft.rememberReference(field.key(),field.value());
                    }
                }
            }
            draft.reconcile(memory);
            for (var event:events.events()) if(event instanceof RuntimeEvent.Reallocated resize&&resize.previous()!=null)
                for(var entry:identities.locations().entrySet()) {
                    var key=entry.getKey(); var schema=descriptor(key.descriptorKey());
                    if(key.allocationId()==resize.previous().allocationId()
                            &&key.offset()+schema.minimumSize()<=resize.replacement().size()
                            &&schema.reallocationPolicy()==DebugStructureDescriptor.ReallocationPolicy.PRESERVE_LOCATION)
                        draft.put(new ObjectKey(resize.replacement().allocationId(),key.offset(),key.descriptorKey()),entry.getValue());
                }
            for (var field : pointers) {
                var remembered = draft.reference(field.key());
                if (remembered.isEmpty() || remembered.get().rawAddress()!=field.value())
                    draft.rememberReference(field.key(),field.value());
            }
            var nodes = new LinkedHashMap<ObjectKey,Node>();
            var edges = new LinkedHashSet<Edge>();
            var members = new LinkedHashSet<Member>();
            Deque<Work> queue = new ArrayDeque<>();
            for (var seed : seeds) {
                var explicit = manual.get(seed);
                if (boundRoots.containsValue(seed)) queue.add(new Work(seed,new Group(null,old.root()),Set.of()));
                else {
                    Objects.requireNonNull(explicit);
                    requirePage(explicit.page()); old.node(explicit.pre());
                    queue.add(new Work(seed,new Group(null,explicit.page()),
                            Set.of(new Owner(null,explicit.pre(),"debug:manual:"+seed))));
                }
            }
            while (!queue.isEmpty()) {
                Work work = queue.removeFirst();
                Node node = nodes.get(work.key());
                boolean first = node==null;
                if (first) {
                    node = new Node(work.key(),work.group(),content(memory,work.key()));
                    nodes.put(work.key(),node);
                }
                boolean changed = node.owners.addAll(work.owners());
                if (!first && !changed) continue;
                var schema = descriptor(work.key().descriptorKey());
                for (var reference : schema.references()) {
                    var referenceKey=new ReferenceKey(work.key(),reference.name());
                    try { memory.readPointer(work.key().address(),reference.offset()); }
                    catch(DebugMemoryReader.MemoryReadException failure) {
                        if(failure.reason()!=DebugMemoryReader.Reason.UNINITIALIZED)throw failure;
                        draft.forgetReference(referenceKey); continue;
                    }
                    var target = draft.referenceTarget(referenceKey);
                    if (target.isEmpty()) continue;
                    ObjectKey child = key(target.get(),reference.targetDescriptorKey());
                    if (reference.relation()==DebugStructureDescriptor.Relation.TOPOLOGY) {
                        edges.add(new Edge(work.key(),child,TopologyEdge.Direction.valueOf(reference.direction().name())));
                        queue.add(new Work(child,node.group,Set.copyOf(node.owners)));
                    } else {
                        Group group = groupFor(child,draft);
                        queue.add(new Work(child,group,Set.of(new Owner(work.key(),null,"debug:ref:"+work.key()+":"+reference.name()))));
                    }
                }
                var array = schema.array();
                if (array!=null) for (int slot=0;slot<array.length();slot++) {
                    ObjectKey child=new ObjectKey(work.key().allocationId(),
                            Math.addExact(work.key().offset(),Math.addExact(array.offset(),Math.multiplyExact(slot,array.stride()))),
                            array.elementDescriptorKey());
                    members.add(new Member(work.key(),child,slot));
                    queue.add(new Work(child,node.group,Set.copyOf(node.owners)));
                }
            }
            var locations = materialize(nodes,draft);
            List<VisualizationCommand> commands = new ArrayList<>();
            for (var node : nodes.values()) {
                ViewLocation location=locations.get(node.key);
                if (!session.model().node(location).content().equals(node.spec))
                    commands.add(new SetContent(path(location,node,locations),node.spec));
                for (var owner : node.owners) {
                    var pre=owner(owner,locations);
                    var current=session.model().ownership().get(new OwnershipBinding.Key(pre,location));
                    if(current==null||!current.sources().contains(owner.source()))
                        commands.add(new AttachOwnership(pre,location,owner.source()));
                }
            }
            for (var member : members) {
                var parent=locations.get(member.parent()); var child=locations.get(member.child());
                boolean present=session.model().pages().get(parent.pageId()).composition().values().stream()
                        .anyMatch(link->link.parent().equals(parent)&&link.child().equals(child));
                if (!present) commands.add(new Compose(parent,child,member.slot()));
            }
            for (var page : session.model().pages().values()) for (var edge : page.topology().values()) {
                boolean keep=edges.stream().anyMatch(e->edge.a().equals(locations.get(e.a()))&&edge.b().equals(locations.get(e.b()))
                        &&edge.direction()==e.direction());
                if (!keep) commands.add(new Disconnect(currentPath(edge.a()),currentPath(edge.b())));
            }
            for (var edge : edges) {
                var a=locations.get(edge.a()); var b=locations.get(edge.b());
                boolean present=session.model().pages().get(a.pageId()).topology().values().stream()
                        .anyMatch(e->e.a().equals(a)&&e.b().equals(b)&&e.direction()==edge.direction());
                if (!present) commands.add(new Connect(path(a,nodes.get(edge.a()),locations),path(b,nodes.get(edge.b()),locations),edge.direction()));
            }
            // Add replacements before removing old ownership sources, so shared children remain alive.
            for (var binding : session.model().ownership().values()) for (var source : binding.sources()) {
                if (!source.startsWith("debug:")) continue;
                boolean keep=nodes.values().stream().anyMatch(n->locations.get(n.key).equals(binding.key().nxt())
                        &&n.owners.stream().anyMatch(o->o.source().equals(source)&&owner(o,locations).equals(binding.key().pre())));
                if (!keep) commands.add(new DetachOwnership(binding.key().pre(),binding.key().nxt(),source));
            }
            var retained = new HashSet<>(locations.values());
            for (var previous : identities.locations().values()) if (!retained.contains(previous)&&exists(previous))
                commands.add(new DeleteNode(currentPath(previous)));
            touch(events,memory,nodes,locations,commands);
            apply(new MutationBatch(commands,"debug:"+index));
            locations.entrySet().removeIf(entry -> !exists(entry.getValue()));
            draft.replaceLocations(locations);
            var next=session.snapshot();
            identities=draft; rootIdentities=boundRoots; published=next;
            var result=new ProjectionResult(index,true,events,events.monitored()?"":"Monitoring disabled",next);
            results.put(index,result);
            return result;
        } catch (RuntimeException failure) {
            if (session.model()!=old) session.restore(before);
            return reject(index,events,String.valueOf(failure.getMessage()));
        }
    }
    private ProjectionResult reject(int index,RuntimeEventBatch events,String detail) {
        var result=new ProjectionResult(index,false,events,detail,published); results.put(index,result); return result;
    }
    private DebugStructureDescriptor descriptor(String key) {
        var schema=descriptors.get(key); if (schema==null) throw new IllegalArgumentException("Unregistered descriptor: "+key);
        types.require(schema.pageTypeKey()); return schema;
    }
    private static ObjectKey key(DebugMemoryReader.Address a,String descriptor) { return new ObjectKey(a.allocationId(),a.offset(),descriptor); }
    private static boolean live(DebugMemoryReader memory,ObjectKey key) {
        try { memory.absolute(key.address()); return true; }
        catch (DebugMemoryReader.MemoryReadException e) { return false; }
    }
    private void requirePage(PageRef page) {
        if (page.containerId()!=session.model().id()||!session.model().pages().containsKey(page.pageId()))
            throw new IllegalArgumentException("Unknown manual page");
    }
    private ViewNode.Spec content(DebugMemoryReader memory,ObjectKey key) {
        var schema=descriptor(key.descriptorKey());
        var block=memory.allocation(key.allocationId());
        if (schema.minimumSize()>block.size()-key.offset()) throw new IllegalArgumentException("Descriptor exceeds allocation: "+key);
        var fields=new LinkedHashMap<String,String>();
        for (var field:schema.fields()) {
            try { fields.put(field.name(),memory.readScalar(key.address(),field.offset(),field.type())); }
            catch(DebugMemoryReader.MemoryReadException e) {
                if(e.reason()!=DebugMemoryReader.Reason.UNINITIALIZED) throw e;
                fields.put(field.name(),"<uninitialized>");
            }
        }
        return new ViewNode.Spec(ViewNode.Kind.valueOf(schema.viewKind().name()),schema.key(),fields);
    }
    private List<PointerField> pointerFields(DebugMemoryReader memory,Set<ObjectKey> seeds) {
        var seen=new HashSet<ObjectKey>(); var result=new ArrayList<PointerField>();
        var queue=new ArrayDeque<>(seeds);
        while(!queue.isEmpty()) {
            var object=queue.removeFirst(); if(!seen.add(object))continue;
            content(memory,object); var schema=descriptor(object.descriptorKey());
            for(var reference:schema.references()) {
                try {
                    long raw=memory.readPointer(object.address(),reference.offset());
                    result.add(new PointerField(new ReferenceKey(object,reference.name()),reference.offset(),raw));
                    if(raw!=0) {
                        try { queue.add(key(memory.resolve(raw),reference.targetDescriptorKey())); }
                        catch(DebugMemoryReader.MemoryReadException e) { if(e.reason()!=DebugMemoryReader.Reason.UNKNOWN_ADDRESS)throw e; }
                    }
                } catch(DebugMemoryReader.MemoryReadException e) { if(e.reason()!=DebugMemoryReader.Reason.UNINITIALIZED)throw e; }
            }
            var array=schema.array();
            if(array!=null)for(int i=0;i<array.length();i++)
                queue.add(new ObjectKey(object.allocationId(),Math.addExact(object.offset(),Math.addExact(array.offset(),Math.multiplyExact(i,array.stride()))),array.elementDescriptorKey()));
        }
        return result;
    }
    private static RuntimeEvent.MemoryRange writeRange(RuntimeEvent event) {
        if(event instanceof RuntimeEvent.Accessed a&&a.access()!=RuntimeEvent.Access.READ)return a.range();
        if(event instanceof RuntimeEvent.Copied c)return c.destination();
        return null;
    }
    private static boolean overlaps(DebugMemoryReader memory,ObjectKey key,int offset,int size,RuntimeEvent.MemoryRange range) {
        if(key.allocationId()!=range.allocationId())return false;
        long start=memory.absolute(key.address())+offset;
        return start<range.address()+range.size()&&range.address()<start+size;
    }
    private Group groupFor(ObjectKey key,DebugObjectIdentityRegistry<ViewLocation> draft) {
        var explicit=manual.get(key); if(explicit!=null)return new Group(null,explicit.page());
        var existing=draft.location(key);
        return existing.isPresent()?new Group(null,existing.get().page()):new Group(key,null);
    }
    private Map<ObjectKey,ViewLocation> materialize(Map<ObjectKey,Node> nodes,DebugObjectIdentityRegistry<ViewLocation> draft) {
        Map<ObjectKey,ViewLocation> locations=new LinkedHashMap<>();
        nodes.keySet().forEach(key->draft.location(key).filter(this::exists).ifPresent(l->locations.put(key,l)));
        Map<Group,PageRef> pages=new HashMap<>();
        for(var node:nodes.values())if(node.group.page()!=null)pages.put(node.group,node.group.page());
        int remaining=nodes.size()-locations.size();
        while(remaining>0) {
            int previous=remaining;
            for(var node:nodes.values()) {
                if(locations.containsKey(node.key))continue;
                ViewLocation pre=node.owners.stream().map(o->o.location()!=null?o.location():locations.get(o.key()))
                        .filter(Objects::nonNull).findFirst().orElse(null);
                if(!node.owners.isEmpty()&&pre==null)continue;
                PageRef page=pages.get(node.group);
                if(page==null) {
                    page=node.owners.isEmpty()?session.initializeRoot(types.require(descriptor(node.key.descriptorKey()).pageTypeKey()))
                            :session.initializePage(types.require(descriptor(node.key.descriptorKey()).pageTypeKey()),pre);
                    pages.put(node.group,page);
                }
                ViewLocation location=session.reserveNodeId(page);
                var batch=new ArrayList<VisualizationCommand>();
                batch.add(new AddNode(new OperationPath(pre,location),node.spec));
                if(pre!=null) {
                    var owner=node.owners.stream().filter(o->pre.equals(o.location()!=null?o.location():locations.get(o.key()))).findFirst().orElseThrow();
                    batch.add(new AttachOwnership(pre,location,owner.source()));
                    batch.add(new DetachOwnership(pre,location,"explicit"));
                }
                // Rules used solely to initialize an adapter page must not retain unreachable objects forever.
                for(var rule:session.model().pageRules().values())
                    if(rule.child().equals(page)&&page.equals(pages.get(node.group))&&node.group.key()!=null)
                        batch.add(new UnbindPage(rule.id()));
                apply(new MutationBatch(batch,"debug:allocation"));
                locations.put(node.key,location); remaining--;
            }
            if(previous==remaining)throw new IllegalArgumentException("Ownership cycle has no creatable parent");
        }
        return locations;
    }
    private static ViewLocation owner(Owner owner,Map<ObjectKey,ViewLocation> locations) {
        return owner.location()!=null?owner.location():Objects.requireNonNull(locations.get(owner.key()));
    }
    private static OperationPath path(ViewLocation location,Node node,Map<ObjectKey,ViewLocation> locations) {
        return new OperationPath(node.owners.isEmpty()?null:owner(node.owners.iterator().next(),locations),location);
    }
    private OperationPath currentPath(ViewLocation location) {
        return new OperationPath(session.model().node(location).parents().selected(),location);
    }
    private boolean exists(ViewLocation location) {
        var page=session.model().pages().get(location.pageId()); return page!=null&&page.nodes().containsKey(location.nodeId());
    }
    private void apply(MutationBatch batch) {
        var result=session.modify(batch);
        if(!result.succeeded())throw new IllegalArgumentException(result.error().code()+": "+result.error().message());
    }
    private void touch(RuntimeEventBatch events,DebugMemoryReader memory,Map<ObjectKey,Node> nodes,
                       Map<ObjectKey,ViewLocation> locations,List<VisualizationCommand> commands) {
        for(var event:events.events()) {
            if(event instanceof RuntimeEvent.Accessed access) {
                addTouch(access.range(),access.access()==RuntimeEvent.Access.READ?AccessKind.READ:AccessKind.WRITE,memory,nodes,locations,commands);
            } else if(event instanceof RuntimeEvent.Copied copy) {
                addTouch(copy.source(),AccessKind.READ,memory,nodes,locations,commands);
                addTouch(copy.destination(),AccessKind.WRITE,memory,nodes,locations,commands);
            } else if(event instanceof RuntimeEvent.Allocated allocated) {
                addTouch(allocated.range(),AccessKind.ALLOCATE,memory,nodes,locations,commands);
            }
        }
    }
    private void addTouch(RuntimeEvent.MemoryRange range,AccessKind kind,DebugMemoryReader memory,Map<ObjectKey,Node> nodes,
                          Map<ObjectKey,ViewLocation> locations,List<VisualizationCommand> commands) {
        for(var node:nodes.values())if(overlaps(memory,node.key,0,descriptor(node.key.descriptorKey()).minimumSize(),range))
            commands.add(new Touch(path(locations.get(node.key),node,locations),kind));
    }
    @Override public synchronized void close() { if (closed) return; closed = true; session.close(); }
}
