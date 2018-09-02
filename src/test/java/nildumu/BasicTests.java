package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static nildumu.Processor.process;
import static nildumu.Lattices.*;

public class BasicTests {

    @Test
    public void testParseValue(){
        new ContextMatcher.ValueMatcher(vl.parse("1")).bit(1, B.ONE).bit(2, B.ZERO);
        new ContextMatcher.ValueMatcher(vl.parse("2")).bit(1, B.ZERO).bit(2, B.ONE).bit(3, B.ZERO);
        new ContextMatcher.ValueMatcher(vl.parse("-1")).bit(1, B.ONE).bit(2, B.ONE);

    }

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
        parse("int x = 1").val("x", 1);
        parse("int x = -10").val("x", -10);
    }

    @Test
    public void testInputAssigment(){
        parse("l input int l = 0b0u").hasInput("l").val("l", "0b0u");
    }

    @Test
    public void testChangingSecLattice(){
        parse("use_sec diamond; n input int l = 0b0u").val("l", "0b0u").hasInputSecLevel("l", DiamondSecLattice.MID2);
    }

    @Test
    public void testBasicOutputAssignment(){
        parse("h output int o = 0").hasOutput("o").val("o", 0).hasOutputSecLevel("o", BasicSecLattice.HIGH);
        parse("l input int l = 0b0u; h output int o = l;").val("o", "0b0u");
    }

    @Test
    public void testBasicProgramLeakage(){
        parse("h output int o = 0").leakage(l -> l.leaks("h", 0));
    }

    @Test
    public void testBasicProgramLeakage2(){
        parse("h input int h = 0b0u; l output int o = h;").leakage(l -> l.leaks("l", 1));
    }

    @Test
    public void testBasicIf(){
        parse("int x = 0; if (1) { x = 1 }").val("x1", 1);
    }

    @Test
    public void testBitwiseOps(){
        parse("int x = 0 | 1").val("x", "1");
        parse("int x = 0b00 | 0b11").val("x", "0b11");
        parse("l input int l = 0b0u; int x = l | 0b11").val("x", "0b11");
        parse("l input int l = 0b0u; int x = l & 0b11").val("x", "0b0u");
    }

    @Test
    public void testBitwiseOps2(){
        parse("h input int l = 0b0u;\n" +
                "int x = 2 & l;").val("x", "0b00");
    }

    @Test
    public void testPlusOperator(){
        parse("int x = 1 + 0").val("x", "1");
    }

    /**
     h input int l = 0b0u;
     int x = 0;
     if (l){
     x = 1;
     } else {
     x = 0;
     }
     l output int o = x;
     */
    @Test
    public void testIf(){
        parse("h input int l = 0b0u; \n" +
                "int x = 0;\n" +
                "if (l){\n" +
                "\tx = 1;\n" +
                "} else {\n" +
                "\tx = 0;\n" +
                "}\n" +
                "l output int o = x;").val("o", vm -> vm.bit(1, B.U)).leakage(l -> l.leaks("l", 1));
    }

    @Test
    public void testIfWithNewLines(){
        parse("h input int l = 0b0u; \n" +
                "int x = 0;\n" +
                "if (l){\n" +
                "\tx = 1;\n" +
                "} else {\n\n" +
                "\tx = 0;\n" +
                "}\n\n" +
                "l output int o = x;").val("o", vm -> vm.bit(1, B.U)).leakage(l -> l.leaks("l", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"high input int h = 0b0u;\n" +
            "    if (h[1]) {\n" +
            "    }",
            "high input int h = 0b0u;\n" +
                    "    if (h[1] == 1) {\n" +
                    "    }",
            "high input int h = 0b0u;\n" +
            "    if (h[1] == h[1]) {\n" +
            "    }"})
    public void testBitSelectInConditionDoesntThrowExceptions(String program){
        parse(program);
    }

    public ContextMatcher parse(String program){
        return new ContextMatcher(process(program));
    }

}
