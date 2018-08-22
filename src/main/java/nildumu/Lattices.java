package nildumu;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import swp.util.Pair;

import java.time.temporal.ValueRange;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Lattices.B.ONE;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.B.X;
import static nildumu.Lattices.B.ZERO;
import static nildumu.Util.*;

/**
 * The basic lattices needed for the project
 */
public class Lattices {

    @FunctionalInterface
    public static interface IdToElement {
        public static IdToElement DEFAULT = x -> null;

        public Object toElem(String id);
    }

    /**
     * A basic lattice that contains elements of type T. Has only a bottom element.
     *
     *
     *
     * @param <T> type of the elements
     */
    public static interface Lattice<T> {

        /**
         * Supremum of the two elements
         */
        T sup(T a, T b);

        /**
         * Calculates the supremum of the elements
         */
        public default T sup(Stream<T> elems){
            return elems.reduce(bot(), this::sup);
        }

        /**
         * Infimum of the two elements
         */
        T inf(T a, T b);

        /**
         * Calculates the supremum of the elements
         *
         * throws an error if elems is empty
         */
        public default T inf(Stream<T> elems){
            return elems.reduce(this::inf).get();
        }

        /**
         * Smallest element
         */
        T bot();

        /**
         * Calculate the minimum on the inputs, if they are comparable
         */
        public default Optional<T> min(T a, T b){
            T infElemen = inf(a, b);
            if (infElemen.equals(a)){
                return Optional.of(a);
            }
            if (equals(infElemen.equals(b))){
                return Optional.of(b);
            }
            return Optional.empty();
        }

        /**
         * Calculate the minimum on the inputs, if they are comparable
         */
        public default Optional<T> max(T a, T b){
            T supElement = sup(a, b);
            if (supElement.equals(a)){
                return Optional.of(a);
            }
            if (supElement.equals(b)){
                return Optional.of(b);
            }
            return Optional.empty();
        }

        /**
         * a < b?
         */
        public default boolean lowerEqualsThan(T a, T b){
            return inf(a, b).equals(a);
        }

        /**
         * a < b?
         */
        public default boolean greaterEqualsThan(T a, T b){
            return sup(a, b).equals(a);
        }

        /**
         * Is elem one of the elements in the passed list
         */
        public default boolean in(T elem, T... elements){
            for (T e : elements) {
                if (elem.equals(e)){
                    return true;
                }
            }
            return false;
        }

        public default String toString(T elem){
            return elem.toString();
        }

        public default boolean isFinite(){
            return false;
        }

        public default T parse(String str){
            return parse(str, IdToElement.DEFAULT);
        }

        public default T parse(String str, IdToElement idToElement){
            return parse(0, str, idToElement).first;
        }

        /**
         *
         * @param start
         * @param str
         * @return (result, index of char after the parsed)
         */
        Pair<T, Integer> parse(int start, String str, IdToElement idToElement);
    }

    public static class ParsingError extends NildumuError {
        public ParsingError(String source, int column, String message){
            super(String.format("Error in '%s' in column %d: %s", source.substring(0, column) + "\uD83D\uDDF2" + source.substring(column), column, message));
        }
    }

    /**
     * A set of lattice elements
     *
     * @param <T> type of the elements
     */
    public static class SetLattice<T, X extends Set<T>> implements Lattice<X> {

        final Lattice<T> elementLattice;
        final Function<Collection<T>, X> setProducer;
        private final X bot;

        public SetLattice(Lattice<T> elementLattice, Function<Collection<T>, X> setProducer) {
            this.elementLattice = elementLattice;
            this.setProducer = setProducer;
            this.bot = setProducer.apply(Collections.emptySet());
        }

        public X create(T... elements){
            return setProducer.apply(Arrays.asList(elements));
        }

        public X create(Collection<T> elements){
            return setProducer.apply(elements);
        }

        @Override
        public X sup(X a, X b) {
            List<T> t = new ArrayList<>();
            t.addAll(a);
            t.addAll(b);
            return setProducer.apply(t);
        }

        @Override
        public X inf(X a, X b) {
            X newSet = setProducer.apply(a);
            newSet.retainAll(b);
            return newSet;
        }

        @Override
        public X bot() {
            return bot;
        }

