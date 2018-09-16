package nildumu;

import java.time.temporal.ValueRange;
import java.util.*;
import java.util.stream.*;

import swp.util.Pair;

import static nildumu.Lattices.Bit;

public class Util {

    public static <T> List<Pair<T, T>> permutatePair(T x, T y){
        return Arrays.asList(new Pair<>(x, y), new Pair<>(y, x));
    }

    @SafeVarargs
    public static <T> Set<T> set(T... ts){
        return new HashSet<>(Arrays.asList(ts));
    }

    public static <S, T> Pair<S, T> p(S s, T t){
        return new Pair<>(s,t);
    }

    static Stream<Lattices.Bit> stream(Bit x, Bit y) {
        return Stream.of(x, y);
    }

    static Stream<Bit> stream(List<Pair<Bit, Bit>> bits) {
        return bits.stream().flatMap(p -> Stream.of(p.first, p.second));
    }

    static IntStream stream(ValueRange range) {
        return IntStream.range((int) range.getMinimum(), (int) range.getMaximum());
    }

    /**
     * Allows to modify values in lambdas
     */
    public static class Box<T> {

        public T val;

        public Box(T val) {
            this.val = val;
        }

        @Override
        public String toString() {
            return val.toString();
        }
    }

    public static String toBinaryString(int num){
        List<Boolean> vals = new ArrayList<>();
        int numAbs = num;
        if (num < 0){
            numAbs = Math.abs(num) - 1;
        }
        boolean one = num >= 0;
        while (numAbs > 0){
            if (numAbs % 2 == 0){
                vals.add(!one);
            } else {
                vals.add(one);
            }
            numAbs = numAbs / 2;
        }
        if (vals.isEmpty()){
            vals.add(0, !one);
        }
        vals.add(!one);
        Collections.reverse(vals);
        return vals.stream().map(b -> b ? "1" : "0").collect(Collectors.joining(""));
    }

    public static String iter(String str, int number){
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number; i++) {
            builder.append(str);
        }
        return builder.toString();
    }

    public static double log2(double val){
        return Math.log(val) / Math.log(2);
    }
}
