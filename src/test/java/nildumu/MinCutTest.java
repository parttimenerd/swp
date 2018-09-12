package nildumu;

import com.pholser.junit.quickcheck.*;
import com.pholser.junit.quickcheck.generator.*;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;

import org.junit.runner.RunWith;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static nildumu.Context.INFTY;
import static nildumu.Lattices.*;
import static nildumu.MethodInvocationHandler.*;
import static nildumu.Util.Box;
import static nildumu.Util.p;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class MinCutTest implements MinimalCounterexampleHook {

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface BGConfig {

        int maxBits() default 5;

        int maxDeps() default 5;

        int maxSources() default 1;

        int maxSinks() default 1;
    }

    public static class BitGraphWrapper {
        public final BitGraph g;
        public final int bitCount;
        public final int depCount;
        public final Set<Bit> bits;

        public BitGraphWrapper(BitGraph g, int bitCount, int depCount, Set<Bit> bits) {
            this.g = g;
            this.bitCount = bitCount;
            this.depCount = depCount;
            this.bits = bits;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }
    }

    public static class BitGraphs extends Generator<BitGraphWrapper> {

        private int maxBitNum = 5;
        private int maxDepNum = 5;
        private int maxSourceNum = 1;
        private int maxSinkNum = 1;

        public BitGraphs() {
            super(BitGraphWrapper.class);
        }

        public void configure(BGConfig config) {
            maxBitNum = config.maxBits();
            maxDepNum = config.maxDeps();
            maxSourceNum = config.maxSources();
            maxSinkNum = config.maxSinks();
            assert maxSourceNum < maxBitNum && maxSinkNum < maxBitNum;
        }

        @Override
        public BitGraphWrapper generate(SourceOfRandomness random, GenerationStatus status) {
            vl.bitWidth = INFTY;
            Bit.resetNumberOfCreatedBits();
            int bitNum = random.nextInt(2, maxBitNum);
            int sourceNum = random.nextInt(1, Math.min(bitNum - 1, maxSourceNum));
            int sinkNum = random.nextInt(1, Math.min(bitNum - sourceNum, maxSinkNum));
            Context con = new Context(BasicSecLattice.LOW, 2);
            Box<Integer> nonInftyBitCount = new Box<>(0);
            Set<Bit> bits = IntStream.range(0, bitNum).mapToObj(i -> {
                Bit bit = bl.create(B.U);
                int weight = random.nextInt(10 ) <= 1 ? INFTY : 1;
                con.weight(bit, weight);
                if (weight != INFTY){
                    nonInftyBitCount.val++;
                }
                return bit;
            }).collect(Collectors.toSet());
            if (nonInftyBitCount.val == 0){
                // we only want graphs that make some sense, graphs with infinite weights aren't meaningful
                // the real reason is that the oracle (the JUNG graph implementation) doesn't work
                // well with it
                con.weight(random.choose(bits), 1);
            }
            int depCount = 0;
            for (Bit bit : bits) {
                bit.addDependencies(IntStream.range(0, maxDepNum).mapToObj(i -> random.choose(bits)).collect(Collectors.toSet()));
                depCount += bit.deps().size();
            }
            Set<Bit> sources = IntStream.range(0, sourceNum).mapToObj(i -> random.choose(bits)).collect(Collectors.toSet());
            Set<Bit> bitsWoSources = new HashSet<>(bits);
            bitsWoSources.removeAll(sources);
            Set<Bit> sinks = IntStream.range(0, sinkNum).mapToObj(i -> random.choose(bitsWoSources)).collect(Collectors.toSet());
            return new BitGraphWrapper(new BitGraph(con, Collections.singletonList(new Value(new ArrayList<>(sinks))), new Value(new ArrayList<>(sources))), bitNum, depCount, bits);
        }

        BitGraphWrapper cloneWithout(SourceOfRandomness random, BitGraphWrapper graph, float deletedDepfraction) {
            Context con = new Context(BasicSecLattice.LOW, 2);
            Set<Bit> sources = graph.g.returnValue.bitSet();
            Set<Bit> sinks = graph.g.parameters.get(0).bitSet();
            Map<Bit, Bit> newBits = new DefaultMap<>((map, b) -> {
                Bit newBit = bl.create(B.U);
                con.weight(newBit, graph.g.context.weight(b));
                return newBit;
            });
            Set<Bit> alreadyVisited = new HashSet<>();
            alreadyVisited.clear();
            for (Bit bit : sources) {
                bl.walkBits(bit, b -> {
                    Bit newBit = newBits.get(b);
                    newBit.addDependencies(b.deps().stream().filter(b1 -> random.nextFloat() <= 1 - deletedDepfraction).map(newBits::get).collect(Collectors.toSet()));
                }, b -> false, alreadyVisited);
            }
            Box<Integer> depCount = new Box<>(0);
            Box<Integer> bitCount = new Box<>(0);
            alreadyVisited.clear();
            List<Bit> newSinks = sinks.stream().map(newBits::get).collect(Collectors.toList());
            List<Bit> newSources = sources.stream().map(newBits::get).collect(Collectors.toList());
            for (Bit bit : newSources) {
                bl.walkBits(bit, b -> {
                    depCount.val += b.deps().size();
                    bitCount.val += 1;
                }, b -> false, alreadyVisited);
            }
            return new BitGraphWrapper(new BitGraph(con, Collections.singletonList(new Value(newSinks)),
                    new Value(newSources)), bitCount.val, depCount.val, alreadyVisited);
        }

        BitGraphWrapper cloneWithoutBit(SourceOfRandomness random, BitGraphWrapper graph, Bit rem) {
            Context con = new Context(BasicSecLattice.LOW, 2);
            Set<Bit> sources = graph.g.returnValue.bitSet();
            Set<Bit> sinks = graph.g.parameters.get(0).bitSet();
            sources.remove(rem);
            sinks.remove(rem);
            if (sources.isEmpty() || sinks.isEmpty()) {
                return graph;
            }
            Map<Bit, Bit> newBits = new DefaultMap<>((map, b) -> {
                Bit newBit = bl.create(B.U);
                con.weight(newBit, graph.g.context.weight(b));
                return newBit;
            });
            Set<Bit> alreadyVisited = new HashSet<>();
            alreadyVisited.clear();
            Box<Integer> nonInftyBitCount = new Box<>(0);
            for (Bit bit : sources) {
                bl.walkBits(bit, b -> {
                    if (b != rem && con.weight(b) != INFTY){
                        nonInftyBitCount.val++;
                    }
                    Bit newBit = newBits.get(b);
                    newBit.addDependencies(b.deps().stream().flatMap(d -> {
                        if (d == rem) {
                            return rem.deps().stream().filter(d2 -> d2 != rem);
                        }
                        return Stream.of(d);
                    }).map(newBits::get).collect(Collectors.toSet()));
                }, b -> false, alreadyVisited);
            }
            Box<Integer> depCount = new Box<>(0);
            Box<Integer> bitCount = new Box<>(0);
            alreadyVisited.clear();
            List<Bit> newSinks = sinks.stream().map(newBits::get).collect(Collectors.toList());
            List<Bit> newSources = sources.stream().map(newBits::get).collect(Collectors.toList());
            for (Bit bit : newSources) {
                bl.walkBits(bit, b -> {
                    depCount.val += b.deps().size();
                    bitCount.val += 1;
                }, b -> false, alreadyVisited);
            }
            if (nonInftyBitCount.val == 0) {
                con.weight(random.choose(alreadyVisited), 1);
            }
            return new BitGraphWrapper(new BitGraph(con, Collections.singletonList(new Value(newSinks)),
                    new Value(newSources)), bitCount.val, depCount.val, alreadyVisited);
        }

        @Override
        public List<BitGraphWrapper> doShrink(SourceOfRandomness random, BitGraphWrapper larger) {
            return larger.bits.stream().map(b -> cloneWithoutBit(random, larger, b)).filter(b -> b != larger && larger.g.minCutBits().size() < INFTY / 2).collect(Collectors.toList());
        }

        @Override
        public BigDecimal magnitude(Object value) {
            return BigDecimal.valueOf(((BitGraphWrapper) value).depCount + ((BitGraphWrapper) value).bitCount * 2);
        }

        @Override
        public boolean canShrink(Object larger) {
            assert larger instanceof BitGraphWrapper;
            return true;// ((BitGraphWrapper) larger).bitCount > ((BitGraphWrapper) larger).g.returnValue.size() + 1;
        }
    }

    public static class LongStatList {
        private List<Pair<Long, Long>> vals = new ArrayList<>();

        public void add(long oldVal, long newVal){
            vals.add(new Pair<>(oldVal, newVal));
        }

        void printStats(String title){
            double[] diffs = vals.stream().mapToDouble(p -> p.second - p.first == 0 ? 0 : ((p.second - p.first) * 1.0 / p.first)).toArray();
            double mean = DoubleStream.of(diffs).sum() * 1.0 / diffs.length;
            double std = Math.sqrt(DoubleStream.of(diffs).map(d -> (d - mean) * (d - mean)).sum()) / diffs.length;
            System.out.println(String.format("%s: runs: %d, mean diff: %2.2f, std: %2.2f", title, diffs.length, mean, std));
            System.out.println(vals);
        }

    }

    private static LongStatList expectedActualMinCutSize = new LongStatList();
    private static LongStatList execTimes = new LongStatList();

    @Property(onMinimalCounterexample = MinCutTest.class, trials = 10)
    public void checkMinCutGE(@When(seed = -5818294918L)
                            @BGConfig(maxBits = 50, maxDeps = 10, maxSinks = 10, maxSources = 10) @From(BitGraphs.class) BitGraphWrapper wrappedGraph) {
        MinCut.Algo oldAlgo = MinCut.usedAlgo;
        MinCut.usedAlgo = MinCut.Algo.EK_APPROX;
        Bit.toStringGivesBitNo = true;
        BitGraph graph = wrappedGraph.g;
        //graph.writeDotGraph(Paths.get(""), "blub");
        long start = System.nanoTime();
        Set<Bit> expectedBits = graph.minCutBits();
        //System.out.println(" " + expectedBits.size());
        long oldTime = (int)(System.nanoTime() - start);
        int expected = expectedBits.size();
        start = System.nanoTime();
        MinCut.ComputationResult actual = MinCut.compute(graph.returnValue.bitSet(),
                graph.parameters.get(0).bitSet(), graph.context::weight);
        MinCut.usedAlgo = oldAlgo;
        long newTime = (int)(System.nanoTime() - start);
        execTimes.add(oldTime, newTime);
        expectedActualMinCutSize.add(expected, actual.minCut.size());
        if (expected > actual.minCut.size()){
            System.err.println("Larger");
        }
        assertTrue(expected <= actual.minCut.size(),
                String.format("Should have the same min cut size as the old implementation, example min cut is %s, attempt is %s", expectedBits, actual.minCut));
        expectedActualMinCutSize.printStats("min cut size");
        execTimes.printStats("execution times");
    }

    @Override
    public void handle(Object[] counterexample, Runnable action) {
        MinCut.DEBUG = true;
        ((BitGraphWrapper)counterexample[0]).g.writeDotGraph(Paths.get(""), "blub");
        checkMinCutGE((BitGraphWrapper) counterexample[0]);
    }

}
