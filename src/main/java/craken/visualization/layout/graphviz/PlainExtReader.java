package craken.visualization.layout.graphviz;

import craken.visualization.layout.*;
import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutResult.*;
import static craken.visualization.layout.LayoutException.Code.INVALID_RESULT;

public final class PlainExtReader {
    public LayoutResult read(String output, DotGraphWriter.EncodedGraph encoded, LayoutRequest request, String version) {
        try {
            if (output.length() > 16 * 1024 * 1024) invalid("Output too large");
            boolean graph = false, stopped = false, edgesStarted = false;
            double graphWidth = 0, graphHeight = 0, padding = request.hints().padding();
            var seenNodes = new HashSet<String>(); var seenEdges = new HashSet<Long>();
            var bounds = new LinkedHashMap<ViewLocation, Rect>(); var paths = new LinkedHashMap<Long, EdgePath>();
            for (String line : output.split("\\R")) {
                if (line.isBlank()) continue;
                if (stopped) invalid("Data after stop");
                var tokens = tokenize(line);
                switch (tokens.getFirst()) {
                    case "graph" -> {
                        if (graph || tokens.size() != 4 || numeric(tokens.get(1)) != 1) invalid("Unexpected graph/scaling header");
                        graphWidth = numeric(tokens.get(2)); graphHeight = numeric(tokens.get(3));
                        if (graphWidth < 0 || graphHeight < 0) invalid("Negative graph bounds"); graph = true;
                    }
                    case "node" -> {
                        if (!graph || edgesStarted || tokens.size() != 11) invalid("Unexpected node record");
                        String name = tokens.get(1); var unit = encoded.nodes().get(name);
                        if (unit == null || !seenNodes.add(name)) invalid("Unknown/duplicate node");
                        double x = numeric(tokens.get(2)), y = numeric(tokens.get(3));
                        double width = numeric(tokens.get(4)) * 96, height = numeric(tokens.get(5)) * 96;
                        if (Math.abs(width - unit.size().width()) > Math.max(.05, width * 1e-4)
                                || Math.abs(height - unit.size().height()) > Math.max(.05, height * 1e-4)) invalid("Changed fixed node size");
                        double left = x * 96 - unit.size().width() / 2 + padding;
                        double top = (graphHeight - y) * 96 - unit.size().height() / 2 + padding;
                        for (var member : unit.members()) {
                            var local = member.bounds();
                            bounds.put(member.node(), new Rect(left + local.x(), top + local.y(), local.width(), local.height()));
                        }
                    }
                    case "edge" -> {
                        if (!graph || seenNodes.size() != encoded.nodes().size() || tokens.size() < 6) invalid("Unexpected edge record");
                        edgesStarted = true;
                        var edge = encoded.edges().get(new DotGraphWriter.EdgeKey(tokens.get(1), tokens.get(2)));
                        if (edge == null || !seenEdges.add(edge.id())) invalid("Unknown/duplicate edge endpoints");
                        int count = Integer.parseInt(tokens.get(3));
                        if (count < 4 || (count - 1) % 3 != 0 || count > 100_000 || tokens.size() != 6 + 2 * count)
                            invalid("Invalid cubic control point count");
                        var points = new ArrayList<Point>();
                        for (int i = 0; i < count; i++) points.add(new Point(numeric(tokens.get(4 + 2 * i)) * 96 + padding,
                                (graphHeight - numeric(tokens.get(5 + 2 * i))) * 96 + padding));
                        var segments = new ArrayList<Segment>();
                        for (int i = 1; i < count; i += 3) segments.add(new Cubic(points.get(i), points.get(i + 1), points.get(i + 2)));
                        paths.put(edge.id(), new EdgePath(points.getFirst(), segments));
                    }
                    case "stop" -> { if (!graph || tokens.size() != 1) invalid("Unexpected stop"); stopped = true; }
                    default -> invalid("Unknown plain-ext record");
                }
            }
            if (!stopped || seenNodes.size() != encoded.nodes().size() || seenEdges.size() != encoded.edges().size())
                invalid("Incomplete native geometry");
            var extent = request.units().isEmpty() ? new Rect(0, 0, 0, 0)
                    : new Rect(0, 0, graphWidth * 96 + 2 * padding, graphHeight * 96 + 2 * padding);
            return new LayoutResult(request.stamp(), bounds, paths, extent, "graphviz/neato-major", version);
        } catch (LayoutException error) { throw error; }
        catch (RuntimeException malformed) { throw new LayoutException(INVALID_RESULT, "Malformed plain-ext geometry", malformed); }
    }
    private static double numeric(String token) {
        double number = Double.parseDouble(token); if (!Double.isFinite(number)) invalid("Non-finite native geometry"); return number;
    }
    private static List<String> tokenize(String line) {
        var tokens = new ArrayList<String>(); var current = new StringBuilder();
        boolean quoted = false, escaped = false, started = false; int htmlDepth = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) { current.append(c); escaped = false; started = true; }
            else if (c == '\\' && quoted) escaped = true;
            else if (c == '"') { quoted = !quoted; started = true; }
            else if (c == '<' && !quoted) { htmlDepth++; current.append(c); started = true; }
            else if (c == '>' && !quoted && htmlDepth > 0) { htmlDepth--; current.append(c); started = true; }
            else if (Character.isWhitespace(c) && !quoted && htmlDepth == 0) {
                if (started) { tokens.add(current.toString()); current.setLength(0); started = false; }
            } else { current.append(c); started = true; }
        }
        if (quoted || escaped || htmlDepth != 0) invalid("Unterminated native quote/HTML label");
        if (started) tokens.add(current.toString()); return tokens;
    }
    private static void invalid(String message) { throw new LayoutException(INVALID_RESULT, message); }
}
