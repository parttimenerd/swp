package nildumu;

import org.junit.jupiter.api.Test;

public class MethodHandlerTest {

    @Test
    public void testParseExampleStrings(){
        MethodInvocationHandler.getExamplePropLines().forEach(MethodInvocationHandler::parse);
    }
}
