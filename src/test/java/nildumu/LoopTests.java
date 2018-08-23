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

    ContextMatcher parse(String program){
        return new ContextMatcher(process(program, Context.Mode.LOOP));
    }
}
