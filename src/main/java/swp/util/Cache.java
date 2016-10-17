package swp.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple lru cache implementation
 */
public class Cache<K, V> {

	private int clock = 0;
	private Map<K, V> map = new HashMap<>();
	private Map<K, Integer> accessTimes = new HashMap<>();

	private final int maximumSize;

	public Cache(int maximumSize) {
		this.maximumSize = maximumSize;
	}

	private void access(K key){
		accessTimes.put(key, clock++);
	}

	private void ensureSize(){
		while (map.size() > maximumSize){
			K minKey = null;
			int minTime = Integer.MAX_VALUE;
			for (K key : accessTimes.keySet()) {
				int keyTime = accessTimes.get(key);
				if (keyTime < minTime){
					minKey = key;
					minTime = keyTime;
				}
			}
			accessTimes.remove(minKey);
			map.remove(minKey);
		}
	}

	public boolean isFull(){
		return map.size() == maximumSize;
	}

	public V getIfPresent(K key){
		if (map.containsKey(key)){
			access(key);
			return map.get(key);
		}
		return null;
	}

	public void put(K key, V value){
		access(key);
		map.put(key, value);
		ensureSize();
	}
}
