package nildumu;

import org.jgrapht.alg.flow.PushRelabelMFImpl;
import org.jgrapht.graph.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import swp.util.Pair;

import static nildumu.Context.INFTY;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.*;
import static nildumu.Util.p;

/**
 * Computation of the minimum cut on graphs.
 *
 * Min-vertex-cut is transformed into min-cut via a basic transformation, described first in
 * S. Even Graph Algorithms p. 122
 */
public class MinCut {

    public static Algo usedAlgo = Algo.GRAPHT_PP;

    public static enum Algo {
        EK_APPROX("approximate Edmonds-Karp"),
        GRAPHT_PP("JGraphT Preflow-Push");

        public final String description;

        Algo(String description){
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public static boolean DEBUG = false;

    public static class ComputationResult {
        public final Set<Bit> minCut;
        public final int maxFlow;

        public ComputationResult(Set<Bit> minCut, long maxFlow) {
            this.minCut = minCut;
            if (maxFlow > INFTY){
                this.maxFlow = INFTY;
            } else {
                this.maxFlow = (int)maxFlow;
            }
            if (minCut.size() > maxFlow) {
                System.err.println("#min cut > max flow");
            }
        }
    }

    public static abstract class Algorithm {

        final Set<Bit> sourceNodes;
        final Set<Bit> sinkNodes;
        final Function<Bit, Integer> weights;

        protected Algorithm(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Integer> weights) {
            this.sourceNodes = sourceNodes;
            this.sinkNodes = sinkNodes;
            this.weights = weights;
        }

        public abstract ComputationResult compute();
    }

    public static class ApproxEdmondsKarp extends Algorithm {

        /**
         * Information stored in each bit
         */
        final static class BitInfo {

            public final int parentVersion;

            /**
             * Flow from the current bit to the bit that is key in the map (flow on the edge
             * between them), the capacity of these outer bit edges is infinite
             */
            Map<Bit, Long> cRev = new HashMap<>();

            /**
             * Inner edge capacity
             */
            final long innerCapacity;

            /**
             * Inner edge flow
             */
            long innerFlow = 0;

            BitInfo(int parentVersion, long innerCapacity) {
                this.parentVersion = parentVersion;
                this.innerCapacity = innerCapacity;
            }

            long residualCapacity(){
                return innerCapacity - innerFlow;
            }

            long residualBackEdgeCapacity(){
                return innerFlow;
            }

            void increaseInnerFlow(long delta){
                innerFlow += delta;
            }

            boolean saturated(){
                return innerCapacity == innerFlow; //|| (innerCapacity == INFTY && innerFlow > INFTY / 2);
            }

            void increaseFlow(Bit dep, long delta){
                try {
                    cRev.put(dep, cRev.get(dep) + delta);
                } catch (NullPointerException ex){}
            }

            long getFlow(Bit dep){
                return cRev.get(dep);
            }
        }

        static class Graph {

            private static int versionCounter = 0;

            final Function<Bit, Integer> weights;
            final Bit source;
            final Bit sink;
            final Map<Bit, Set<Bit>> revs = new DefaultMap<>((map, b) -> new HashSet<>());
            final Set<Bit> bits = new HashSet<>();
            final int version;

            Graph(Function<Bit, Integer> weights, Bit source, Bit sink){
                this.weights = weights;
                this.source = source;
                this.sink = sink;
                this.version = versionCounter++;
                initBits();
            }

            void initBits(){
                bl.walkBits(source, this::initBit, b -> false, bits);
                sink.store = new BitInfo(version, INFTY);
            }

            void initBit(Bit bit){
                BitInfo info = new BitInfo(version, bit == source ? INFTY : weights.apply(bit));
                bit.store = info;
                bit.deps().forEach(d -> {
                    revs.get(d).add(bit);
                    info.cRev.put(d, 0L);
                });
            }

            BitInfo info(Bit bit){
                if (bit.store == null || ((BitInfo)bit.store).parentVersion != version){
                    initBit(bit);
                }
                return (BitInfo)bit.store;
            }

            public class Node {
                static final boolean START = true;
                static final boolean END = false;

                public final Bit bit;
                public final boolean start;

                public Node(Bit bit, boolean start) {
                    this.bit = bit;
                    this.start = start;
                }

                public boolean isStart() {
                    return start;
                }

                public boolean isEnd() {
                    return !start;
                }

                @Override
                public String toString() {
                    return String.format("%s_%s", bit, start ? "s" : "e");
                }

                public BitInfo info(){
                    return Graph.this.info(bit);
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;
                    Node node = (Node) o;
                    return start == node.start &&
                            Objects.equals(bit, node.bit);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(bit, start);
                }
            }

            public Node start(Bit bit){
                return new Node(bit, Node.START);
            }

            public Node end(Bit bit){
                return new Node(bit, Node.END);
            }

            public Node end(Node node){
                return end(node.bit);
            }

            public Node start(Node node){
                return start(node.bit);
            }

            Pair<Set<Bit>, Set<Bit>> reachableInResidualGraph(){
                final boolean START = true;
                final boolean END = false;
                Queue<Node> q = new ArrayDeque<>();
                Set<Node> reachableNodes = new HashSet<>();
                // BFS
                q.add(start(source));
                while (!q.isEmpty()){
                    Node cur = q.poll();
                    if (reachableNodes.contains(cur)){
                        continue;
                    }
                    reachableNodes.add(cur);
                    if (cur.isStart()){
                        // forward only through the node
                        if (!cur.info().saturated()){
                            q.add(end(cur));
                        }

                        // backwards through all rev deps
                        for (Bit rev : revs.get(cur.bit)){
                            if (info(rev).getFlow(cur.bit) > 0){
                                q.add(end(rev));
                            }
                        }
                    }
                    if (cur.isEnd()){
                        // backward only through the node
                        if (cur.info().innerFlow > 0){
                            q.add(start(cur));
                        }

                        // forward through all deps
                        for (Bit dep : cur.bit.deps()){
                                q.add(start(dep));
                        }
                    }
                }
                return p(reachableNodes.stream().filter(Node::isStart).map(n -> n.bit).collect(Collectors.toSet()),
                        reachableNodes.stream().filter(Node::isEnd).map(n -> n.bit).collect(Collectors.toSet()));
            }

            Map<Bit, Pair<Bit, Boolean>> bfs(){
                Queue<Bit> q = new ArrayDeque<>();
                Map<Bit, Pair<Bit, Boolean>> predInPath = new HashMap<>(); // bit → predecessor + forward edge?
                // BFS
                q.add(source);
                predInPath.put(source, null);
                while (!q.isEmpty()){
                    Bit cur = q.poll();
                    // take any outer edge from cur or an reverse edge
                    // but only if they have a non zero residual capacity
                    // (they always have one, as the capacity is ∞)
                    // from end node to end node of other node
                    // essentially goes over an outer edge to another node and through this node on its inner edge)

                    // cur is the end node of the current bit
                    // try first to take the forward edge (as above)
                    // essentially goes over a forward outer edge to another node and through
                    // this node on its forward inner edge
                    // cur[end] → next[start] → next[end]
                    // pre[next] = cur, true
                    // if next == sink || next[start → end] not saturated
                    for (Bit dep : cur.deps()){
                        // only use unused // cannot depend on source
                        if (!predInPath.containsKey(dep) && cur != dep){ // ignore self loops
                            // have to go through the node to get to the sink
                            if (dep == sink || !info(dep).saturated()) {
                                predInPath.put(dep, p(cur, true));
                                q.add(dep);
                            }
                        }
                    }
                    // take a reverse edge
                    // from cur[end] → cur[start] → rev[end]
                    // pre[next] = cur, false
                    // if cur[end → start] is not saturated && (rev[end] → cur[start]).flow > 0
                    for (Bit rev : revs.get(cur)){
                        // only use unused // cannot depend on source
                        // not used && ignore self loops && don't use rev for: rev ⇄ cur, done before
                        if (!predInPath.containsKey(rev) && cur != rev && !cur.deps().contains(rev)){
                            if (info(cur).residualBackEdgeCapacity() > 0 && info(rev).getFlow(cur) > 0) {
                                predInPath.put(rev, p(cur, false));
                                q.add(rev);
                            }
                        }
                    }
                }
                return predInPath;
            }

            Pair<Long, List<Bit>> augmentPath(){
                // see https://en.wikipedia.org/wiki/Edmonds%E2%80%93Karp_algorithm
                Map<Bit, Pair<Bit, Boolean>> predInPath = bfs();
                // augmenting path found
                long df = INFTY;
                // calculate the flow on this path
                List<Bit> path = new ArrayList<>();

                predInPath.forEach((b, p) -> {
                    if (p != null && predInPath.get(p.first) != null && predInPath.get(p.first).first == b){
                        throw new RuntimeException(String.format("cycle: %s, %s, preds = %s", b, predInPath.get(p.first), predInPath));
                    }
                });

                for (Bit cur = sink; predInPath.get(cur) != null; cur = predInPath.get(cur).first){
                    Pair<Bit, Boolean> prePair = predInPath.get(cur);
                    path.add(cur);
                    if (cur == source || prePair.first == sink){
                        continue;
                    }
                    if (prePair.second){
                        // took the forward edge through the node
                        // pre[end] → cur[start] → cur[end]
                        // the capacity of the outer edge is infinite and therefore has not to be considered
                        df = Math.min(df, info(cur).residualCapacity());
                    } else {
                        // took the backward edge…
                        // from pre[end] → pre[start] → cur[end]
                        // the capacity of the outer backedge has to be considered
                        // df = Math.min(df, Math.min(info(cur).residualBackEdgeCapacity(), info(prePair.first).getFlow(cur)));
                        df = Math.min(df, Math.min(info(prePair.first).residualBackEdgeCapacity(), info(cur).getFlow(prePair.first)));
                    }
                }
                // subtract the flow
                for (Bit cur = sink; predInPath.get(cur) != null; cur = predInPath.get(cur).first){
                    Pair<Bit, Boolean> prePair = predInPath.get(cur);
                    Bit pre = prePair.first;
                    if (prePair.second){
                        // took the forward edge through the node
                        // pre[end] → cur[start] → cur[end]
                        //if (pre != sink && pre != source) {
                            info(pre).increaseFlow(cur, df);
                        //}
                        //if (cur != sink && cur != source) {
                            info(cur).increaseInnerFlow(df);
                        //}
                    } else {
                        // took the backward edge…
                        // from pre[end] → pre[start] → cur[end]
                        //if (pre != sink && pre != source) {
                            info(pre).increaseInnerFlow(-df);
                        //}
                        //if (cur != sink && cur != source) {
                            info(cur).increaseFlow(cur, -df);
                        //}
                    }
                }
                return p(path.size() > 0 ? df : 0, path);
            }

           /* void writeDotGraph(String name, Bit source, Bit sink, Pair<Long, List<Bit>> roundRes){
                Path path = Paths.get(name + ".dot");
                DotGraph dotGraph = new DotGraph(name);
                DotGraph.Digraph g = dotGraph.getDigraph();
                String graph = createDotGraph(g, name, source, sink, roundRes).render();
                try {
                    Files.createDirectories(path.toAbsolutePath().getParent());
                    Files.write(path, Arrays.asList(graph.split("\n")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            private DotGraph.Digraph createDotGraph(DotGraph.Digraph g, String name, Bit source, Bit sink, Pair<Long, List<Bit>> roundRes){
                Function<Bit, String> dotLabel = b -> (weights.apply(b) == INFTY ? "∞" : weights.apply(b)) +
                        "|" + info(b).innerFlow + "|" + b.bitNo + (b == source ? "source" : "") + (b == sink ? "sink" : "");
                bl.walkBits(source, b -> {
                    DotGraph.Node node = g.addNode(b.hashCode());
                    node.setLabel(dotLabel.apply(b));
                    List<String> options = new ArrayList<>();
                    if (roundRes != null && roundRes.second.contains(b)){
                        options.add("color=red");
                        node.setLabel("f" + roundRes.first + "|" + node.getLabel());
                    }
                    if (info(b).saturated()){
                        options.add("style=filled");
                        options.add("fillcolor=gray");
                    }
                    node.setOptions(String.join(";", options));
                    b.deps().forEach(d -> g.addAssociation(b.hashCode(), d.hashCode()).setLabel(info(b).getFlow(d) + ""));
                }, b -> b == sink, new HashSet<>());
                return g;
            }*/

            ComputationResult findMinCut(){
                Pair<Long, List<Bit>> roundRes;
                /*if (DEBUG) {
                    writeDotGraph("0", source, sink, null);
                }*/
                int iteration = 0;
                long flow = 0;
                while ((roundRes = augmentPath()).first > 0){
                    iteration++;
                    /*if (DEBUG) {
                        writeDotGraph(iteration + "", source, sink, roundRes);
                    }*/
                    flow += roundRes.first;
                }
                // reachable from source
                Pair<Set<Bit>, Set<Bit>> reachableStartAndEndNodes = reachableInResidualGraph();
                Set<Bit> reachableStartNodes = reachableStartAndEndNodes.first;
                Set<Bit> reachableEndNodes = reachableStartAndEndNodes.second;
                Set<Bit> union = new HashSet<>(reachableStartNodes);
                union.addAll(reachableEndNodes);
                Set<Bit> intersection = new HashSet<>(reachableStartNodes);
                intersection.retainAll(reachableEndNodes);
                Set<Bit> minCut = new HashSet<>(union);
                minCut.removeAll(intersection);


                //Set<Bit> minCut = reachable.stream().filter(b -> b.deps().stream().anyMatch(d -> !reachable.contains(b))).collect(Collectors.toSet());

                /*Set<Bit> reachable = new HashSet<>();
                bl.walkBits(source, b -> {}, b -> info(b).saturated(), reachable);
                // remove bits that are only reachable via an edge from source with flow == 1
                info(source).cRev.forEach((b, f) -> {
                    //reachable.remove(b);
                });
                Set<Bit> minCut = reachable.stream().filter(b -> info(b).saturated() && b.deps().stream().anyMatch(d -> !reachable.contains(d))).collect(Collectors.toSet());*/
                //System.out.println(String.format("reachable start nodes %s, reachable end nodes %s, minCut %s", reachableStartNodes, reachableEndNodes, minCut));
                //System.out.println("flow " + flow);
                if (flow >= INFTY / 2 || (minCut.isEmpty() && flow > 0)){
                    Set<Bit> sourceDeps = nonInftyDeps(source);
                    Set<Bit> sinkDeps = nonInftyRevDeps(sink);
                    if (sourceDeps.isEmpty() || sinkDeps.isEmpty()){
                        sourceDeps = source.deps();
                        sinkDeps = revs.get(sink);
                    }
                    if (sourceDeps.size() < sinkDeps.size()){
                        return new ComputationResult(sourceDeps, sourceDeps.size());
                    }
                    return new ComputationResult(sinkDeps, sinkDeps.size());
                }
                //System.out.println(String.format("reachable %s, minCut %s", reachable, minCut));
                //minCut = minimize(minCut);
                return new ComputationResult(minCut, minCut.stream().mapToInt(b -> weights.apply(b)).sum());
            }

            Set<Bit> nonInftyDeps(Bit bit){
                Set<Bit> ret = new HashSet<>();
                Util.Box<Boolean> wouldIncludeSink = new Util.Box<>(false);
                bl.walkBits(bit, b -> {
                    if (info(b).innerCapacity != INFTY && b != bit){
                        ret.add(b);
                    }
                    if (b == sink){
                        wouldIncludeSink.val = true;
                    }
                }, b -> info(b).innerCapacity != INFTY && b != bit, new HashSet<>());
                return wouldIncludeSink.val ? bit.deps() : ret;
            }

            Set<Bit> nonInftyRevDeps(Bit bit){
                Set<Bit> ret = new HashSet<>();
                Util.Box<Boolean> wouldIncludeSource = new Util.Box<>(false);
                bl.walkBits(bit, b -> {
                    if (info(b).innerCapacity != INFTY && b != bit){
                        ret.add(b);
                    }
                    if (b == source){
                        wouldIncludeSource.val = true;
                    }
                }, b -> info(b).innerCapacity != INFTY && b != bit, new HashSet<>(), revs::get);
                return wouldIncludeSource.val ? revs.get(bit) : ret;
            }

            Set<Bit> minimize(Set<Bit> bits){
                return bits.stream().filter(b -> reachable(b, bits).contains(sink)).collect(Collectors.toSet());
            }

            Set<Bit> reachable(Bit bit, Set<Bit> anchors){
                Set<Bit> reachable = new HashSet<>();
                bl.walkBits(bit, b -> {
                    if (b != bit && (anchors.contains(b) || b == sink)){
                        reachable.add(b);
                    }
                }, b -> reachable.contains(b));
                return reachable;
            }
        }

        ApproxEdmondsKarp(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Integer> weights) {
            super(sourceNodes, sinkNodes, weights);
        }

        @Override
        public ComputationResult compute() {
            Bit source = bl.create(U);
            Bit sink = bl.forceCreateXBit();
            source.addDependencies(sourceNodes);
            sinkNodes.forEach(b -> b.addDependency(sink));
            Graph graph = new Graph(weights, source, sink);
            ComputationResult minCut = graph.findMinCut();
            sinkNodes.forEach(b -> b.removeXDependency(sink));
            return minCut;
        }
    }

    private static class Vertex {
        public final Bit bit;
        public final boolean isStart;

        private Vertex(Bit bit, boolean isStart) {
            this.bit = bit;
            this.isStart = isStart;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bit, isStart);
        }

        @Override
        public boolean equals(Object obj) {
            return obj.getClass() == this.getClass() && bit.equals(((Vertex)obj).bit) && isStart == ((Vertex)obj).isStart;
        }

        @Override
        public String toString() {
            return bit + "_" + (isStart ? "s" : "e");
        }
    }


