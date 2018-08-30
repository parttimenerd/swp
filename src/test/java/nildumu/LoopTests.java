package nildumu;

import org.junit.jupiter.api.*;

import java.time.Duration;

import static java.time.Duration.ofMillis;
import static nildumu.Processor.process;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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

    /**
     * <code>
       h input int h = 0b0u;
       int x = 0;
       while (h == 0){
            x = x + 1;
       }
       l output int o = x;
     * </code>
     */
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
        assertTimeoutPreemptively(ofMillis(1000), () -> {
            parse("h input int h = 0b0u;\n" +
                "while (h == h){\n" +
                "\th + 1;\n" +
                "}\n");
        });
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
    public void testBasicLoop4_1(){
        assertTimeoutPreemptively(ofMillis(10000), () -> {
            parse("h input int h = 0b0u;\n" +
                    "while (h == h){\n" +
                    "\th = h + 1;\n" +
                    "}\n");
        });
    }

    /**
     * <code>
     *     bit_width 2;
     *     h input int h = 0b0u;
     *     l input int l = 0bu;
     *     while (l){
     *         h = [2](h[2] | h[1]);
     *     }
     *     l output int o = h;
     * </code>
     */
    @Test
    public void testBasicLoop4_condensed(){
        assertTimeoutPreemptively(ofMillis(1000000), () ->
                parse("bit_width 2;\n" +
                "h input int h = 0b0u;\n" +
                "l input int l = 0bu;\n" +
                "while (l){\n" +
                "  h = [2](h[2] | h[1]);\n" +
                "}\n" +
                "l output int o = h;"));
    }

    /**
     <code>
     bit_width 2;
     h input int h = 0buu;
     l input int l = 0bu;
     while (l){
        h = [2](h[2] | h[1]);
     }
     l output int o = h;
     </code>
     */
    @Test
    public void testBasicLoop4_condensed2(){
        assertTimeoutPreemptively(ofMillis(1000000), () ->
                parse("bit_width 2;\n" +
                        "h input int h = 0buu;\n" +
                        "l input int l = 0bu;\n" +
                        "while (l){\n" +
                        "  h = [2](h[2] | h[1]);\n" +
                        "}\n" +
                        "l output int o = h;"));
    }

    ContextMatcher parse(String program){
        return new ContextMatcher(process(program, Context.Mode.LOOP));
    }
}
