package swp.util;

import java.io.Serializable;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Simple implementations of tuples
 */
public class Tuple {

	public static class Tuple3<T, R, S>  implements Serializable {
		public final T v1;
		public final R v2;
		public final S v3;

		public Tuple3(T v1, R v2, S v3) {
			this.v1 = v1;
			this.v2 = v2;
			this.v3 = v3;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Tuple3<?, ?, ?> tuple3 = (Tuple3<?, ?, ?>) o;
			return Objects.equals(v1, tuple3.v1) &&
					Objects.equals(v2, tuple3.v2) &&
					Objects.equals(v3, tuple3.v3);
		}

		@Override
		public int hashCode() {
			return Objects.hash(v1, v2, v3);
		}

		@Override
		public String toString() {
			return String.format("(%s,%s,%s)", v1, v2, v3);
		}
	}

	public static <T, R, S> Tuple3<T, R, S> t(T v1, R v2, S v3){
		return new Tuple3<>(v1, v2, v3);
	}
}
