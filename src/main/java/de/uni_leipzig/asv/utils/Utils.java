package de.uni_leipzig.asv.utils;

public class Utils {

	public static String create_rule(String vollform, String grundform) {

		String regel = "", teil = "";
		int bestlength = -1, anfang = -1, ende = -1;
		int vorkommen = -1, vorkommen2 = 0;

		int vf_laenge = vollform.length();
		for (int i = 0; i < vf_laenge; i++) {
			for (int j = vf_laenge; j > i; j--) {
				teil = vollform.substring(i, j);
				vorkommen = grundform.indexOf(teil);
				vorkommen2 = vollform.indexOf(teil);

				if ((teil.length() > bestlength) && (vorkommen == 0)) {
					bestlength = teil.length();
					anfang = i;
					ende = j;
					regel = "";
					if (vorkommen2 != 0) {
						regel = vollform.substring(0, anfang) + "#";
					}
					regel = regel + (vollform.length() - ende);
					regel = regel
							+ grundform.substring(teil.length(), grundform
									.length());
				}
			}
		}
		if (regel.equals("") == true) {
			regel = vollform.length() + grundform;
		}
		return regel;

	}

	public static String apply_rule(String vollform, String wclass) {
		String grundform = "fehlende Behandlung in apply_rule";

		String pattern1 = "[0-9][0-9].*";
		String pattern2 = "[0-9].*";
		int temp = -1;

		if (wclass == "undecided") { // konnte nicht entschieden werden
			grundform = "undecided";

		} else if (wclass.substring(0, 1).equals("ä")) {
			temp = Integer.parseInt(wclass.substring(1, 2));
			grundform = vollform.substring(0, vollform.length() - temp);
			grundform = umlaut_ersetzung(grundform);
		} else { // Standartbehandlung
			int rautenindex = wclass.indexOf("#");
			if (rautenindex != -1) { // "#" in wclass enthalten
				grundform = vollform.substring(rautenindex);
				if (wclass.substring(rautenindex + 1).matches(pattern1)) {
					temp = Integer.parseInt(wclass.substring(rautenindex + 1,
							rautenindex + 3));
					grundform = grundform.substring(0, grundform.length()
							- temp)
							+ wclass.substring(rautenindex + 3);
				} else if (wclass.substring(rautenindex + 1).matches(pattern2)) {
					temp = Integer.parseInt(wclass.substring(rautenindex + 1,
							rautenindex + 2));
					grundform = grundform.substring(0, grundform.length()
							- temp)
							+ wclass.substring(rautenindex + 2);
				}
			} else { // ohne "#" in wclass
				if (wclass.matches(pattern1)) {
					temp = Integer.parseInt(wclass.substring(0, 2));
					grundform = vollform.substring(0, vollform.length() - temp)
							+ wclass.substring(2);
				} else if (wclass.substring(rautenindex + 1).matches(pattern2)) {
					temp = Integer.parseInt(wclass.substring(0, 1));
					if (temp == 0) {
						grundform = vollform;
					} else {
						// System.out.println(temp);
						temp = Integer.parseInt(wclass.substring(0, 1));
						grundform = vollform.substring(0, vollform.length()
								- temp)
								+ wclass.substring(1);
					}
				}
			}
		}
		return grundform;
	}

	public static String umlaut_ersetzung(String wort) {
		wort = wort.replace("ä", "a");
		wort = wort.replace("Ä", "A");
		wort = wort.replace("ü", "u");
		wort = wort.replace("Ü", "U");
		wort = wort.replace("ö", "o");
		wort = wort.replace("Ö", "O");

		return wort;
	}
}
