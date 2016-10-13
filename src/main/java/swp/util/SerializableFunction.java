package swp.util;

import java.io.Serializable;
import java.util.function.Function;

/**
 * A serializable version of the Function class.
 */
@FunctionalInterface public interface SerializableFunction<I, O> extends Function<I, O>, Serializable { }
