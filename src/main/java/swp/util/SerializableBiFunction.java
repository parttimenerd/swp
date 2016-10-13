package swp.util;

import java.io.Serializable;
import java.util.function.BiFunction;

/**
 * A serializable version of the BiFunction class.
 */
@FunctionalInterface public interface SerializableBiFunction<I, J, O> extends BiFunction<I, J, O>, Serializable { }
