package nildumu;

import org.junit.jupiter.api.Test;

import static nildumu.FunctionTests.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests to check especially the SSA generation.
 *
 * Some are contributed by Simon Bischof.
 */
public class SSATests {

    /**
     <code>
     int r = 0;
     if (true) {
        r = 3;
     }
     if (true) {
        r = 1;
     }
     </code>
     should result in
     <code>
     int r = 0;
     if (1) {
        int r1 = 3;
     } else {

     }
     int r2 = ɸ[1](r1, r);
     if (1) {
        int r3 = 1;
     } else {

     }
     int r4 = ɸ[1](r3, r2);
     </code>
     with the focus on the last line
     */
    @Test
    public void testTwoIfsInARow(){
        assertEquals("int r4 = ɸ[1](r3, r2);", toSSA("int r = 0;\n" +
                "     if (true) {\n" +
                "        r = 3;\n" +
                "     }\n" +
                "     if (true) {\n" +
                "        r = 1;\n" +
                "     }").globalBlock.getLastStatementOrNull().toPrettyString());
    }

    /**
     <code>
     int r = 0;
     while (true) {
        r = 3;
     }
     while (true) {
        r = 1;
     }
     </code>
     should result in
     <code>
     int r = 0;
     while (1) {
        int r11 = ɸ[1](r1, r);
        int r1 = 3;
     }
     int r2 = ɸ[1](r1, r);
     while (1) {
        int r31 = ɸ[1](r3, r2);
        int r3 = 1;
     }
     int r4 = ɸ[1](r3, r2);
     </code>
     with the focus on the last line
     */
    @Test
    public void testTwoWhilesInARow(){
        assertEquals("int r4 = ɸ[1](r3, r2);", toSSA("int r = 0;\n" +
                "     while (true) {\n" +
                "        r = 3;\n" +
                "     }\n" +
                "     while (true) {\n" +
                "        r = 1;\n" +
                "     }").globalBlock.getLastStatementOrNull().toPrettyString());
    }

    @Test
    public Parser.ProgramNode toSSA(String program){
        System.out.println(Parser.process(program).toPrettyString());
        return Parser.process(program);
    }
}
