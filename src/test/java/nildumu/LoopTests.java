package nildumu;

import org.junit.jupiter.api.Test;

import static nildumu.Processor.process;

public class LoopTests {

    /**
     h input int h = 0b0u;
     l output int o = h;
     */
    @Test
    public void testBasic(){
        parse("h input int h = 0b0u;\n" +
                "l output int o = h;").leakage(l -> l.hasLeakage("l", 1));
    }

    @Test
    public void testBasicLoop(){
        parse("h input int h = 0b0u;\n" +
                "int x = 0;\n" +
                "while (h == 0){\n" +
                "\tx = x + 1;\n" +
                "}\n" +
                "l output int o = x;").leakage(l -> l.hasLeakage("l", 1));
    }

    @Test
    public void testBasicLoop2(){
        parse("h input int h = 0b0u; int htmp = h;\n" +
                "int x = 0;\n" +
                "while (htmp){\n" +
                "\thtmp = htmp;\n" +
                "\tx = htmp;\n" +
                "}");
    }

    @Test
    public void testBasicLoop3(){
        parse("h input int h = 0bu;\n" +
                "while (h){\n" +
                "\th = h;\n" +
                "}\n" +
                "l output int o = h").leakage(l -> l.hasLeakage("l", 1));
    }

    /**
     * This one didn't terminate
     * <code>
     *     h input int h = 0b0u;
     *     while (h == h){
     * 	        h + 1;
     *     }
     * </code>
     */
    @Test
    public void testBasicLoop4(){
        parse("h input int h = 0b0u;\n" +
                "while (h == h){\n" +
                "\th + 1;\n" +
                "}\n");
    }

    ContextMatcher parse(String program){
        return new ContextMatcher(process(program, Context.Mode.LOOP));
    }
}
