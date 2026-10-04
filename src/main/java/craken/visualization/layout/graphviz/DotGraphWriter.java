package craken.visualization.layout.graphviz;

import java.util.*;
import craken.visualization.layout.LayoutRequest;
import static craken.visualization.layout.LayoutRequest.*;

public final class DotGraphWriter {
    public record EdgeKey(String tail, String head) {}
    public record EncodedGraph(String dot, Map<String, Unit> nodes, Map<EdgeKey, Link> edges,
                               Map<PortRef, String> endpoints) {
        public EncodedGraph { nodes = Map.copyOf(nodes); edges = Map.copyOf(edges); endpoints = Map.copyOf(endpoints); }
    }
    public EncodedGraph write(LayoutRequest request) {
        var nodes = new LinkedHashMap<String, Unit>(); var edges = new LinkedHashMap<EdgeKey, Link>();
        var endpoints = new LinkedHashMap<PortRef, String>();
        var dot = new StringBuilder("digraph G {\ngraph [mode=major,maxiter=200,overlap=prism,sep=\"+10\",splines=polyline,start=42];\n");
        dot.append("node [shape=box,fixedsize=true,margin=0];\nedge [dir=none,arrowhead=none,arrowtail=none];\n");
        for (var unit : request.units().stream().sorted(Comparator.comparingLong(u -> u.node().nodeId())).toList()) {
            if (unit.size().width() < .96 || unit.size().height() < .96)
                throw new IllegalArgumentException("Native fixed dimensions must be at least 0.01 inch");
            String name = "n" + unit.node().nodeId(); nodes.put(name, unit);
            var ports = unit.ports().stream().sorted(Comparator.comparingLong((Port p) -> p.ref().node().nodeId())
                    .thenComparing(p -> p.ref().key())).toList();
            int widthPoints = Math.max(1, (int)Math.ceil(unit.size().width() * .75));
            int heightPoints = Math.max(1, (int)Math.ceil(unit.size().height() * .75));
            var label = new StringBuilder("<<TABLE BORDER=\"0\" CELLBORDER=\"0\" CELLPADDING=\"0\" CELLSPACING=\"0\" FIXEDSIZE=\"TRUE\" WIDTH=\"")
                    .append(widthPoints).append("\" HEIGHT=\"").append(heightPoints).append("\"><TR>");
            for (int i = 0; i < ports.size(); i++) {
                String port = "p" + i; endpoints.put(ports.get(i).ref(), name + ":" + port);
                label.append("<TD PORT=\"").append(port).append("\" FIXEDSIZE=\"TRUE\" WIDTH=\"")
                        .append(Math.max(1, widthPoints / Math.max(1, ports.size()))).append("\" HEIGHT=\"")
                        .append(heightPoints).append("\"> </TD>");
            }
            label.append("</TR></TABLE>>");
            dot.append(name).append(" [width=\"").append(unit.size().width() / 96).append("\",height=\"")
                    .append(unit.size().height() / 96).append("\",label=")
                    .append(ports.isEmpty() ? "\"\"" : label).append("];\n");
        }
        for (var link : request.links().stream().sorted(Comparator.comparingLong(Link::id)).toList()) {
            String tail = endpoints.get(link.tail()), head = endpoints.get(link.head());
            edges.put(new EdgeKey(tail, head), link);
            dot.append(tail).append(" -> ").append(head).append(" [id=\"e").append(link.id()).append("\"];\n");
        }
        return new EncodedGraph(dot.append("}\n").toString(), nodes, edges, endpoints);
    }
}
