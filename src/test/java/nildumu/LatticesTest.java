package nildumu;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import swp.util.Pair;

import static nildumu.Lattices.BasicSecLattice.HIGH;
import static nildumu.Lattices.BasicSecLattice.LOW;
import static nildumu.Util.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Tests for all the basic lattices. These lattices are the foundation of the whole analysis, they should
 * work as intended.
 */
public class LatticesTest {

    @Nested
    public class TestSecLattice {
        @Test
        public void testBasicSecurityLattice(){
            assertParsingCorrect(HIGH, p("h", HIGH));
        }

        @Test
        public void testSup(){
            assertEquals(HIGH,
                    Lattices.BasicSecLattice.get().sup(HIGH, LOW));
            assertEquals(LOW, Lattices.BasicSecLattice.get().sup(Stream.of(LOW, LOW)));
            assertEquals(LOW, Lattices.BasicSecLattice.get().sup(Stream.of()));
        }

        @Test
        public void testInf(){
            assertEquals(LOW,
                    Lattices.BasicSecLattice.get().inf(HIGH, LOW));
            assertEquals(HIGH, Lattices.BasicSecLattice.get().inf(Stream.of()));
        }

        @Test
        public void testIn(){
            assertTrue(Lattices.BasicSecLattice.get().in(HIGH, HIGH, LOW));
        }
    }

    @Nested
    public class TestSetLattice {
        @Test
        public void testParsing(){
            assertParsingCorrect(new Lattices.SetLattice<>(Lattices.BasicSecLattice.get(), c -> {
                        HashSet<Lattices.BasicSecLattice> h = new HashSet<>();
                        h.addAll(c);
                        return h;
                    }),
            p("{}", set()),
                    p("{ h }", set(HIGH)),
                    p("{h, l}", set(HIGH, LOW)));
            assertEquals(set(HIGH), new Lattices.SetLattice<>(Lattices.BasicSecLattice.get(), c -> {
                HashSet<Lattices.BasicSecLattice> h = new HashSet<>();
                h.addAll(c);
                return h;
            }).parse("{#bla}", x -> HIGH));
        }
    }

    @Nested
    public class ValueLatticeTest {
        @Test
        public void testParsing(){
            assertParsingCorrect(Lattices.ValueLattice.get(), Lattices.Value::valueEquals,
                    p("0b00", new Lattices.Value(Lattices.Bit.ZERO, Lattices.Bit.ZERO)));
        }
    }

    @SafeVarargs
    final <T> void assertParsingCorrect(Lattices.Lattice<T> lattice, BiPredicate<T, T> eq, Pair<String, T>... pairs){
        for (Pair<String, T> pair : pairs) {
            T actual = lattice.parse(pair.first);
            assertTrue(String.format("Expected '%s', got '%s'", lattice.toString(pair.second), lattice.toString(actual)),
                    eq.test(actual, pair.second));
        }
    }

    @SafeVarargs
    final <T> void assertParsingCorrect(Lattices.Lattice<T> lattice, Pair<String, T>... pairs){
        assertParsingCorrect(lattice, Object::equals, pairs);
    }
}