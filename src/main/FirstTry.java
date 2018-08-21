package nildumu;

import swp.util.Pair;

import java.time.temporal.ValueRange;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Lattices.B.*;
import static nildumu.Util.set;

/**
 * Rough implementation of a first try of a QIF algorithm. Should help to build an abstract framework.
 */
public class FirstTry {

    /**
     * The current security lattice to work with.
     */
    static Lattices.SecurityLattice secLattice = Lattices.BasicSecLattice.HIGH;
    static int BIT_WIDTH = Parser.BIT_WIDTH;

    /**
     * Value like {@link nildumu.Lattices.Value} but algorithm specific
     */
    static class Value {

        static final Value TOP = new Value(Bit.TOP);
        static final Value BOT = new Value(Bit.BOT);

        final List<Bit> bits;

        /**
         * fills with low ones
         *
         * @param bits
         */
        Value(List<Bit> bits) {
            this.bits = bits;
            while (bits.size() < BIT_WIDTH) {
                bits.add(Bit.of(new Lattices.Bit(ONE, secLattice.bot())));
            }
        }

        /**
         * Fills with copies of the first bit
         *
         * @param bit
         */
        Value(Bit bit) {
            this.bits = new ArrayList<>();
            for (int i = 0; i < BIT_WIDTH; i++) {
                bits.add(bit.copy());
            }
        }

        /**
         * Converts from a {@link Lattices.Value}
         */
        static Value of(Lattices.Value value) {
            return new Value(value.bits.stream().map(Bit::of).collect(Collectors.toList()));
        }

        static Value sup(Value first, Value second) {
            List<Bit> bits = new ArrayList<>();
            for (int i = 0; i < first.bits.size(); i++) {
                bits.add(first.bits.get(i).sup(second.bits.get(i)));
            }
            return new Value(bits);
        }

        int size() {
            return bits.size();
        }

        List<Pair<Bit, Bit>> zip(Value other) {
            List<Pair<Bit, Bit>> bitPairs = new ArrayList<>();
            for (int i = 0; i < size(); i++) {
                bitPairs.add(new Pair<>(bits.get(i), other.bits.get(i)));
            }
            return bitPairs;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Value && zip((Value) obj).stream().allMatch(p -> p.first.equals(p.second));
        }

        @Override
        public int hashCode() {
            return bits.hashCode();
        }

        /**
         * Per bit: uses the dataVal as the assumed value, and the dataSec as the assumed security level
         *
         * @param assumedValue
         * @return
         */
        public BitModifications getModifications(Value assumedValue) {
            BitModificationsBuilder builder = new BitModificationsBuilder();
            zip(assumedValue).stream().forEach(p -> builder.put(p.first.bitModificator.generateModifications(p.first, p.second.dataVal, p.second.dataSec)));
            return builder.getBitModifications();
        }

        /**
         * Value compare the both values bit wise (disregard their identities)
         */
        public boolean valueEquals(Value value) {
            return zip(value).stream().allMatch(p -> p.first.valueEquals(p.second));
        }

        int leakage() {
            return leakage(secLattice.bot());
        }

        int leakage(Lattices.SecurityLattice baseSec) {
            // TODO: rewrite
            Set<Bit> unknownHighDataBits = bits.stream().filter(b -> b.dataVal == U && b.dataSec.biggerThan(baseSec)).collect(Collectors.toSet());
            //Set<Bit> constantHighDependentBits = bits.stream().filter(b -> b.dataVal.value.isPresent() && b.dataDependencies.stream().anyMatch(bit -> bit.dataSec.biggerThan(baseSec))).collect(Collectors.toSet());
            //unknownHighDataBits.addAll(constantHighDependentBits);
            int numberOfControlDependencies = bits.stream().filter(b -> !unknownHighDataBits.contains(b))
                    .flatMap(b -> b.controlDependencies.dependencies.values().stream()).distinct()
                    .filter(c -> c.getSecurityLevel().biggerThan(baseSec))
                    .collect(Collectors.toSet()).size();
            return unknownHighDataBits.size() + numberOfControlDependencies;
        }

        @Override
        public String toString() {
            List<Bit> reversed = new ArrayList<>(bits);
            Collections.reverse(reversed);
            return reversed.stream().map(b -> b.toString()).collect(Collectors.joining("  "));
        }

        public Bit get(int i) {
            return bits.get(i);
        }

        public Set<Bit> getRange(ValueRange range) {
            return stream(range).mapToObj(bits::get).collect(Collectors.toSet());
        }
    }

    /**
     * Bit like {@link nildumu.Lattices.Bit} but algorithm specific
     */
    static class Bit {

        static final Bit TOP = new Bit(U, secLattice.top());
        static final Bit BOT = new Bit(Lattices.B.X, secLattice.bot());

        final Identity<Bit> identity = new Identity<>(null);
        /**
         * the bit value
         */
        final Lattices.B dataVal;
        /**
         * the data dependency induced security level of this bit
         */
        final Lattices.SecurityLattice dataSec;
        /**
         * the data dependencies
         */
        final Set<Bit> dataDependencies;

        /**
         * the control dependencies
         */
        final ControlDependencies controlDependencies;

        /**
         * the bit modifications that this bit introduces
         */
        private BitModificationFunction bitModificator;

        Bit(Lattices.B dataVal, Lattices.SecurityLattice dataSec, Set<Bit> dataDependencies, ControlDependencies controlDependencies, BitModificationFunction bitModificator) {
            this.dataVal = dataVal;
            this.dataSec = dataSec;
            this.dataDependencies = dataDependencies;
            this.controlDependencies = controlDependencies;
            this.bitModificator = bitModificator;
        }

        Bit(Lattices.B dataVal, Lattices.SecurityLattice dataSec, Set<Bit> dataDependencies, ControlDependencies controlDependencies) {
            this.dataVal = dataVal;
            this.dataSec = dataSec;
            this.dataDependencies = dataDependencies;
            this.controlDependencies = controlDependencies;
        }

        Bit(Lattices.B dataVal, Lattices.SecurityLattice dataSec, BitModificationFunction bitModificator) {
            this(dataVal, dataSec, new HashSet<>(), new ControlDependencies(), bitModificator);
        }

