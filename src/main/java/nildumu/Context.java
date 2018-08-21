package nildumu;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
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

    public static class NotAnInputBit extends NildumuError {
        NotAnInputBit(Bit offendingBit, String reason){
            super(String.format("%s is not an input bit: %s", offendingBit.repr(), reason));
        }
    }

    public final SecurityLattice<?> sl;
    /** The stack of currently affecting conditions */
    public final Stack<Bit> ccStack = new Stack<>();

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

    private final DefaultMap<Parser.MJNode, Value> nodeValueMap = new DefaultMap<>(new IdentityHashMap<>(), new DefaultMap.Extension<MJNode, Value>() {
        @Override
        public void handleValueUpdate(DefaultMap<MJNode, Value> map, MJNode key, Value value) {
            throw new UnsupportedOperationException(String.format("Cannot update the value of '%s' from '%s' to '%s', updates are not supported", key, map.get(key), value));
        }

        @Override
        public Value defaultValue(Map<MJNode, Value> map, MJNode key) {
            return ValueLattice.get().bot();
        }
    }, FORBID_DELETIONS);

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

    /** Pushes an element onto the cc-stack */
    public void pushCC(Bit bit) {
        ccStack.push(bit);
    }

    public void popCC(){
        ccStack.pop();
    }

    public Bit origin(Bit bit) {
        return originMap.get(bit);
    }

    /**
     * Sets the origin of the bit
     *
     * <p><b>Important note: updating the origin bit of a bit is prohibited</b>
     */
    public void origin(Bit bit, Bit origin) {
        originMap.put(bit, origin);
    }

    /** Base origin of a bit, that is its own origin. */
    public Bit origin_(Bit bit) {
        if (origin(bit) == bit) {
            return bit;
        }
        return origin_(origin(bit));
    }

    public Bit applyCondition(Bit bit) {
        if (ccStack.isEmpty() || c(bit).contains(ccStack.peek())) {
            return bit;
        }
        Bit newBit =
                new Bit(
                        v(bit),
                        v(bit) == B.U ? d(bit) : DependencySetLattice.get().bot(),
                        new DependencySet(ccStack.peek()));
        origin(newBit, bit);
        return newBit;
    }

    public Value applyCondition(Value value) {
        return value.stream().map(this::applyCondition).collect(Value.collector());
    }

    public boolean checkInvariants(Bit bit) {
        return (sec(bit) == sl.bot() || (v(bit).isConstant() && d(bit).isEmpty() && c(bit).isEmpty()))
                && (!v(bit).isConstant() || (d(bit).isEmpty() && sec(bit) == sl.bot()));
    }

    public boolean isInputBit(Bit bit) {
        return v(bit) == B.U && d(bit).isEmpty();
    }

    public boolean isInputBit_(Bit bit) {
        return isInputBit(origin_(bit));
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
        return operatorForNode(node).compute(this, arguments);
    }

    public List<MJNode> paramNode(MJNode node){
        return (List<MJNode>)(List<?>)node.children();
    }

    public Value evaluate(MJNode node){
        System.out.println("Evaluate node " + node);
        List<Value> args = paramNode(node).stream().map(this::nodeValue).map(this::applyCondition).collect(Collectors.toList());
        Value opVal = op(node, args);
        Value newValue = applyCondition(opVal);
        nodeValue(node, newValue);
        newValue.description(node.getTextualId());
        if (!opVal.equals(newValue)){
            opVal.description("pre:" + newValue.description());
        }
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
        builder.append(String.format("CC-Stack: %s\n", ccStack.stream().map(Bit::toString).collect(Collectors.joining(" -> "))));
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
}
