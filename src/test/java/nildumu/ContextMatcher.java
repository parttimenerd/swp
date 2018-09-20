package nildumu;

import org.junit.jupiter.api.function.Executable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.*;

import static nildumu.Lattices.*;
import static org.junit.jupiter.api.Assertions.*;

public class ContextMatcher {

    public static class TestBuilder {
        List<Executable> testers = new ArrayList<>();


        public TestBuilder add(Executable tester){
            testers.add(tester);
            return this;
        }

        public void run(){
            assertAll(testers.toArray(new Executable[0]));
        }
    }

    private final Context context;
    private final TestBuilder builder = new TestBuilder();

    public ContextMatcher(Context context) {
        this.context = context;
    }

    public ContextMatcher val(String variable, int value){
        Lattices.Value actual = getValue(variable);
        builder.add(() -> assertTrue(actual.isConstant(), String.format("Variable %s should have an integer val, has %s", variable, actual.repr())));
        builder.add(() -> assertEquals(value, actual.asInt(),
                String.format("Variable %s should have integer val %d", variable, value)));
        return this;
    }

    public ContextMatcher val(String variable, String value){
        Lattices.Value expected = vl.parse(value);
        Lattices.Value actual = getValue(variable);
        builder.add(() -> assertEquals(expected.toLiteralString(), actual.toLiteralString(),
                String.format("Variable %s should have val %s, has val %s", variable, expected.repr(), actual.repr())));
        return this;
    }

    private Lattices.Value getValue(String variable){
        return context.getVariableValue(variable);
    }

    public ContextMatcher hasInput(String variable){
        builder.add(() -> assertTrue(context.isInputValue(getValue(variable)),
                String.format("The val of %s is an input val", variable)));
        return this;
    }

    public ContextMatcher hasInputSecLevel(String variable, Lattices.Sec<?> expected){
        builder.add(() -> assertEquals(expected, context.getInputSecLevel(getValue(variable)), String.format("Variable %s should be an input variable with level %s", variable, expected)));
        return this;
    }

    public ContextMatcher hasOutput(String variable){
        builder.add(() -> assertTrue(context.output.contains(getValue(variable)),
                String.format("The val of %s is an output val", variable)));
        return this;
    }

    public ContextMatcher hasOutputSecLevel(String variable, Lattices.Sec<?> expected){
        builder.add(() -> assertEquals(expected, context.output.getSec(getValue(variable)), String.format("Variable %s should be an output variable with level %s", variable, expected)));
        return this;
    }

    public ContextMatcher leakage(Consumer<LeakageMatcher> leakageTests){
        leakageTests.accept(new LeakageMatcher());
        return this;
    }

    public class LeakageMatcher {

        public LeakageMatcher leaks(Lattices.Sec<?> attackerSec, int leakage){
            builder.add(() -> {
                MinCut.ComputationResult comp = MinCut.compute(context, attackerSec);
                assertEquals(leakage, comp.maxFlow, () -> {
                    return String.format("The calculated leakage for an attacker of level %s should be %d, leaking %s", attackerSec, leakage, comp.minCut.stream().map(Lattices.Bit::toString).collect(Collectors.joining(", ")));
                });
            });
            return this;
        }

        public LeakageMatcher leaks(String attackerSec, int leakage){
            return leaks(context.sl.parse(attackerSec), leakage);
        }

        public LeakageMatcher leaksAtLeast(Lattices.Sec sec, int leakage) {
            builder.add(() -> {
                MinCut.ComputationResult comp = MinCut.compute(context, sec);
                assertTrue(comp.maxFlow >= leakage, String.format("The calculated leakage for an attacker of level %s should be at least %d, leaking %d", sec, leakage, comp.maxFlow));
            });
            return this;
        }
    }

    public ContextMatcher val(String var, Consumer<ValueMatcher> test){
        test.accept(new ValueMatcher(context.getVariableValue(var)));
        return this;
    }

    public ContextMatcher leaks(String attackerSec, int leakage){
        return leakage(l -> l.leaks(attackerSec, leakage));
    }

    public ContextMatcher leaks(int leakage){
        return leakage(l -> l.leaks(context.sl.bot(), leakage));
    }

    public ContextMatcher leaksAtLeast(int leakage){
        return leakage(l -> l.leaksAtLeast(context.sl.bot(), leakage));
    }

    public ContextMatcher bitWidth(int bitWidth){
        builder.add(() -> assertEquals(bitWidth, context.maxBitWidth, "Check of the used maximum bit width"));
        return this;
    }

    /**
     *
     * @param varAndIndex "var[1]"
     * @param val
     * @return
     */
    public ContextMatcher bit(String varAndIndex, String val){
        String var = varAndIndex.split("\\[")[0];
        int i = Integer.parseInt(varAndIndex.split("\\[")[1].split("\\]")[0]);
        builder.add(() -> assertEquals(bs.parse(val), context.getVariableValue(var).get(i).val(), String.format("%s should have the bit val %s", varAndIndex, val)));
        return this;
    }

    /**
     *
     * @param varIndexVals "var[1] = 1; a[3] = 1"
     */
    public ContextMatcher bits(String varIndexVals){
        if (!varIndexVals.contains("=")){
            return this;
        }
        Stream.of(varIndexVals.split(";")).forEach(str -> {
            String[] parts = str.split("=");
            bit(parts[0].trim(), parts[1].trim());
        });
        return this;
    }

    public class ValueMatcher {
        private final Lattices.Value value;

        public ValueMatcher(Lattices.Value value) {
            this.value = value;
        }

        public ValueMatcher bit(int i, Lattices.B val){
            builder.add(() -> assertEquals(val, value.get(i).val(), String.format("The %dth bit of %s should have the bit val %s", i, value, val)));
            return this;
        }
    }

    public void run(){
        builder.run();
    }
}
