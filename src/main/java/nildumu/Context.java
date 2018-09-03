package nildumu;

import java.util.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.*;

import swp.util.Pair;

import static nildumu.DefaultMap.ForbiddenAction.*;
import static nildumu.Lattices.*;
import static nildumu.LeakageCalculation.jungEdgeGraph;
import static nildumu.Parser.*;

/**
 * The context contains the global state and the global functions from the thesis.
 * <p/>
 * This is this basic idea version, but with the loop extension.
 */
public class Context {

    /**
     * Mode
     */
    public static enum Mode {
        BASIC,
        /**
         * Combines the basic mode with the tracking of path knowledge
         */
        EXTENDED,
        /**
         * Combines the extended mode with the support for loops
         */
        LOOP;

        @Override
        public String toString() {
            return name().toLowerCase().replace("_", " ");
        }
    }

    public static class NotAnInputBit extends NildumuError {
        NotAnInputBit(Bit offendingBit, String reason){
            super(String.format("%s is not an input bit: %s", offendingBit.repr(), reason));
        }
    }

    public static @FunctionalInterface interface ModsCreator {
        public Mods apply(Context context, Bit bit, Bit assumedValue);
    }

    public static class InvariantViolationError extends NildumuError {
        InvariantViolationError(String msg){
            super(msg);
        }
    }

    public static final Logger LOG = Logger.getLogger("Analysis");
    static {
        LOG.setLevel(Level.FINEST);
    }

    public final SecurityLattice<?> sl;

    public final int maxBitWidth;

    public final IOValues input = new IOValues();

    public final IOValues output = new IOValues();

    private final Stack<State> variableStates = new Stack<>();

    private final DefaultMap<Bit, Sec<?>> secMap =
            new DefaultMap<>(
                    new IdentityHashMap<>(),
                    new DefaultMap.Extension<Bit, Sec<?>>() {
                        @Override
                        public Sec<?> defaultValue(Map<Bit, Sec<?>> map, Bit key) {
                            return sl.bot();
                        }
                    },
                    FORBID_DELETIONS,
                    FORBID_VALUE_UPDATES);

    private final DefaultMap<MJNode, Operator> operatorPerNode = new DefaultMap<>(new IdentityHashMap<>(), new DefaultMap.Extension<MJNode, Operator>() {

        @Override
        public Operator defaultValue(Map<MJNode, Operator> map, MJNode key) {
            Operator op = key.getOperator(Context.this);
            if (op == null){
                throw new NildumuError(String.format("No operator for %s implemented", key));
            }
            return op;
        }
    }, FORBID_DELETIONS, FORBID_VALUE_UPDATES);

    public static class CallPath {
        final List<MethodInvocationNode> path;

        CallPath(){
            this(Collections.emptyList());
        }

        CallPath(List<MethodInvocationNode> path) {
            this.path = path;
        }

        public CallPath push(MethodInvocationNode callSite){
            List<MethodInvocationNode> newPath = new ArrayList<>(path);
            newPath.add(callSite);
            return new CallPath(newPath);
        }

        public CallPath pop(){
            List<MethodInvocationNode> newPath = new ArrayList<>(path);
            newPath.remove(newPath.size() - 1);
            return new CallPath(newPath);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CallPath && ((CallPath) obj).path.equals(path);
        }

        @Override
        public String toString() {
            return path.stream().map(Object::toString).collect(Collectors.joining(" → "));
        }
    }

    public static class NodeValueState {

        long nodeValueUpdateCount = 0;

        final DefaultMap<MJNode, Value> nodeValueMap = new DefaultMap<>(new LinkedHashMap<>(), new DefaultMap.Extension<MJNode, Value>() {

            @Override
            public void handleValueUpdate(DefaultMap<MJNode, Value> map, MJNode key, Value value) {
                if (vl.mapBits(map.get(key), value, (a, b) -> a != b).stream().anyMatch(p -> p)){
                    nodeValueUpdateCount++;
                }
            }

            @Override
            public Value defaultValue(Map<MJNode, Value> map, MJNode key) {
                return ValueLattice.get().bot();
            }
        }, FORBID_DELETIONS);