        Bit(Lattices.B dataVal, Lattices.SecurityLattice dataSec) {
            this(dataVal, dataSec, new HashSet<>(), new ControlDependencies());
        }

        /**
         * Convert a {@link Lattices.Bit} to a {@link Bit}
         */
        static Bit of(Lattices.Bit bit) {
            return new Bit(bit.val, bit.sec, (bit_, b, sec) -> {
                return new BitModifications(new BitModifications.BitModification(bit_, Bit.of(new Lattices.Bit(b, sec))));
            });
        }

        Bit sup(Bit other) {
            return new Bit(dataVal.supremum(other.dataVal), secLattice.sup(dataSec, other.dataSec),
                    (Set<Bit>) Lattices.supremum((Set<Bit>) dataDependencies, (Set<Bit>) other.dataDependencies),
                    controlDependencies.sup(other.controlDependencies),
                    BitModificationFunction.NONE);
        }

        @Override
        public String toString() {
            return String.format("(%s%s %s%s%s)", dataVal, dataSec, omitEmpty(dataDependencies), controlDependencies.isEmpty() ? "" : controlDependencies.toString(),
                    bitModificator == BitModificationFunction.NONE || dataVal != U ? "" : "mod");
        }

        private String omitEmpty(Collection<?> collection) {
            if (collection.isEmpty()) {
                return "";
            }
            return collection.toString();
        }

        public boolean valueEquals(Bit otherBit) {
            return dataVal == otherBit.dataVal
                    && dataSec.equals(otherBit.dataSec) && dataDependencies.equals(otherBit.dataDependencies)
                    && controlDependencies.equals(otherBit.controlDependencies);
        }

        /**
         * Not a deep copy.
         */
        Bit copy() {
            Bit newBit = new Bit(dataVal, dataSec, copy(dataDependencies), controlDependencies.copy(), bitModificator);
            return newBit;
        }

        /**
         * based on {@link Bit#dataVal}
         */
        boolean isConstant() {
            return dataVal.value.isPresent();
        }

        static <T> Set<T> copy(Set<T> set) {
            Set<T> newSet = new HashSet<>(set);
            return newSet;
        }

        public Lattices.SecurityLattice getDataSec() {
            return dataSec;
        }

        public BitModificationFunction getBitModificator() {
            assert bitModificator != null;
            return bitModificator;
        }

        public void setBitModificator(BitModificationFunction bitModificator) {
            assert bitModificator == null;
            this.bitModificator = bitModificator;
        }

        /**
         * Returns {@link BitModifications#EMPTY} if secLevel is empty
         */
        public BitModifications bitMods(Lattices.B assumedValue, Optional<Lattices.SecurityLattice> secLevel) {
            if (secLevel.isPresent()) {
                return bitMods(assumedValue, secLevel.get());
            }
            return BitModifications.EMPTY;
        }

        public BitModifications bitMods(Lattices.B assumedValue, Lattices.SecurityLattice secLevel) {
            return bitModificator.generateModifications(this, assumedValue, secLevel);
        }
    }


    /**
     * Just wraps an object that might change at a specific point of time.
     * Used to say, that two objects reference the same (object, (implicit) time) tuple. This can be done
     * by assigning two objects the same {@link Identity} instance.
     * => map the equality onto the instance equality of {@link Identity} instances.
     *
     * @param <T> type of the wrapped object
     */
    static class Identity<T> {
        final T wrapped;

        Identity(T wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public String toString() {
            if (wrapped != null) {
                return String.format("[%s, %s]", hashCode() % 100, wrapped);
            }
            return String.format("%d", hashCode() % 100);
        }
    }

    /**
     * A control dependency: condition + bits
     */
    static class ControlDependency {

        final Identity<Parser.ExpressionNode> condition;
        final Bit condRes;

        ControlDependency(Identity<Parser.ExpressionNode> condition, Bit condRes) {
            this.condition = condition;
            this.condRes = condRes;
        }

        @Override
        public String toString() {
            return String.format("(%s, {%s})", condition, condRes.dataVal);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ControlDependency && ((ControlDependency) obj).condRes.dataDependencies.equals(condRes.dataDependencies) && condition == ((ControlDependency) obj).condition;
        }

        @Override
        public int hashCode() {
            return condition.hashCode() % 13 ^ condRes.dataDependencies.hashCode() % 17;
        }

        public Lattices.SecurityLattice getSecurityLevel() {
            return calcSecLevel(condRes.dataDependencies);
        }
    }

    static Lattices.SecurityLattice calcSecLevel(Collection<Bit> bits) {
        return bits.stream().map(b -> b.dataSec).reduce(secLattice.bot(), secLattice::sup);
    }

    /**
     * A set of control dependencies
     */
    static class ControlDependencies {
        final Map<Identity<Parser.ExpressionNode>, ControlDependency> dependencies;

        ControlDependencies(Map<Identity<Parser.ExpressionNode>, ControlDependency> dependencies) {
            this.dependencies = dependencies;
        }

        /**
         * Can merge control dependencies that only differ in the branch
         */
        ControlDependencies(Set<ControlDependency> dependencies) {
            this.dependencies = new HashMap<>();
            dependencies.forEach(this::add);
        }

        ControlDependencies(ControlDependency... dependencies) {
            this(new HashSet<>());
            Arrays.stream(dependencies).forEach(this::add);
        }

        /**
         * Can merge control dependencies that only differ in the branch
         */
        ControlDependencies sup(ControlDependencies other) {
            Set<ControlDependency> newDeps = new HashSet<>(other.dependencies.values());
            newDeps.addAll(dependencies.values());
            return new ControlDependencies(newDeps);
        }

        /**
         * Can merge control dependencies that only differ in the branch
         */
        void add(ControlDependency newDep) {
            if (this.dependencies.containsKey(newDep.condition)) {
                ControlDependency oldDep = this.dependencies.get(newDep.condition);
                ControlDependency dep = new ControlDependency(newDep.condition, newDep.condRes.sup(oldDep.condRes));
                this.dependencies.put(newDep.condition, dep);
            } else {
                this.dependencies.put(newDep.condition, newDep);
            }
        }

        boolean isEmpty() {
            return dependencies.isEmpty();
        }

