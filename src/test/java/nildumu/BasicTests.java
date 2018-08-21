package nildumu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import nildumu.ui.JungPanel;

import static nildumu.PythonCaller.calcLeakageShowImage;
import static nildumu.ui.DemoContainer.GraphVisTester.show;
import static org.junit.jupiter.api.Assertions.*;
import static nildumu.Processor.process;
import static nildumu.Lattices.*;

public class BasicTests {

    @Test
    public void testParser(){
        process("h input int l = 0b0u; l output int o = l;");
    }

    @Test
    public void testParser2(){
        process("if (1) {}");
    }

    @Test
    public void testParser3(){
        process("if (1) {} if (1) {}");
    }


    @Test
    public void testSimpleAssignment(){
        parse("int x = 1").hasValue("x", 1);
        parse("int x = -10").hasValue("x", -10);
    }

    @Test
    public void testInputAssigment(){
        parse("l input int l = 0b0u").hasInput("l").hasValue("l", "0b0u");
    }

    @Test
    public void testChangingSecLattice(){
        parse("use_sec diamond; n input int l = 0b0u").hasValue("l", "0b0u").hasInputSecLevel("l", DiamondSecLattice.MID2);
    }

    @Test
    public void testBasicOutputAssignment(){
        parse("h output int o = 0").hasOutput("o").hasValue("o", 0).hasOutputSecLevel("o", BasicSecLattice.HIGH);
        parse("l input int l = 0b0u; h output int o = l;").hasValue("o", "0b0u");
    }

    @Test
    public void testBasicProgramLeakage(){
        parse("h output int o = 0").leakage(l -> l.hasLeakage("h", 0));
    }

    @Test
    public void testBasicProgramLeakage2(){
        parse("h input int h = 0b0u; l output int o = h;").leakage(l -> l.hasLeakage("l", 1));
    }

    @Test
    public void testBasicIf(){
        parse("int x; if (1) { x = 1 }").hasValue("x", 1);
    }

    public ContextMatcher parse(String program){
        return new ContextMatcher(process(program));
    }

    public static class ContextMatcher {

        private final Context context;

        public ContextMatcher(Context context) {
            this.context = context;
        }

        public ContextMatcher hasValue(String variable, int value){
            Value actual = getValue(variable);
            assertTrue(actual.isConstant(), String.format("Variable %s should have an integer value, has %s", variable, actual.repr()));
            assertEquals(value, actual.asInt(),
                    String.format("Variable %s should have integer value %d", variable, value));
            return this;
        }

        public ContextMatcher hasValue(String variable, String value){
            Value expected = vl.parse(value);
            Value actual = getValue(variable);
            assertTrue(expected.valueEquals(actual),
                    String.format("Variable %s should have value %s, has value %s", variable, expected.repr(), actual.repr()));
            return this;
        }

        private Value getValue(String variable){
            return context.getVariableValue(variable);
        }

        public ContextMatcher hasInput(String variable){
            assertTrue(context.isInputValue(getValue(variable)),
                    String.format("The value of %s is an input value", variable));
            return this;
        }

        public ContextMatcher hasInputSecLevel(String variable, Sec<?> expected){
            assertEquals(expected, context.getInputSecLevel(getValue(variable)), String.format("Variable %s should be an input variable with level %s", variable, expected));
            return this;
        }

        public ContextMatcher hasOutput(String variable){
            assertTrue(context.output.contains(getValue(variable)),
                    String.format("The value of %s is an output value", variable));
            return this;
        }

        public ContextMatcher hasOutputSecLevel(String variable, Sec<?> expected){
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

            public LeakageMatcher hasLeakage(Sec<?> attackerSec, int leakage){
                assertEquals(leakage, graph.leakage(attackerSec), () -> {

                    try {
                        calcLeakageShowImage(context, attackerSec);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }

                    return String.format("The calculated leakage for an attacker of level %s should be %d, leaking %s", attackerSec, leakage, graph.minCutBits(attackerSec).stream().map(Bit::toString).collect(Collectors.joining(", ")));
                });
                return this;
            }

            public LeakageMatcher hasLeakage(String attackerSec, int leakage){
                return hasLeakage(context.sl.parse(attackerSec), leakage);
            }
        }
    }
}
