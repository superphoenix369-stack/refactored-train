package com.custommusic;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Human-readable titles and artists.
 * <p>
 * Vanilla tracks are looked up by file name (without folders or extension) in a table taken from the
 * Minecraft Wiki's music list. Tracks that are not in the table (new additions, other packs) get a
 * tidied-up file name and the artist "Unknown artist". Custom tracks use the "Artist - Title.ogg"
 * naming convention when the file name follows it.
 */
public final class TrackNames {
	public static final String UNKNOWN_ARTIST = "Unknown artist";

	public record Info(String title, String artist) {
		public boolean hasArtist() {
			return !UNKNOWN_ARTIST.equals(artist);
		}
	}

	/** Artists first (A to Z, ignoring case), then by title. Tracks with an unknown artist go last. */
	public static final Comparator<Info> ORDER = Comparator
		.comparing((Info info) -> !info.hasArtist())
		.thenComparing(info -> info.artist().toLowerCase(Locale.ROOT))
		.thenComparing(info -> info.title().toLowerCase(Locale.ROOT));

	private static final Map<String, Info> KNOWN = new HashMap<>();

	private static void add(String stem, String title, String artist) {
		KNOWN.put(stem, new Info(title, artist));
	}

	static {
		// C418
		add("aria_math", "Aria Math", "C418");
		add("biome_fest", "Biome Fest", "C418");
		add("blind_spots", "Blind Spots", "C418");
		add("clark", "Clark", "C418");
		add("danny", "Danny", "C418");
		add("dreiton", "Dreiton", "C418");
		add("dry_hands", "Dry Hands", "C418");
		add("haggstrom", "Haggstrom", "C418");
		add("haunt_muskie", "Haunt Muskie", "C418");
		add("key", "Key", "C418");
		add("living_mice", "Living Mice", "C418");
		add("mice_on_venus", "Mice on Venus", "C418");
		add("minecraft", "Minecraft", "C418");
		add("oxygene", "Oxyg\u00e8ne", "C418");
		add("subwoofer_lullaby", "Subwoofer Lullaby", "C418");
		add("sweden", "Sweden", "C418");
		add("taswell", "Taswell", "C418");
		add("wet_hands", "Wet Hands", "C418");
		add("axolotl", "Axolotl", "C418");
		add("dragon_fish", "Dragon Fish", "C418");
		add("shuniji", "Shuniji", "C418");
		add("ballad_of_the_cats", "Ballad of the Cats", "C418");
		add("concrete_halls", "Concrete Halls", "C418");
		add("dead_voxel", "Dead Voxel", "C418");
		add("warmth", "Warmth", "C418");
		add("boss", "Boss", "C418");
		add("the_end", "The End", "C418");
		add("alpha", "Alpha", "C418");
		add("beginning_2", "Beginning 2", "C418");
		add("floating_trees", "Floating Trees", "C418");
		add("moog_city_2", "Moog City 2", "C418");
		add("mutation", "Mutation", "C418");

		// Lena Raine
		add("aerie", "Aerie", "Lena Raine");
		add("ancestry", "Ancestry", "Lena Raine");
		add("deeper", "Deeper", "Lena Raine");
		add("eld_unknown", "Eld Unknown", "Lena Raine");
		add("endless", "Endless", "Lena Raine");
		add("firebugs", "Firebugs", "Lena Raine");
		add("infinite_amethyst", "Infinite Amethyst", "Lena Raine");
		add("labyrinthine", "Labyrinthine", "Lena Raine");
		add("left_to_bloom", "Left to Bloom", "Lena Raine");
		add("one_more_day", "One More Day", "Lena Raine");
		add("stand_tall", "Stand Tall", "Lena Raine");
		add("wending", "Wending", "Lena Raine");
		add("chrysopoeia", "Chrysopoeia", "Lena Raine");
		add("rubedo", "Rubedo", "Lena Raine");
		add("so_below", "So Below", "Lena Raine");

		// Kumi Tanioka
		add("an_ordinary_day", "An Ordinary Day", "Kumi Tanioka");
		add("comforting_memories", "Comforting Memories", "Kumi Tanioka");
		add("floating_dream", "Floating Dream", "Kumi Tanioka");
		add("komorebi", "komorebi", "Kumi Tanioka");
		add("pokopoko", "pokopoko", "Kumi Tanioka");
		add("yakusoku", "yakusoku", "Kumi Tanioka");

		// Aaron Cherof
		add("a_familiar_room", "A Familiar Room", "Aaron Cherof");
		add("bromeliad", "Bromeliad", "Aaron Cherof");
		add("crescent_dunes", "Crescent Dunes", "Aaron Cherof");
		add("echo_in_the_wind", "Echo in the Wind", "Aaron Cherof");
		add("featherfall", "Featherfall", "Aaron Cherof");
		add("puzzlebox", "Puzzlebox", "Aaron Cherof");
		add("watcher", "Watcher", "Aaron Cherof");

		// Amos Roddy
		add("below_and_above", "Below and Above", "Amos Roddy");
		add("broken_clocks", "Broken Clocks", "Amos Roddy");
		add("fireflies", "Fireflies", "Amos Roddy");
		add("lilypad", "Lilypad", "Amos Roddy");
		add("os_piano", "O's Piano", "Amos Roddy");

		// fingerspit
		add("ebb", "Ebb", "fingerspit");
		add("home", "Home", "fingerspit");
		add("memories", "Memories", "fingerspit");
		add("nightly", "Nightly", "fingerspit");
		add("shores", "Shores", "fingerspit");
	}

	private TrackNames() {}

	/** {@code normalized} looks like "minecraft:music/game/creative/blind_spots". */
	public static Info forVanilla(String normalized) {
		String stem = stemOf(normalized);
		Info known = KNOWN.get(stem);
		return known != null ? known : new Info(prettify(stem), UNKNOWN_ARTIST);
	}

	/** {@code baseName} is the file name without ".ogg". "Artist - Title" is split; anything else is just a title. */
	public static Info forCustom(String baseName) {
		String name = baseName.trim();
		int dash = name.indexOf(" - ");
		if (dash > 0) {
			String artist = name.substring(0, dash).trim();
			String title = name.substring(dash + 3).trim();
			if (!artist.isEmpty() && !title.isEmpty()) return new Info(title, artist);
		}
		return new Info(name, UNKNOWN_ARTIST);
	}

	/**
	 * Search matching: every word typed must appear in the title or artist, ignoring case and accents
	 * (so "oxygene" finds "Oxyg\u00e8ne"). An empty query matches everything.
	 */
	public static boolean matches(Info info, String query) {
		String q = fold(query).trim();
		if (q.isEmpty()) return true;
		String haystack = fold(info.title() + " " + info.artist());
		for (String word : q.split("\\s+")) {
			if (!haystack.contains(word)) return false;
		}
		return true;
	}

	private static String fold(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	static String stemOf(String normalized) {
		String path = normalized;
		int colon = path.indexOf(':');
		if (colon >= 0) path = path.substring(colon + 1);
		int slash = path.lastIndexOf('/');
		if (slash >= 0) path = path.substring(slash + 1);
		if (path.endsWith(".ogg")) path = path.substring(0, path.length() - 4);
		return path.toLowerCase(Locale.ROOT);
	}

	/** "some_new_song" becomes "Some New Song". */
	static String prettify(String stem) {
		StringBuilder out = new StringBuilder();
		for (String word : stem.split("[_\\s]+")) {
			if (word.isEmpty()) continue;
			if (out.length() > 0) out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.length() == 0 ? stem : out.toString();
	}
}
