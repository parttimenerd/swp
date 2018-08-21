package swp.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Simple implementation of an immutable pair
 */
public class Pair<T, V> implements Serializable {
	public final T first;

	public final V second;

	public Pair(T first, V second) {
		this.first = first;
		this.second = second;
	}

	@Override
	public int hashCode() {
		return first.hashCode() ^ second.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Pair)){
			return false;
		}
		Pair pair = (Pair)obj;
		return pair.first == this.first && pair.second == this.second;
	}

	public Stream<T> firstStream(){
		return Stream.of(first);
	}
}
