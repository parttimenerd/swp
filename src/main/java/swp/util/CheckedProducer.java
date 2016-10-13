package swp.util;

public interface CheckedProducer<R> {
	R apply() throws Throwable;
}