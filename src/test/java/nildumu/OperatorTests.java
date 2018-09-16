package nildumu;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static nildumu.FunctionTests.parse;

public class OperatorTests {
    @ParameterizedTest
    @CsvSource({
            "1, <<, 2, 4",
            "1, >>, 2, 0",
            "1,+, 1, 2",
            "2,*,4, 8",
            "3, %, 2, 1"
    })
    public void test(String arg1, String op, String arg2, String result){
        parse(String.format("bit_width 10; l input int a = %s; l input int b = %s; int x = a %s b", arg1, arg2, op)).val("x", result).run();
    }

    @ParameterizedTest
    @CsvSource({
            "0b0u, *, 3, 1",
            "0bu, *, 2, 1"
    })
    public void testLeakage(String arg1, String op, String arg2, int leakage){
        parse(String.format("bit_width 10; h input int a = %s; h input int b = %s; l output int x = a %s b", arg1, arg2, op)).leaks(leakage).run();
    }

    @Test
    public void testShiftToZero(){
        parse("h input int a = 0b0u; l output int x = a << 2").val("x", 0);
    }

    @Test
    public void testMultToZero(){
        parse("h input int a = 0b0u; l output int x = a * 2 * 2").val("x", 0);
    }
}
