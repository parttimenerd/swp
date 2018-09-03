package nildumu;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import io.github.livingdocumentation.dotdiagram.DotGraph;
import swp.util.Pair;

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
        Properties properties = new PropertyScheme().add("handler").parse(props);
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
                c.pushNewMethodInvocationState(callSite);
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

        private LeakageCalculation.JungEdgeGraph jungEdgeGraph;

        final Value returnValue;

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

        public static Bit cloneBit(Context context, Bit bit, DependencySet dataDeps, DependencySet controlDeps){
            Bit clone;
            if (bit.isUnknown()) {
                clone = bl.create(U, dataDeps, controlDeps);
            } else {
                clone = bl.create(v(bit));
            }
            context.repl(clone, context.repl(bit));
            return clone;
        }

        public Value applyToArgs(Context context, List<Value> arguments){
            List<Value> extendedArguments = IntStream.range(0, arguments.size()).mapToObj(i -> arguments.get(i).extend(parameters.get(i).size())).collect(Collectors.toList());
            Map<Bit, Bit> newBits = new HashMap<>();
            // populate
            vl.walkBits(returnValue, bit -> {
                if (parameterBits.contains(bit)){
                    Pair<Integer, Integer> loc = bitInfo.get(bit);
                    Bit argBit = extendedArguments.get(loc.first).get(loc.second);
                    newBits.put(bit, argBit);
                } else {
                    Bit clone = cloneBit(context, bit, d(bit), c(bit));
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
                    b.setDeps(d(b).map(newBits::get), c(b).map(newBits::get));
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
            }, b -> bits.contains(b) || parameterBits.contains(b));
            return reachableBits;
        }

        public Set<Bit> calcReachableParamBits(Bit bit){
            return calcReachableBits(bit, parameterBits);
        }

        public LeakageCalculation.JungEdgeGraph getJungEdgeGraph(){
            if (jungEdgeGraph == null){
                Map<Sec<?>, Bit> inputAnchorBits = new HashMap<>();
                Map<Sec<?>, Bit> outputAnchorBits = new HashMap<>();
                inputAnchorBits.put(null, bit("i"));
                outputAnchorBits.put(null, bit("h"));

                Map<Bit, LeakageCalculation.Rule> rules = new HashMap<>();
                Set<Bit> alreadyVisited = new HashSet<>();
                for (Bit bit : returnValue) {
                    bl.walkBits(bit, b -> {
                        if (!context.isInputBit(b)) {
                            rules.put(b, new LeakageCalculation.Rule(b, rule(b), context.hasInfiniteWeight(b)));
                        }
                    }, b -> !b.isUnknown());
                }
                LeakageCalculation.Rules rulesObj = new LeakageCalculation.Rules(inputAnchorBits, outputAnchorBits, rules);
                jungEdgeGraph = new LeakageCalculation.JungEdgeGraph(rulesObj, LeakageCalculation.EdgeGraph.fromRules(rulesObj));
            }
            return jungEdgeGraph;
        }

        public Set<Bit> minCutBits(){
            return getJungEdgeGraph().minCutBits(null);
        }

        public DotGraph createDotGraph(String name){
            DotGraph dotGraph = new DotGraph(name);
            DotGraph.Digraph g = dotGraph.getDigraph();
            Set<Bit> alreadyVisited = new HashSet<>();
            Function<Bit, String> dotLabel = b -> b.uniqueId() + ": " + b.toString().replace("|", "or");
            for (Bit bit : returnValue) {
                bl.walkBits(bit, b -> {
                    DotGraph.Node node = g.addNode(b.uniqueId());
                    node.setLabel(dotLabel.apply(b));
                    b.deps.forEach(d -> g.addAssociation(b.uniqueId(), d.uniqueId()));
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
            return dotGraph;
        }

        public void writeDotGraph(Path folder, String name, int version){
            Path path = folder.resolve(name + "_" + version + ".dot");
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

        final int maxIterations;

        final MethodInvocationHandler botHandler;

        final Path dotFolder;

        Map<MethodNode, BitGraph> methodGraphs;

        public SummaryHandler(int maxIterations, MethodInvocationHandler botHandler, Path dotFolder) {
            this.maxIterations = maxIterations;
            this.botHandler = botHandler;
            this.dotFolder = dotFolder;
        }

        @Override
        public void setup(ProgramNode program) {
            Context c = program.context;
            Map<MethodNode, MethodInvocationNode> callSites = new DefaultMap<>((map, method) ->{
                MethodInvocationNode callSite = new MethodInvocationNode(method.location, method.name, null);
                callSite.definition = method;
                return callSite;
            });
            Map<MethodNode, BitGraph> curVersion = new HashMap<>();
            program.methods().forEach(m -> {
                List<Value> parameters = generateParameters(program, m);
                Value returnValue = botHandler.analyze(c, callSites.get(m), parameters);
                curVersion.put(m, new BitGraph(c, parameters, returnValue));
            });
            outputDots(curVersion, 0);
            MethodInvocationHandler handler = createHandler(curVersion);
            for (int i = 0; i < maxIterations; i++) {
                for (MethodNode method : program.methods()) {
                    curVersion.put(method, methodIteration(program.context, callSites.get(method), handler, curVersion.get(method).parameters));
                }
                outputDots(curVersion, i + 1);
            }
            methodGraphs = curVersion;
        }

        void outputDots(Map<MethodNode, BitGraph> curVersion, int version){
            if (dotFolder != null){
                curVersion.entrySet().forEach(e -> e.getValue().writeDotGraph(dotFolder, e.getKey().name, version));
            }
        }

        List<Value> generateParameters(ProgramNode program, MethodNode method){
            return method.parameters.parameterNodes.stream().map(p ->
                IntStream.range(0, program.context.maxBitWidth).mapToObj(i -> bl.create(U)).collect(Value.collector())
            ).collect(Collectors.toList());
        }

        BitGraph methodIteration(Context c, MethodInvocationNode callSite, MethodInvocationHandler handler, List<Value> parameters){
            c.pushNewMethodInvocationState(callSite);
            for (int i = 0; i < parameters.size(); i++) {
                c.setVariableValue(callSite.definition.parameters.get(i).definition, parameters.get(i));
            }
            c.forceMethodInvocationHandler(handler);
            Processor.process(c, callSite.definition.body);
            Value ret = c.getReturnValue();
            c.popMethodInvocationState();
            return new BitGraph(c, parameters, ret);
        }

        MethodInvocationHandler createHandler(Map<MethodNode, BitGraph> curVersions){
            return new MethodInvocationHandler() {
                @Override
                public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
                    return curVersions.get(callSite.definition).applyToArgs(c, arguments);
                }
            };
        }

        /**
         * basic implementation, just connects a result bit with all reachable parameter bits
         */
        BitGraph reduce(Context context, BitGraph bitGraph){
            DefaultMap<Bit, Bit> newBits = new DefaultMap<Bit, Bit>((map, bit) -> {
               return BitGraph.cloneBit(context, bit, ds.create(bitGraph.calcReachableParamBits(bit)), ds.bot());
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

        public SummaryMinCutHandler(int maxIterations, MethodInvocationHandler botHandler, Path dotFolder) {
            super(maxIterations, botHandler, dotFolder);
        }

        @Override
        BitGraph reduce(Context context, BitGraph bitGraph) {
            Set<Bit> anchorBits = new HashSet<>(bitGraph.parameterBits);
            Set<Bit> minCutBits = bitGraph.minCutBits();
            anchorBits.addAll(minCutBits);
            DefaultMap<Bit, Bit> newBits = new DefaultMap<Bit, Bit>((map, bit) -> {
                if (bitGraph.parameterBits.contains(bit)){
                    return bit; // don't replace the parameter bits
                }
                return BitGraph.cloneBit(context, bit, ds.create(bitGraph.calcReachableBits(bit, anchorBits)).map(map::get), ds.bot());
            });
            Set<Bit> alreadyVisited = new HashSet<>();
            for (Bit bit : bitGraph.returnValue) {
                // for every result bit: topological walk that creates the middle bits in an
                // order that doesn't lead to recursion
                /*bl.walkTopologicalOrder(bit, b -> {
                    if (minCutBits.contains(b)){
                        newBits.get(b);
                    }
                }, bitGraph.parameterBits::contains, alreadyVisited);*/
            }
            // now all middle bits are created and the return bits can be created
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
                return IntStream.range(0, arguments.stream().mapToInt(Value::size).max().getAsInt()).mapToObj(i -> bl.create(U, set, ds.bot())).collect(Value.collector());
            }
        });
        examplePropLines.add("handler=basic");
        register("call_string", s -> s.add("maxrec", "1").add("bot", "basic"), ps -> {
            return new CallStringHandler(Integer.parseInt(ps.getProperty("maxrec")), parse(ps.getProperty("bot")));
        });
        examplePropLines.add("handler=call_string;maxrec=2;bot=basic");
        register("summary", s -> s.add("maxiter", "1").add("bot", "basic").add("dot", ""), ps -> {
            Path dotFolder = ps.getProperty("dot").equals("") ? null : Paths.get(ps.getProperty("dot"));
            return new SummaryHandler(Integer.parseInt(ps.getProperty("maxiter")), parse(ps.getProperty("bot")), dotFolder);
        });
        examplePropLines.add("handler=summary;maxiter=2;bot=basic");
        register("summary_mc", s -> s.add("maxiter", "1").add("bot", "basic").add("dot", ""), ps -> {
            Path dotFolder = ps.getProperty("dot").equals("") ? null : Paths.get(ps.getProperty("dot"));
            return new SummaryHandler(Integer.parseInt(ps.getProperty("maxiter")), parse(ps.getProperty("bot")), dotFolder);
        });
        examplePropLines.add("handler=summary_mc;maxiter=2;bot=basic");
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
