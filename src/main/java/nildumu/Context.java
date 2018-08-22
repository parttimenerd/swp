package nildumu;

import java.net.ContentHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.uci.ics.jung.graph.DirectedGraph;
import swp.util.Pair;

import static nildumu.DefaultMap.ForbiddenAction.FORBID_DELETIONS;
import static nildumu.DefaultMap.ForbiddenAction.FORBID_VALUE_UPDATES;
import static nildumu.Lattices.B;
import static nildumu.Lattices.Bit;
import static nildumu.Lattices.DependencySet;
import static nildumu.Lattices.DependencySetLattice;
import static nildumu.Lattices.Sec;
import static nildumu.Lattices.SecurityLattice;
import static nildumu.Lattices.*;
import static nildumu.LeakageCalculation.jungEdgeGraph;
import static nildumu.Parser.MJNode;
import static nildumu.Parser.process;

/**
 * The context contains the global state and the global functions from the thesis.
 * <p/>
 * This is this basic idea version, but with the loop extension.
 */
public class Context {

    public static enum Mode {
        BASIC,
        EXTENDED;

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

    public final SecurityLattice<?> sl;

    public final IOValues input = new IOValues();

    public final IOValues output = new IOValues();

    public final Stack<State> variableStates = new Stack<>();

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
    private final DefaultMap<Bit, Bit> originMap =
            new DefaultMap<>(
                    new IdentityHashMap<>(),
                    new DefaultMap.Extension<Bit, Bit>() {
                        @Override
                        public Bit defaultValue(Map<Bit, Bit> map, Bit key) {
                            return key;
                        }
                    },
                    FORBID_DELETIONS,
                    FORBID_VALUE_UPDATES);

    private final DefaultMap<MJNode, Operator> operatorPerNode = new DefaultMap<>(new IdentityHashMap<>(), new DefaultMap.Extension<MJNode, Operator>() {

        @Override
        public Operator defaultValue(Map<MJNode, Operator> map, MJNode key) {
            return key.getOperator(Context.this);
        }
    }, FORBID_DELETIONS, FORBID_VALUE_UPDATES);

    private final DefaultMap<Parser.MJNode, Value> nodeValueMap = new DefaultMap<>(new LinkedHashMap<>(), new DefaultMap.Extension<MJNode, Value>() {
        @Override
        public void handleValueUpdate(DefaultMap<MJNode, Value> map, MJNode key, Value value) {
            throw new UnsupportedOperationException(String.format("Cannot update the value of '%s' from '%s' to '%s', updates are not supported", key, map.get(key), value));
        }

        @Override
        public Value defaultValue(Map<MJNode, Value> map, MJNode key) {
            return ValueLattice.get().bot();
        }
    }, FORBID_DELETIONS);

    private final DefaultMap<Bit, ModsCreator> replMap = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<Bit, ModsCreator>() {
        @Override
        public ModsCreator defaultValue(Map<Bit, ModsCreator> map, Bit key) {
            return ((c, b, a) -> choose(b, a) == a ? new Mods(b, a) : Mods.empty());
        }
    });

    private final DefaultMap<Bit, Integer> c1LeakageMap = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<Bit, Integer>() {
        @Override
        public Integer defaultValue(Map<Bit, Integer> map, Bit key) {
            if (isInputBit(key) && sec(key) != sl.bot()){
                return 1;
            }
            return key.deps.stream().filter(Bit::isUnknown).mapToInt(c1LeakageMap::get).sum();
        }
    });

    private Mode mode;

    public final Stack<Mods> modsStack = new Stack<>();

    public Context(SecurityLattice sl) {
        this.sl = sl;
        this.variableStates.push(new State());
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
        return (sec(bit) == sl.bot() || (v(bit).isConstant() && d(bit).isEmpty() && c(bit).isEmpty()))
                && (!v(bit).isConstant() || (d(bit).isEmpty() && sec(bit) == sl.bot()));
    }

    public boolean isInputBit(Bit bit) {
        return input.contains(bit);
    }

    public Value nodeValue(MJNode node){
        return nodeValueMap.get(node);
    }

    public Value nodeValue(MJNode node, Value value){
        return nodeValueMap.put(node, value);
    }

    Operator operatorForNode(Parser.MJNode node){
        return operatorPerNode.get(node);
    }

    public Value op(Parser.MJNode node, List<Value> arguments){
        return operatorForNode(node).compute(this, node, arguments);
    }

    public List<MJNode> paramNode(MJNode node){
        return (List<MJNode>)(List<?>)node.children();
    }

    public Value evaluate(MJNode node){
        System.out.println("Evaluate node " + node);
        List<Value> args = paramNode(node).stream().map(this::nodeValue).map(this::replace).collect(Collectors.toList());
        Value newValue = op(node, args);
        nodeValue(node, newValue);
        newValue.description(node.getTextualId()).node(node);
        return newValue;
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
        return new LeakageCalculation.JungGraph(getLeakageGraph().rules, secLevel, getLeakageGraph().minCutBits(secLevel));
    }

    public Set<MJNode> nodes(){
        return nodeValueMap.keySet();
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

    public boolean inExtendedMode(){
        return mode == Mode.EXTENDED;
    }

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
        return c1LeakageMap.get(bit);
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

    public Context mode(Mode mode){
        assert this.mode == null;
        this.mode = mode;
        return this;
    }
}
