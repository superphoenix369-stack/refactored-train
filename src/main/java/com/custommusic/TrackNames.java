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

	/** {@code seconds} is the song's length, or 0 when it is not known. */
	public record Info(String title, String artist, int seconds) {
		public Info(String title, String artist) {
			this(title, artist, 0);
		}

		public boolean hasArtist() {
			return !UNKNOWN_ARTIST.equals(artist);
		}
	}

	/** Artists first (A to Z, ignoring case), then by title. Tracks with an unknown artist go last. */
	public static final Comparator<Info> ORDER = Comparator
		.comparing((Info info) -> !info.hasArtist())
		.thenComparing(info -> info.artist().toLowerCase(Locale.ROOT))
		.thenComparing(info -> info.title().toLowerCase(Locale.ROOT));

	/** A music disc: {@code seconds} is the length the jukebox plays it for (0 if unknown). */
	public record Disc(String title, String artist, int seconds) {
		public Info info() {
			return new Info(title, artist, seconds);
		}
	}

	private static final Map<String, Info> KNOWN = new HashMap<>();
	private static final Map<String, Disc> DISCS = new HashMap<>();

	private static void disc(String key, String title, String artist, int seconds) {
		DISCS.put(key, new Disc(title, artist, seconds));
	}

	private static void add(String stem, String title, String artist, int seconds) {
		KNOWN.put(stem, new Info(title, artist, seconds));
	}

	static {
		// Lengths are from the Minecraft Wiki's music list.
		// C418
		add("aria_math", "Aria Math", "C418", 309);
		add("biome_fest", "Biome Fest", "C418", 377);
		add("blind_spots", "Blind Spots", "C418", 331);
		add("clark", "Clark", "C418", 191);
		add("danny", "Danny", "C418", 254);
		add("dreiton", "Dreiton", "C418", 496);
		add("dry_hands", "Dry Hands", "C418", 68);
		add("haggstrom", "Haggstrom", "C418", 204);
		add("haunt_muskie", "Haunt Muskie", "C418", 360);
		add("key", "Key", "C418", 65);
		add("living_mice", "Living Mice", "C418", 177);
		add("mice_on_venus", "Mice on Venus", "C418", 281);
		add("minecraft", "Minecraft", "C418", 254);
		add("oxygene", "Oxyg\u00e8ne", "C418", 65);
		add("subwoofer_lullaby", "Subwoofer Lullaby", "C418", 208);
		add("sweden", "Sweden", "C418", 215);
		add("taswell", "Taswell", "C418", 514);
		add("wet_hands", "Wet Hands", "C418", 90);
		add("axolotl", "Axolotl", "C418", 303);
		add("dragon_fish", "Dragon Fish", "C418", 373);
		add("shuniji", "Shuniji", "C418", 244);
		add("ballad_of_the_cats", "Ballad of the Cats", "C418", 274);
		add("concrete_halls", "Concrete Halls", "C418", 253);
		add("dead_voxel", "Dead Voxel", "C418", 295);
		add("warmth", "Warmth", "C418", 238);
		add("boss", "Boss", "C418", 346);
		add("the_end", "The End", "C418", 903);
		add("alpha", "Alpha", "C418", 602);
		add("beginning_2", "Beginning 2", "C418", 175);
		add("floating_trees", "Floating Trees", "C418", 244);
		add("moog_city_2", "Moog City 2", "C418", 179);
		add("mutation", "Mutation", "C418", 184);

		// Lena Raine
		add("aerie", "Aerie", "Lena Raine", 296);
		add("ancestry", "Ancestry", "Lena Raine", 343);
		add("deeper", "Deeper", "Lena Raine", 303);
		add("eld_unknown", "Eld Unknown", "Lena Raine", 296);
		add("endless", "Endless", "Lena Raine", 402);
		add("firebugs", "Firebugs", "Lena Raine", 312);
		add("infinite_amethyst", "Infinite Amethyst", "Lena Raine", 271);
		add("labyrinthine", "Labyrinthine", "Lena Raine", 324);
		add("left_to_bloom", "Left to Bloom", "Lena Raine", 342);
		add("one_more_day", "One More Day", "Lena Raine", 278);
		add("stand_tall", "Stand Tall", "Lena Raine", 308);
		add("wending", "Wending", "Lena Raine", 314);
		add("chrysopoeia", "Chrysopoeia", "Lena Raine", 243);
		add("rubedo", "Rubedo", "Lena Raine", 312);
		add("so_below", "So Below", "Lena Raine", 319);

		// Kumi Tanioka
		add("an_ordinary_day", "An Ordinary Day", "Kumi Tanioka", 331);
		add("comforting_memories", "Comforting Memories", "Kumi Tanioka", 275);
		add("floating_dream", "Floating Dream", "Kumi Tanioka", 206);
		add("komorebi", "komorebi", "Kumi Tanioka", 288);
		add("pokopoko", "pokopoko", "Kumi Tanioka", 304);
		add("yakusoku", "yakusoku", "Kumi Tanioka", 271);

		// Aaron Cherof
		add("a_familiar_room", "A Familiar Room", "Aaron Cherof", 241);
		add("bromeliad", "Bromeliad", "Aaron Cherof", 312);
		add("crescent_dunes", "Crescent Dunes", "Aaron Cherof", 248);
		add("echo_in_the_wind", "Echo in the Wind", "Aaron Cherof", 296);
		add("featherfall", "Featherfall", "Aaron Cherof", 345);
		add("puzzlebox", "Puzzlebox", "Aaron Cherof", 299);
		add("watcher", "Watcher", "Aaron Cherof", 332);

		// Amos Roddy
		add("below_and_above", "Below and Above", "Amos Roddy", 212);
		add("broken_clocks", "Broken Clocks", "Amos Roddy", 213);
		add("fireflies", "Fireflies", "Amos Roddy", 155);
		add("lilypad", "Lilypad", "Amos Roddy", 235);
		add("os_piano", "O's Piano", "Amos Roddy", 275);

		// fingerspit
		add("ebb", "Ebb", "fingerspit", 306);
		add("home", "Home", "fingerspit", 380);
		add("memories", "Memories", "fingerspit", 245);
		add("nightly", "Nightly", "fingerspit", 302);
		add("shores", "Shores", "fingerspit", 320);
	}

	static {
		// Music discs: title, composer and length, from the Minecraft Wiki's music disc list.
		disc("13", "13", "C418", 178);
		disc("cat", "cat", "C418", 185);
		disc("blocks", "blocks", "C418", 345);
		disc("chirp", "chirp", "C418", 185);
		disc("far", "far", "C418", 174);
		disc("mall", "mall", "C418", 197);
		disc("mellohi", "mellohi", "C418", 96);
		disc("stal", "stal", "C418", 150);
		disc("strad", "strad", "C418", 188);
		disc("ward", "ward", "C418", 251);
		disc("11", "11", "C418", 71);
		disc("wait", "wait", "C418", 237);
		disc("pigstep", "Pigstep", "Lena Raine", 148);
		disc("otherside", "otherside", "Lena Raine", 195);
		disc("creator", "Creator", "Lena Raine", 176);
		disc("creator_music_box", "Creator (Music Box)", "Lena Raine", 73);
		disc("5", "5", "Samuel \u00c5berg", 178);
		disc("relic", "Relic", "Aaron Cherof", 219);
		disc("precipice", "Precipice", "Aaron Cherof", 299);
		disc("tears", "Tears", "Amos Roddy", 175);
		disc("lava_chicken", "Lava Chicken", "Hyper Potions", 135);
		disc("bounce", "Bounce", "fingerspit", 234);
	}

	private TrackNames() {}

	/** {@code eventKey} looks like "music_disc.cat". Discs the table does not know get a tidied name. */
	public static Disc discFor(String eventKey) {
		String suffix = eventKey.startsWith("music_disc.") ? eventKey.substring("music_disc.".length()) : eventKey;
		Disc known = DISCS.get(suffix);
		return known != null ? known : new Disc(prettify(suffix), UNKNOWN_ARTIST, 0);
	}

	/** 185 becomes "3:05". */
	public static String formatTime(double seconds) {
		int total = (int) Math.round(seconds);
		return (total / 60) + ":" + String.format(Locale.ROOT, "%02d", total % 60);
	}

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