        /**
         * Parses comma separated sets, that are encased in "{" and "}", ids are prefixed by "#"
         * @param str
         * @return
         */
        public Pair<X, Integer> parse(int start, String str, IdToElement idToElement) {
            while (str.charAt(start) == ' ' && start < str.length() - 1){
                start++;
            }
            if (str.charAt(start) != '{'){
                if (str.charAt(start) == 'ø'){
                    return new Pair<>(bot, start + 1);
                }
                throw new ParsingError(str, start, "Expected '{'");
            }
            int i = start + 1;
            Set<T> elements = new HashSet<>();
            for (; i < str.length() - 1; i++) {
                while (str.charAt(i) == ' ' && i < str.length() - 1){
                    i++;
                }
                if (str.charAt(i) == '#'){
                    i++;
                    int end = i;
                    while (Character.isJavaIdentifierPart(str.charAt(end)) && end < str.length() - 1){
                        end++;
                    }
                    String id = str.substring(i, end);
                    Object res = idToElement.toElem(id);
                    if (res == null){
                        throw new NildumuError(String.format("No such id %s", id));
                    }
                    elements.add((T)res);
                    i = end;
                } else {
                    Pair<T, Integer> ret = elementLattice.parse(i, str, idToElement);
                    elements.add(ret.first);
                    i = ret.second;
                }
                while (str.charAt(i) == ' ' && i < str.length() - 1){
                    i++;
                }
                switch (str.charAt(i)){
                    case ',':
                        continue;
                    case '}':
                        return new Pair<>(create(elements), i + 1);
                    default:
                        throw new ParsingError(str, i, "Expected '}'");
                }
            }
            if (str.charAt(i) != '}'){
                throw new ParsingError(str, i, "Expected '}'");
            }
            return new Pair<>(create(elements), i + 1);
        }

        @Override
        public String toString(X elem) {
            if (elem.isEmpty()){
                return "ø";
            }
            return "{" + elem.stream().map(elementLattice::toString).collect(Collectors.joining(", ")) + "}";
        }

    }

    public static interface CompleteLattice<T> extends Lattice<T> {
        T top();

        /**
         * Calculates the supremum of the elements
         *
         * throws an error if elems is empty
         */
        public default T inf(Stream<T> elems){
            return elems.reduce(top(), this::inf);
        }

    }

    public static interface BoundedLattice<T> extends CompleteLattice<T> {
        Set<T> elements();
    }

    public static interface SecurityLattice<T extends Sec> extends BoundedLattice<T> {

        public static final Map<String, SecurityLattice> LATTICES = lattices();

        public static SecurityLattice<?> forName(String name){
            if (LATTICES.containsKey(name)){
                return LATTICES.get(name);
            }
            throw new NoSuchElementException(String.format("No such security lattice %s, expected one of these: %s", name, String.join(", ",LATTICES.keySet())));
        }

        public static Map<String, SecurityLattice> lattices(){
            Map<String, SecurityLattice> map = new HashMap<>();
            map.put("basic", BasicSecLattice.get());
            map.put("diamond", DiamondSecLattice.get());
            return Collections.unmodifiableMap(map);
        }
    }

    public static interface LatticeElement<T, L extends Lattice<T>> {

        public L lattice();
    }

    public static interface Sec<T extends Sec<T>> extends LatticeElement<T, SecurityLattice<T>>{}

    public enum BasicSecLattice implements SecurityLattice<BasicSecLattice>, Sec<BasicSecLattice> {
        LOW, HIGH;


        @Override
        public BasicSecLattice top() {
            return HIGH;
        }

        @Override
        public BasicSecLattice bot() {
            return LOW;
        }

        @Override
        public BasicSecLattice sup(BasicSecLattice a, BasicSecLattice b) {
            return a == LOW ? b : a;
        }

        @Override
        public BasicSecLattice inf(BasicSecLattice a, BasicSecLattice b) {
            return a == HIGH ? b : a;
        }

        @Override
        public Pair<BasicSecLattice, Integer> parse(int start, String str, IdToElement idToElement) {
            switch (str.charAt(start)){
                case 'h':
                    return new Pair<>(HIGH, start + 1);
                case 'l':
                    return new Pair<>(LOW, start + 1);
            }
            throw new ParsingError(str, start, String.format("No such security lattice element '%s'", str.substring(start, start + 1)));
        }

