package nildumu;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import swp.util.Pair;

import static nildumu.Lattices.*;
import static nildumu.DefaultMap.ForbiddenAction.*;

/**
 * Contains the bits that are marked as input or output, that have an unknown value
 */
public class IOValues {

    public static class MultipleLevelsPerValue extends NildumuError {
        MultipleLevelsPerValue(Value value){
            super(String.format("Multiple security levels per value are not supported, attempted it for value %s", value));
        }
    }

    private final Map<Sec<?>, Set<Value>> valuesPerSec;
    private final Map<Value, Sec<?>> secPerValue;
    private final Set<Bit> bits;

    IOValues() {
        this.valuesPerSec = new DefaultMap<>(new LinkedHashMap<>(), new DefaultMap.Extension<Sec<?>, Set<Value>>() {
            @Override
            public Set<Value> defaultValue(Map<Sec<?>, Set<Value>> map, Sec<?> key) {
                return new LinkedHashSet<>();
            }
        }, FORBID_DELETIONS, FORBID_VALUE_UPDATES);
        this.secPerValue = new DefaultMap<>(new HashMap<>(), FORBID_DELETIONS, FORBID_VALUE_UPDATES);
        this.bits = new LinkedHashSet<>();
    }


    public void add(Sec<?> sec, Value value){
        if (contains(value)){
            throw new MultipleLevelsPerValue(value);
        }
        valuesPerSec.get(sec).add(value);
        value.forEach(this::add);
        secPerValue.put(value, sec);
    }

    private void add(Bit bit){
        if (bit.val == B.U){
            bits.add(bit);
        }
    }

    public List<Pair<Sec, Value>> getValues(){
        return valuesPerSec.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(v -> new Pair<>((Sec)e.getKey(), v)))
                .collect(Collectors.toList());
    }

    public boolean contains(Bit bit){
        return bits.contains(bit);
    }

    public boolean contains(Value value){
        return valuesPerSec.values().stream().anyMatch(vs -> vs.contains(value));
    }

    public List<Pair<Sec, Bit>> getBits(){
        return bits.stream().map(b -> new Pair<>((Sec)getSec(b), b)).collect(Collectors.toList());
    }

    public List<Bit> getBits(Sec sec){
        return bits.stream().filter(b -> getSec(b.value()) == sec).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return valuesPerSec.entrySet().stream().map(e -> String.format(" level %s: %s",e.getKey(), e.getValue().stream().map(Value::toString).collect(Collectors.joining(", ")))).collect(Collectors.joining("\n"));
    }

    public Sec<?> getSec(Value value){
        return secPerValue.get(value);
    }

    public Sec<?> getSec(Bit bit){
        return getSec(bit.value());
    }
}
