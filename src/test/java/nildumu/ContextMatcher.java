package nildumu;

import java.util.function.Consumer;
import java.util.stream.Collectors;

import static nildumu.Lattices.bs;
import static nildumu.Lattices.vl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextMatcher {

    private final Context context;

    public ContextMatcher(Context context) {
        this.context = context;
    }

    public ContextMatcher val(String variable, int value){
        Lattices.Value actual = getValue(variable);
        assertTrue(actual.isConstant(), String.format("Variable %s should have an integer val, has %s", variable, actual.repr()));
        assertEquals(value, actual.asInt(),
                String.format("Variable %s should have integer val %d", variable, value));
        return this;
    }

    public ContextMatcher val(String variable, String value){
        Lattices.Value expected = vl.parse(value);
        Lattices.Value actual = getValue(variable);
        assertEquals(expected.toLiteralString(), actual.toLiteralString(),
                String.format("Variable %s should have val %s, has val %s", variable, expected.repr(), actual.repr()));
        return this;
    }

    private Lattices.Value getValue(String variable){
        return context.getVariableValue(variable);
    }

    public ContextMatcher hasInput(String variable){
        assertTrue(context.isInputValue(getValue(variable)),
                String.format("The val of %s is an input val", variable));
        return this;
    }

    public ContextMatcher hasInputSecLevel(String variable, Lattices.Sec<?> expected){
        assertEquals(expected, context.getInputSecLevel(getValue(variable)), String.format("Variable %s should be an input variable with level %s", variable, expected));
        return this;
    }

    public ContextMatcher hasOutput(String variable){
        assertTrue(context.output.contains(getValue(variable)),
                String.format("The val of %s is an output val", variable));
        return this;
    }

    public ContextMatcher hasOutputSecLevel(String variable, Lattices.Sec<?> expected){
        assertEquals(expected, context.output.getSec(getValue(variable)), String.format("Variable %s should be an output variable with level %s", variable, expected));
        return this;
    }

    public ContextMatcher leakage(Consumer<LeakageMatcher> leakageTests){
        leakageTests.accept(new LeakageMatcher(context.getLeakageGraph()));
        return this;
    }

    public class LeakageMatcher {
        private final LeakageCalculation.AbstractLeakageGraph graph;

        public LeakageMatcher(LeakageCalculation.AbstractLeakageGraph graph) {
            this.graph = graph;
        }

        public LeakageMatcher leaks(Lattices.Sec<?> attackerSec, int leakage){
            assertEquals(leakage, graph.leakage(attackerSec), () -> {

               /* try {
                    calcLeakageShowImage(context, attackerSec);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }*/

                return String.format("The calculated leakage for an attacker of level %s should be %d, leaking %s", attackerSec, leakage, graph.minCutBits(attackerSec).stream().map(Lattices.Bit::toString).collect(Collectors.joining(", ")));
            });
            return this;
        }

        public LeakageMatcher leaks(String attackerSec, int leakage){
            return leaks(context.sl.parse(attackerSec), leakage);
        }

        public LeakageMatcher leaksAtLeast(Lattices.Sec sec, int leakage) {
            assertTrue(graph.leakage(sec) >= leakage, String.format("The calculated leakage for an attacker of level %s should be at least %d, leaking %s", sec, leakage, graph.minCutBits(sec).stream().map(Lattices.Bit::toString).collect(Collectors.joining(", "))));
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

    /**
     *
     * @param varAndIndex "var[1]"
     * @param val
     * @return
     */
    public ContextMatcher bit(String varAndIndex, String val){
        String var = varAndIndex.split("\\[")[0];
        int i = Integer.parseInt(varAndIndex.split("\\[")[0].split("\\]")[0]);
        return val(var, vm -> vm.bit(i, bs.parse(val)));
    }

    public static class ValueMatcher {
        private final Lattices.Value value;

        public ValueMatcher(Lattices.Value value) {
            this.value = value;
        }

        public ValueMatcher bit(int i, Lattices.B val){
            assertEquals(val, value.get(i).val, String.format("The %dth bit of %s should have the bit val %s", i, value, val));
            return this;
        }
    }
}
