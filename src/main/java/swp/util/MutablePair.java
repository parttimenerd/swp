package swp.util;

import java.io.Serializable;

/**
 * Simple implementation of a mutable pair.
 */
public class MutablePair<T, V> extends Pair<T, V> implements Serializable {
	public T first;

	public V second;

	public MutablePair(T first, V second) {
		super(first, second);
	}

	@Override
	public int hashCode() {
		return first.hashCode() ^ second.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof MutablePair)){
			return false;
		}
		MutablePair pair = (MutablePair)obj;
		return pair.first == this.first && pair.second == this.second;
	}
}
