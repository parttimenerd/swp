package nildumu;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static nildumu.Lattices.*;

/**
 * State of the variables
 */
class State {

    private final DefaultMap<String, Value> map = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<String, Value>() {
        @Override
        public Value defaultValue(Map<String, Value> map, String key) {
            return vl.bot();
        }
    });

    public Value get(String variable){
        return map.get(variable);
    }

    public Value get(Variable variable){
        return get(variable.name);
    }

    public void set(Variable variable, Value value){
        set(variable.name, value);
    }

    public void set(String variable, Value value){
        this.map.put(variable, value);
    }

    @Override
    public String toString() {
        return map.entrySet().stream().map(e -> String.format("%s => %s",e.getKey(), e.getValue().repr())).collect(Collectors.joining("\n"));
    }

    public Set<String> variableNames(){
        return map.keySet();
    }
}