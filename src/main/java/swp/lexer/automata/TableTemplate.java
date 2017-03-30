/**
 * Auto generated, don't edit it manually.
 */

package swp.lexer.automata;

/**
 * Auto generated automaton table for the lexer.
 */
public class TableTemplate {
    /**
     * [current state][character] => next state, -1 for error state
     */
    private final int[][] transitions = {
            new int[]{}
    };
    /**
     * [state] => null: non final state, else terminal
     */
    private final EnumTemplate[] finalTerminals = new EnumTemplate[]{
            null
    };
    public final int[] tokenTypeTranslations = new int[0];
    public final int[][] reverseTranslations = new int[][]{
            new int[]{1}
    };

    private final int initialState = 1;

    public int getInitialState(){
        return initialState;
    }

    public int getStateTransition(int oldState, int input) throws ArrayIndexOutOfBoundsException {
        return transitions[oldState][input];
    }

    public boolean hasStateTransition(int oldState, int input) throws ArrayIndexOutOfBoundsException {
        return transitions[oldState][input] != -1;
    }
}