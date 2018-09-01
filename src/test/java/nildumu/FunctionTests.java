package nildumu;

import java.time.Duration;
import java.util.logging.Level;

import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static java.time.Duration.ofSeconds;
import static nildumu.Processor.process;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import org.junit.jupiter.api.Test;

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
        parse(program).val("x", expectedValue);
    }

    @ParameterizedTest
    @MethodSource("nildumu.MethodInvocationHandler#getExamplePropLines")
    public void testTrivialRecursionTerminates(String handler){
        assertTimeoutPreemptively(ofSeconds(100), () -> parse("int bla(){ return bla() }", MethodInvocationHandler.parse(handler)));
    }
    
    /**
     <code>
     bit_width 2;
h input int h = 0b0u;
int fib(int a){
	int r = 1;
	if (a > 1){
		r = fib(a - 1);
	}
	return r;
}
l output int o = fib(h);
     </code>
     */
    @ParameterizedTest
    @ValueSource(strings = {"handler=call_string;maxrec=1;bot=basic", "handler=call_string;maxrec=2;bot=basic"})
    @MethodSource("nildumu.MethodInvocationHandler#getExamplePropLines")
    public void testFibonacci(String handler){
        assertTimeoutPreemptively(ofSeconds(10000), () -> parse("bit_width 2;\n" +
"h input int h = 0b0u;\n" +
"int fib(int a){\n" +
"	int r = 1;\n" +
"	if (a > 1){\n" +
"		r = fib(a - 1);\n" +
"	}\n" +
"	return r;\n" +
"}\n" +
"l output int o = fib(h);", MethodInvocationHandler.parse(handler))).leaks(1);
    }

    /**
     <code>
     bit_width 2;
     h input int h = 0b0u;
     l input int l = 0b0u;
     int res = 0;
     int fib(int a){
         int r = 1;
         while (a > 0){
             if (a > 1){
                r = r + fib(a - 1);
             }
         }
         return r;
     }
     while (l) {
        res = res + fib(h);
     }
     l output int o = fib(h);
     </code>
     */
    @ParameterizedTest
    @ValueSource(strings = {"handler=basic", "handler=call_string;maxrec=1;bot=basic", "handler=call_string;maxrec=2;bot=basic"})
    @MethodSource("nildumu.MethodInvocationHandler#getExamplePropLines")
    public void testWeirdFibonacciTermination(String handler){
        Context.LOG.setLevel(Level.INFO);
        assertTimeoutPreemptively(ofSeconds(10000), () -> parse("     bit_width 2;\n" +
                "     h input int h = 0b0u;\n" +
                "     l input int l = 0b0u;\n" +
                "     int res = 0;\n" +
                "     int fib(int a){\n" +
                "         int r = 1;\n" +
                "         while (a > 0){\n" +
                "             if (a > 1){\n" +
                "                r = r + fib(a - 1);\n" +
                "             }\n" +
                "         }\n" +
                "         return r;\n" +
                "     }\n" +
                "     while (l) {\n" +
                "        res = res + fib(h);\n" +
                "     }\n" +
                "     l output int o = fib(h);", MethodInvocationHandler.parse(handler))).leaks(1);
    }

    public static void main(String[] args){
       parse("bit_width 2;\n" +
"h input int h = 0b0u;\n" +
"int fib(int a){\n" +
"	int r = 1;\n" +
"	if (a > 1){\n" +
"		r = fib(a - 1);\n" +
"	}\n" +
"	return r;\n" +
"}\n" +
"l output int o = fib(h);", MethodInvocationHandler.parse("handler=call_string;maxrec=2;bot=basic"));
    }

    static ContextMatcher parse(String program){
        return parse(program, MethodInvocationHandler.createDefault());
    }

    static ContextMatcher parse(String program, MethodInvocationHandler handler){
        return new ContextMatcher(process(program, Context.Mode.LOOP, handler));
    }
}
