package nildumu;

import java.util.*;

/**
 * Known as <em>Definition</em> in the compiler lab.
 * Only {@code int} is allowed as a type so it's omitted.
 */
public class Variable {
    /**
     * The name of the variable
     */
    final String name;
    /**
     * Is this variable an public output variable, through which information gets potentially leaked?
     */
    final boolean isOutput;
    /**
     * Is this a secret or public input variable
     */
    final boolean isInput;

    public Variable(String name, boolean isInput, boolean isOutput) {
        this.name = name;
        this.isOutput = isOutput;
        this.isInput = isInput;
    }

    public Variable(String name){
        this(name, false, false);
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        if (isInput){
            parts.add("input");
        }
        if (isOutput){
            parts.add("output");
        }
        parts.add(name);
        return String.join(" ", parts);
    }
}
