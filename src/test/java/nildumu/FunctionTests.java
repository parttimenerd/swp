package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static java.time.Duration.ofMillis;
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
            "'int bla(int a){return a + 1} int x = bla(1)', '2'"
    })
    public void testBasicFunctionCalls(String program, String expectedValue){
        parse(program).hasValue("x", expectedValue);
    }

    @Test
    public void testTrivialRecursionTerminates(){
        assertTimeoutPreemptively(ofMillis(100), () -> parse("int bla(){ return bla() }"));
    }

    ContextMatcher parse(String program){
        return new ContextMatcher(process(program, Context.Mode.LOOP));
    }
}
