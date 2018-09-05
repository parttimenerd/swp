package nildumu;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static nildumu.Lattices.bs;
import static nildumu.Lattices.vl;
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
        builder.add(() -> Assertions.assertEquals(value, actual.asInt(),
                String.format("Variable %s should have integer val %d", variable, value)));
        return this;
    }

    public ContextMatcher val(String variable, String value){
        Lattices.Value expected = vl.parse(value);
        Lattices.Value actual = getValue(variable);
        assertEquals(expected.toLiteralString(), actual.toLiteralString(),
                String.format("Variable %s should have val %s, has val %s", variable, expected.repr(), actual.repr()));
        return this;
    }

    private <T> void assertEquals(T expected, T actual, String message){
        builder.add(() -> assertEquals(expected, actual, message));
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
        assertEquals(expected, context.getInputSecLevel(getValue(variable)), String.format("Variable %s should be an input variable with level %s", variable, expected));
        return this;
    }

    public ContextMatcher hasOutput(String variable){
        builder.add(() -> assertTrue(context.output.contains(getValue(variable)),
                String.format("The val of %s is an output val", variable)));
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
            builder.add(() -> Assertions.assertEquals(leakage, graph.leakage(attackerSec), () -> {

               /* try {
                    calcLeakageShowImage(context, attackerSec);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }*/

                return String.format("The calculated leakage for an attacker of level %s should be %d, leaking %s", attackerSec, leakage, graph.minCutBits(attackerSec).stream().map(Lattices.Bit::toString).collect(Collectors.joining(", ")));
            }));
            return this;
        }

        public LeakageMatcher leaks(String attackerSec, int leakage){
            return leaks(context.sl.parse(attackerSec), leakage);
        }

        public LeakageMatcher leaksAtLeast(Lattices.Sec sec, int leakage) {
            builder.add(() -> assertTrue(graph.leakage(sec) >= leakage, String.format("The calculated leakage for an attacker of level %s should be at least %d, leaking %d", sec, leakage, graph.leakage(sec))));
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
        int i = Integer.parseInt(varAndIndex.split("\\[")[1].split("\\]")[0]);
        builder.add(() -> Assertions.assertEquals(bs.parse(val), context.getVariableValue(var).get(i).val(), String.format("%s should have the bit val %s", varAndIndex, val)));
        return this;
    }

    public class ValueMatcher {
        private final Lattices.Value value;

        public ValueMatcher(Lattices.Value value) {
            this.value = value;
        }

        public ValueMatcher bit(int i, Lattices.B val){
            builder.add(() -> Assertions.assertEquals(val, value.get(i).val(), String.format("The %dth bit of %s should have the bit val %s", i, value, val)));
            return this;
        }
    }

    public void run(){
        builder.run();
    }
}