        public String toString() {
            return name().toLowerCase().substring(0, 1);
        }

        @Override
        public Set<BasicSecLattice> elements() {
            return new LinkedHashSet<>(Arrays.asList(LOW, HIGH));
        }

        @Override
        public SecurityLattice<BasicSecLattice> lattice() {
            return HIGH;
        }

        public static BasicSecLattice get(){
            return HIGH;
        }

    }

    public enum DiamondSecLattice implements SecurityLattice<DiamondSecLattice>, Sec<DiamondSecLattice> {
        LOW(0, "l"), MID1(1, "m"), MID2(1, "n"), HIGH(2, "h");

        final int level;
        final String shortName;

        DiamondSecLattice(int level, String shortName){
            this.level = level;
            this.shortName = shortName;
        }

        @Override
        public DiamondSecLattice top() {
            return HIGH;
        }

        @Override
        public DiamondSecLattice bot() {
            return LOW;
        }

        @Override
        public DiamondSecLattice sup(DiamondSecLattice a, DiamondSecLattice b) {
            if (a.level == b.level){
                if (a != b){
                    return HIGH;
                }
                return a;
            }
            if (a.level > b.level){
                return a;
            } else {
                return b;
            }
        }

        @Override
        public DiamondSecLattice inf(DiamondSecLattice a, DiamondSecLattice b) {
            if (a.level == b.level){
                if (a != b){
                    return LOW;
                }
                return a;
            }
            if (a.level < b.level){
                return a;
            } else {
                return b;
            }
        }

        @Override
        public Pair<DiamondSecLattice, Integer> parse(int start, String str, IdToElement idToElement) {
            switch (str.charAt(start)){
                case 'h':
                    return new Pair<>(HIGH, start + 1);
                case 'm':
                    return new Pair<>(MID1, start + 1);
                case 'n':
                    return new Pair<>(MID2, start + 1);
                case 'l':
                    return new Pair<>(LOW, start + 1);
            }
            throw new ParsingError(str, start, String.format("No such security lattice element '%s'", str.substring(start, start + 1)));
        }

        @Override
        public String toString() {
            return shortName;
        }

        @Override
        public Set<DiamondSecLattice> elements() {
            return new LinkedHashSet<>(Arrays.asList(HIGH, MID1, MID2, LOW));
        }

        @Override
        public SecurityLattice<DiamondSecLattice> lattice() {
            return HIGH;
        }

        public static DiamondSecLattice get(){
            return HIGH;
        }
    }


    /**
     * The bit nildumu, known from the BitValue paper
     */
    public static enum B implements BoundedLattice<B>, LatticeElement<B, B> {

        /**
         * Bit isn't needed
         */
        X("x", Optional.empty()),
        /**
         * Value is unknown, can be 1 or 0
         */
        U("u", Optional.empty()),
        ZERO("0", Optional.of(0)),
        ONE("1", Optional.of(1));

        public final String name;
        public final Optional<Integer> value;

        private B(String name, Optional<Integer> value) {
            this.name = name;
            this.value = value;
        }

        public B top(){
            return U;
        }

        @Override
        public B sup(B a, B b) {
            if (a != b && a.isConstant() && b.isConstant()){
                return U;
            }
            return lowerEqualsThan(a, b) ? b : a;
        }

        @Override
        public boolean lowerEqualsThan(B a, B b) {
            return a == X || a == b || (a.isConstant() && b == U);
        }

        @Override
        public boolean greaterEqualsThan(B a, B b) {
            return lowerEqualsThan(b, a);
        }

        @Override
        public B inf(B a, B b) {
            if (a != b && a.isConstant() && b.isConstant()){
                return X;
            }
            return lowerEqualsThan(a, b) ? a : b;
        }

        public B bot(){
            return X;
        }

        @Override
        public Pair<B, Integer> parse(int start, String str, IdToElement idToElement) {
            switch (str.charAt(start)){
                case 'x': return new Pair<>(X, start + 1);
                case 'u': return new Pair<>(U, start + 1);
                case '0': return new Pair<>(ZERO, start + 1);
                case '1': return new Pair<>(ONE, start + 1);
            }
            throw new ParsingError(str, start, String.format("No such bit lattice element '%s'", str.substring(start, start + 1)));
        }