        ControlDependencies copy() {
            Map<Identity<Parser.ExpressionNode>, ControlDependency> newMap = new HashMap<>();
            dependencies.forEach(newMap::put);
            return new ControlDependencies(newMap);
        }

        @Override
        public String toString() {
            return String.format("{%s}", dependencies.values().stream().map(ControlDependency::toString).sorted().collect(Collectors.joining()));
        }

        Stream<ControlDependency> stream() {
            return dependencies.values().stream();
        }
    }

    /**
     * Update for bits based on conditions, essentially a mapping from Bit to Bit
     */
    static class BitModifications {

        final static BitModifications EMPTY = new BitModifications(Collections.emptyMap());

        static class BitModification {
            final Bit search;
            final Bit replacement;

            BitModification(Bit search, Bit replacement) {
                this.search = search;
                this.replacement = replacement;
            }
        }

        final Map<Bit, Bit> modifications;

        BitModifications(Map<Bit, Bit> modifications) {
            this.modifications = modifications;
        }

        BitModifications(BitModification... modifications) {
            this(Arrays.stream(modifications).collect(Collectors.toMap(b -> b.search, b -> b.replacement)));
        }

        BitModifications(Set<BitModification> modifications) {
            this(modifications.stream().collect(Collectors.toMap(b -> b.search, b -> b.replacement)));
        }

        /**
         * Default supremum action:
         * Merges both bit modification maps and excludes all bit modifications
         * that modify a bit differently in both {@link BitModifications} objects
         */
        BitModifications sup(BitModifications other) {
            Map<Bit, Bit> newMap = new HashMap<>();
            for (Map.Entry<Bit, Bit> modification : Lattices.supremum(modifications.entrySet(), other.modifications.entrySet())) {
                if (modifications.containsKey(modification.getKey()) && other.modifications.containsKey(modification.getKey())) {
                    Bit otherModBit = other.modifications.get(modification.getKey());
                    if (otherModBit.valueEquals(modification.getValue())) {
                        newMap.put(modification.getKey(), modification.getValue());
                    }
                } else {
                    newMap.put(modification.getKey(), modification.getValue());
                }
            }
            return new BitModifications(newMap);
        }

        /**
         * Get the modified version of the passed bit or the bit itself if it isn't modified.
         *
         * @param bit passed bit
         * @return modified bit or passed bit
         */
        Bit get(Bit bit) {
            return modifications.getOrDefault(bit, bit);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BitModifications && ((BitModifications) obj).modifications.equals(modifications);
        }

