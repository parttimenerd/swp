package nildumu;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import io.github.livingdocumentation.dotdiagram.*;
import swp.util.Pair;

import static nildumu.CallGraph.CallNode;
import static nildumu.Context.*;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.*;
import static nildumu.LeakageCalculation.*;
import static nildumu.Parser.*;
import static nildumu.Util.p;

/**
 * Handles the analysis of methods
 */
public abstract class MethodInvocationHandler {

    private static Map<String, Pair<PropertyScheme, Function<Properties, MethodInvocationHandler>>> registry = new HashMap<>();

    private static List<String> examplePropLines = new ArrayList<>();

    public static void register(String name, Consumer<PropertyScheme> propSchemeCreator, Function<Properties, MethodInvocationHandler> creator){
        PropertyScheme scheme = new PropertyScheme();
        propSchemeCreator.accept(scheme);
        scheme.add("handler", null);
        registry.put(name, p(scheme, creator));
    }

    public static MethodInvocationHandler parse(String props){
        Properties properties = new PropertyScheme().add("handler").parse(props, true);
        String handlerName = properties.getProperty("handler");
        if (!registry.containsKey(handlerName)){
            throw new MethodInvocationHandlerInitializationError(String.format("unknown handler %s", handlerName));
        }
        try {
            Pair<PropertyScheme, Function<Properties, MethodInvocationHandler>> pair = registry.get(handlerName);
            return pair.second.apply(pair.first.parse(props));
        } catch (MethodInvocationHandlerInitializationError error){
            throw error;
        } catch (Error error){
            throw new MethodInvocationHandlerInitializationError(String.format("parsing \"%s\": %s", props, error.getMessage()));
        }
    }

    public static List<String> getExamplePropLines(){
        return Collections.unmodifiableList(examplePropLines);
    }

    public static class MethodInvocationHandlerInitializationError extends NildumuError {

        public MethodInvocationHandlerInitializationError(String message) {
            super("Error initializing the method invocation handler: " + message);
        }
    }

    public static class PropertyScheme {
        public final char SEPARATOR = ';';
        private final Map<String, String> defaultValues;

        public PropertyScheme() {
            defaultValues = new HashMap<>();
        }

        public PropertyScheme add(String param, String defaultValue){
            defaultValues.put(param, defaultValue);
            return this;
        }

        public PropertyScheme add(String param){
            return add(param, null);
        }

        public Properties parse(String props){
            return parse(props, false);
        }

