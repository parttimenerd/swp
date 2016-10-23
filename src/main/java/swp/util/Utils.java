package swp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import swp.SWPException;
import swp.lexer.Lexer;

/**
 * Class with utility methods...
 */
public class Utils {

	/**
	 * The color used for hidden nodes in the DiffGraph
	 */
	public static final String HIDDEN_DIFF_COLOR = "#00000000";
	/**
	 * The HTML color used for hidden things in the DiffGraph
	 */
	public static final String HIDDEN_DIFF_HTML_COLOR = "#00000000";
	/**
	 * Possibly brighter version of the HTML color used for hidden things in the DiffGraph
	 */
	public static final String HIDDEN_DIFF_HTML_COLOR2 = "#00000000";
	/**
	 * The HTML color used for hidden things in the DiffGraph
	 */
	public static final String EXACT_DIFF_HTML_COLOR = "red";
	/**
	 * Brighter version of the HTML color used for just added things in the DiffGraph
	 */
	public static final String EXACT_DIFF_HTML_COLOR2 = "white";
	/**
	 * The HTML color used for used things in the DiffGraph
	 */
	public static final String USED_DIFF_HTML_COLOR = "blue";
	/**
	 * Brighter version of the HTML color used for used things in the DiffGraph
	 */
	public static final String USED_DIFF_HTML_COLOR2 = "white";
	/**
	 * The dpi of images produced by graphviz (and the DiffGraph)
	 */
	public static final int GRAPHVIZ_IMAGE_DPI = 300;

	public static class ColorPair {

		public final String color;
		public final String brighterColor;

		public ColorPair(String color, String brighterColor) {
			this.color = color;
			this.brighterColor = brighterColor;
		}

		@Override
		public String toString() {
			return color;
		}
	}

	/**
	 * HtML color pair based on the current status of a thing in DiffGraph
	 */
	public static ColorPair diffHTMLColorPair(boolean justAdded, boolean hidden, boolean used){
		if (used && !justAdded && !hidden){
			return diffUsedHTMLColorPair();
		}
		return diffHTMLColorPair(justAdded, hidden, new ColorPair("black", "white"));
	}

	/**
	 * HtML color pair based on the current status of a thing in DiffGraph
	 */
	public static ColorPair diffHTMLColorPair(boolean justAdded, boolean hidden){
		return diffHTMLColorPair(justAdded, hidden, new ColorPair("black", "white"));
	}

	/**
	 * HtML color pair based on the current status of a thing in DiffGraph
	 */
	public static ColorPair diffHTMLColorPair(boolean justAdded, boolean hidden, ColorPair defaultPair){
		if (hidden){
			return new ColorPair(HIDDEN_DIFF_HTML_COLOR, HIDDEN_DIFF_HTML_COLOR2);
		} else if (justAdded){
			return new ColorPair(EXACT_DIFF_HTML_COLOR, EXACT_DIFF_HTML_COLOR2);
		}
		return defaultPair;
	}

	/**
	 * ColorPair for used things in DiffGraph
	 */
	public static ColorPair diffUsedHTMLColorPair(){
		return new ColorPair(USED_DIFF_HTML_COLOR, USED_DIFF_HTML_COLOR2);
	}

	/**
	 * Minimum ASCII char used by the AlphabetLexer and others.
	 * Do not change!
	 */
	public static final int MIN_CHAR = 0;
	/**
	 * Maximum ASCII char used by the AlphabetLexer and others
	 */
	public static final int MAX_CHAR = 126;

