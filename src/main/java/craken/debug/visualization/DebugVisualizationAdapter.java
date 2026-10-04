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
    private record ManualObject(DebugObjectIdentityRegistry.ObjectKey key, PageRef page, Set<ViewLocation> parents) {
        ManualObject { parents=Collections.unmodifiableSet(new LinkedHashSet<>(parents)); }
    }
    private final VisualizationSession session;
    private final PageTypeRegistry types;
    private final Map<String, DebugStructureDescriptor> descriptors = new LinkedHashMap<>();
    private final Set<DebugStructureDescriptor.RootAddress> roots = new LinkedHashSet<>();
    private final Map<DebugObjectIdentityRegistry.ObjectKey, ManualObject> manual = new LinkedHashMap<>();
    // Unconsumed caller registrations are configuration, not a historical model value.
    private final Map<ObjectKey,ManualObject> pendingManual = new LinkedHashMap<>();
    private final Set<DebugStructureDescriptor.RootAddress> pendingRoots = new LinkedHashSet<>();
    private DebugObjectIdentityRegistry<ViewLocation> identities = new DebugObjectIdentityRegistry<>();
    // Successful VM observations must advance even when publication of a model frame is rejected.
    private DebugObjectIdentityRegistry<ViewLocation> observations = new DebugObjectIdentityRegistry<>();
    private VisualizationSnapshot published;
    private boolean closed;
    private boolean ownsSession = true;
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
    private record Edge(ObjectKey a, ObjectKey b, TopologyEdge.Direction direction,String aPort,String bPort) {}
    private record EdgePorts(ViewLocation a,ViewLocation b,String aPort,String bPort) {}
    private record Member(ObjectKey parent, ObjectKey child, int slot) {}
    private record PointerField(ReferenceKey key, int offset, long value) {}

    public DebugVisualizationAdapter(VisualizationSession session, PageTypeRegistry types) {
        this.session = Objects.requireNonNull(session);
        this.types = Objects.requireNonNull(types);
        published = session.snapshot();
    }
    /** Borrowed sessions are restored by this adapter but remain open when it is closed. */
    public DebugVisualizationAdapter(VisualizationSession session,PageTypeRegistry types,boolean ownsSession) {
        this(session,types); this.ownsSession=ownsSession;
    }
    static final class Checkpoint {
        final VisualizationSnapshot snapshot;
        final DebugObjectIdentityRegistry<ViewLocation> identities;
        final DebugObjectIdentityRegistry<ViewLocation> observations;
        final Map<DebugStructureDescriptor.RootAddress,ObjectKey> roots;
        final Map<ObjectKey,ManualObject> manual;
        Checkpoint(VisualizationSnapshot snapshot,DebugObjectIdentityRegistry<ViewLocation> identities,
                   DebugObjectIdentityRegistry<ViewLocation> observations,
                   Map<DebugStructureDescriptor.RootAddress,ObjectKey> roots,Map<ObjectKey,ManualObject> manual) {
            this.snapshot=snapshot;this.identities=identities.copy();this.observations=observations.copy();
            this.roots=Map.copyOf(roots);this.manual=Map.copyOf(manual);
        }
    }
    synchronized Checkpoint checkpoint() { return new Checkpoint(published,identities,observations,rootIdentities,manual); }
    synchronized void restore(Checkpoint checkpoint) {
        if(closed)throw new IllegalStateException("Adapter closed");
        // Session.restore validates and publishes atomically before identity publication.
        session.restore(checkpoint.snapshot);
        identities=checkpoint.identities.copy();rootIdentities=new LinkedHashMap<>(checkpoint.roots);
        observations=checkpoint.observations.copy();
        manual.clear();manual.putAll(checkpoint.manual);
        published=session.snapshot();
    }
    synchronized void forgetResult(int index) { results.remove(index); }
    public synchronized void registerDescriptor(DebugStructureDescriptor descriptor) {
        requireOpen();Objects.requireNonNull(descriptor);
        var previous = descriptors.putIfAbsent(descriptor.key(), descriptor);
        if (previous != null && !previous.equals(descriptor)) throw new IllegalArgumentException("Descriptor key already registered");
    }
    public synchronized void registerRoot(DebugStructureDescriptor.RootAddress root) {
        requireOpen();Objects.requireNonNull(root); roots.add(root);pendingRoots.add(root);
    }
    public synchronized void registerObject(String descriptorKey, DebugMemoryReader.Address address, PageRef page, ViewLocation pre) {
        requireOpen();Objects.requireNonNull(address);Objects.requireNonNull(page);Objects.requireNonNull(pre);
        if(page.containerId()!=pre.containerId()||page.containerId()!=session.model().id())
            throw new IllegalArgumentException("Manual ownership must stay in its container");
        var key = new DebugObjectIdentityRegistry.ObjectKey(address.allocationId(), address.offset(), descriptorKey);
        mergeManual(pendingManual,new ManualObject(key,page,Set.of(pre)));
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
        DebugObjectIdentityRegistry<ViewLocation> observedInterval=null;
        Map<DebugStructureDescriptor.RootAddress,ObjectKey> observedRoots=null;
        Map<ObjectKey,ManualObject> registered=null;
        try {
            var draft = observations.copy();
            var boundRoots = new LinkedHashMap<>(rootIdentities);
            pendingRoots.forEach(boundRoots::remove);
            boundRoots.replaceAll((root,key)->relocated(key,events.events()));
            registered=new LinkedHashMap<>();
            var registrations=new LinkedHashMap<>(manual);
            pendingManual.values().forEach(object->mergeManual(registrations,object));
            Map<ViewLocation,ObjectKey> priorObjects=new HashMap<>();
            identities.locations().forEach((key,location)->priorObjects.put(location,key));
            for(var object:registrations.values()) {
                ObjectKey key=relocated(object.key(),events.events());
                var parents=new LinkedHashSet<ViewLocation>();
                for(var parent:object.parents()) {
                    ObjectKey identity=priorObjects.get(parent);
                    if(exists(parent)&&(identity==null||live(memory,relocated(identity,events.events()))))parents.add(parent);
                }
                if(!parents.isEmpty())mergeManual(registered,new ManualObject(key,object.page(),parents));
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
            registered.keySet().stream().filter(key -> live(memory,key)).forEach(seeds::add);
            // First enumerate declared pointer fields. Their generation is then captured in event order.
            var observedSources=new LinkedHashSet<>(seeds);
            observations.referenceSources().stream().filter(key->live(memory,key)).forEach(observedSources::add);
            List<PointerField> pointers = pointerFields(memory, observedSources);
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
            for(var entry:observations.locations().entrySet()) {
                var key=entry.getKey();var relocated=relocated(key,events.events());
                if(!key.equals(relocated)&&live(memory,relocated)
                        &&descriptor(key.descriptorKey()).reallocationPolicy()==DebugStructureDescriptor.ReallocationPolicy.PRESERVE_LOCATION)
                    draft.put(relocated,entry.getValue());
            }
            for (var field : pointers) {
                var remembered = draft.reference(field.key());
                if (remembered.isEmpty() || remembered.get().rawAddress()!=field.value())
                    draft.rememberReference(field.key(),field.value());
            }
            observedInterval=draft.copy();observedRoots=new LinkedHashMap<>(boundRoots);
            var nodes = new LinkedHashMap<ObjectKey,Node>();
            var edges = new LinkedHashSet<Edge>();
            var members = new LinkedHashSet<Member>();
            Deque<Work> queue = new ArrayDeque<>();
            for (var seed : seeds) {
                var explicit = registered.get(seed);
                if (explicit != null) {
                    requirePage(explicit.page());
                    var owners=new LinkedHashSet<Owner>();
                    for(var parent:explicit.parents()) {
                        old.node(parent);owners.add(new Owner(null,parent,"debug:manual:"+seed));
                    }
                    queue.add(new Work(seed,new Group(null,explicit.page()),owners));
                } else queue.add(new Work(seed,new Group(null,old.root()),Set.of()));
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
                        edges.add(new Edge(work.key(),child,TopologyEdge.Direction.valueOf(reference.direction().name()),
                                reference.sourcePort(),reference.targetPort()));
                        queue.add(new Work(child,node.group,Set.copyOf(node.owners)));
                    } else {
                        Group group = groupFor(child,draft,registered);
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
            var locations = materialize(nodes,edges,draft);
            List<VisualizationCommand> commands = new ArrayList<>();
            for (var node : nodes.values()) {
                ViewLocation location=locations.get(node.key);
                for (var owner : node.owners) {
                    var pre=owner(owner,locations);
                    var current=session.model().ownership().get(new OwnershipBinding.Key(pre,location));
                    if(current==null||!current.sources().contains(owner.source()))
                        commands.add(new AttachOwnership(pre,location,owner.source()));
                }
            }
            // Establish the selected upstream before any command that requires that path.
            for (var node : nodes.values()) {
                ViewLocation location=locations.get(node.key);
                if (!session.model().node(location).content().equals(node.spec))
                    commands.add(new SetContent(path(location,node,locations),node.spec));
            }
            var resolved=resolveEdges(edges,locations);
            var currentEdges=new LinkedHashMap<EdgePorts,TopologyEdge>();
            for (var page : session.model().pages().values()) for (var edge : page.topology().values()) {
                var ports=new EdgePorts(edge.a(),edge.b(),edge.aPort(),edge.bPort());currentEdges.put(ports,edge);
                if (!resolved.containsKey(ports)) commands.add(new Disconnect(currentPath(edge.a()),currentPath(edge.b()),edge.aPort(),edge.bPort()));
            }
            var retained = new HashSet<>(locations.values());
            var deleted = new LinkedHashSet<ViewLocation>();
            for (var previous : identities.locations().values()) if (!retained.contains(previous)&&exists(previous)) deleted.add(previous);
            // Children still need a live pre. Delete leaves before parents, before detaching their paths.
            for (var previous : deletionOrder(deleted)) commands.add(new DeleteNode(currentPath(previous)));
            // Explicitly deleted old members release their slots for a later Compose in this same batch.
            for (var member : members) {
                var parent=locations.get(member.parent()); var child=locations.get(member.child());
                boolean present=session.model().pages().get(parent.pageId()).composition().values().stream()
                        .anyMatch(link->link.parent().equals(parent)&&link.child().equals(child));
                if (!present) commands.add(new Compose(parent,child,member.slot()));
            }
            var nodeByLocation=new HashMap<ViewLocation,Node>();nodes.values().forEach(node->nodeByLocation.put(locations.get(node.key),node));
            for(var entry:resolved.entrySet()) {
                var ports=entry.getKey();var existing=currentEdges.get(ports);
                if(existing==null||existing.direction()!=entry.getValue())
                    commands.add(new Connect(path(ports.a(),nodeByLocation.get(ports.a()),locations),
                            path(ports.b(),nodeByLocation.get(ports.b()),locations),entry.getValue(),ports.aPort(),ports.bPort(),
                            existing==null?craken.visualization.style.EdgeStyle.DEFAULT:existing.style()));
            }
            // Replacement sources have already been attached. Explicit deletion removes its own sources.
            for (var binding : session.model().ownership().values()) for (var source : binding.sources()) {
                if (!source.startsWith("debug:")) continue;
                if (deleted.contains(binding.key().pre())||deleted.contains(binding.key().nxt())) continue;
                boolean keep=nodes.values().stream().anyMatch(n->locations.get(n.key).equals(binding.key().nxt())
                        &&n.owners.stream().anyMatch(o->o.source().equals(source)&&owner(o,locations).equals(binding.key().pre())));
                if (!keep) commands.add(new DetachOwnership(binding.key().pre(),binding.key().nxt(),source));
            }
            touch(events,memory,nodes,locations,deleted,commands);
            apply(new MutationBatch(commands,"debug:"+index));
            locations.entrySet().removeIf(entry -> !exists(entry.getValue()));
            draft.replaceLocations(locations);
            var next=session.snapshot();
            identities=draft;observations=draft.copy();rootIdentities=boundRoots;published=next;
            publishRegistrations(registered);
            var result=new ProjectionResult(index,true,events,events.monitored()?"":"Monitoring disabled",next);
            results.put(index,result);
            return result;
        } catch (RuntimeException failure) {
            if (session.model()!=old) session.restore(before);
            if(observedInterval!=null) {
                observations=observedInterval;rootIdentities=observedRoots;publishRegistrations(registered);
            }
            return reject(index,events,String.valueOf(failure.getMessage()));
        }
    }
    private ProjectionResult reject(int index,RuntimeEventBatch events,String detail) {
        var result=new ProjectionResult(index,false,events,detail,published); results.put(index,result); return result;
    }
    private DebugStructureDescriptor descriptor(String key) {
        var schema=descriptors.get(key); if (schema==null) throw new IllegalArgumentException("Unregistered descriptor: "+key);
        var array=schema.array();
        if(array!=null) {
            var element=descriptors.get(array.elementDescriptorKey());
            if(element==null)throw new IllegalArgumentException("Unregistered array element descriptor: "+array.elementDescriptorKey());
            if(element.minimumSize()>array.stride())throw new IllegalArgumentException("Array element minimumSize exceeds declared stride");
            long end=(long)array.offset()+(array.length()==0?0:(long)(array.length()-1)*array.stride()+element.minimumSize());
            if(end>schema.minimumSize())throw new IllegalArgumentException("Array element exceeds declared object minimumSize");
        }
        types.require(schema.pageTypeKey()); return schema;
    }
    private void requireOpen() { if(closed)throw new IllegalStateException("Adapter closed"); }
    private static void mergeManual(Map<ObjectKey,ManualObject> registrations,ManualObject object) {
        var previous=registrations.get(object.key());
        if(previous==null) { registrations.put(object.key(),object);return; }
        if(!previous.page().equals(object.page()))throw new IllegalArgumentException("Manual object already registered in another page");
        var parents=new LinkedHashSet<>(previous.parents());parents.addAll(object.parents());
        registrations.put(object.key(),new ManualObject(object.key(),object.page(),parents));
    }
    private void publishRegistrations(Map<ObjectKey,ManualObject> registrations) {
        manual.clear();
        for(var object:registrations.values()) {
            var parents=new LinkedHashSet<ViewLocation>();
            object.parents().stream().filter(this::exists).forEach(parents::add);
            if(!parents.isEmpty())manual.put(object.key(),new ManualObject(object.key(),object.page(),parents));
        }
        pendingManual.clear();pendingRoots.clear();
    }
    private ObjectKey relocated(ObjectKey key,List<RuntimeEvent> events) {
        for(var event:events)if(event instanceof RuntimeEvent.Reallocated resize&&resize.previous()!=null
                &&key.allocationId()==resize.previous().allocationId()
                &&key.offset()+descriptor(key.descriptorKey()).minimumSize()<=resize.replacement().size())
            key=new ObjectKey(resize.replacement().allocationId(),key.offset(),key.descriptorKey());
        return key;
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
        for(var reference:schema.references())if(!fields.containsKey(reference.name())) {
            try { fields.put(reference.name(),memory.readScalar(key.address(),reference.offset(),DebugMemoryReader.ScalarType.POINTER)); }
            catch(DebugMemoryReader.MemoryReadException e) {
                if(e.reason()!=DebugMemoryReader.Reason.UNINITIALIZED)throw e;
                fields.put(reference.name(),"<uninitialized>");
            }
        }
        return new ViewNode.Spec(ViewNode.Kind.valueOf(schema.viewKind().name()),schema.key(),fields);
    }
    private List<PointerField> pointerFields(DebugMemoryReader memory,Set<ObjectKey> seeds) {
        var seen=new HashSet<ObjectKey>(); var result=new ArrayList<PointerField>();
        var queue=new ArrayDeque<>(seeds);
        while(!queue.isEmpty()) {
            var object=queue.removeFirst(); if(!seen.add(object))continue;
            var schema=descriptor(object.descriptorKey());
            // Raw-address pre-scanning must not claim that a stale pointer owns a replacement generation.
            // Actual reachable nodes are validated later, after event-ordered reference resolution.
            if(schema.minimumSize()>memory.allocation(object.allocationId()).size()-object.offset())continue;
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
    private Group groupFor(ObjectKey key,DebugObjectIdentityRegistry<ViewLocation> draft,Map<ObjectKey,ManualObject> registered) {
        var explicit=registered.get(key); if(explicit!=null)return new Group(null,explicit.page());
        var existing=draft.location(key);
        return existing.isPresent()?new Group(null,existing.get().page()):new Group(key,null);
    }
    private Map<ObjectKey,ViewLocation> materialize(Map<ObjectKey,Node> nodes,Set<Edge> edges,DebugObjectIdentityRegistry<ViewLocation> draft) {
        Map<ObjectKey,ViewLocation> locations=new LinkedHashMap<>();
        nodes.keySet().forEach(key->draft.location(key).filter(this::exists).ifPresent(l->locations.put(key,l)));
        Map<Group,Group> groups=new HashMap<>();
        nodes.values().forEach(node->groups.putIfAbsent(node.group,node.group));
        for(var edge:edges) {
            Group a=representative(groups,nodes.get(edge.a()).group),b=representative(groups,nodes.get(edge.b()).group);
            if(!a.equals(b))groups.put(b,a);
        }
        Map<Group,PageRef> pages=new HashMap<>();
        for(var node:nodes.values()) {
            Group group=representative(groups,node.group);
            PageRef page=node.group.page()!=null?node.group.page():locations.containsKey(node.key)?locations.get(node.key).page():null;
            if(page!=null) {
                PageRef previous=pages.putIfAbsent(group,page);
                if(previous!=null&&!previous.equals(page))throw new IllegalArgumentException("Topology joins objects registered in different pages");
            }
        }
        Set<Long> initializationRules=new HashSet<>();
        int remaining=nodes.size()-locations.size();
        while(remaining>0) {
            int previous=remaining;
            for(var node:nodes.values()) {
                if(locations.containsKey(node.key))continue;
                ViewLocation pre=node.owners.stream().map(o->o.location()!=null?o.location():locations.get(o.key()))
                        .filter(Objects::nonNull).findFirst().orElse(null);
                if(!node.owners.isEmpty()&&pre==null)continue;
                Group group=representative(groups,node.group);
                PageRef page=pages.get(group);
                if(page==null) {
                    page=node.owners.isEmpty()?session.initializeRoot(types.require(descriptor(node.key.descriptorKey()).pageTypeKey()))
                            :session.initializePage(types.require(descriptor(node.key.descriptorKey()).pageTypeKey()),pre);
                    pages.put(group,page);
                    for(var rule:session.model().pageRules().values())if(rule.child().equals(page))initializationRules.add(rule.id());
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
                    if(rule.child().equals(page)&&initializationRules.remove(rule.id()))
                        batch.add(new UnbindPage(rule.id()));
                apply(new MutationBatch(batch,"debug:allocation"));
                locations.put(node.key,location); remaining--;
            }
            if(previous==remaining)throw new IllegalArgumentException("Ownership cycle has no creatable parent");
        }
        return locations;
    }
    private static Group representative(Map<Group,Group> groups,Group group) {
        Group root=group;
        while(!groups.get(root).equals(root))root=groups.get(root);
        while(!group.equals(root)) {Group next=groups.get(group);groups.put(group,root);group=next;}
        return root;
    }
    private static Map<EdgePorts,TopologyEdge.Direction> resolveEdges(Set<Edge> edges,Map<ObjectKey,ViewLocation> locations) {
        var resolved=new LinkedHashMap<EdgePorts,TopologyEdge.Direction>();
        for(var edge:edges) {
            var a=locations.get(edge.a());var b=locations.get(edge.b());var aPort=edge.aPort();var bPort=edge.bPort();var direction=edge.direction();
            if(a.nodeId()>b.nodeId()||a.equals(b)&&aPort.compareTo(bPort)>0) {
                var swap=a;a=b;b=swap;var port=aPort;aPort=bPort;bPort=port;direction=direction.reversed();
            }
            resolved.merge(new EdgePorts(a,b,aPort,bPort),direction,DebugVisualizationAdapter::mergeDirections);
        }
        return resolved;
    }
    private static TopologyEdge.Direction mergeDirections(TopologyEdge.Direction a,TopologyEdge.Direction b) {
        if(a==b||b==TopologyEdge.Direction.NONE)return a;
        if(a==TopologyEdge.Direction.NONE)return b;
        return TopologyEdge.Direction.BOTH;
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
    private List<ViewLocation> deletionOrder(Set<ViewLocation> deleted) {
        var children=new HashMap<ViewLocation,Integer>();
        var parents=new HashMap<ViewLocation,List<ViewLocation>>();
        deleted.forEach(node->children.put(node,0));
        for(var binding:session.model().ownership().values()) {
            var pre=binding.key().pre();var nxt=binding.key().nxt();
            if(deleted.contains(pre)&&deleted.contains(nxt)) {
                children.merge(pre,1,Integer::sum);parents.computeIfAbsent(nxt,ignored->new ArrayList<>()).add(pre);
            }
        }
        var leaves=new ArrayDeque<ViewLocation>();
        deleted.stream().filter(node->children.get(node)==0).forEach(leaves::add);
        var ordered=new ArrayList<ViewLocation>();
        while(!leaves.isEmpty()) {
            var node=leaves.removeFirst();ordered.add(node);
            for(var parent:parents.getOrDefault(node,List.of()))if(children.merge(parent,-1,Integer::sum)==0)leaves.add(parent);
        }
        if(ordered.size()!=deleted.size())throw new IllegalArgumentException("Cyclic ownership cleanup");
        return ordered;
    }
    private boolean exists(ViewLocation location) {
        var page=session.model().pages().get(location.pageId()); return page!=null&&page.nodes().containsKey(location.nodeId());
    }
    private void apply(MutationBatch batch) {
        var result=session.modify(batch);
        if(!result.succeeded())throw new IllegalArgumentException(result.error().code()+": "+result.error().message());
    }
    private void touch(RuntimeEventBatch events,DebugMemoryReader memory,Map<ObjectKey,Node> nodes,
                       Map<ObjectKey,ViewLocation> locations,Set<ViewLocation> deleted,List<VisualizationCommand> commands) {
        for(var event:events.events()) {
            if(event instanceof RuntimeEvent.Accessed access) {
                addTouch(access.range(),access.access()==RuntimeEvent.Access.READ?AccessKind.READ:AccessKind.WRITE,memory,nodes,locations,commands);
            } else if(event instanceof RuntimeEvent.Copied copy) {
                addTouch(copy.source(),AccessKind.READ,memory,nodes,locations,commands);
                addTouch(copy.destination(),AccessKind.WRITE,memory,nodes,locations,commands);
            } else if(event instanceof RuntimeEvent.Allocated allocated) {
                addTouch(allocated.range(),AccessKind.ALLOCATE,memory,nodes,locations,commands);
            } else if(event instanceof RuntimeEvent.Released released) {
                // Replay actual release order after structural cleanup. A freed node navigates to its
                // nearest surviving selected upstream, just as a core DeleteNode would do at finish.
                for(var entry:identities.locations().entrySet())if(entry.getKey().allocationId()==released.range().allocationId()
                        &&deleted.contains(entry.getValue())) {
                    ViewLocation fallback=session.model().node(entry.getValue()).parents().selected();
                    while(fallback!=null&&deleted.contains(fallback))fallback=session.model().node(fallback).parents().selected();
                    if(fallback==null) {
                        if(session.model().interaction().options().autoNavigate())commands.add(new ClearFocus());
                        continue;
                    }
                    Node target=null;
                    for(var candidate:nodes.values())if(locations.get(candidate.key).equals(fallback)) {target=candidate;break;}
                    commands.add(new Touch(target==null?currentPath(fallback):path(fallback,target,locations),AccessKind.DELETE));
                }
            }
        }
    }
    private void addTouch(RuntimeEvent.MemoryRange range,AccessKind kind,DebugMemoryReader memory,Map<ObjectKey,Node> nodes,
                          Map<ObjectKey,ViewLocation> locations,List<VisualizationCommand> commands) {
        for(var node:nodes.values())if(overlaps(memory,node.key,0,descriptor(node.key.descriptorKey()).minimumSize(),range))
            commands.add(new Touch(path(locations.get(node.key),node,locations),kind));
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;identities=new DebugObjectIdentityRegistry<>();observations=new DebugObjectIdentityRegistry<>();
        rootIdentities.clear();roots.clear();manual.clear();pendingManual.clear();pendingRoots.clear();descriptors.clear();results.clear();
        if(ownsSession)session.close();
        published=session.snapshot();
    }
}
