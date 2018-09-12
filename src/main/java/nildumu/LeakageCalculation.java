package nildumu;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.uci.ics.jung.algorithms.flows.EdmondsKarpMaxFlow;
import edu.uci.ics.jung.graph.*;
import edu.uci.ics.jung.graph.util.EdgeType;
import swp.util.Pair;

import static nildumu.Context.INFTY;
import static nildumu.Lattices.*;

public class LeakageCalculation {

    /** A replacement rule for bits */
    public static class Rule {
        public final boolean hasInfiniteStartWeight;
        public final Bit start;
        public final Set<Bit> replacements;

        public Rule(Bit start, Set<Bit> replacements, boolean hasInfiniteStartWeight) {
            this.start = start;
            this.replacements = replacements;
            this.hasInfiniteStartWeight = hasInfiniteStartWeight;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s%s → %s",
                    start,
                    hasInfiniteStartWeight ? "∞": "",
                    replacements.stream().map(Bit::toString).sorted().collect(Collectors.joining(" ")));
        }
    }

    /**
     * Rule graph, from the output anchor bits (per sec) to the input anchor bits (per sec).
     *
     * <p>The output anchor bit for a specific level is connected to all outputs that an attacker of
     * this level can view (all outputs with level <= its own). The input anchor bit for a specific
     * level has incoming connections for inputs of all not lower or equals levels.
     */
    public static class Rules {
        public final Map<Sec<?>, Bit> inputAnchorBits;
        public final Map<Sec<?>, Bit> outputAnchorBits;
        public final Map<Bit, Rule> rules;

        public Rules(
                Map<Sec<?>, Bit> inputAnchorBits, Map<Sec<?>, Bit> outputAnchorBits, Map<Bit, Rule> rules) {
            this.inputAnchorBits = inputAnchorBits;
            this.outputAnchorBits = outputAnchorBits;
            this.rules = rules;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("output anchors: %s", stringifyMap(inputAnchorBits)));
            builder.append(String.format("output anchors: %s", stringifyMap(outputAnchorBits)));
            builder.append(rules.values().stream().map(Rule::toString).collect(Collectors.joining("\n")));
            return builder.toString();
        }

        private String stringifyMap(Map<Sec<?>, Bit> map) {
            return map.entrySet()
                    .stream()
                    .map(e -> String.format("%s → %s", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(", "));
        }
    }

    /** An abstract leakage graph */
    public abstract static class AbstractLeakageGraph {
        public final Rules rules;

        protected AbstractLeakageGraph(Rules rules) {
            this.rules = rules;
        }

        public abstract int leakage(Sec<?> outputLevel);

        public Map<Sec<?>, Integer> leakages() {
            SecurityLattice<?> lattice = rules.inputAnchorBits.keySet().iterator().next().lattice();
            return lattice.elements().stream().collect(Collectors.toMap(s -> s, s -> s == lattice.top() ? 0 : leakage(s)));
        }

        public abstract Set<Bit> minCutBits(Sec<?> sec);
    }

    public static interface VisuEdge {
        public String repr();
    }

    public static interface VisuNode {
        public String repr();
        public boolean marked();
        public Parser.MJNode node();
    }

    public static class EdgeGraph {

        enum NodeMode {
            START,
            END
        }

        public static class Node implements VisuNode {
            public final Bit bit;
            public final NodeMode mode;

            public Node(Bit bit, NodeMode mode) {
                this.bit = bit;
                this.mode = mode;
            }

            @Override
            public String toString() {
                if (mode == NodeMode.START){
                    return "s|" + bit.toString();
                }
                if (mode == NodeMode.END){
                    return "e|" + bit.toString();
                }
                return bit.toString();
            }

            @Override
            public boolean marked() {
                return false;
            }

            @Override
            public String repr() {
                return bit.repr();
            }

            @Override
            public Parser.MJNode node() {
                return bit.value().node();
            }
        }

        public static class Edge implements VisuEdge {
            public final Node start;
            public final Node end;
            public final Bit bit;
            public final int weight;

            public Edge(Node start, Node end, Bit bit, int weight) {
                this.start = start;
                this.end = end;
                this.bit = bit;
                this.weight = weight;
            }


            public boolean isInnerBitEdge() {
                return bit != null;
            }

            @Override
            public String toString() {
                if (isInnerBitEdge()){
                    return bit.toString();
                }
                return "";
            }

            @Override
            public String repr() {
                if (isInnerBitEdge()){
                    return bit.repr();
                }
                return "";
            }
        }

        public final Map<Sec<?>, Node> inputAnchorNodes;
        public final Map<Sec<?>, Node> outputAnchorNodes;
        public final List<Node> nodes;
        public final List<Edge> edges;

        private EdgeGraph(
                Map<Sec<?>, Node> inputAnchorNodes,
                Map<Sec<?>, Node> outputAnchorNodes,
                List<Node> nodes,
                List<Edge> edges) {
            this.inputAnchorNodes = inputAnchorNodes;
            this.outputAnchorNodes = outputAnchorNodes;
            this.nodes = nodes;
            this.edges = edges;
        }

        public static EdgeGraph fromRules(Rules rules) {
            Map<Sec<?>, Node> inputs;
            Map<Sec<?>, Node> outputs;
            List<Node> nodes = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            Map<Bit, Pair<Node, Node>> bitToNodes =
                    new DefaultMap<>(
                            new HashMap<>(),
                            new DefaultMap.Extension<Bit, Pair<Node, Node>>() {
                                @Override
                                public Pair<Node, Node> defaultValue(Map<Bit, Pair<Node, Node>> map, Bit key) {
                                    Node start = new Node(key, NodeMode.START);
                                    Node end = new Node(key, NodeMode.END);
                                    if (rules.inputAnchorBits.values().contains(key)
                                            || rules.outputAnchorBits.values().contains(key)) {
                                        start = end;
                                        nodes.add(start);
                                    } else {
                                        nodes.add(start);
                                        nodes.add(end);
                                        edges.add(new Edge(start, end, key, rules.rules.containsKey(key) ? (rules.rules.get(key).hasInfiniteStartWeight ? INFTY : 1) : INFTY));
                                    }
                                    return new Pair<>(start, end);
                                }
                            });
            inputs =
                    rules
                            .inputAnchorBits
                            .entrySet()
                            .stream()
                            .collect(
                                    Collectors.toMap(Map.Entry::getKey, e -> bitToNodes.get(e.getValue()).first));
            outputs =
                    rules
                            .outputAnchorBits
                            .entrySet()
                            .stream()
                            .collect(
                                    Collectors.toMap(Map.Entry::getKey, e -> bitToNodes.get(e.getValue()).first));
            for (Rule rule : rules.rules.values()) {
                Bit start = rule.start;
                for (Bit end : rule.replacements) {
                    edges.add(new Edge(bitToNodes.get(start).second, bitToNodes.get(end).first, null, INFTY));
                }
            }
            return new EdgeGraph(inputs, outputs, nodes, edges);
        }
    }

    public static class JungEdgeGraph extends AbstractLeakageGraph {

        private final EdgeGraph edgeGraph;
        public final DirectedGraph<EdgeGraph.Node, EdgeGraph.Edge> graph;
        private final DefaultMap<Sec<?>, EdmondsKarpMaxFlow<EdgeGraph.Node, EdgeGraph.Edge>> karps;

        public JungEdgeGraph(Rules rules, EdgeGraph edgeGraph) {
            super(rules);
            this.edgeGraph = edgeGraph;
            graph = new DirectedSparseGraph<>();
            edgeGraph.nodes.forEach(graph::addVertex);
            edgeGraph.edges.forEach(e -> graph.addEdge(e, e.start, e.end, EdgeType.DIRECTED));
            karps =
                    new DefaultMap<>(
                            new HashMap<>(),
                            new DefaultMap.Extension<
                                    Sec<?>, EdmondsKarpMaxFlow<EdgeGraph.Node, EdgeGraph.Edge>>() {
                                @Override
                                public EdmondsKarpMaxFlow<EdgeGraph.Node, EdgeGraph.Edge> defaultValue(
                                        Map<Sec<?>, EdmondsKarpMaxFlow<EdgeGraph.Node, EdgeGraph.Edge>> map,
                                        Sec<?> key) {
                                    EdmondsKarpMaxFlow<EdgeGraph.Node, EdgeGraph.Edge> karp =
                                            new EdmondsKarpMaxFlow<>(
                                                    graph,
                                                    edgeGraph.outputAnchorNodes.get(key),
                                                    edgeGraph.inputAnchorNodes.get(key),
                                                    e -> (e.isInnerBitEdge() && e.weight != INFTY) ? 1 : edgeGraph.edges.size(),
                                                    new HashMap<>(),
                                                    () -> new EdgeGraph.Edge(null, null, null, INFTY));
                                    karp.evaluate();
                                    return karp;
                                }
                            });
        }

        @Override
        public int leakage(Sec<?> outputLevel) {
            return karps.get(outputLevel).getMinCutEdges().size();
        }

        @Override
        public Set<Bit> minCutBits(Sec<?> sec) {
            System.out.println("jung flow " + karps.get(sec).getMaxFlow());
            if (karps.get(sec).getMaxFlow() >= edgeGraph.edges.size()){
                Set<Bit> inputs = rules.rules.values().stream().filter(e -> e.replacements.contains(rules.inputAnchorBits.get(sec))).map(e -> e.start).collect(Collectors.toSet());
                Set<Bit> outputs = this.rules.rules.get(this.rules.outputAnchorBits.get(sec)).replacements;
                if (inputs.size() > outputs.size()){
                    return outputs;
                }
                return inputs;
            }
            return karps.get(sec).getMinCutEdges().stream().map(e -> e.bit).collect(Collectors.toSet());
        }
    }

    /**
     * A graph only for visualization
     */
    public static class JungGraph {

        public static class Node implements VisuNode {
            public final Bit bit;
            public final boolean inMinCut;

            public Node(Bit bit, boolean inMinCut) {
                this.bit = bit;
                this.inMinCut = inMinCut;
            }

            @Override
            public String repr() {
                return bit.repr();
            }

            @Override
            public String toString() {
                return bit.toString();
            }

            @Override
            public boolean marked() {
                return inMinCut;
            }

            @Override
            public Parser.MJNode node() {
                return bit.value().node();
            }
        }

        public static class Edge implements VisuEdge {

            @Override
            public String repr() {
                return "";
            }

            @Override
            public String toString() {
                return "";
            }
        }


        public final DirectedSparseGraph<VisuNode, VisuEdge> graph;
        public final VisuNode input;
        public final VisuNode output;

        public JungGraph(Context context, Rules rules, Sec<?> sec, Set<Bit> minCutBits) {
            graph = new DirectedSparseGraph<>();
            List<Node> nodes = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            Map<Bit, Node> bitToNode =
                    new DefaultMap<>(
                            new HashMap<>(),
                            new DefaultMap.Extension<Bit, Node>() {
                                @Override
                                public Node defaultValue(Map<Bit, Node> map, Bit key) {
                                    Node node = new Node(key, minCutBits.contains(key));
                                    graph.addVertex(node);
                                    return node;
                                }
                            });
            input = bitToNode.get(rules.inputAnchorBits.get(sec));
            output = bitToNode.get(rules.outputAnchorBits.get(sec));
            Set<Bit> excludedOutputBits = new HashSet<>(rules.outputAnchorBits.values());
            Set<Bit> excludedInputBits = new HashSet<>(rules.inputAnchorBits.values());
            excludedInputBits.remove(rules.inputAnchorBits.get(sec));
            excludedOutputBits.remove(rules.outputAnchorBits.get(sec));

            Predicate<Bit> ignoreBit = b -> false;

            for (Rule rule : rules.rules.values()) {
                Bit start = rule.start;
                if (excludedOutputBits.contains(start) || ignoreBit.test(start)){
                    continue;
                }
                for (Bit end : rule.replacements) {
                    if (excludedInputBits.contains(end)){
                        continue;
                    }
                    graph.addEdge(new Edge(), bitToNode.get(end), bitToNode.get(start), EdgeType.DIRECTED);
                }
            }
        }

    }

    public static Set<Bit> rule(Bit bit) {
        return bit.deps();
    }

    public static Rules rules(Context context) {
        Map<Sec<?>, Bit> inputAnchorBits = new HashMap<>();
        Map<Sec<?>, Bit> outputAnchorBits = new HashMap<>();
        for (Sec<?> sec : context.sl.elements()) {
            inputAnchorBits.put(sec, bit(String.format("i[%s]", sec)));
            outputAnchorBits.put(sec, bit(String.format("o[%s]", sec)));
        }
        Map<Bit, Rule> rules = new HashMap<>();
        context.walkBits(b -> {
            if (!context.isInputBit(b)) {
                rules.put(b, new Rule(b, rule(b), context.hasInfiniteWeight(b)));
            }
        }, b -> !b.isUnknown());
        Map<Bit, Set<Bit>> inputBitRules = new DefaultMap<>(new HashMap<>(),new DefaultMap.Extension<Bit, Set<Bit>>(){
            @Override
            public Set<Bit> defaultValue(Map<Bit, Set<Bit>> map, Bit key) {
                return new HashSet<>();
            }
        });
        for (Sec<?> sec : context.sl.elements()) {
            Bit outputAnchor = outputAnchorBits.get(sec);
            // an attacker at level sec can see all outputs with level <= sec
            rules.put(
                    outputAnchor,
                    new Rule(
                            outputAnchor,
                            context
                                    .sl
                                    .elements()
                                    .stream()
                                    .map(s -> (Sec<?>) s)
                                    .filter(s -> ((Lattice) context.sl).lowerEqualsThan(s, sec))
                                    .flatMap(s -> context.output.getBits((Sec) s).stream())
                                    .collect(Collectors.toSet()), false));
            // an attacker at level sec can see all inputs h
            Bit inputAnchor = inputAnchorBits.get(sec);
            for (Bit bit : context
                    .sl
                    .elements()
                    .stream()
                    .map(s -> (Sec<?>) s)
                    .filter(s -> !((Lattice) context.sl).lowerEqualsThan(s, sec))
                    .flatMap(s -> context.input.getBits((Sec) s).stream())
                    .collect(Collectors.toSet())) {
                inputBitRules.get(bit).add(inputAnchor);
            }
            inputBitRules.entrySet().forEach(e -> rules.put(e.getKey(), new Rule(e.getKey(), e.getValue(), context.hasInfiniteWeight(e.getKey()))));
        }
        return new Rules(inputAnchorBits, outputAnchorBits, rules);
    }

    static Bit bit(String description) {
        Bit bit = bl.forceCreateXBit();
        Value value = new Value(bit, bit).description(description);
        return bit;
    }

    public static JungEdgeGraph jungEdgeGraph(Context context) {
        Rules rules = rules(context);
        return new JungEdgeGraph(rules, EdgeGraph.fromRules(rules));
    }
}