        @Override
        public String toString() {
            return name;
        }

        B supremum(B other){
            if (this == other){
                return this;
            }
            if (this.isConstant() && other.isConstant()){
                return U;
            }
            if (this == U || other == X){
                return this;
            }
            return other;
        }

        public boolean isConstant(){
            return value.isPresent();
        }

        @Override
        public Set<B> elements() {
            return new HashSet<>(Arrays.asList(X, U, ZERO, ONE));
        }

        @Override
        public B lattice() {
            return X;
        }

        public B neg(){
            switch (this){
                case ZERO:
                    return ONE;
                case ONE:
                    return ZERO;
                default:
                    return this;
            }
        }
    }

    public static class DependencySet extends HashSet<Bit> {

        private boolean initialized = false;

        public DependencySet(Collection<? extends Bit> c) {
            super(c);
            initialized = true;
        }

        public DependencySet(Bit bit){
            this(Collections.singleton(bit));
        }

        @Override
        public boolean add(Bit bit) {
            if (initialized) {
                throw new UnsupportedOperationException();
            }
            return super.add(bit);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return DependencySetLattice.get().toString(this);
        }

        public static Collector<Bit, ?, DependencySet> collector(){
            return Collectors.collectingAndThen(Collectors.toList(), DependencySet::new);
        }

        public Bit getSingleBit(){
            assert size() == 1;
            return iterator().next();
        }
    }

    public static class DependencySetLattice extends SetLattice<Bit, DependencySet> {

        private static final DependencySetLattice instance = new DependencySetLattice();

        public DependencySetLattice() {
            super(BitLattice.get(), DependencySet::new);
        }

        public static DependencySetLattice get(){
            return instance;
        }
    }

    static final DependencySetLattice ds = DependencySetLattice.get();
    static final B bs = B.U;
    static final BitLattice bl = BitLattice.get();
    static final ValueLattice vl = ValueLattice.get();

    /**
     *
     */
    public static class BitLattice implements Lattice<Bit> {

        private final static BitLattice BIT_LATTICE = new BitLattice();

        Bit bot;

        public BitLattice() {

        }

        @Override
        public Bit sup(Bit a, Bit b) {
            return new Bit(bs.sup(a.val, b.val), ds.sup(a.dataDeps, b.dataDeps), ds.sup(a.dataDeps, b.dataDeps));
        }

        @Override
        public Bit inf(Bit a, Bit b) {
            return new Bit(bs.inf(a.val, b.val), ds.inf(a.dataDeps, b.dataDeps), ds.inf(a.dataDeps, b.dataDeps));
        }

        @Override
        public Bit bot() {
            if (bot == null){
                bot = new Bit(X);
            }
            return bot;
        }

        @Override
        public Pair<Bit, Integer> parse(int start, String str, IdToElement idToElement) {
            while (str.charAt(start) == ' '){
                start++;
            }
            if (str.charAt(start) != '(' && str.charAt(start) != '#'){
                return new Pair<>(new Bit(bs.parse(start, str, idToElement).first), start + 1);
            }
            Pair<List<Object>, Integer> ret = parseTuple(start, str, idToElement, bs, ds, ds);
            return new Pair<>(
                    new Bit((B)ret.first.get(0),
                    (DependencySet)ret.first.get(1),
                    (DependencySet)ret.first.get(0)), ret.second);
        }

        @Override
        public String toString(Bit bit) {
            return bit.toString();
        }

        public static BitLattice get(){
            return BIT_LATTICE;
        }

        public void walkTopologicalOrder(Bit startBit, Consumer<Bit> consumer, Predicate<Bit> ignoreBit){
            List<Bit> bits = new ArrayList<>();
            walkBits(startBit, bits::add, ignoreBit);
            for (int i = bits.size() - 1; i >= 0; i++){
                consumer.accept(bits.get(i));
            }
        }

        public void walkBits(Bit startBit, Consumer<Bit> consumer, Predicate<Bit> ignoreBit){
            walkBits(startBit, consumer, ignoreBit, new HashSet<>());
        }