        long nodeVersionUpdateCount = 0;

        final DefaultMap<MJNode, Integer> nodeVersionMap = new DefaultMap<>(new LinkedHashMap<>(), new DefaultMap.Extension<MJNode, Integer>() {

            @Override
            public void handleValueUpdate(DefaultMap<MJNode, Integer> map, MJNode key, Integer value) {
                if (map.get(key) != value){
                    nodeVersionUpdateCount++;
                }
            }

            @Override
            public Integer defaultValue(Map<MJNode, Integer> map, MJNode key) {
                return 0;
            }
        }, FORBID_DELETIONS);

        final CallPath path;

        public NodeValueState(CallPath path) {
            this.path = path;
        }
    }

    private final DefaultMap<CallPath, NodeValueState> nodeValueStates = new DefaultMap<>((map, path) -> new NodeValueState(path));

    private CallPath currentCallPath = new CallPath();

    private NodeValueState nodeValueState = nodeValueStates.get(currentCallPath);

    private Mode mode;

    /*-------------------------- extended mode specific -------------------------------*/

    public final Stack<Mods> modsStack = new Stack<>();

    private final DefaultMap<Bit, ModsCreator> replMap = new DefaultMap<>(new WeakHashMap<>(), new DefaultMap.Extension<Bit, ModsCreator>() {
        @Override
        public ModsCreator defaultValue(Map<Bit, ModsCreator> map, Bit key) {
            return ((c, b, a) -> choose(b, a) == a ? new Mods(b, a) : Mods.empty());
        }
    });

    /*-------------------------- loop mode specific -------------------------------*/

    private final DefaultMap<Bit, Integer> weightMap = new DefaultMap<>(new WeakHashMap<>(), new DefaultMap.Extension<Bit, Integer>() {
        @Override
        public Integer defaultValue(Map<Bit, Integer> map, Bit key) {
            return 1;
        }
    });

    public static final int INFTY = Integer.MAX_VALUE;

    /*-------------------------- methods -------------------------------*/

    private MethodInvocationHandler methodInvocationHandler;

    /*-------------------------- unspecific -------------------------------*/

    public Context(SecurityLattice sl, int maxBitWidth) {
        this.sl = sl;
        this.maxBitWidth = maxBitWidth;
        this.variableStates.push(new State());
        ValueLattice.get().bitWidth = maxBitWidth;
    }

    public static B v(Bit bit) {
        return bit.val;
    }

    public static DependencySet d(Bit bit) {
        return bit.dataDeps;
    }

    public static DependencySet c(Bit bit) {
        return bit.controlDeps;
    }

    /**
     * Returns the security level of the bit
     *
     * @return sec or bot if not assigned
     */
    public Sec sec(Bit bit) {
        return secMap.get(bit);
    }

    /**
     * Sets the security level of the bit
     *
     * <p><b>Important note: updating the security level of a bit is prohibited</b>
     *
     * @return the set level
     */
    private Sec sec(Bit bit, Sec<?> level) {
        return secMap.put(bit, level);
    }

    public Value addInputValue(Sec<?> sec, Value value){
        input.add(sec, value);
        for (Bit bit : value){
            if (bit.val == B.U){
                if (!bit.dataDeps.isEmpty()){
                    throw new NotAnInputBit(bit, "has data dependencies");
                }
                if (!bit.controlDeps.isEmpty()){
                    throw new NotAnInputBit(bit, "has control dependencies");
                }
                sec(bit, sec);
            }
        }
        return value;
    }

    public Value addOutputValue(Sec<?> sec, Value value){
        output.add(sec, value);
        return value;
    }

    public boolean checkInvariants(Bit bit) {
        return (sec(bit) == sl.bot() || (!v(bit).isConstant() && d(bit).isEmpty() && c(bit).isEmpty()))
                && (!v(bit).isConstant() || (d(bit).isEmpty() && sec(bit) == sl.bot() && c(bit).isEmpty()));
    }

