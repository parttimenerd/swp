package nildumu;

import org.junit.jupiter.api.Test;

import static nildumu.Processor.process;

/**
 * SSA bug:
 *
 h input int h = 0b0u;
 int x = 1;
 if (h){
 int x = h;

 =>
 h input int h = 0b0u;
 int x = 1;
 if (h) {
 int x = h;
 } else {

 }
 }


 */

public class ExtendedTests {

    /**
     h input int h = 0b0u;
     int x = 0b01;
     if (h){
        x = h;
     }
     */
    @Test
    public void testBasicIfWithMods() {
        parse("h input int h = 0b0u;\n" +
                "int x = 1;\n" +
                "if (h){\n" +
                "    x = h;\n" +
                "}").val("x2", "0b01").run();
    }

    public ContextMatcher parse(String program){
        return new ContextMatcher(process(program, Context.Mode.EXTENDED));
    }
}
