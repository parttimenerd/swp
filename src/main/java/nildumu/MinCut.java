package nildumu;

import java.util.*;
import java.util.function.*;

import swp.util.*;

import static nildumu.Context.INFTY;
import static nildumu.Lattices.*;
import static nildumu.Lattices.B.U;
import static nildumu.Util.p;
import static swp.util.Tuple.*;

/**
 * Computation of the minimum cut on graphs.
 *
 * Min-vertex-cut is transformed into min-cut via a basic transformation, described first in
 * S. Even Graph Algorithms p. 122
 */
public class MinCut {

    public static class ComputationResult {
        public final Set<Bit> minCut;

        public ComputationResult(Set<Bit> minCut) {
            this.minCut = minCut;
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

    public static class EdmondsKarp extends Algorithm {

        final static class InnerEdge {
            final Bit bit;
            final boolean forward;
            final long capacity;
            long flow = 0;

            InnerEdge(Bit bit, long capacity, boolean forward) {
                this.bit = bit;
                this.forward = forward;
                this.capacity = capacity;
            }

            @Override
            public String toString() {
                return bit.toString() + (forward ? "→" : "←");
            }

            public long freeCapacity(){
                return capacity - flow;
            }

            public boolean hasFreeCapacity(){
                return capacity > flow;
            }
        }

        final static class InnerEdges {
            final InnerEdge forward;
            final InnerEdge backward;

            InnerEdges(InnerEdge forward, InnerEdge backward) {
                this.forward = forward;
                this.backward = backward;
            }

            InnerEdges(Bit bit, long weight){
                this(new InnerEdge(bit, weight,true), new InnerEdge(bit, 0,false));
            }

            void alterFlow(long diff){
                forward.flow += diff;
                backward.flow -= diff;
            }
        }

        final static class OuterEdge {
            final Bit start;
            final Bit end;

            OuterEdge(Bit start, Bit end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public String toString() {
                return String.format("%s → %s", start, end);
            }
        }

        /**
         * Information stored in each bit
         */
        final static class BitInfo {
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

            BitInfo(long innerCapacity) {
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
                return innerCapacity == innerFlow;
            }

            void increaseFlow(Bit dep, long delta){
                cRev.put(dep, cRev.get(dep) + delta);
            }

            long getFlow(Bit dep){
                return cRev.get(dep);
            }
        }

        static class Graph {

            final Function<Bit, Integer> weights;
            final Bit source;
            final Bit sink;
            final Map<Bit, Set<Bit>> revs = new DefaultMap<>((map, b) -> new HashSet<>());
            final Map<Bit, Set<OuterEdge>> outerEdges = new HashMap<>();

            Graph(Function<Bit, Integer> weights, Bit source, Bit sink){
                this.weights = weights;
                this.source = source;
                this.sink = sink;
                initBits();
            }

            void initBits(){
                bl.walkBits(source, b -> {
                    BitInfo info = new BitInfo(weights.apply(b));
                    b.store = info;
                    Set<OuterEdge> outers = new HashSet<>();
                    b.deps().forEach(d -> {
                        revs.get(d).add(b);
                        info.cRev.put(d, 0L);
                        outers.add(new OuterEdge(b, d));
                    });
                    outerEdges.put(b, outers);
                }, b -> b.deps().contains(sink));

            }

            BitInfo info(Bit bit){
                return (BitInfo)bit.store;
            }

            boolean inGraphOuter(Bit start, Bit end){
                return start.deps().contains(end);
            }

            boolean isGraphOuter(OuterEdge edge){
                return edge.start.deps().contains(edge.end);
            }

            Set<OuterEdge> outerEdges(Bit start){
                return outerEdges.get(start);
            }

            void increaseFlow(OuterEdge edge, long delta){
                if (isGraphOuter(edge)){
                    info(edge.start).increaseFlow(edge.end, delta);
                } else {
                    info(edge.end).increaseFlow(edge.start, -delta);
                }
            }


            long augmentPath(){
                // see https://en.wikipedia.org/wiki/Edmonds%E2%80%93Karp_algorithm
                Queue<Bit> q = new ArrayDeque<>();
                Map<Bit, Pair<Bit, Boolean>> predInPath = new HashMap<>(); // bit → predecessor + forward edge?
                // BFS
                q.add(source);

                /*
                Is this really correct:
                Consider the following

                   sink
                  ↑    ↑
                 a      b
                 ↑ ↓    ↑
                 ↑   c
                 ↑   ↑
                 ↑
                source

                The inner edges of the node c can be skipped

                Not a problem…
                 */

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
                        if (!predInPath.containsKey(dep)){
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
                        if (!predInPath.containsKey(rev)){
                            if (info(cur).residualBackEdgeCapacity() > 0 && info(rev).getFlow(cur) > 0) {
                                predInPath.put(rev, p(cur, false));
                                q.add(rev);
                            }
                        }
                    }
                }
                if (!predInPath.containsKey(sink)){
                    return 0;
                }
                // augmenting path found
                long df = INFTY;
                List<Pair<Bit, Boolean>> path = new ArrayList<>();
                // calculate the flow on this path
                for (Bit cur = sink; cur == source; cur = predInPath.get(cur).first){
                    Pair<Bit, Boolean> prePair = predInPath.get(cur);
                    if (prePair.second){
                        // took the forward edge through the node
                        // pre[end] → cur[start] → cur[end]
                        // the capacity of the outer edge is infinite and therefore has not to be considered
                        df = Math.min(df, info(cur).residualCapacity());
                    } else {
                        // took the backward edge…
                        // from cur[end] → cur[start] → pre[end]
                        // the capacity of the outer backedge has to be considered
                        df = Math.min(df, Math.min(info(cur).residualBackEdgeCapacity(), info(prePair.first).getFlow(cur)));
                    }
                }
                // subtract the flow
                for (Bit cur = sink; cur == source; cur = predInPath.get(cur).first){
                    Pair<Bit, Boolean> prePair = predInPath.get(cur);
                    Bit pre = prePair.first;
                    if (prePair.second){
                        // took the forward edge through the node
                        // pre[end] → cur[start] → cur[end]
                        info(pre).increaseFlow(cur, df);
                        info(cur).increaseInnerFlow(df);
                    } else {
                        // took the backward edge…
                        // from cur[end] → cur[start] → pre[end]
                        info(cur).increaseInnerFlow(-df);
                        info(pre).increaseFlow(cur, -df);
                    }
                }
                return df;
            }

            Set<Bit> findMinCut(){
                long delta = 0;
                while ((delta = augmentPath()) > 0);
                Queue<Bit> q = new ArrayDeque<>();
                Set<Bit> minCut = new HashSet<>();
                q.addAll(sink.deps());
                while (!q.isEmpty()){
                    Bit cur = q.poll();
                    if (info(cur).saturated()){
                        minCut.add(cur);
                    } else {
                        q.addAll(cur.deps());
                    }
                }
                return minCut;
            }
        }

        EdmondsKarp(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Integer> weights) {
            super(sourceNodes, sinkNodes, weights);
            removeTwoNodeCycles();
        }

        /**
         * Place a filler node in between → there is only one edge between two nodes
         */
        private void removeTwoNodeCycles(){
            HashSet<Bit> alreadyVisited = new HashSet<>();
            for (Bit sourceNode : sourceNodes) {
                bl.walkBits(sourceNode, b -> {
                    for (Bit d : b.deps()) {
                        if (d.deps().contains(b)){ // cycle found
                            Bit filler = bl.create(U, ds.create(d));
                            b.alterDependencies(dep -> dep == d ? filler : dep);
                        }
                    }
                }, sinkNodes::contains, alreadyVisited);
            }
        }

        @Override
        public ComputationResult compute() {
            Bit source = bl.forceCreateXBit();
            Bit sink = bl.forceCreateXBit();
            source.addDependencies(sourceNodes);
            sinkNodes.forEach(b -> b.addDependency(sink));
            Graph graph = new Graph(weights, source, sink);
            Set<Bit> minCut = graph.findMinCut();
            sinkNodes.forEach(b -> b.removeXDependency(sink));
            return new ComputationResult(minCut);
        }
    }

    /**
     * Chooses the (asymtotically) best algorithm that is implemented
     * @return
     */
    public static ComputationResult compute(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Integer> weights){
        return new EdmondsKarp(sourceNodes, sinkNodes, weights).compute();
    }

}