    public void checkInvariants(){
        List<String> errorMessages = new ArrayList<>();
        walkBits(b -> {
            if (!checkInvariants(b)){
                errorMessages.add(String.format("Invariants don't hold for %s", b.repr()));
            }
        }, p -> false);
        throw new InvariantViolationError(String.join("\n", errorMessages));
    }

    public void log(Supplier<String> msgProducer){
        if (LOG.isLoggable(Level.FINE)){
            System.out.println(msgProducer.get());
        }
    }

    public boolean isInputBit(Bit bit) {
        return input.contains(bit);
    }

    public Value nodeValue(MJNode node){
        if (node instanceof ParameterAccessNode){
            return getVariableValue(((ParameterAccessNode) node).definition);
        } else if (node instanceof VariableAccessNode){
            return nodeValue(((VariableAccessNode) node).definingExpression);
        } else if (node instanceof WrapperNode){
            return ((WrapperNode<Value>) node).wrapped;
        }
        return nodeValueState.nodeValueMap.get(node);
    }

    public Value nodeValue(MJNode node, Value value){
        return nodeValueState.nodeValueMap.put(node, value);
    }

    Operator operatorForNode(MJNode node){
        return operatorPerNode.get(node);
    }

    public Value op(MJNode node, List<Value> arguments){
        return operatorForNode(node).compute(this, node, arguments);
    }

    @SuppressWarnings("unchecked")
    public List<MJNode> paramNode(MJNode node){
        return (List<MJNode>) (List<?>) node.children().stream().map(n -> {
            if (n instanceof  VariableAccessNode){
                return ((VariableAccessNode) n).definingExpression;
            }
            return n;
        }).collect(Collectors.toList());
    }

    private final Map<MJNode, List<Integer>> lastParamVersions = new HashMap<>();

    private boolean compareAndStoreParamVersion(MJNode node){
        List<Integer> curVersions = paramNode(node).stream().map(nodeValueState.nodeVersionMap::get).collect(Collectors.toList());
        boolean somethingChanged = true;
        if (lastParamVersions.containsKey(node)){
            somethingChanged = lastParamVersions.get(node).equals(curVersions);
        }
        lastParamVersions.put(node, curVersions);
        return somethingChanged;
    }

    public boolean evaluate(MJNode node){
        log(() -> "Evaluate node " + node + " " + nodeValue(node).get(1).deps.size());

        boolean paramsChanged = compareAndStoreParamVersion(node);
        if (!paramsChanged){
            return false;
        }

        List<MJNode> paramNodes = paramNode(node);

        List<Value> args = paramNodes.stream().map(this::nodeValue).map(this::replace).collect(Collectors.toList());
        Value newValue = op(node, args);
        boolean somethingChanged = false;
        if (inLoopMode() && nodeValue(node) != vl.bot()) { // dismiss first iteration
            Value oldValue = nodeValue(node);
            List<Bit> newBits = new ArrayList<>();
            somethingChanged = vl.mapBits(oldValue, newValue, (a, b) -> {
                boolean changed = false;
                if (a.value() == null){
                    a.value(oldValue); // newly created bit
                    changed = true;
                    newBits.add(a);
                }
                return merge(a, b) || changed;
            }).stream().anyMatch(p -> p);
            if (newBits.size() > 0){
                newValue = Stream.concat(oldValue.stream(), newBits.stream()).collect(Value.collector());
            } else {
                newValue = oldValue;
            }
        } else {
            somethingChanged = nodeValue(node) == vl.bot();
        }
        nodeValue(node, newValue);
        newValue.description(node.getTextualId()).node(node);
        return somethingChanged;
    }

    /**
     * Returns the unknown output bits with lower or equal security level
     */
    public List<Bit> getOutputBits(Sec<?> maxSec){
        return output.getBits().stream().filter(p -> ((SecurityLattice)sl).lowerEqualsThan(p.first, (Sec)maxSec)).map(p -> p.second).collect(Collectors.toList());
    }

