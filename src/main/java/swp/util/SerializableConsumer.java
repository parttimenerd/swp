package swp.util;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * A serializable version of the Consumer class.
 */
@FunctionalInterface public interface SerializableConsumer<T> extends Consumer<T>, Serializable { }
