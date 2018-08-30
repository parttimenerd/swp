package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.lang.reflect.Method;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static nildumu.Processor.process;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class FunctionTests {

    @ParameterizedTest
    @ValueSource(strings = { "int bla(){}", "int bla1(int blub){}", "int bla1r(int blub){ return blub }" })
    public void testFunctionDefinition(String program){
        parse(program);
    }

    @ParameterizedTest
    @ValueSource(strings = {"int bla(){} bla()", "int bla(int i){} bla(1)", "int bla(int i, int j){} bla(1,2)"})
    public void testBasicFunctionCall(String program){
        parse(program);
    }

    @ParameterizedTest
    @CsvSource({
            "'int bla(){return 1} int x = bla()', '1'",
            "'int bla(int a){return a} int x = bla(1)', '1'",
            "'int bla(int a){return a | 1} int x = bla(1)', '1'",
            "'int bla(int a){return a + 1} int x = bla(1)', '2'"
    })
    public void testBasicFunctionCalls(String program, String expectedValue){
        parse(program).hasValue("x", expectedValue);
    }

    @ParameterizedTest
    @MethodSource("nildumu.MethodInvocationHandler#getExamplePropLines")
    public void testTrivialRecursionTerminates(String handler){
        assertTimeoutPreemptively(ofSeconds(100), () -> parse("int bla(){ return bla() }", MethodInvocationHandler.parse(handler)));
    }

    ContextMatcher parse(String program){
        return parse(program, MethodInvocationHandler.createDefault());
    }

    ContextMatcher parse(String program, MethodInvocationHandler handler){
        return new ContextMatcher(process(program, Context.Mode.LOOP, handler));
    }
}
