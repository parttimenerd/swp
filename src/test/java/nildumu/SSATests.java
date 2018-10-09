package nildumu;

import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertEquals("int r8 = ɸ[1](r5, r4);", toSSA("int r = 0;\n" +
                "     while (true) {\n" +
                "        r = 3;\n" +
                "     }\n" +
                "     while (true) {\n" +
                "        r = 1;\n" +
                "     }").globalBlock.getLastStatementOrNull().toPrettyString());
    }

    @Test
    public void testWhileSetsExpr(){
        List<Parser.StatementNode> stmts = ((Parser.WhileStatementNode)toSSA("h input int h = 0b0u;\n" +
                "int x = 0;\n" +
                "while (h == 0){\n" +
                "\tx = x | 0b11;\n" +
                "}\n" +
                "l output int o = x;").globalBlock.statementNodes.get(2)).body.statementNodes;
        assertEquals(((Parser.VariableAssignmentNode)stmts.get(1)).definition, ((Parser.PhiNode)((Parser.VariableAssignmentNode)stmts.get(0)).expression).joinedVariables.get(0).definition);
    }

    /**
     * <code>
     *     int _3_bla(int a) {
     *    int r = 1;
     *    while (a != 0){
     *       a = 0
     *    }
     *    return r;
     * }
     * h input int h = 0b00u;
     * l output int o = _3_bla(h);
     * </code>
     */
    @Test
    public void testWhileWithBasicAssignmentInLoop(){
        toSSA("int _3_bla(int a) {\n" +
                "   int r = 1;\n" +
                "   while (a != 0){\n" +
                "      a = 0\n" +
                "   }\n" +
                "   return r;\n" +
                "}\n" +
                "h input int h = 0b00u;\n" +
                "l output int o = _3_bla(h);");
    }

    @Test
    public Parser.ProgramNode toSSA(String program){
        System.out.println(Parser.process(program).toPrettyString());
        return Parser.process(program);
    }
}