        /**
         * Create a copy of this modification mapping and replace all bits that are mapped to another bit and equal the
         * passed old bit with the passed new bit
         */
        BitModifications copy(Bit oldBit, Bit newBit) {
            return new BitModifications(modifications.entrySet().stream().map(e -> {
                if (e.getKey() == oldBit) {
                    return new AbstractMap.SimpleEntry<Bit, Bit>(newBit, e.getValue());
                }
                return e;
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        static Collector<BitModifications, Set<BitModifications>, BitModifications> collector() {
            return new Collector<BitModifications, Set<BitModifications>, BitModifications>() {

                @Override
                public Supplier<Set<BitModifications>> supplier() {
                    return () -> new HashSet<>();
                }

                @Override
                public BiConsumer<Set<BitModifications>, BitModifications> accumulator() {
                    return (set, bitMods) -> {
                        set.add(bitMods);
                    };
                }

                @Override
                public java.util.function.BinaryOperator<Set<BitModifications>> combiner() {
                    return (set1, set2) -> {
                        set1.addAll(set2);
                        return set1;
                    };
                }

                @Override
                public Function<Set<BitModifications>, BitModifications> finisher() {
                    return set -> set.stream().reduce(new BitModifications(), BitModifications::sup);
                }

                @Override
                public Set<Characteristics> characteristics() {
                    return EnumSet.of(Characteristics.UNORDERED);
                }
            };
        }
    }

    static class BitModificationsBuilder {
        private final Map<Bit, Bit> modifications;
        private final Set<Bit> omittedBits;

        BitModificationsBuilder() {
            modifications = new HashMap<>();
            omittedBits = new HashSet<>();
        }

        BitModificationsBuilder put(Bit search, Bit replacement) {
            if (modifications.containsKey(search)) {
                if (!modifications.get(search).valueEquals(replacement)) {
                    modifications.remove(search);
                    omittedBits.add(search);
                }
            } else if (!omittedBits.contains(search)) {
                modifications.put(search, replacement);
            }
            return this;
        }

        BitModificationsBuilder put(BitModifications modifications) {
            modifications.modifications.entrySet().forEach(e -> put(e.getKey(), e.getValue()));
            return this;
        }

        BitModifications getBitModifications() {
            return new BitModifications(modifications);
        }
    }

    /**
     * A function that gives for a value the bit modification that are result of setting
     * the bit it belongs to a specific value and a specific security level.
     * <p/>
     * <b>It should only use the passed bit, not the bit it was declared for. As the bits identity might have
     * changed</b>
     */
    @FunctionalInterface
    static interface BitModificationFunction {

        static final BitModificationFunction NONE = (bit, b, sec) -> BitModifications.EMPTY;

        static final BitModificationFunction ERROR = (bit, b, sec) -> {
            throw new NildumuError(String.format("Can't set %s to value %s", bit, b));
        };

        /**
         * @param bit          that this function is called on
         * @param assumedValue value the bit is set to
         * @return list of bit modifications
         */
        default BitModifications generateModifications(Bit bit, Lattices.B assumedValue, Lattices.SecurityLattice assumedSec) {
            if (bit.dataVal != U) {
                return BitModifications.EMPTY;
            }
            return generateModificationsImpl(bit, assumedValue, assumedSec);
        }

        /**
         * @param bit          that this function is called on
         * @param assumedValue value the bit is set to
         * @return list of bit modifications
         */
        BitModifications generateModificationsImpl(Bit bit, Lattices.B assumedValue, Lattices.SecurityLattice assumedSec);
    }

    /**
     * Context that stores the current value of each variable
     */
    static class Context {
        final Map<Variable, Value> variables;

        Context(Map<Variable, Value> variables) {
            this.variables = variables;
        }

        @Override
        public String toString() {
            return "{\n" + variables.entrySet().stream().map(e -> (e.getKey().isInput ? "i " : "  ") + (e.getKey().isOutput ? "o " : "  ") + e.getKey().name + " → " + e.getValue() + ": " + e.getValue().leakage()).collect(Collectors.joining("\n")) + "\n"
                    + " leakage = " + leakage() + "\n"
                    + " input entropy = " + inputEntropy() + "}";
        }

        Value get(Variable variable) {
            return variables.getOrDefault(variable, Value.BOT);
        }

        Context copy() {
            return new Context(new HashMap<Variable, Value>(variables));
        }

        boolean valueEquals(Context other) {
            return other.variables.keySet().equals(variables.keySet()) && variables.entrySet().stream().allMatch(e -> other.variables.get(e.getKey()).valueEquals(e.getValue()));
        }

        int inputEntropy() {
            return entropy(v -> v.isInput);
        }

        int leakage() {
            return entropy(v -> v.isOutput);
        }

        /**
         * min-entropy of the variables that match the predicate.
         * <p/>
         * Treats the bits of all the matching variables as one big value for entropy calculation.
         */
        private int entropy(Predicate<Variable> variablePredicate) {
            return new Value(variables.entrySet().stream().filter(e -> variablePredicate.test(e.getKey())).flatMap(v -> variables.get(v.getKey()).bits.stream()).collect(Collectors.toList())).leakage();
        }
    }

    /**
     * Stores the current global state of the analysis and allows to update and reset it
     */
    static class State {
        /**
         * Mapping of variables to values
         */
        Context context;
        /**
         * Applicable bit modifications
         */
        private Stack<BitModifications> modifications;
        /**
         * Stores the current control dependencies
         */
        private Stack<ControlDependencies> controlDependenciesStack;

        public State() {
            this.context = new Context(new HashMap<>());
            this.modifications = new Stack<>();
            this.controlDependenciesStack = new Stack<>();
        }

        public BitModifications getModifications() {
            return modifications.empty() ? BitModifications.EMPTY : modifications.peek();
        }

        public Context getContext() {
            return context;
        }

        /**
         * Adds the passed modifications to the current modifications (uses the supremum operation for this),
         * same for the control dependencies
         */
        public void push(BitModifications modifications, ControlDependency controlDependency) {
            if (this.modifications.isEmpty()) {
                this.modifications.push(modifications);
            } else {
                this.modifications.push(this.modifications.peek().sup(modifications));
            }
            if (controlDependenciesStack.isEmpty()) {
                this.controlDependenciesStack.push(new ControlDependencies(controlDependency));
            } else {
                this.controlDependenciesStack.push(controlDependenciesStack.peek().sup(new ControlDependencies(controlDependency)));
            }
        }

        /**
         * Resets the bit modification mappings and the control dependencies to the prior state
         */
        public void pop() {
            this.modifications.pop();
            this.controlDependenciesStack.pop();
        }

        /**
         * Returns the currently active control dependencies
         *
         * @return
         */
        public ControlDependencies getControlDependencies() {
            return controlDependenciesStack.empty() ? new ControlDependencies() : controlDependenciesStack.peek();
        }

        /**
         * Returns the value of the variable and applies modifications if applicable.
         * <p/>
         * <b>Only applies the modifications on the bits but not on their properties. Correct? Bug?</b>
         */
        public Value get(Variable variable) {
            Value raw = getContext().get(variable);
            return new Value(raw.bits.stream().map(b -> getModifications().get(b)).collect(Collectors.toList()));
        }
    }

    /**
     * An unary operator. Computes a new {@link Value} from one given {@link Value}s and a {@link State}.
     */
    @FunctionalInterface
    static interface UnaryOperator {

        Value compute(State state, Value val);


        default Optional<Lattices.SecurityLattice> propagateS(Bit x, Lattices.SecurityLattice assumedS) {
            return Optional.of(secLattice.sup(assumedS, x.dataSec));
        }
    }

    /**
     * A binary operator. Computes a new {@link Value} from two given {@link Value}s and a {@link State}.
     */
    static interface BinaryOperator {

        default Value compute(State state, Value first, Value second) {
            return compute(first, second); // TODO: correct?
        }

        default Value compute(Value first, Value second) {
            throw new UnsupportedOperationException();
        }

        default Optional<Lattices.SecurityLattice> propagateS(Bit x, Bit y, Lattices.SecurityLattice assumedS) {
            return secLattice.sup(assumedS, secLattice.min(x.dataSec, y.dataSec));
        }
    }

    /**
     * A simple bit wise operator
     */
    static interface BitWiseBinaryOperator extends BinaryOperator {

        @Override
        public default Value compute(State state, Value first, Value second) {
            List<Bit> bits = new ArrayList<>();
            for (int i = 0; i < first.size(); i++) {
                bits.add(compute(state, first.bits.get(i), second.bits.get(i)));
            }
            return new Value(bits);
        }

        default Bit compute(State state, Bit first, Bit second) {
            return compute(state.getModifications().get(first), state.getModifications().get(second));
        }

        Bit compute(Bit first, Bit second);
    }

    static Set<Bit> filterNonUnknownDataDependencies(Set<Bit> dataDependencies) {
        return dataDependencies.stream().filter(b -> b.dataVal == U).collect(Collectors.toSet());
    }

    static Lattices.SecurityLattice computeSecLevel(Set<Bit> dataDependencies) {
        return secLattice.sup(dataDependencies.stream().map(Bit::getDataSec));
    }

    static ControlDependencies computeControlDependencies(Set<Bit> dataDependencies) {
        return new ControlDependencies(dataDependencies.stream().flatMap(b -> b.controlDependencies.stream()).collect(Collectors.toSet()));
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
    static interface BitWiseBinaryOperatorStructured extends BitWiseBinaryOperator {

        /**
         * Important: the {@link BitWiseBitModification#compute(Bit, Bit, Bit, Lattices.SecurityLattice, Lattices.B)}
         * adds the default bit modification for the own bit to all computed modifications, except for the unused
         * case.
         */
        static interface BitWiseBitModification {

            default BitModifications compute(Bit r, Bit x, Bit y, Lattices.SecurityLattice s, Lattices.B assumedVal) {
                BitModifications mods = null;
                switch (assumedVal) {
                    case ONE:
                        mods = assumeOne(r, x, y, s);
                        break;
                    case ZERO:
                        mods = assumeZero(r, x, y, s);
                        break;
                    case U:
                        mods = assumeUnknown(r, x, y, s);
                        break;
                    case X:
                        return assumeUnused(r, x, y, s);
                }
                return mods.sup(defaultOwnBitMod(r, s, assumedVal));
            }

            BitModifications assumeOne(Bit r, Bit x, Bit y, Lattices.SecurityLattice s);

            BitModifications assumeZero(Bit r, Bit x, Bit y, Lattices.SecurityLattice s);

            default BitModifications assumeUnknown(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                return BitModifications.EMPTY;
            }

            default BitModifications defaultOwnBitMod(Bit r, Lattices.SecurityLattice s, Lattices.B assumedVal) {
                Bit replacement = new Bit(assumedVal, s, new HashSet<>(), new ControlDependencies());
                replacement.bitModificator = BitModificationFunction.ERROR;
                return new BitModifications(new BitModifications.BitModification(r, replacement));
            }

            default BitModifications assumeUnused(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                return BitModifications.EMPTY;
            }
        }

        @Override
        default Bit compute(Bit x, Bit y) {
            Lattices.B bitValue = computeBitValue(x, y);
            if (bitValue.isConstant()) {
                return new Bit(bitValue, secLattice.bot());
            }
            Set<Bit> dataDependencies = computeDataDependencies(x, y, bitValue);
            Lattices.SecurityLattice secLevel = computeSecLevel(dataDependencies);
            ControlDependencies controlDependencies = computeControlDeps(x, y, bitValue, dataDependencies);
            Bit r = new Bit(bitValue, secLevel, dataDependencies, controlDependencies);
            BitWiseBitModification modificator = computeBitModificator(x, y, bitValue, dataDependencies);
            r.setBitModificator(new BitModificationFunction() {
                @Override
                public BitModifications generateModificationsImpl(Bit bit, Lattices.B assumedValue, Lattices.SecurityLattice assumedSec) {
                    return modificator.compute(bit, x, y, secLevel, assumedValue);
                }
            });
            return r;
        }

        Lattices.B computeBitValue(Bit x, Bit y);

        Set<Bit> computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue);

        default ControlDependencies computeControlDeps(Bit x, Bit y, Lattices.B computedBitValue, Set<Bit> computedDataDependencies) {
            return computeControlDependencies(computedDataDependencies);
        }

        BitWiseBitModification computeBitModificator(Bit x, Bit y, Lattices.B computedBitValue, Set<Bit> computedDataDependencies);
    }

    /**
     * A bit wise operator that uses a preset computation structure. Computation steps:
     *
     * <ol>
     * <li>computation of the bit values</li>
     * <li>computation of the dependencies → automatic computation of the security levels and control dependencies</li>
     * <li>computation of the bit modifications</li>
     * </ol>
     */
    static interface BinaryOperatorStructured extends BinaryOperator {

        static interface StructuredBitModification {

            static final StructuredBitModification NONE = new StructuredBitModification() {
                @Override
                public BitModifications compute(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s, Lattices.B assumedVal) {
                    return BitModifications.EMPTY;
                }
            };

            default BitModifications compute(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s, Lattices.B assumedVal) {
                BitModifications mods = null;
                switch (assumedVal) {
                    case ONE:
                        mods = assumeOne(i, r, x, y, s);
                        break;
                    case ZERO:
                        mods = assumeZero(i, r, x, y, s);
                        break;
                    case U:
                        mods = assumeUnknown(i, r, x, y, s);
                        break;
                    case X:
                        return assumeUnused(i, r, x, y, s);
                }
                return mods.sup(defaultOwnBitMod(r, s, assumedVal));
            }

            default BitModifications assumeOne(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s) {
                return BitModifications.EMPTY;
            }

            default BitModifications assumeZero(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s) {
                return BitModifications.EMPTY;
            }

            default BitModifications assumeUnknown(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s) {
                return BitModifications.EMPTY;
            }

            default BitModifications defaultOwnBitMod(Bit r, Lattices.SecurityLattice s, Lattices.B assumedVal) {
                Bit replacement = new Bit(assumedVal, s, new HashSet<>(), new ControlDependencies());
                replacement.bitModificator = BitModificationFunction.ERROR;
                return new BitModifications(new BitModifications.BitModification(r, replacement));
            }

            default BitModifications assumeUnused(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s) {
                return BitModifications.EMPTY;
            }
        }

        @Override
        default Value compute(Value x, Value y) {
            List<Lattices.B> bitValues = computeBitValues(x, y);
            List<Set<Bit>> dataDependencies = computeDataDependencies(x, y, bitValues).stream().map(FirstTry::filterNonUnknownDataDependencies).collect(Collectors.toList());
            List<Lattices.SecurityLattice> secLevels = dataDependencies.stream().map(FirstTry::computeSecLevel).collect(Collectors.toList());
            List<ControlDependencies> controlDependencies = dataDependencies.stream().map(FirstTry::computeControlDependencies).collect(Collectors.toList());
            assert bitValues.size() == dataDependencies.size();
            assert dataDependencies.size() == secLevels.size();
            assert secLevels.size() == controlDependencies.size();
            assert controlDependencies.size() == BIT_WIDTH;
            List<Bit> bits = new ArrayList<>();
            for (int i = 0; i < BIT_WIDTH; i++) {
                Bit r = new Bit(bitValues.get(i), secLevels.get(i), dataDependencies.get(i), controlDependencies.get(i));
                bits.add(r);
                StructuredBitModification modificator = computeBitModificator(i, r, x, y, bitValues, dataDependencies);
                final int j = i;
                r.setBitModificator(new BitModificationFunction() {
                    @Override
                    public BitModifications generateModificationsImpl(Bit bit, Lattices.B assumedValue, Lattices.SecurityLattice assumedSec) {
                        return modificator.compute(j, bit, x, y, assumedSec, assumedValue);
                    }
                });
            }
            return new Value(bits);
        }

        default List<Lattices.B> computeBitValues(Value x, Value y) {
            List<Lattices.B> bitValues = new ArrayList<>();
            for (int i = 0; i < BIT_WIDTH; i++) {
                bitValues.add(computeBitValue(i, x, y));
            }
            return bitValues;
        }

        Lattices.B computeBitValue(int i, Value x, Value y);

        default List<Set<Bit>> computeDataDependencies(Value x, Value y, List<Lattices.B> computedBitValues) {
            return IntStream.range(0, BIT_WIDTH).mapToObj(i -> computeDataDependencies(i, x, y, computedBitValues)).collect(Collectors.toList());
        }

        Set<Bit> computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues);

        StructuredBitModification computeBitModificator(int i, Bit r, Value x, Value y, List<Lattices.B> computedBitValues, List<Set<Bit>> computedDataDependencies);
    }

    /**
     * the bitwise or operator (can be used for booleans (ints of which only the first bit matters) too)
     */
    static final BitWiseBinaryOperatorStructured OR = new BitWiseBinaryOperatorStructured() {

        @Override
        public Lattices.B computeBitValue(Bit x, Bit y) {
            if (x.dataVal == ONE || y.dataVal == ONE) {
                return ONE;
            }
            if (x.dataVal == ZERO && y.dataVal == ZERO) {
                return ZERO;
            }
            return U;
        }

        @Override
        public Set<Bit> computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.dataVal == U && p.second.dataVal != ONE)
                    .flatMap(Pair::firstStream).collect(Collectors.toSet());
        }

        @Override
        public BitWiseBitModification computeBitModificator(Bit x, Bit y, Lattices.B computedBitValue, Set<Bit> computedDataDependencies) {
            return new BitWiseBitModification() {
                @Override
                public BitModifications assumeOne(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    if (y.dataVal == ZERO) {
                        return x.bitMods(ONE, propagateS(x, y, s));
                    }
                    if (x.dataVal == ZERO) {
                        return y.bitMods(ONE, propagateS(x, y, s));
                    }
                    return BitModifications.EMPTY;
                }

                @Override
                public BitModifications assumeZero(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    return stream(x, y).map(bit -> bit.bitMods(ZERO, propagateS(x, y, s))).collect(BitModifications.collector());
                }
            };
        }
    };

