package swp.util;

@FunctionalInterface
public interface TriConsumer<U, V, W> {
	public void accept(U u, V v, W w);
}
