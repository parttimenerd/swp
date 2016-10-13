package swp.parser.early;

import swp.grammar.Production;

/**
 * Created by parttimenerd on 07.07.16.
 */
public class EarlyItem {

	public Situation currentSituation;
	public final int startTokenId;

	public EarlyItem(Situation currentSituation, int startTokenId){
		this.currentSituation = currentSituation;
		this.startTokenId = startTokenId;
	}

	public EarlyItem(Production production){
		this(new Situation(production), 0);
	}

	public EarlyItem(Production production, int startTokenId){
		this(new Situation(production), startTokenId);
	}

	@Override
	public String toString() {
		return "[" + currentSituation + ", " + startTokenId + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof EarlyItem){
			EarlyItem item = (EarlyItem)obj;
			return item.startTokenId == startTokenId && this.currentSituation.equals(item.currentSituation);
		}
		return false;
	}
}