        public void walkBits(Bit startBit, Consumer<Bit> consumer, Predicate<Bit> ignoreBit, Set<Bit> alreadyVisitedBits){
            Stack<Bit> bitsToVisit = new Stack<>();
            if (ignoreBit.test(startBit)){
                return;
            }
            bitsToVisit.push(startBit);
            while (!bitsToVisit.isEmpty()){
                Bit cur = bitsToVisit.pop();
                if (alreadyVisitedBits.contains(cur)){
                    continue;
                }
                consumer.accept(cur);
                if (!ignoreBit.test(cur)){
                    bitsToVisit.addAll(cur.dataDeps);
                    bitsToVisit.addAll(cur.controlDeps);
                }
                alreadyVisitedBits.add(cur);
            }
        }
    }

    /**
     * Parse a tuple of lattice elements
     */
    static Pair<List<Object>, Integer> parseTuple(int start, String str, IdToElement idToElement, Lattice<?>... latticesForParsing){
        while (str.charAt(start) == ' '){
            start++;
        }
        if (str.charAt(start) != '('){
            throw new ParsingError(str, start, "Expected '('");
        }
        int i = start + 1;
        List<Object> elements = new ArrayList<>();
        Lattice<?> curLattice = latticesForParsing[0];
        for (; i < str.length() - 1 && elements.size() < latticesForParsing.length; i++) {
            while (str.charAt(i) == ' '){
                i++;
            }
            Pair<?, Integer> ret = curLattice.parse(i, str, idToElement);
            i = ret.second;
            elements.add(ret.first);
            curLattice = latticesForParsing[elements.size()];
            while (str.charAt(i) == ' '){
                i++;
            }
            switch (str.charAt(i)){
                case ',':
                    continue;
                case ')':
                    return new Pair<>(elements, i + 1);
                default:
                    throw new ParsingError(str, i, "Expected ')'");
            }
        }
        if (str.charAt(i) != ')'){
            throw new ParsingError(str, i, "Expected ')'");
        }
        return new Pair<>(elements, i + 1);
    }

    public static class Bit implements LatticeElement<Bit, BitLattice> {

        private static long NUMBER_OF_BITS = 0;

        static final Bit ONE = new Bit(B.ONE);
        static final Bit ZERO = new Bit(B.ZERO);
        final B val;
        final DependencySet dataDeps;
        final DependencySet controlDeps;
        final DependencySet deps;
        /**
         * Like the identity in the thesis
         */
        final long bitNo;
        private int valueIndex = 0;
        private Value value = null;

        public Bit(B val, DependencySet dataDeps, DependencySet controlDeps) {
            this.val = val;
            this.dataDeps = dataDeps;
            this.controlDeps = controlDeps;
            this.bitNo = NUMBER_OF_BITS++;
            this.deps = ds.sup(dataDeps, controlDeps);
            assert checkInvariant();
        }

        Bit(B val){
            this(val, ds == null ? new DependencySet(set()) : ds.bot(),
                    ds == null ? new DependencySet(set()) : ds.bot());
        }

        @Override
        public String toString() {
            /*if (value == null || value.description.isEmpty()) {
                if (!hasDependencies()) {
                    return bs.toString(val);
                }
                return String.format("(%s, %s, %s)", bs.toString(val), ds.toString(dataDeps), ds.toString(controlDeps));
            }*/
            if (valueIndex == 0){
                return val.toString();
            }
            return String.format("%s[%d]%s", value == null ? "" : (value.node() == null ? value.description() : value.node().getTextualId()), valueIndex, val);
        }

        @Override
        public BitLattice lattice() {
            return BitLattice.get();
        }

        public boolean hasDependencies(){
            return dataDeps.size() + controlDeps.size() > 0;
        }

        /**
         * Check the (const → no data deps) invariant
         * @return
         */
        public boolean checkInvariant(){
            return !val.isConstant() || dataDeps.isEmpty();
        }

        public boolean isConstant(){
            return val.isConstant();
        }

        /**
         * Compares the val and the dependencies
         */
        public boolean valueEquals(Bit other){
            return val.equals(other.val) && dataDeps.equals(other.dataDeps) && controlDeps.equals(other.controlDeps);
        }