    /**
     * the bitwise and operator (can be used for booleans (ints of which only the first bit matters) too)
     */
    static final BitWiseBinaryOperatorStructured AND = new BitWiseBinaryOperatorStructured() {

        @Override
        public Lattices.B computeBitValue(Bit x, Bit y) {
            if (x.dataVal == ONE && y.dataVal == ONE) {
                return ONE;
            }
            if (x.dataVal == ZERO || y.dataVal == ZERO) {
                return ZERO;
            }
            return U;
        }

        @Override
        public Set<Bit> computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.dataVal == U && p.second.dataVal != ZERO)
                    .flatMap(Pair::firstStream).collect(Collectors.toSet());
        }

        @Override
        public BitWiseBitModification computeBitModificator(Bit x, Bit y, Lattices.B computedBitValue, Set<Bit> computedDataDependencies) {
            return new BitWiseBitModification() {
                @Override
                public BitModifications assumeZero(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    if (y.dataVal == ONE) {
                        return x.bitMods(ZERO, propagateS(x, y, s));
                    }
                    if (x.dataVal == ONE) {
                        return y.bitMods(ZERO, propagateS(x, y, s));
                    }
                    return BitModifications.EMPTY;
                }

                @Override
                public BitModifications assumeOne(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    return stream(x, y).map(bit -> bit.bitMods(ONE, propagateS(x, y, s))).collect(BitModifications.collector());
                }
            };
        }
    };

