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
        assertTimeoutPreemptively(ofSeconds(1), () -> parse("     bit_width 2;\n" +
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

    /**
     <code>
     int f(int x) {
        return g(x);
     }
     int g(int x) {
        return h(x);
     }
     int h(int x) {
        return x;
     }
     high input int h = 0buu;
     low output int o = f(h);
     </code>
     Should lead to a leakage of at least 2 bits
     */
    @ParameterizedTest
    @MethodSource("nildumu.MethodInvocationHandler#getExamplePropLines")
    public void testNestedMethodCalls(String handler){
        parse("int f(int x) {\n" +
                "\t    return g(x);\n" +
                "    }\n" +
                "    int g(int x) {\n" +
                "\t    return h(x);\n" +
                "    }\n" +
                "    int h(int x) {\n" +
                "\t    return x;\n" +
                "    }\n" +
                "    high input int h = 0buu;\n" +
                "    low output int o = f(h);", handler).leaksAtLeast(2);
    }

    /**
     <code>
     bit_width 3;
     int f(int x, int y, int z, int w, int v, int l) {
         int r = 0;
         if (l == 0) {
            r = v;
         } else {
            r = f(0, x, y, z, w, l+0b111);
         }
         return r;
     }
     high input int h = 0buuu;
     low output int o = f(h, 0, 0, 0, 0, 4);
     </code>
     should leak 3 bits
     */
    @ParameterizedTest
    @MethodSource("nildumu.MethodInvocationHandler#getExamplePropLines")
    public void testConditionalRecursion(String handler){
        parse("bit_width 3;\n" +
                "    int f(int x, int y, int z, int w, int v, int l) {\n" +
                "\t    int r = 0;\n" +
                "\t    if (l == 0) {\n" +
                "\t\t    r = v;\n" +
                "\t    } else {\n" +
                "\t\t    r = f(0, x, y, z, w, l+0b111);\n" +
                "\t    }\n" +
                "\t    return r;\n" +
                "    }\n" +
                "    high input int h = 0buuu;\n" +
                "    low output int o = f(h, 0, 0, 0, 0, 4);", handler).leaksAtLeast(3);
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

    static ContextMatcher parse(String program, String handler){
        return new ContextMatcher(process(program, Context.Mode.LOOP, MethodInvocationHandler.parse(handler)));
    }

    static ContextMatcher parse(String program, MethodInvocationHandler handler){
        return new ContextMatcher(process(program, Context.Mode.LOOP, handler));
    }
}