        public String repr() {
            if (!hasDependencies()) {
                return bs.toString(val);
            }
            String name = "";
            if (value != null && !value.description.isEmpty()){
                name = String.format("%s[%d]|", value.node() == null ? value.description() : value.node().getTextualId(), valueIndex);
            }
            return String.format("(%s%s, %s, %s)", name, bs.toString(val), ds.toString(dataDeps), ds.toString(controlDeps));
        }

        public int valueIndex(){
            return valueIndex;
        }

        public Bit valueIndex(int index){
            if (valueIndex == 0) {
                valueIndex = index;
            }
            return this;
        }

        public Value value(){
            return value;
        }

        public Bit value(Value value){
            if (this.value == null) {
                this.value = value;
            }
            return this;
        }

        public boolean isUnknown(){
            return val == B.U;
        }
    }

    public static class ValueLattice implements Lattice<Value> {

        private static final ValueLattice lattice = new ValueLattice();

        private static final Value BOT = ValueLattice.get().parse("0bxx");

        @Override
        public Value sup(Value a, Value b) {
            return mapBitsToValue(a, b, bl::sup);
        }

        @Override
        public Value inf(Value a, Value b) {
            return mapBitsToValue(a, b, bl::inf);
        }

        @Override
        public Value bot() {
            return BOT;
        }

        /**
         * 0b[Bits] or the integer number
         */
        @Override
        public Pair<Value, Integer> parse(int start, String str, IdToElement idToElement) {
            if (str.length() > start + 1 && str.charAt(start) == '0' && str.charAt(start + 1) == 'b') {
                int i = start + 2;
                List<Bit> bits = new ArrayList<>();
                while (i < str.length()) {
                    while (str.charAt(i) == ' ') {
                        i++;
                    }
                    Pair<Bit, Integer> ret = bl.parse(i, str, idToElement);
                    i = ret.second;
                    bits.add(0, ret.first);
                    while (i < str.length() && str.charAt(i) == ' ') {
                        i++;
                    }
                }
                return new Pair<>(new Value(bits), i);
            } else {
                int end = start;
                char startChar = str.charAt(start);
                if (startChar != '+' && startChar != '-' && !Character.isDigit(startChar)) {
                    throw new ParsingError(str, start, "Expected number or sign");
                }
                end++;
                while (end < str.length() && Character.isDigit(str.charAt(end))) {
                    end++;
                }
                return new Pair<>(parse("0b" + toBinaryString(Integer.parseInt(str.substring(start, end)))), end);
            }
        }

        public Value parse(int val){
            return parse(Integer.toString(val));
        }

        public void assertSizeMatches(Value a, Value b) {
            assert a.size() == b.size() : "can only work with values of the same widths";
        }

        public static ValueLattice get() {
            return lattice;
        }

        /**
         * Ensures the equal length of both values before they are passed to the consumer
         */
        public void apply(Value a, Value b, BiConsumer<Value, Value> consumer) {
            int size = Math.max(a.size(), b.size());
            consumer.accept(a.extend(size), b.extend(size));
        }

        /**
         * Ensures the equal length of both values before they are passed to the transformer
         */
        public <R> R map(Value a, Value b, BiFunction<Value, Value, R> transformer) {
            int size = Math.max(a.size(), b.size());
            return transformer.apply(a.extend(size), b.extend(size));
        }

        public <R> List<R> mapBits(Value a, Value b, BiFunction<Bit, Bit, R> transformer) {
            return map(a, b, (aVal, bVal) -> {
                Iterator<Bit> it = bVal.iterator();
                return aVal.stream().map(aBit -> transformer.apply(aBit, it.next())).collect(Collectors.toList());
            });
        }

        public Value mapBitsToValue(Value a, Value b, BiFunction<Bit, Bit, Bit> transformer) {
            return new Value(mapBits(a, b, transformer));
        }

        public String toString(Value elem) {
            return elem.toString();
        }
    }

    public static class Value implements LatticeElement<Value, ValueLattice>, Iterable<Bit> {

        final List<Bit> bits;

        private String description = "";
        private Parser.MJNode node = null;

