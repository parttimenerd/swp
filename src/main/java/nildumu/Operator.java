package nildumu;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.time.temporal.ValueRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import swp.util.Pair;

import static nildumu.Context.c;
import static nildumu.Lattices.*;
import static nildumu.Lattices.B.ONE;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.B.ZERO;

public interface Operator {

    public static class WrongArgumentNumber extends NildumuError {
        WrongArgumentNumber(String op, int actualNumber, int expectedNumber){
            super(String.format("%s, expected %d, but got %d argument(s)", op, expectedNumber, actualNumber));
        }
    }

    public static class VariableAccess implements Operator {

        private final Variable variable;

        public VariableAccess(Variable variable) {
            this.variable = variable;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            checkArguments(arguments);
            return c.getVariableValue(variable);
        }

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return variable.toString();
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 0){
                throw new WrongArgumentNumber(variable.toString(), arguments.size(), 0);
            }
        }
    }

    public static class VariableAssignment implements Operator {

        final Variable variable;

        public VariableAssignment(Variable variable) {
            this.variable = variable;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            checkArguments(arguments);
            return c.setVariableValue(variable, arguments.get(0));
        }

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return variable.toString();
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 1){
                throw new WrongArgumentNumber(variable.toString() + "=", arguments.size(), 1);
            }
        }
    }

    public static class OutputVariableAssignment extends VariableAssignment {

        private final Sec<?> sec;

        public OutputVariableAssignment(Variable variable, Sec<?> sec) {
            super(variable);
            assert variable.isOutput;
            this.sec = sec;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            checkArguments(arguments);
            c.addOutputValue(sec, arguments.get(0));
            return c.setVariableValue(variable, arguments.get(0));
        }

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return variable.toString();
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 1){
                throw new WrongArgumentNumber(variable.toString() + "=", arguments.size(), 1);
            }
        }
    }

    public static class LiteralOperator implements Operator {
        private final Value literal;

        public LiteralOperator(Value literal) {
            this.literal = literal;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            return literal;
        }

        @Override
        public String toString(List<Value> arguments) {
            return literal.toString();
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 0){
                throw new WrongArgumentNumber(literal.toString(), arguments.size(), 0);
            }
        }
    }

    public static abstract class UnaryOperator implements Operator {

        public final String symbol;

        public UnaryOperator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            checkArguments(arguments);
            return compute(c, arguments.get(0));
        }

        abstract Value compute(Context c, Value argument);

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return String.format("%s%s", symbol, arguments.get(0).toString());
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 1){
                throw new WrongArgumentNumber(symbol, arguments.size(), 1);
            }
        }
    }

    public static abstract class BinaryOperator implements Operator {

        public final String symbol;

        public BinaryOperator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            checkArguments(arguments);
            return compute(c, arguments.get(0), arguments.get(1));
        }

        abstract Value compute(Context c, Value first, Value second);

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return String.format("%s%s%s", arguments.get(0), symbol, arguments.get(1));
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 2){
                throw new WrongArgumentNumber(symbol, arguments.size(), 2);
            }
        }
    }

    public static abstract class BitWiseBinaryOperator extends BinaryOperator {

        public BitWiseBinaryOperator(String symbol) {
            super(symbol);
        }

        @Override
        Value compute(Context c, Value first, Value second) {
            return new Value(first.lattice().mapBits(first, second, (a, b) -> compute(c, a, b)));
        }

        abstract Bit compute(Context c, Bit first, Bit second);
    }

    /**
     * A bit wise operator that uses a preset computation structure. Computation steps:
     *operatorPerNode
     * <ol>
     * <li>computation of the bit value</li>
     * <li>computation of the dependencies → automatic computation of the security level and the control dependencies</li>
     * <li>computation of the bit modifications</li>
     * </ol>
     */
    public static abstract class BitWiseBinaryOperatorStructured extends BitWiseBinaryOperator {
        public BitWiseBinaryOperatorStructured(String symbol) {
            super(symbol);
        }

        @Override
        Bit compute(Context c, Bit x, Bit y) {
            Lattices.B bitValue = computeBitValue(x, y);
            if (bitValue.isConstant()) {
                return new Bit(bitValue);
            }
            DependencySet dataDeps = computeDataDependencies(x, y, bitValue);
            DependencySet controlDeps = computeControlDeps(x, y, bitValue, dataDeps);
            return new Bit(bitValue, dataDeps, controlDeps);
        }

        abstract B computeBitValue(Bit x, Bit y);

        abstract DependencySet computeDataDependencies(Bit x, Bit y, B computedBitValue);

        DependencySet computeControlDeps(Bit x, Bit y, Lattices.B computedBitValue, DependencySet computedDataDeps) {
            return computeControllDeps(computedDataDeps);
        }
    }

    public static abstract class BitWiseOperator implements Operator {

        private final String symbol;

        public BitWiseOperator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public Value compute(Context c, List<Value> values) {
            int maxWidth = values.stream().mapToInt(Value::size).max().getAsInt();
            List<Value> extendedValues = values.stream().map(v -> v.extend(maxWidth)).collect(Collectors.toList());
            return IntStream.range(1, extendedValues.size() + 1).mapToObj(i -> computeBit(c, extendedValues.stream().map(v -> v.get(i)).collect(Collectors.toList()))).collect(Value.collector());
        }

        abstract Bit computeBit(Context c, List<Bit> bits);

        @Override
        public String toString(List<Value> arguments) {
            return arguments.stream().map(Value::toString).collect(Collectors.joining(symbol));
        }
    }

    /**
     * A bit wise operator that uses a preset computation structure. Computation steps:
     *
     * <ol>
     * <li>computation of the bit value</li>
     * <li>computation of the dependencies → automatic computation of the security level and the control dependencies</li>
     * <li>computation of the bit modifications</li>
     * </ol>
     */
    public static abstract class BitWiseOperatorStructured extends BitWiseOperator {
        public BitWiseOperatorStructured(String symbol) {
            super(symbol);
        }

        @Override
        Bit computeBit(Context c, List<Bit> bits) {
            Lattices.B bitValue = computeBitValue(bits);
            if (bitValue.isConstant()) {
                return new Bit(bitValue);
            }
            DependencySet dataDeps = computeDataDependencies(bits, bitValue);
            DependencySet controlDeps = computeControlDeps(bits, bitValue, dataDeps);
            return new Bit(bitValue, dataDeps, controlDeps);
        }

        abstract B computeBitValue(List<Bit> bits);

        abstract DependencySet computeDataDependencies(List<Bit> bits, B computedBitValue);

        DependencySet computeControlDeps(List<Bit> bits, Lattices.B computedBitValue, DependencySet computedDataDeps) {
            return computeControllDeps(computedDataDeps);
        }
    }

    public static abstract class BinaryOperatorStructured extends BinaryOperator {

        public BinaryOperatorStructured(String symbol) {
            super(symbol);
        }

        @Override
        public Value compute(Context c, Value x, Value y) {
            List<B> bitValues = computeBitValues(x, y);
            List<DependencySet> dataDeps = computeDataDependencies(x, y, bitValues);
            List<DependencySet> controlDeps = dataDeps.stream().map(this::computeControllDeps).collect(Collectors.toList());
            return x.lattice().map(x, y, (a, b) -> {
                List<Bit> bits = new ArrayList<>();
                for (int i = 0; i < x.size(); i++){
                    if (bitValues.get(i).isConstant()){
                        bits.add(new Bit(bitValues.get(i)));
                    } else {
                        bits.add(new Bit(bitValues.get(i), dataDeps.get(i), controlDeps.get(i)));
                    }
                }
                return new Value(bits);
            });
        }

        public List<B> computeBitValues(Value x, Value y) {
            return x.lattice().map(x, y, (a, b) -> {
                List<B> bs = new ArrayList<>();
                for (int i = 1; i <= x.size(); i++){
                    bs.add(computeBitValue(i,a, b));
                }
                return bs;
            });
        }

        abstract B computeBitValue(int i, Value x, Value y);

        List<DependencySet> computeDataDependencies(Value x, Value y, List<B> computedBitValues) {
            return IntStream.range(1, Math.max(x.size(), y.size())).mapToObj(i -> computeDataDependencies(i, x, y, computedBitValues)).collect(Collectors.toList());
        }

        abstract DependencySet computeDataDependencies(int i, Value x, Value y, List<B> computedBitValues);

    }

    /**
     * the bitwise or operator (can be used for booleans (ints of which only the first bit matters) too)
     */
    static final BitWiseBinaryOperatorStructured OR = new BitWiseBinaryOperatorStructured("|") {

        @Override
        public B computeBitValue(Bit x, Bit y) {
            if (x.val == ONE || y.val == ONE) {
                return ONE;
            }
            if (x.val == ZERO && y.val == ZERO) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.val == U && p.second.val != ONE)
                    .flatMap(Pair::firstStream).collect(DependencySet.collector());
        }
    };

    static final BitWiseBinaryOperatorStructured AND = new BitWiseBinaryOperatorStructured("&") {

        @Override
        public B computeBitValue(Bit x, Bit y) {
            if (x.val == ONE && y.val == ONE) {
                return ONE;
            }
            if (x.val == ZERO || y.val == ZERO) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.val == U && p.second.val != ZERO)
                    .flatMap(Pair::firstStream).collect(DependencySet.collector());
        }
    };

    static final BitWiseBinaryOperatorStructured XOR = new BitWiseBinaryOperatorStructured("^") {

        @Override
        public B computeBitValue(Bit x, Bit y) {
            if (x.val != y.val && x.isConstant() && y.isConstant()) {
                return ONE;
            }
            if (x.val == y.val && y.isConstant()) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.val == U)
                    .flatMap(Pair::firstStream).collect(DependencySet.collector());
        }
    };

    static final UnaryOperator NOT = new UnaryOperator("~") {
        @Override
        public Value compute(Context c, Value val) {
            return XOR.compute(c, val, new Value(new Bit(ONE), new Bit(ONE)));
        }
    };

    static final BinaryOperator EQUALS = new BinaryOperatorStructured("==") {
        @Override
        public Lattices.B computeBitValue(int i, Value x, Value y) {
            if (i > 0) {
                return ZERO;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> a.val.equals(b.val) && a.isConstant()).stream().allMatch(Boolean::booleanValue)) {
                return ONE;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> !a.val.equals(b.val) && a.isConstant() && b.isConstant()).stream().anyMatch(Boolean::booleanValue)) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 1 || computedBitValues.get(0).isConstant()) {
                return DependencySetLattice.get().bot();
            }
            return Stream.concat(x.stream().filter(b -> b.val == U),
                    y.stream().filter(b -> b.val == U)).collect(DependencySet.collector());
        }
    };

    static final BinaryOperator UNEQUALS = new BinaryOperatorStructured("!=") {

        @Override
        public Lattices.B computeBitValue(int i, Value x, Value y) {
            if (i > 0) {
                return ZERO;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> a.val.equals(b.val) && a.isConstant()).stream().allMatch(Boolean::booleanValue)) {
                return ZERO;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> !a.val.equals(b.val) && a.isConstant() && b.isConstant()).stream().anyMatch(Boolean::booleanValue)) {
                return ONE;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 1 || computedBitValues.get(0).isConstant()) {
                return DependencySetLattice.get().bot();
            }
            return Stream.concat(x.stream().filter(b -> b.val == U),
                    y.stream().filter(b -> b.val == U)).collect(DependencySet.collector());
        }
    };

    static final BinaryOperatorStructured LESS = new BinaryOperatorStructured("<") {

        Stack<DependencySet> dependentBits = new Stack<>();

        @Override
        public B computeBitValue(int i, Value x, Value y) {
            if (i > 1) {
                return ZERO;
            }
            return x.lattice().map(x, y, (a, b) -> {
                Lattices.B val = U;
                DependencySet depBits = Stream.concat(x.stream(), y.stream()).filter(Bit::isUnknown).collect(DependencySet.collector());
                Bit a_n = a.signBit();
                Bit b_n = b.signBit();
                B v_x_n = a_n.val;
                B v_y_n = b_n.val;
                Optional<Integer> differingNonConstantIndex = firstNonMatching(a, b, (c, d) -> a.isConstant() && c == d);
                if (v_x_n.isConstant() && v_y_n.isConstant() && v_x_n != v_y_n) {
                    depBits = DependencySetLattice.get().bot();
                    if (v_x_n == ONE) { // x is negative
                        val = ONE;
                    } else {
                        depBits = DependencySetLattice.get().bot();
                        val = ZERO;
                    }
                } else if (v_x_n == ZERO && v_y_n == ZERO && differingNonConstantIndex.isPresent() &&
                        x.get(differingNonConstantIndex.get()).isConstant() && y.get(differingNonConstantIndex.get()).isConstant()) {
                    val = y.get(differingNonConstantIndex.get()).val;
                    depBits = DependencySetLattice.get().bot();
                }
                dependentBits.push(depBits);
                return val;
            });
        }

        Optional<Integer> firstNonMatching(Value x, Value y, BiPredicate<B, B> pred) {
            int j = x.size() - 1;
            while (j >= 1 && pred.test(x.get(j).val, y.get(j).val)) {
                j--;
            }
            if (j >= 1) {
                return Optional.of(j);
            }
            return Optional.empty();
        }

        Set<Bit> unknownBitsInRange(ValueRange range, Value... values) {
            return Arrays.stream(values).flatMap(value -> value.getRange(range)).filter(Bit::isUnknown).collect(Collectors.toSet());
        }

        @Override
        public DependencySet computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 0 || computedBitValues.get(0).isConstant()) {
                return DependencySetLattice.get().bot();
            }
            return dependentBits.pop();
        }
    };

    static final BitWiseBinaryOperator PHI = new BitWiseBinaryOperatorStructured("phi") {
        @Override
        public Lattices.B computeBitValue(Bit x, Bit y) {
            return bs.sup(x.val, y.val);
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Stream.of(x, y).filter(Bit::isUnknown).collect(DependencySet.collector());
        }

        @Override
        public DependencySet computeControlDeps(Bit x, Bit y, B computedBitValue, DependencySet computedDataDependencies) {
            return ds.sup(c(x), c(y));
        }
    };

    static final BitWiseOperator PHI_GENERIC = new BitWiseOperatorStructured("phi") {

        @Override
        public Lattices.B computeBitValue(List<Bit> bits) {
            return bs.sup(bits.stream().map(b -> b.val));
        }

        @Override
        public DependencySet computeDataDependencies(List<Bit> bits, Lattices.B computedBitValue) {
            return bits.stream().filter(Bit::isUnknown).collect(DependencySet.collector());
        }

        @Override
        public DependencySet computeControlDeps(List<Bit> bits, B computedBitValue, DependencySet computedDataDependencies) {
            return ds.sup(bits.stream().map(b -> b.controlDeps));
        }
    };

    /**
     * TODO: not the canonical implementation
     */
    static final BinaryOperator ADD = new BinaryOperator("+") {
        @Override
        Value compute(Context c, Value first, Value second) {
            List<Bit> res = new ArrayList<>();
            Util.Box<Bit> carry = new Util.Box<>(Bit.ZERO);
            vl.mapBitsToValue(first, second, (a, b) -> {
                Pair<Bit, Bit> add = fullAdder(c, a, b, carry.val);
                carry.val = add.second;
                return add.first;
            });
            return new Value(res);
        }

        Pair<Bit, Bit> fullAdder(Context context, Bit a, Bit b, Bit c) {
            Pair<Bit, Bit> pair = halfAdder(context, a, b);
            Pair<Bit, Bit> pair2 = halfAdder(context, pair.first, c);
            Bit carry = OR.compute(context, pair.second, pair2.second);
            return new Pair<>(pair2.first, carry);
        }

        Pair<Bit, Bit> halfAdder(Context context, Bit first, Bit second) {
            return new Pair<>(XOR.compute(context, first, second), AND.compute(context, first, second));
        }
    };

    /**
     * TODO: not the canonical implementation
     */
    static final BinaryOperator MINUS = new BinaryOperator("-") {
        @Override
        Value compute(Context c, Value first, Value second) {
            return ADD.compute(c, first, NOT.compute(c, second));
        }
    };

    static final BinaryOperator MULTIPLY = new BinaryOperator("*") {
        @Override
        Value compute(Context c, Value first, Value second) {
            throw new NotImplementedException();
        }
    };

    default Value compute(Context c, List<Value> arguments){
        throw new NotImplementedException();
    }

    default DependencySet computeControllDeps(DependencySet dataDeps){
        return dataDeps.stream().flatMap(d -> d.controlDeps.stream()).collect(DependencySet.collector());
    }

    public String toString(List<Value> arguments);
}