    /**
     * the bitwise xor operator (can be used for booleans (ints of which only the first bit matters) too)
     */
    static final BitWiseBinaryOperatorStructured XOR = new BitWiseBinaryOperatorStructured() {

        @Override
        public Lattices.B computeBitValue(Bit x, Bit y) {
            if (set(x, y).equals(set(ZERO, ONE))) {
                return ONE;
            }
            if (x.dataVal == y.dataVal && y.dataVal.isConstant()) {
                return ZERO;
            }
            return U;
        }

        @Override
        public Set<Bit> computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.dataVal == U)
                    .flatMap(Pair::firstStream).collect(Collectors.toSet());
        }

        @Override
        public BitWiseBitModification computeBitModificator(Bit x, Bit y, Lattices.B computedBitValue, Set<Bit> computedDataDependencies) {
            return new BitWiseBitModification() {
                @Override
                public BitModifications assumeOne(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    return Util.permutatePair(x, y).stream().map(p -> {
                        if (p.second.dataVal == ZERO) {
                            return p.first.bitMods(ONE, propagateS(x, y, s));
                        }
                        if (p.second.dataVal == ONE) {
                            return p.first.bitMods(ZERO, propagateS(x, y, s));
                        }
                        return BitModifications.EMPTY;
                    }).collect(BitModifications.collector());
                }

                @Override
                public BitModifications assumeZero(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    return Util.permutatePair(x, y).stream().map(p -> {
                        if (p.second.dataVal == ONE) {
                            return p.first.bitMods(ONE, propagateS(x, y, s));
                        }
                        if (p.second.dataVal == ZERO) {
                            return p.first.bitMods(ZERO, propagateS(x, y, s));
                        }
                        return BitModifications.EMPTY;
                    }).collect(BitModifications.collector());
                }
            };
        }
    };

    static final UnaryOperator NOT = new UnaryOperator() {
        @Override
        public Value compute(State state, Value val) {
            return XOR.compute(state, val, new Value(Bit.of(new Lattices.Bit(ONE, secLattice.bot()))));
        }
    };


    static final BinaryOperator EQUALS = new BinaryOperatorStructured() {
        @Override
        public Lattices.B computeBitValue(int i, Value x, Value y) {
            if (i > 0) {
                return ZERO;
            }
            if (x.zip(y).stream().allMatch(p -> p.first.dataVal == p.second.dataVal && p.first.isConstant())) {
                return ONE;
            }
            if (x.zip(y).stream().anyMatch(p -> p.first.dataVal != p.second.dataVal && p.first.isConstant() && p.second.isConstant())) {
                return ZERO;
            }
            return U;
        }

        @Override
        public Set<Bit> computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 0 || computedBitValues.get(0).isConstant()) {
                return new HashSet<>();
            }
            return stream(x.zip(y)).filter(b -> b.dataVal == U).collect(Collectors.toSet());
        }

        @Override
        public StructuredBitModification computeBitModificator(int i, Bit r, Value x, Value y, List<Lattices.B> computedBitValues, List<Set<Bit>> computedDataDependencies) {
            if (i > 0 || computedBitValues.get(0).isConstant()) {
                return StructuredBitModification.NONE;
            }
            return new StructuredBitModification() {
                @Override
                public BitModifications assumeOne(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s) {
                    BitModifications def = defaultOwnBitMod(r, s, ONE);
                    return IntStream.range(0, BIT_WIDTH).mapToObj(j -> {
                        Bit x_j = x.get(j);
                        Bit y_j = y.get(j);
                        Lattices.B v_x = x_j.dataVal;
                        Lattices.B v_y = y_j.dataVal;
                        Optional<Lattices.SecurityLattice> sec = propagateS(x_j, y_j, s);
                        if (v_x == v_y) {
                            return x_j.bitMods(v_x, sec).sup(y_j.bitMods(v_x, sec));
                        }
                        Lattices.B alpha = null;
                        if (v_x.isConstant() && v_y == U) {
                            alpha = v_x;
                        }
                        if (v_y.isConstant() && v_x == U) {
                            alpha = v_y;
                        }
                        if (alpha != null) {
                            return x_j.bitMods(alpha, sec).sup(y_j.bitMods(alpha, sec));
                        }
                        return BitModifications.EMPTY;
                    }).collect(BitModifications.collector());
                }

                @Override
                public BitModifications assumeZero(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s) {
                    return defaultOwnBitMod(r, s, ZERO);
                }

                @Override
                public BitModifications assumeUnknown(int i, Bit r, Value x, Value y, Lattices.SecurityLattice s) {
                    return defaultOwnBitMod(r, s, U);
                }
            };
        }
    };

    static final BinaryOperator ADD = new BinaryOperator() {
        @Override
        public Value compute(State state, Value first, Value second) {
            List<Bit> res = new ArrayList<>();
            Bit carry = Bit.of(new Lattices.Bit(ZERO, secLattice.bot()));
            for (Pair<Bit, Bit> pair : first.zip(second)) {
                Pair<Bit, Bit> add = fullAdder(state, pair.first, pair.second, carry);
                carry = add.second;
                res.add(add.first);
            }
            return new Value(res);
        }

        Pair<Bit, Bit> fullAdder(State state, Bit a, Bit b, Bit c) {
            Pair<Bit, Bit> pair = halfAdder(state, a, b);
            Pair<Bit, Bit> pair2 = halfAdder(state, pair.first, c);
            Bit carry = OR.compute(state, pair.second, pair2.second);
            return new Pair<>(pair2.first, carry);
        }

        Pair<Bit, Bit> halfAdder(State state, Bit first, Bit second) {
            return new Pair<>(XOR.compute(state, first, second), AND.compute(state, first, second));
        }
    };

    static final BinaryOperatorStructured LESS = new BinaryOperatorStructured() {

        Stack<Set<Bit>> dependentBits = new Stack<>();

        @Override
        public Lattices.B computeBitValue(int i, Value x, Value y) {
            if (i > 0) {
                return ZERO;
            }
            Lattices.B val = U;
            Set<Bit> depBits = stream(x.zip(y)).collect(Collectors.toSet());
            Bit x_n = x.get(BIT_WIDTH - 1);
            Bit y_n = x.get(BIT_WIDTH - 1);
            Lattices.B v_x_n = x_n.dataVal;
            Lattices.B v_y_n = y_n.dataVal;

            Optional<Integer> differingNonConstantIndex = firstNonMatching(x, y, (a, b) -> a.isConstant() && a == b);

            if (v_x_n.isConstant() && v_y_n.isConstant() && v_x_n != v_y_n && v_x_n.isConstant() && v_y_n.isConstant()) {
                depBits = set();
                if (v_x_n == ONE) { // x is negative
                    val = ONE;
                } else {
                    depBits = set();
                    val = ZERO;
                }
            } else if (v_x_n == ZERO && v_y_n == ZERO && differingNonConstantIndex.isPresent() &&
                    x.get(differingNonConstantIndex.get()).isConstant() && y.get(differingNonConstantIndex.get()).isConstant()) {
                val = y.get(differingNonConstantIndex.get()).dataVal;
                depBits = set();
            }
            dependentBits.push(depBits);
            return val;
        }

        Optional<Integer> firstNonMatching(Value x, Value y, BiFunction<Lattices.B, Lattices.B, Boolean> pred) {
            int j = BIT_WIDTH - 1;
            while (j >= 0 && pred.apply(x.get(j).dataVal, y.get(j).dataVal)) {
                j--;
            }
            if (j >= 0) {
                return Optional.of(j);
            }
            return Optional.empty();
        }

        Set<Bit> unknownBitsInRange(ValueRange range, Value... values) {
            return Arrays.stream(values).flatMap(value -> value.getRange(range).stream()).filter(v -> v.dataVal == U).collect(Collectors.toSet());
        }

        @Override
        public Set<Bit> computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 0 || computedBitValues.get(0).isConstant()) {
                return set();
            }
            return dependentBits.pop();
        }

        @Override
        public StructuredBitModification computeBitModificator(int i, Bit r, Value x, Value
                y, List<Lattices.B> computedBitValues, List<Set<Bit>> computedDataDependencies) {
            return StructuredBitModification.NONE;
        }

    };

    static final BitWiseBinaryOperator PHI = new BitWiseBinaryOperatorStructured() {
        @Override
        public Lattices.B computeBitValue(Bit x, Bit y) {
            return x.dataVal.supremum(y.dataVal);
        }

        @Override
        public Set<Bit> computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Stream.of(x, y).filter(b -> b.dataVal == U).collect(Collectors.toSet());
        }

        @Override
        public ControlDependencies computeControlDeps(Bit x, Bit y, Lattices.B computedBitValue, Set<Bit> computedDataDependencies) {
            return x.controlDependencies.sup(y.controlDependencies);
        }

        @Override
        public BitWiseBitModification computeBitModificator(Bit x, Bit y, Lattices.B computedBitValue, Set<Bit> computedDataDependencies) {
            return new BitWiseBitModification() {
                @Override
                public BitModifications assumeOne(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    return BitModifications.EMPTY;
                }

                @Override
                public BitModifications assumeZero(Bit r, Bit x, Bit y, Lattices.SecurityLattice s) {
                    return BitModifications.EMPTY;
                }
            };
        }
    };


    public static Context process(Parser.MJNode node) {
        final State state = new State();
        final HashSet<Parser.StatementNode> statementNodesToOmitOneTime = new HashSet<>();
        FixpointIteration.worklist(new Parser.NodeVisitor<Boolean>() {

            private IdentityHashMap<Parser.StatementNode, Context> lastContextOfStmtEvaluation = new IdentityHashMap<>();

            @Override
            public Boolean visit(Parser.MJNode node) {
                return false;
            }

            @Override
            public Boolean visit(Parser.ProgramNode program) {
                return false;
            }

            @Override
            public Boolean visit(Parser.VariableAssignmentNode assignment) {
                Value oldValue = state.context.get(assignment.definition);
                Value newValue = Value.BOT;
                if (assignment.expression != null) {
                    newValue = eval(assignment.expression);
                }
                state.context.variables.put(assignment.definition, newValue);
                return !oldValue.equals(newValue);
            }

            @Override
            public Boolean visit(Parser.IfStatementNode ifStatement) {
                if (lastContextOfStmtEvaluation.containsKey(ifStatement) && state.context.valueEquals(lastContextOfStmtEvaluation.get(ifStatement))) {
                    return false;
                }
                lastContextOfStmtEvaluation.put(ifStatement, state.context.copy());
                Value cond = eval(ifStatement.conditionalExpression);
                Lattices.B condVal = cond.bits.get(0).dataVal;
                List<Pair<Lattices.B, Parser.BlockNode>> evaluatedBranches = new ArrayList<>();
                if (condVal == ONE || condVal == U) {
                    evaluatedBranches.add(new Pair<>(ONE, ifStatement.ifBlock));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.ifBlock);
                }
                if (condVal == ZERO || condVal == U) {
                    evaluatedBranches.add(new Pair<>(ZERO, ifStatement.elseBlock));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.elseBlock);
                }
                ControlDependency controlDependency = new ControlDependency(new Identity<>(ifStatement.conditionalExpression), cond.bits.get(0));
                for (Pair<Lattices.B, Parser.BlockNode> evaluatedBranch : evaluatedBranches) {
                    state.push(cond.getModifications(Value.of(Lattices.Value.of(evaluatedBranch.first.value.get(), secLattice.bot()))), controlDependency);
                }
                return true;
            }

            @Override
            public Boolean visit(Parser.WhileStatementNode whileStatement) {
                if (lastContextOfStmtEvaluation.containsKey(whileStatement) && state.context.valueEquals(lastContextOfStmtEvaluation.get(whileStatement))) {
                    return false;
                }
                lastContextOfStmtEvaluation.put(whileStatement, state.context.copy());
                Value cond = eval(whileStatement.conditionalExpression);
                Lattices.B condVal = cond.bits.get(0).dataVal;
                List<Pair<Lattices.B, Parser.BlockNode>> evaluatedBranches = new ArrayList<>();
                if (condVal == ONE || condVal == U) {
                    evaluatedBranches.add(new Pair<>(ONE, whileStatement.body));
                } else {
                    statementNodesToOmitOneTime.add(whileStatement.body);
                }
                ControlDependency controlDependency = new ControlDependency(new Identity<>(whileStatement.conditionalExpression), cond.bits.get(0));
                for (Pair<Lattices.B, Parser.BlockNode> evaluatedBranch : evaluatedBranches) {
                    state.push(cond.getModifications(Value.of(Lattices.Value.of(evaluatedBranch.first.value.get(), secLattice.bot()))), controlDependency);
                }
                return true;
            }

            @Override
            public Boolean visit(Parser.IfStatementEndNode ifEndStatement) {
                state.pop();
                return false;
            }

            @Override
            public Boolean visit(Parser.WhileStatementEndNode whileEndStatement) {
                state.pop();
                return false;
            }

            Value eval(Parser.ExpressionNode expression) {
                Value val = FixpointIteration.walkExpression(new Parser.ExpressionVisitorWArgs<Value, List<Value>>() {
                    @Override
                    public Value visit(Parser.ExpressionNode expression, List<Value> argument) {
                        throw new UnsupportedOperationException(expression.toString());
                    }

                    @Override
                    public Value visit(Parser.PhiNode phi, List<Value> argument) {
                        return PHI.compute(state, argument.get(0), argument.get(1));
                    }

                    @Override
                    public Value visit(Parser.VariableAccessNode variableAccess, List<Value> argument) {
                        assert variableAccess.definition != null;
                        return state.get(variableAccess.definition);
                    }

                    @Override
                    public Value visit(Parser.IntegerLiteralNode literal, List<Value> argument) {
                        return Value.of(literal.value);
                    }

                    @Override
                    public Value visit(Parser.BinaryOperatorNode binaryOperator, List<Value> argument) {
                        BinaryOperator op = null;
                        switch (binaryOperator.operator) {
                            case BAND:
                                op = AND;
                                break;
                            case BOR:
                                op = OR;
                                break;
                            case XOR:
                                op = XOR;
                                break;
                            case PLUS:
                                op = ADD;
                                break;
                            case EQUALS:
                                op = EQUALS;
                                break;
                            case LOWER:
                                op = LESS;
                                break;
                            default:
                                throw new UnsupportedOperationException(binaryOperator.toString());
                        }
                        return op.compute(state, argument.get(0), argument.get(1));
                    }
                }, expression);
                for (Bit bit : val.bits) {
                    state.getControlDependencies().dependencies.values().forEach(bit.controlDependencies::add);
                }
                return val;
            }
        }, node, statementNodesToOmitOneTime);
        return state.context;
    }
}