        public Properties parse(String props, boolean allowAnyProps){
            if (!props.contains("=")){
                props = String.format("handler=%s", props);
            }
            Properties properties = new Properties();
            try {
                properties.load(new StringReader(props.replace(SEPARATOR, '\n')));
            } catch (IOException e) {
                e.printStackTrace();
                throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": %s", props, e.getMessage()));
            }
            for (Map.Entry<String, String> defaulValEntry : defaultValues.entrySet()) {
                if (!properties.containsKey(defaulValEntry.getKey())){
                    if (defaulValEntry.getValue() == null){
                        throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": property %s not set", props, defaulValEntry.getKey()));
                    }
                    properties.setProperty(defaulValEntry.getKey(), defaulValEntry.getValue());
                }
            }
            if (!allowAnyProps) {
                for (String prop : properties.stringPropertyNames()) {
                    if (!defaultValues.containsKey(prop)) {
                        throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": property %s unknown, valid properties are: %s", props, prop, defaultValues.keySet().stream().sorted().collect(Collectors.joining(", "))));
                    }
                }
            }
            return properties;
        }
    }

    public static class CallStringHandler extends MethodInvocationHandler {

        final int maxRec;

        final MethodInvocationHandler botHandler;

        private DefaultMap<Parser.MethodNode, Integer> methodCallCounter = new DefaultMap<>((map, method) -> 0);

        CallStringHandler(int maxRec, MethodInvocationHandler botHandler) {
            this.maxRec = maxRec;
            this.botHandler = botHandler;
        }

        @Override
        public void setup(ProgramNode program) {
            botHandler.setup(program);
        }

        @Override
        public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
            MethodNode method = callSite.definition;
            if (methodCallCounter.get(method) < maxRec) {
                methodCallCounter.put(method, methodCallCounter.get(method) + 1);
                c.pushNewMethodInvocationState(callSite, arguments);
                for (int i = 0; i < arguments.size(); i++) {
                    c.setVariableValue(method.parameters.get(i).definition, arguments.get(i));
                }
                Processor.process(c, method.body);
                Value ret = c.getReturnValue();
                c.popMethodInvocationState();
                methodCallCounter.put(method, methodCallCounter.get(method) - 1);
                return ret;
            }
            return botHandler.analyze(c, callSite, arguments);
        }
    }

    static class BitGraph {

        final Context context;
        final List<Value> parameters;
        private final Set<Bit> parameterBits;
        /**
         * bit → parameter number, index
         */
        private final Map<Bit, Pair<Integer, Integer>> bitInfo;

        final Value returnValue;

        final List<Integer> paramBitsPerReturnValue;

        BitGraph(Context context, List<Value> parameters, Value returnValue) {
            this.context = context;
            this.parameters = parameters;
            this.parameterBits = parameters.stream().flatMap(Value::stream).collect(Collectors.toSet());
            this.bitInfo = new HashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                Value param = parameters.get(i);
                for (int j = 1; j <= param.size(); j++) {
                    bitInfo.put(param.get(j), p(i, j));
                }
            }
            this.returnValue = returnValue;
            assertThatAllBitsAreNotNull();
            paramBitsPerReturnValue = returnValue.stream().map(b -> calcReachableParamBits(b).size()).collect(Collectors.toList());
        }

        private void assertThatAllBitsAreNotNull(){
            returnValue.forEach(b -> {
                assert b != null: "Return bits shouldn't be null";
            });
            vl.walkBits(Arrays.asList(returnValue), b -> {
                assert b != null: "Bits shouldn't be null";
            });
            vl.walkBits(parameters, b -> {
                assert b != null: "Parameters bits shouldn't null";
            });
        }

        public static Bit cloneBit(Context context, Bit bit, DependencySet deps){
            Bit clone;
            if (bit.isUnknown()) {
                clone = bl.create(U, deps);
            } else {
                clone = bl.create(v(bit));
            }
            context.repl(clone, context.repl(bit));
            return clone;
        }

        public Value applyToArgs(Context context, List<Value> arguments){
            List<Value> extendedArguments = arguments;
            Map<Bit, Bit> newBits = new HashMap<>();
            // populate
            vl.walkBits(returnValue, bit -> {
                if (parameterBits.contains(bit)){
                    Pair<Integer, Integer> loc = bitInfo.get(bit);
                    Bit argBit = extendedArguments.get(loc.first).get(loc.second);
                    newBits.put(bit, argBit);
                } else {
                    Bit clone = cloneBit(context, bit, d(bit));
                    clone.value(bit.value());
                    newBits.put(bit, clone);
                }
            });
            DefaultMap<Value, Value> newValues = new DefaultMap<Value, Value>((map, value) -> {
                if (parameters.contains(value)){
                    return arguments.get(parameters.indexOf(value));
                }
                Value clone = value.map(b -> {
                    if (!parameterBits.contains(b)) {
                        return newBits.get(b);
                    }
                    return b;
                });
                clone.node(value.node());
                return value;
            });
            // update dependencies
            newBits.forEach((old, b) -> {
                if (!parameterBits.contains(old)) {
                    b.alterDependencies(newBits::get);
                }
                //b.value(old.value());
            });
            return returnValue.map(newBits::get);
        }

        /**
         * Returns the bit of the passed set, that are reachable from the bit
         */
        public Set<Bit> calcReachableBits(Bit bit, Set<Bit> bits){
            Set<Bit> reachableBits = new HashSet<>();
            bl.walkBits(bit, b -> {
                if (bits.contains(b)){
                    reachableBits.add(b);
                }
            }, b -> false);
            return reachableBits;
        }

        public Set<Bit> calcReachableParamBits(Bit bit){
            return calcReachableBits(bit, parameterBits);
        }

        public Set<Bit> minCutBits(){
            return minCutBits(returnValue.bitSet(), parameterBits);
        }

        public Set<Bit> minCutBits(Set<Bit> outputBits, Set<Bit> inputBits){
            Map<Sec<?>, Bit> inputAnchorBits = new HashMap<>();
            Map<Sec<?>, Bit> outputAnchorBits = new HashMap<>();
            Sec<?> sec = BasicSecLattice.HIGH;
            Bit inputBit = bit("i");
            Bit outputBit = bit("o");
            inputAnchorBits.put(sec, inputBit);
            outputAnchorBits.put(sec, outputBit);
            Map<Bit, Set<Bit>> rules = new HashMap<>();
            Set<Bit> alreadyVisited = new HashSet<>();
            for (Bit bit : returnValue) {
                bl.walkBits(bit, b -> {
                    if (!context.isInputBit(b)) {
                        rules.put(b, new HashSet<>(rule(b)));
                    }
                }, b -> !b.isUnknown());
            }
            rules.put(outputBit, new HashSet<Bit>(outputBits));
            inputBits.forEach(b -> rules.getOrDefault(b, new HashSet<>()).add(inputBit));
            LeakageCalculation.Rules rulesObj = new LeakageCalculation.Rules(inputAnchorBits, outputAnchorBits, rules.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e ->new Rule(e.getKey(), e.getValue(), context.hasInfiniteWeight(e.getKey())))));
            return new LeakageCalculation.JungEdgeGraph(rulesObj, LeakageCalculation.EdgeGraph.fromRules(rulesObj)).minCutBits(sec);
        }

        public DotGraph createDotGraph(String name){
            DotGraph dotGraph = new DotGraph(name);
            DotGraph.Digraph g = dotGraph.getDigraph();
            createDotGraph(g.addCluster(name), name);
            return dotGraph;
        }

        private DotGraph.Cluster createDotGraph(DotGraph.Cluster g, String name){
            Set<Bit> alreadyVisited = new HashSet<>();
            Function<Bit, String> dotLabel = b -> b.uniqueId() + ": " + b.toString().replace("|", "or");
            for (Bit bit : returnValue) {
                bl.walkBits(bit, b -> {
                    DotGraph.Node node = g.addNode(b.uniqueId());
                    node.setLabel(dotLabel.apply(b));
                    b.deps().forEach(d -> g.addAssociation(b.uniqueId(), d.uniqueId()));
                }, b -> false, alreadyVisited);
            }
            vl.walkBits(parameters, b -> {
                if (!alreadyVisited.contains(b)){
                    g.addNode(b.uniqueId());
                    alreadyVisited.add(b);
                }
            });
            g.addNode("return").setLabel("return");
            returnValue.forEach(b -> g.addAssociation("return", b.uniqueId()));
            g.addNode(name).setLabel(name);
            for (int i = 0; i < parameters.size(); i++) {
                Value param = parameters.get(i);
                String nodeId = String.format("param %d", i);
                g.addNode(nodeId).setLabel(String.format("param %d", i));
                param.forEach(b -> g.addAssociation(b.uniqueId(), nodeId));
                g.addAssociation(nodeId, name);
            }
            return g;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BitGraph){
                //assert ((BitGraph)obj).parameterBits == this.parameterBits;
                return paramBitsPerReturnValue.equals(((BitGraph)obj).paramBitsPerReturnValue);
            }
            return false;
        }

        public void writeDotGraph(Path folder, String name){
            Path path = folder.resolve(name + ".dot");

            String graph = createDotGraph(name).render();
            try {
                Files.createDirectories(folder);
                Files.write(path, Arrays.asList(graph.split("\n")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class SummaryHandler extends MethodInvocationHandler {

        public static enum Mode {
            COINDUCTION,
            /**
             * The induction mode doesn't work with recursion and has spurious errors
             */
            INDUCTION
        }

        final int maxIterations;

        final Mode mode;

        final MethodInvocationHandler botHandler;

        final Path dotFolder;

        Map<MethodNode, BitGraph> methodGraphs;

        CallGraph callGraph;

        public SummaryHandler(int maxIterations, Mode mode, MethodInvocationHandler botHandler, Path dotFolder) {
            this.maxIterations = maxIterations;
            this.mode = mode;
            assert !(mode == Mode.INDUCTION) || (maxIterations == Integer.MAX_VALUE);
            this.botHandler = botHandler;
            this.dotFolder = dotFolder;
        }

        @Override
        public void setup(ProgramNode program) {
            callGraph = new CallGraph(program);
            if (dotFolder != null){
                callGraph.writeDotGraph(dotFolder, "call_graph");
            }
            if (callGraph.containsRecursion() && mode == Mode.INDUCTION){
                throw new MethodInvocationHandlerInitializationError("Induction cannot be used for programs with reduction");
            }
            Context c = program.context;
            Map<MethodNode, MethodInvocationNode> callSites = new DefaultMap<>((map, method) ->{
                MethodInvocationNode callSite = new MethodInvocationNode(method.location, method.name, null);
                callSite.definition = method;
                return callSite;
            });
            Map<CallNode, BitGraph> state = new HashMap<>();
            MethodInvocationHandler handler = createHandler(m -> state.get(callGraph.callNode(m)));
            Map<CallNode, Integer> nodeEvaluationCount = new HashMap<>();
            Util.Box<Integer> iteration = new Util.Box<>(0);
            methodGraphs = callGraph.worklist((node, s) -> {
                if (node.isMainNode || nodeEvaluationCount.getOrDefault(node, 0) > maxIterations){
                    return s.get(node);
                }
                nodeEvaluationCount.put(node, nodeEvaluationCount.getOrDefault(node, 0));
                iteration.val += 1;
                BitGraph graph = methodIteration(program.context, callSites.get(node.method), handler, s.get(node).parameters);
                if (dotFolder != null) {
                    graph.writeDotGraph(dotFolder, iteration.val + "_" + node.method.name);
                }
                BitGraph reducedGraph = reduce(c, graph);
                if (dotFolder != null) {
                    reducedGraph.writeDotGraph(dotFolder, "r" + iteration.val + "_" + node.method.name);
                }
                return reducedGraph;
            }, node ->  {
                BitGraph graph = bot(program, node.method, callSites);
                        if (dotFolder != null) {
                            graph.writeDotGraph(dotFolder, iteration.val + "_" + node.method.name);
                        }
                        return graph;
                    }
            , node -> node.getCallers().stream().filter(n -> !n.isMainNode).collect(Collectors.toSet()),
            state).entrySet().stream().collect(Collectors.toMap(e -> e.getKey().method, Map.Entry::getValue));
        }

        BitGraph bot(ProgramNode program, MethodNode method, Map<MethodNode, MethodInvocationNode> callSites){
            List<Value> parameters = generateParameters(program, method);
            if (mode == Mode.COINDUCTION) {
                Value returnValue = botHandler.analyze(program.context, callSites.get(method), parameters);
                return new BitGraph(program.context, parameters, returnValue);
            }
            return new BitGraph(program.context, parameters, createUnknownValue(program));
        }

        List<Value> generateParameters(ProgramNode program, MethodNode method){
            return method.parameters.parameterNodes.stream().map(p ->
                createUnknownValue(program)
            ).collect(Collectors.toList());
        }

        Value createUnknownValue(ProgramNode program){
            return IntStream.range(0, program.context.maxBitWidth).mapToObj(i -> bl.create(U)).collect(Value.collector());
        }

        BitGraph methodIteration(Context c, MethodInvocationNode callSite, MethodInvocationHandler handler, List<Value> parameters){
            c.pushNewMethodInvocationState(callSite, parameters.stream().flatMap(Value::stream).collect(Collectors.toSet()));
            for (int i = 0; i < parameters.size(); i++) {
                c.setVariableValue(callSite.definition.parameters.get(i).definition, parameters.get(i));
            }
            c.forceMethodInvocationHandler(handler);
            Processor.process(c, callSite.definition.body);
            Value ret = c.getReturnValue();
            c.popMethodInvocationState();
            return new BitGraph(c, parameters, ret);
        }

        MethodInvocationHandler createHandler(Function<MethodNode, BitGraph> curVersion){
            return new MethodInvocationHandler() {
                @Override
                public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
                    return curVersion.apply(callSite.definition).applyToArgs(c, arguments);
                }
            };
        }

        /**
         * basic implementation, just connects a result bit with all reachable parameter bits
         */
        BitGraph reduce(Context context, BitGraph bitGraph){
            DefaultMap<Bit, Bit> newBits = new DefaultMap<Bit, Bit>((map, bit) -> {
               return BitGraph.cloneBit(context, bit, ds.create(bitGraph.calcReachableParamBits(bit)));
            });
            Value ret = bitGraph.returnValue.map(newBits::get);
            ret.node(bitGraph.returnValue.node());
            return new BitGraph(context, bitGraph.parameters, ret);
        }

        @Override
        public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
            return methodGraphs.get(callSite.definition).applyToArgs(c, arguments);
        }
    }

    public static class SummaryMinCutHandler extends SummaryHandler {

        public SummaryMinCutHandler(int maxIterations, Mode mode, MethodInvocationHandler botHandler, Path dotFolder) {
            super(maxIterations, mode, botHandler, dotFolder);
        }

        @Override
        BitGraph reduce(Context context, BitGraph bitGraph) {
            Set<Bit> anchorBits = new HashSet<>(bitGraph.parameterBits);
            Map<Pair<Set<Bit>, Set<Bit>>, Set<Bit>> outInMincut = new HashMap<>();
            Function<Pair<Set<Bit>, Set<Bit>>, Set<Bit>> calcMinCut = p -> bitGraph.minCutBits(p.first, p.second);
            Pair<Set<Bit>, Set<Bit>> initialPair = p(bitGraph.returnValue.bitSet(), bitGraph.parameterBits);
            outInMincut.put(initialPair, calcMinCut.apply(initialPair));
            Set<Bit> minCutBits = new HashSet<>();
            for (Bit bit : bitGraph.returnValue) {
                minCutBits.addAll(bitGraph.minCutBits(bitGraph.parameterBits, Collections.singleton(bit)));
            }
            anchorBits.addAll(minCutBits);
            Map<Bit, Bit> newBits = new HashMap<>();
            // create the new bits
            Stream.concat(bitGraph.returnValue.stream(), minCutBits.stream()).forEach(b -> {
                Set<Bit> reachable = bitGraph.calcReachableBits(b, anchorBits);
                if (!b.deps().contains(b)){
                    reachable.remove(b);
                }
                Bit newB = BitGraph.cloneBit(context, b, ds.create(reachable));
                newB.value(b.value());
                newBits.put(b, newB);
            });
            bitGraph.parameterBits.forEach(b -> newBits.put(b, b));
            // update the control dependencies
            newBits.forEach((o, b) -> {
                b.alterDependencies(newBits::get);
            });
            Value ret = bitGraph.returnValue.map(newBits::get);
            ret.node(bitGraph.returnValue.node());
            return new BitGraph(context, bitGraph.parameters, ret);
        }
    }

    static {
        register("basic", s -> {}, ps -> new MethodInvocationHandler(){
            @Override
            public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
                if (arguments.isEmpty() || !callSite.definition.hasReturnValue()){
                    return vl.bot();
                }
                DependencySet set = arguments.stream().flatMap(Value::stream).collect(DependencySet.collector());
                return IntStream.range(0, arguments.stream().mapToInt(Value::size).max().getAsInt()).mapToObj(i -> bl.create(U, set)).collect(Value.collector());
            }
        });
        examplePropLines.add("handler=basic");
        register("call_string", s -> s.add("maxrec", "1").add("bot", "basic"), ps -> {
            return new CallStringHandler(Integer.parseInt(ps.getProperty("maxrec")), parse(ps.getProperty("bot")));
        });
        examplePropLines.add("handler=call_string;maxrec=2;bot=basic");
        Consumer<PropertyScheme> propSchemeCreator = s -> s.add("maxiter", "1").add("bot", "basic").add("dot", "").add("mode", "coind");
        register("summary", propSchemeCreator, ps -> {
            Path dotFolder = ps.getProperty("dot").equals("") ? null : Paths.get(ps.getProperty("dot"));
            return new SummaryHandler(ps.getProperty("mode").equals("coind") ? Integer.parseInt(ps.getProperty("maxiter")) : Integer.MAX_VALUE, ps.getProperty("mode").equals("ind") ? SummaryHandler.Mode.INDUCTION : SummaryHandler.Mode.COINDUCTION, parse(ps.getProperty("bot")), dotFolder);
        });
        examplePropLines.add("handler=summary;maxiter=2;bot=basic");
        //examplePropLines.add("handler=summary;mode=ind");
        register("summary_mc", propSchemeCreator, ps -> {
            Path dotFolder = ps.getProperty("dot").equals("") ? null : Paths.get(ps.getProperty("dot"));
            return new SummaryMinCutHandler(ps.getProperty("mode").equals("coind") ? Integer.parseInt(ps.getProperty("maxiter")) : Integer.MAX_VALUE, ps.getProperty("mode").equals("ind") ? SummaryHandler.Mode.INDUCTION : SummaryHandler.Mode.COINDUCTION, parse(ps.getProperty("bot")), dotFolder);
        });
        examplePropLines.add("handler=summary_mc;maxiter=2;bot=basic");
        //examplePropLines.add("handler=summary_mc;mode=ind");
    }

    public static MethodInvocationHandler createDefault(){
        return parse(getDefaultPropString());
    }

    public static String getDefaultPropString(){
        return "handler=call_string;maxrec=2;bot=basic";
    }

    public void setup(ProgramNode program){
    }

    public abstract Lattices.Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments);
}