    /**
     * Returns the unknown input bits with not lower security or equal level
     */
    public List<Bit> getInputBits(Sec<?> minSecEx){
        return input.getBits().stream().filter(p -> !(((SecurityLattice)sl).lowerEqualsThan(p.first, (Sec)minSecEx))).map(p -> p.second).collect(Collectors.toList());
    }

    public Value getVariableValue(Variable variable){
        return variableStates.peek().get(variable);
    }

    public Value setVariableValue(Variable variable, Value value){
        if (variableStates.size() == 1) {
            if (variable.isInput && !variableStates.get(0).get(variable).equals(vl.bot())) {
                throw new UnsupportedOperationException(String.format("Setting an input variable (%s)", variable));
            }
        }
        variableStates.peek().set(variable, value);
        return value;
    }

    public Value getVariableValue(String variable){
        return variableStates.peek().get(variable);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Variable states\n");
        for (int i = 0; i < variableStates.size(); i++){
            builder.append(variableStates.get(i));
        }
        builder.append("Input\n" + input.toString()).append("Output\n" + output.toString());
        return builder.toString();
    }

    public boolean isInputValue(Value value){
        return input.contains(value);
    }

    public Sec<?> getInputSecLevel(Value value){
        assert isInputValue(value);
        return input.getSec(value);
    }

    public Sec<?> getInputSecLevel(Bit bit){
        return input.getSec(bit);
    }

    /**
     * Walk in pre order
     * @param ignoreBit ignore bits (and all that depend on it, if not reached otherwise)
     */
    public void walkBits(Consumer<Bit> consumer, Predicate<Bit> ignoreBit){
        Set<Bit> alreadyVisitedBits = new HashSet<>();
        for (Pair<Sec, Bit> secBitPair : output.getBits()){
            BitLattice.get().walkBits(secBitPair.second, consumer, ignoreBit, alreadyVisitedBits);
        }
    }

    private LeakageCalculation.AbstractLeakageGraph leakageGraph = null;

    public LeakageCalculation.AbstractLeakageGraph getLeakageGraph(){
        if (leakageGraph == null){
            leakageGraph = jungEdgeGraph(this);
        }
        return leakageGraph;
    }

    public LeakageCalculation.JungGraph getJungGraphForVisu(Sec<?> secLevel){
        return new LeakageCalculation.JungGraph(this, getLeakageGraph().rules, secLevel, getLeakageGraph().minCutBits(secLevel));
    }

    public Set<MJNode> nodes(){
        return nodeValueState.nodeValueMap.keySet();
    }

    public List<String> variableNames(){
        List<String> variables = new ArrayList<>();
        for (int i = variableStates.size() - 1; i >= 0; i--){
            variables.addAll(variableStates.get(i).variableNames());
        }
        return variables;
    }

    public void pushMods(Bit condBit, Bit assumedValue){
        if (inExtendedMode()){
            modsStack.push(repl(condBit).apply(this, condBit, assumedValue));
        }
    }

    public void popMods(){
        if (inExtendedMode()) {
            modsStack.pop();
        }
    }

    /**
     * In extended or later mode?
     */
    boolean inExtendedMode(){
        return mode == Mode.EXTENDED || mode == Mode.LOOP;
    }

    boolean inLoopMode(){
        return mode == Mode.LOOP;
    }

    public Context mode(Mode mode){
        assert this.mode == null;
        this.mode = mode;
        return this;
    }

    /* -------------------------- extended mode specific -------------------------------*/

    public Bit replace(Bit bit){
        if (inExtendedMode()) {
            for (int i = modsStack.size() - 1; i >= 0; i--){
                Mods cur = modsStack.get(i);
                if (cur.definedFor(bit)){
                    return cur.replace(bit);
                }
            }
        }
        return bit;
    }