    public static class GraphTPP extends Algorithm {

        protected GraphTPP(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Integer> weights) {
            super(sourceNodes, sinkNodes, weights);
        }

        @Override
        public ComputationResult compute() {
            SimpleDirectedWeightedGraph<Vertex, DefaultWeightedEdge> graph =
                    new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            Vertex source = new Vertex(bl.forceCreateXBit(), false);
            Vertex sink = new Vertex(bl.forceCreateXBit(), true);
            graph.addVertex(source);
            graph.addVertex(sink);
            double infty = Bit.getNumberOfCreatedBits() * 2;
            Map<Bit, Pair<Vertex, Vertex>> bitToNodes =
                    new DefaultMap<>((map, bit) -> {
                        Vertex start = new Vertex(bit, true);
                        Vertex end = new Vertex(bit, false);
                        graph.addVertex(start);
                        graph.addVertex(end);
                        DefaultWeightedEdge edge = graph.addEdge(start, end);
                        graph.setEdgeWeight(edge, weights.apply(bit) == INFTY ? infty : 1);
                        return new Pair<>(start, end);
                    });
            Set<Bit> alreadyVisited = new HashSet<>();
            for (Bit bit : sourceNodes){
                bl.walkBits(bit, b -> {
                    for (Bit d : b.deps()){
                        graph.setEdgeWeight(graph.addEdge(bitToNodes.get(b).second, bitToNodes.get(d).first), infty * infty);
                    }
                }, sinkNodes::contains, alreadyVisited);
                graph.setEdgeWeight(graph.addEdge(source, bitToNodes.get(bit).first), infty * infty);
            }
            for (Bit bit : sinkNodes){
                graph.setEdgeWeight(graph.addEdge(bitToNodes.get(bit).second, sink), infty * infty);
            }
            PushRelabelMFImpl<Vertex, DefaultWeightedEdge> pp = new PushRelabelMFImpl<Vertex, DefaultWeightedEdge>(graph, 0.5);
            double maxFlow = pp.calculateMinCut(source, sink);
            Set<Bit> minCut = pp.getCutEdges().stream().map(e -> graph.getEdgeSource(e).bit).collect(Collectors.toSet());
            return new ComputationResult(minCut, Math.min(Math.round(maxFlow), Math.min(sourceNodes.size(), sinkNodes.size())));
        }
    }

    /**
     * Choose the algorithm by setting the static {@link MinCut#usedAlgo} variable
     */
    public static ComputationResult compute(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Integer> weights){
        Algorithm cur = null;
        switch (usedAlgo){
            case GRAPHT_PP:
                cur = new GraphTPP(sourceNodes, sinkNodes, weights);
                break;
            case EK_APPROX:
                cur = new ApproxEdmondsKarp(sourceNodes, sinkNodes, weights);
        }
        return cur.compute();
    }

    public static ComputationResult compute(Context context, Sec<?> sec){
        con = context;
        if (sec == context.sl.top()){
            return new ComputationResult(Collections.emptySet(), 0);
        }
        return compute(context.sources(sec), context.sinks(sec), context::weight);
    }

    private static Context con;

    public static Map<Sec<?>, ComputationResult> compute(Context context){
        return context.sl.elements().stream()
                .collect(Collectors.toMap(s -> s, s -> s == context.sl.top() ?
                        new ComputationResult(Collections.emptySet(), 0) :
                        compute(context, s)));
    }
}