	private static final char CONTROL_LIMIT = ' ';
	private static final char PRINTABLE_LIMIT = '\u007e';
	private static final char[] HEX_DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b',
			'c', 'd', 'e', 'f' };

	/**
	 * Return an escaped version of the passed string.
	 *
	 * Shamelessly copied from http://stackoverflow.com/a/1351973
	 *
	 * @param source passed string
	 * @return escaped version
	 */
	public static String toPrintableRepresentation(String source) {

		if( source == null ) return null;
		else {

			final StringBuilder sb = new StringBuilder();
			final int limit = source.length();
			char[] hexbuf = null;

			int pointer = 0;

			sb.append('"');

			while( pointer < limit ) {

				int ch = source.charAt(pointer++);

				switch( ch ) {

					case '\0': sb.append("\\0"); break;
					case '\t': sb.append("\\t"); break;
					case '\n': sb.append("\\n"); break;
					case '\r': sb.append("\\r"); break;
					case '\"': sb.append("\\\""); break;
					case '\\': sb.append("\\\\"); break;

					default:
						if( CONTROL_LIMIT <= ch && ch <= PRINTABLE_LIMIT ) sb.append((char)ch);
						else {

							sb.append("\\u");

							if( hexbuf == null )
								hexbuf = new char[4];

							for( int offs = 4; offs > 0; ) {

								hexbuf[--offs] = HEX_DIGITS[ch & 0xf];
								ch >>>= 4;
							}

							sb.append(hexbuf, 0, 4);
						}
				}
			}

			return sb.append('"').toString();
		}
	}

	/**
	 * Joins the string representations of several objects passed via a list.
	 *
	 * @param strs passed list of objects
	 * @param separator separator between those representations
	 * @param <T> type of the passed objects
	 * @return joined string
	 */
	public static <T> String join(List<T> strs, String separator){
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < strs.size(); i++){
			if (i != 0){
				builder.append(separator);
			}
			Object obj = strs.get(i);
			if (!(obj instanceof String)){
				obj = obj.toString();
			}
			builder.append((String)obj);
		}
		return builder.toString();
	}

	/**
	 * Simple immutable triple (tuple with three entries) implementation.
	 */
	public static class Triple<T, V, W> {

		public final T first;

		public final V second;

		public final W third;

		public Triple(T first, V second, W third) {
			this.first = first;
			this.second = second;
			this.third = third;
		}

		@Override
		public int hashCode() {
			return first.hashCode() ^ second.hashCode() ^ third.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Triple)){
				return false;
			}
			Triple triple = (Triple)obj;
			return triple.first == this.first && triple.second == this.second && triple.third == this.third;
		}
	}

	public static <T> ArrayList<T> makeArrayList(T... elements){
		ArrayList<T> ret = new ArrayList<>(elements.length);
		for (int i = 0; i < elements.length; i++){
			ret.add(elements[i]);
		}
		return ret;
	}

	public static <T> HashSet<T> makeHashSet(T... elements){
		HashSet<T> ret = new HashSet<>(elements.length);
		for (int i = 0; i < elements.length; i++){
			ret.add(elements[i]);
		}
		return ret;
	}

	public static <T> List<T> addToListIfNeeded(List<T> list, T... elements){
		for (T elem : elements){
			if (!list.contains(elem)){
				list.add(elem);
			}
		}
		return list;
	}

	public static <T> List<T> addToListIfNeeded(List<T> list, List<T> newElements){
		for (T elem : newElements){
			if (!list.contains(elem)){
				list.add(elem);
			}
		}
		return list;
	}

	public static String escapeHtml(String text){
		String ret = text + "";
		String[] search = new String[]{"&", "\"", "<", ">", "·"};
		String[] replacement = new String[]{"&amp;", "&quot;", "&lt;", "&gt;", "&bull;"};
		for (int i = 0; i < search.length; i++){
			ret = ret.replaceAll(search[i], replacement[i]);
		}
		return ret;
	}

	public static Object[] appendToArray(Object[] arr, Object... appendix){
		Object[] ret = new Object[arr.length + 1];
		System.arraycopy(arr, 0, ret, 0, arr.length);
		System.arraycopy(appendix, 0, ret, arr.length, appendix.length);
		return ret;
	}

	public static Object[] prependToArray(Object[] arr, Object... suffix){
		Object[] ret = new Object[arr.length + 1];
		System.arraycopy(suffix, 0, ret, 0, suffix.length);
		System.arraycopy(arr, 0, ret, suffix.length, arr.length);
		return ret;
	}

	/**
	 * Creates a REPL for a lexer and prints the token types for an entered string.
	 *
	 * The user can end the REPL by entering an empty line.
	 *
	 * @param lexerConstructor function to create an lexer for a given input
	 */
	public static void repl(Function<String, Lexer> lexerConstructor){
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String line = "";
		System.out.print("");
		try {
			while ((line = input.readLine()) != null && !line.equals("")){
				Lexer lexer = lexerConstructor.apply(line);
				System.out.print("=> ");
				try {
					do {
						System.out.print(lexer.next().toSimpleString() + " ");
					} while (lexer.cur().type != 0);
				} catch (SWPException ex){
					System.out.print("Caught error: " + ex.getMessage());
				}
				System.out.print("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a REPL for a parser and prints the result for an entered string.
	 *
	 * The user can end the REPL by entering an empty line.
	 *
	 * @param eval function to evaluate the entered string
	 */
	public static void parserRepl(Function<String, Object> eval){
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String line = "";
		System.out.print("");
		try {
			while (!(line = input.readLine()).equals("")){
				try {
					Object result = eval.apply(line);
					System.out.print("=> " + result);
					System.out.print("\n");
				} catch (Error ex){
					ex.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Groups the passed integers together in ranges (represented by pair <start, end>).
	 *
	 * The passed list is sorted!
	 *
	 * @param integers passed list of integers
	 * @return list of range pairs
	 */
	public static List<Pair<Integer, Integer>> groupIntegers(List<Integer> integers){
		Collections.sort(integers);
		List<Pair<Integer, Integer>> ret = new ArrayList<>();
		boolean firstCharSet = false;
		for (int i : integers){
			if (!firstCharSet){
				firstCharSet = true;
				ret.add(new Pair<>(i, i));
			} else {
				Pair<Integer, Integer> lastPair = ret.get(ret.size() - 1);
				if (lastPair.second + 1 == i){
					ret.set(ret.size() - 1, new Pair<>(lastPair.first, lastPair.second + 1));
				} else {
					ret.add(new Pair<>(i, i));
				}
			}
		}
		return ret;
	}

	public static <T> String toString(String joiner, List<T> objs){
		return objs.stream().map(Object::toString).collect(Collectors.joining(joiner));
	}
}