        public Value(List<Bit> bits) {
            assert bits.size() > 1;
            this.bits = bits;
            for (int i = 0; i < bits.size(); i++) {
                Bit bit = bits.get(i);
                bit.valueIndex(i + 1);
                bit.value(this);
            }
        }

        public Value(Bit... bits) {
            this(Arrays.asList(bits));
        }

        /**
         * Extend with low sign bit
         */
        private static List<Bit> extend(List<Bit> bits, int resultBitWidth){
            List<Bit> newBits = new ArrayList<>(bits);
            Bit signBit = newBits.get(newBits.size() - 1);
            for (int i = newBits.size(); i < resultBitWidth; i++){
                newBits.add(signBit);
            }
            return newBits;
        }

        public Value extend(int bitWidth){
            if (size() == bitWidth){
                return this;
            }
            return new Value(extend(bits, bitWidth));
        }


        @Override
        public boolean equals(Object obj) {
            return obj instanceof Value && ((Value) obj).bits.equals(bits);
        }

        @Override
        public int hashCode() {
            return bits.hashCode();
        }

        /**
         * Compares the val and the dependencies of bits
         */
        public boolean valueEquals(Value other){
            return ValueLattice.get().mapBits(this, other, Bit::valueEquals).stream().allMatch(Boolean::booleanValue);
        }

        @Override
        public String toString() {
            List<Bit> reversedBits = new ArrayList<>(bits);
            Collections.reverse(reversedBits);
            return reversedBits.stream().map(b -> b.val.toString()).collect(Collectors.joining(""));
        }

        public String repr() {
            List<Bit> reversedBits = new ArrayList<>(bits);
            Collections.reverse(reversedBits);
            String ret = reversedBits.stream().map(Bit::repr).collect(Collectors.joining(""));
            if (!description.equals("")) {
                return String.format("(%s|%s)", description, ret);
            }
            return ret;
        }

        public String toString(Function<Bit, String> bitToId) {
            return ValueLattice.get().toString(this);
        }

        @Override
        public ValueLattice lattice() {
            return ValueLattice.get();
        }

        public int size(){
            return bits.size();
        }

        /**
         * Returns the sign bit if the index is too big
         * @param index index that starts at 1
         * @return
         */
        public Bit get(int index){
            assert index > 0;
            return bits.get(Math.min(index, size()) - 1);
        }

        public boolean hasDependencies(){
            return bits.stream().anyMatch(Bit::hasDependencies);
        }

        public boolean isConstant(){
            return bits.stream().allMatch(Bit::isConstant);
        }

        public int asInt(){
            assert isConstant();
            int result = 0;
            boolean neg = signBit().val == ONE;
            int signBitVal = signBit().val.value.get();
            for (int i = bits.size() - 1; i >= 0; i--){
                result = result * 2;
                int bitVal = bits.get(i).val.value.get();
                if (signBitVal != bitVal){
                    result += 1;
                }
            }
            if (neg){
                return -result - 1;
            }
            return result;
        }


        @Override
        public Iterator<Bit> iterator() {
            return bits.iterator();
        }

        public Stream<Bit> stream(){
            return bits.stream();
        }

        public static Collector<Bit, ?, Value> collector(){
            return Collectors.collectingAndThen(Collectors.toList(), Value::new);
        }

        public String description(){
            return description;
        }

        public Value description(String description){
            if (this.description.isEmpty()) {
                this.description = description;
            }
            return this;
        }

        public Bit signBit(){
            return bits.get(bits.size() - 1);
        }

        public Stream<Bit> getRange(ValueRange range) {
            return Util.stream(range).mapToObj(bits::get);
        }

        public Value node(Parser.MJNode node) {
            if (this.node == null) {
                this.node = node;
            }
            return this;
        }

        public Parser.MJNode node(){
            return node;
        }

        public String toLiteralString(){
            if (isConstant()){
                return Integer.toString(asInt());
            }
            List<Bit> reversedBits = new ArrayList<>(bits);
            Collections.reverse(reversedBits);
            return "0b" + reversedBits.stream().map(b -> b.val.toString()).collect(Collectors.joining(""));
        }

        public boolean isNegative(){
            return signBit().val == ONE;
        }

        public boolean isNonNegative(){
            return signBit().val == ZERO;
        }
    }

}