    public Value replace(Value value) {
        Util.Box<Boolean> replacedABit = new Util.Box<>(false);
        Value newValue = value.stream().map(b -> {
            Bit r = replace(b);
            if (r != b){
                replacedABit.val = true;
            }
            return r;
        }).collect(Value.collector());
        if (replacedABit.val){
            return newValue;
        }
        return value;
    }

    public void repl(Bit bit, ModsCreator modsCreator){
        replMap.put(bit, modsCreator);
    }

    /**
     * Applies the repl function to get mods
     * @param bit
     * @param assumed
     */
    public Mods repl(Bit bit, Bit assumed){
        return repl(bit).apply(this, bit, assumed);
    }

    public ModsCreator repl(Bit bit){
        return replMap.get(bit);
    }

    public int c1(Bit bit){
        return c1(bit, new HashSet<>());
    }

    private int c1(Bit bit, Set<Bit> alreadyVisitedBits){
        if (isInputBit(bit) && sec(bit) != sl.bot()){
            return 1;
        }
        return bit.deps.stream().filter(Bit::isUnknown).filter(b -> {
            if (alreadyVisitedBits.contains(b)) {
                return false;
            }
            alreadyVisitedBits.add(b);
            return true;
        }).mapToInt(b -> c1(b, alreadyVisitedBits)).sum();
    }

    public Bit choose(Bit a, Bit b){
        if (c1(a) <= c1(b) || a.isConstant()){
            return a;
        }
        return b;
    }

    public Bit notChosen(Bit a, Bit b){
        if (choose(a, b) == b){
            return a;
        }
        return b;
    }

    /* -------------------------- loop mode specific -------------------------------*/

    public int weight(Bit bit){
        return weightMap.get(bit);
    }

    public void weight(Bit bit, int weight){
        assert weight == 1 || weight == INFTY;
        weightMap.put(bit, weight);
    }

    public boolean hasInfiniteWeight(Bit bit){
        return weight(bit) == INFTY;
    }

    /**
     * merges n into m
     * @param o
     * @param n
     * @return true if o value equals the merge result
     */
    public boolean merge(Bit o, Bit n){

        B vt = bs.sup(v(o), v(n));
        DependencySet dt = ds.sup(d(o), d(n));
        DependencySet ct = ds.sup(c(o), c(n));
        if (dt.equals(d(o)) && ct.equals(c(o)) && vt == v(o)){
            return false;
        }
        o.setVal(vt);
        o.setDeps(dt, ct);
        repl(o, (c, b, a) -> {
            Mods oMods = repl(o).apply(c, b, a);
            Mods nMods = repl(n).apply(c, b, a);
            return Mods.empty().add(oMods).overwrite(nMods);
        });
        return true;
    }

    public void setReturnValue(Value value){
        variableStates.get(variableStates.size() - 1).setReturnValue(value);
    }

    public Value getReturnValue(){
        return variableStates.get(variableStates.size() - 1).getReturnValue();
    }

    public long getNodeVersionUpdateCount(){
        return nodeValueState.nodeVersionUpdateCount;
    }

    /*-------------------------- methods -------------------------------*/

    public Context forceMethodInvocationHandler(MethodInvocationHandler handler) {
        methodInvocationHandler = handler;
        return this;
    }

    public Context methodInvocationHandler(MethodInvocationHandler handler) {
        assert methodInvocationHandler == null;
        methodInvocationHandler = handler;
        return this;
    }

    public MethodInvocationHandler methodInvocationHandler(){
        if (methodInvocationHandler == null){
            methodInvocationHandler(MethodInvocationHandler.createDefault());
        }
        return methodInvocationHandler;
    }

    public void pushNewMethodInvocationState(MethodInvocationNode callSite){
        currentCallPath = currentCallPath.push(callSite);
        variableStates.push(new State());
        nodeValueState = nodeValueStates.get(currentCallPath);
    }

    public void popMethodInvocationState(){
        currentCallPath = currentCallPath.pop();
        variableStates.pop();
        nodeValueState = nodeValueStates.get(currentCallPath);
    }

    public CallPath callPath(){
        return currentCallPath;
    }
}
