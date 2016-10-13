package swp.util;

public interface CheckedConsumer<T> {
	void consume(T t) throws Throwable;
}