package com.custommusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layout, top to bottom: a search box, tabs, (on the song tabs) a fixed row of artist buttons, then the list.
 * <ul>
 *   <li>Yours: your own tracks, sorted by artist, with where each one plays</li>
 *   <li>Vanilla: the built-in songs, sorted by artist, each allowed or blocked</li>
 *   <li>Discs: every music disc, with the custom track (if any) that replaces its audio</li>
 * </ul>
 * A green check means the song may play, a red cross means it is blocked.
 * "Apply" writes the resource pack, turns it on and reloads resources.
 */
public final class MusicScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int SEARCH_Y = 8;
	private static final int ARTIST_ROW_Y = 54;
	private static final int LIST_TOP = 80;
	private static final int MAX_ROWS = 7;
	private static final int LIST_WIDTH = 310;
	private static final int SEARCH_WIDTH = 170;
	private static final int ARROW_WIDTH = 20;
	private static final int GAP = 5;
	private static final int MIN_ARTIST_BUTTON = 127;

	// Legacy formatting codes are written as escapes so the source file never depends on its encoding.
	private static final String GREEN_CHECK = "\u00a7a\u2714\u00a7r ";
	private static final String RED_CROSS = "\u00a7c\u2718\u00a7r ";
	private static final String YELLOW_MIXED = "\u00a7e~\u00a7r ";
	private static final String GRAY = "\u00a77";
	private static final String GREEN = "\u00a7a";
	private static final String RED = "\u00a7c";
	private static final String RESET = "\u00a7r";

	private enum Tab { YOURS, VANILLA, DISCS }

	private record VanillaSong(String name, TrackNames.Info info) {}

	private record DiscRow(String key, TrackNames.Disc disc) {}

	private final Screen parent;
	private final MusicConfig config = MusicConfig.load();

	private VanillaCatalog catalog = new VanillaCatalog();
	private List<VanillaSong> vanillaSongs = List.of();
	private List<CustomTracks.Found> customTracks = List.of();
	private List<DiscRow> allDiscs = List.of();

	// What the search currently lets through (recomputed on every rebuild). Artist buttons act on these.
	private List<VanillaSong> shownVanilla = List.of();
	private List<ArtistGroup> shownVanillaArtists = List.of();
	private List<CustomTracks.Found> shownCustom = List.of();
	private List<ArtistGroup> shownCustomArtists = List.of();
	private List<DiscRow> shownDiscs = List.of();

	/** Created once and re-added on every rebuild, so typing keeps its cursor and focus. */
	private EditBox searchBox;
	private String query = "";

	private boolean packActive = true;
	private Tab tab = Tab.YOURS;
	private int page = 0;
	private int artistOffset = 0;
	private String status = "";

	public MusicScreen(Screen parent) {
		super(Component.literal("Custom Music"));
		this.parent = parent;
		scanEverything();
	}

	private void scanEverything() {
		var resources = Minecraft.getInstance().getResourceManager();
		this.catalog = VanillaCatalog.scan(resources);
		this.packActive = Platform.packActive(resources);

		List<VanillaSong> songs = new ArrayList<>();
		for (String name : catalog.tracks) {
			songs.add(new VanillaSong(name, TrackNames.forVanilla(name)));
		}
		songs.sort(Comparator.comparing(VanillaSong::info, TrackNames.ORDER).thenComparing(VanillaSong::name));
		this.vanillaSongs = songs;

		List<DiscRow> discs = new ArrayList<>();
		for (String key : catalog.discs.keySet()) {
			discs.add(new DiscRow(key, TrackNames.discFor(key)));
		}
		discs.sort(Comparator.comparing((DiscRow row) -> row.disc().info(), TrackNames.ORDER).thenComparing(DiscRow::key));
		this.allDiscs = discs;

		rescanCustom();
	}

	/** Reads the custom_music folder and keeps saved settings for tracks that are still there. */
	private int rescanCustom() {
		CustomTracks.Result result = CustomTracks.scan(MusicConfig.musicDir());

		List<CustomTracks.Found> sorted = new ArrayList<>(result.tracks());
		sorted.sort(Comparator
			.comparing((CustomTracks.Found track) -> TrackNames.forCustom(track.name()), TrackNames.ORDER)
			.thenComparing(CustomTracks.Found::id));
		this.customTracks = sorted;

		Map<String, MusicConfig.CustomEntry> merged = new LinkedHashMap<>();
		for (CustomTracks.Found track : customTracks) {
			merged.put(track.id(), config.custom.getOrDefault(track.id(), new MusicConfig.CustomEntry()));
		}
		config.custom = merged;
		// A disc can only be replaced by a track that still exists.
		config.discs.values().removeIf(id -> !merged.containsKey(id));
		return result.skipped().size();
	}

	@Override
	protected void init() {
		buildWidgets();
	}

	private int listTop() {
		return tab == Tab.DISCS ? ARTIST_ROW_Y : LIST_TOP;
	}

	private int rows() {
		// Keep the list clear of the status line and the two button rows at the bottom.
		return Math.max(3, Math.min(MAX_ROWS, (this.height - listTop() - 76) / ROW_HEIGHT));
	}

	/** Wide windows get two columns for the (long) vanilla list. */
	private int columns() {
		return tab == Tab.VANILLA && this.width >= 660 ? 2 : 1;
	}

	private int perPage() {
		return rows() * columns();
	}

	/** Disc rows are wider because they hold two names. */
	private int rowWidth() {
		return tab == Tab.DISCS ? Math.min(380, this.width - 30) : LIST_WIDTH;
	}

	private int listWidth() {
		return columns() * rowWidth() + (columns() - 1) * 10;
	}

	private int itemCount() {
		return switch (tab) {
			case YOURS -> shownCustom.size();
			case VANILLA -> shownVanilla.size();
			case DISCS -> shownDiscs.size();
		};
	}

	private int pageCount() {
		return Math.max(1, (itemCount() + perPage() - 1) / perPage());
	}

	/** Applies the search text to every tab and regroups the artists from what is left. */
	private void refreshView() {
		shownVanilla = vanillaSongs.stream()
			.filter(song -> TrackNames.matches(song.info(), query))
			.toList();
		shownVanillaArtists = ArtistGroup.group(shownVanilla.stream()
			.map(song -> new ArtistGroup.Member(song.name(), song.info()))
			.toList());

		shownCustom = customTracks.stream()
			.filter(track -> TrackNames.matches(TrackNames.forCustom(track.name()), query))
			.toList();
		shownCustomArtists = ArtistGroup.group(shownCustom.stream()
			.map(track -> new ArtistGroup.Member(track.id(), TrackNames.forCustom(track.name())))
			.toList());

		shownDiscs = allDiscs.stream()
			.filter(row -> TrackNames.matches(row.disc().info(), query))
			.toList();
	}

	private boolean isAllowed(String key) {
		return tab == Tab.YOURS ? config.custom.get(key).enabled : !config.disabledVanilla.contains(key);
	}

	private void setAllowed(String key, boolean allowed) {
		if (tab == Tab.YOURS) {
			config.custom.get(key).enabled = allowed;
		} else if (allowed) {
			config.disabledVanilla.remove(key);
		} else {
			config.disabledVanilla.add(key);
		}
	}

	private void switchTab(Tab newTab) {
		tab = newTab;
		page = 0;
		artistOffset = 0;
		buildWidgets();
	}

	private static String tabLabel(String name, boolean selected) {
		return selected ? "[" + name + "]" : name;
	}

	private void buildWidgets() {
		boolean searchHadFocus = searchBox != null && searchBox.isFocused();
		refreshView();
		this.clearWidgets();
		page = Math.max(0, Math.min(page, pageCount() - 1));

		int left = this.width / 2 - 155;

		// Search box (the same instance every time).
		if (searchBox == null) {
			searchBox = new EditBox(this.font, left, SEARCH_Y, SEARCH_WIDTH, 18, Component.literal("Search"));
			searchBox.setMaxLength(40);
			searchBox.setResponder(text -> {
				if (!text.equals(query)) {
					query = text;
					page = 0;
					artistOffset = 0;
					buildWidgets();
				}
			});
		}
		searchBox.setPosition(left, SEARCH_Y);
		searchBox.setWidth(SEARCH_WIDTH);
		addRenderableWidget(searchBox);
		if (searchHadFocus) {
			this.setFocused(searchBox);
		}

		// Volume of all custom music, next to the search box. It takes effect on Apply.
		int sliderX = left + SEARCH_WIDTH + GAP;
		addRenderableWidget(new VolumeSlider(sliderX, SEARCH_Y - 1, LIST_WIDTH - SEARCH_WIDTH - GAP, 20, config.customVolume, percent -> {
			config.customVolume = percent;
			status = "Custom volume " + percent + "%. Press Apply to hear the change.";
		}));

		// Tabs and the master switch for vanilla music.
		addRenderableWidget(Button.builder(Component.literal(tabLabel("Yours", tab == Tab.YOURS)), b -> switchTab(Tab.YOURS))
			.bounds(left, 30, 66, 20).build());
		addRenderableWidget(Button.builder(Component.literal(tabLabel("Vanilla", tab == Tab.VANILLA)), b -> switchTab(Tab.VANILLA))
			.bounds(left + 70, 30, 66, 20).build());
		addRenderableWidget(Button.builder(Component.literal(tabLabel("Discs", tab == Tab.DISCS)), b -> switchTab(Tab.DISCS))
			.bounds(left + 140, 30, 66, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Vanilla: " + (config.vanillaEnabled ? "ON" : "OFF")), b -> {
			config.vanillaEnabled = !config.vanillaEnabled;
			buildWidgets();
		}).bounds(left + 210, 30, 100, 20).build());

		if (tab != Tab.DISCS) {
			buildArtistRow();
		}
		buildList();

		// Paging.
		int navY = this.height - 54;
		Button prev = Button.builder(Component.literal("< Prev"), b -> {
			page--;
			buildWidgets();
		}).bounds(left, navY, 70, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);

		Button next = Button.builder(Component.literal("Next >"), b -> {
			page++;
			buildWidgets();
		}).bounds(left + 240, navY, 70, 20).build();
		next.active = page < pageCount() - 1;
		addRenderableWidget(next);

		// Actions.
		int actionY = this.height - 28;
		addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> {
			status = Platform.openFolder(MusicConfig.musicDir())
				? "Opened the custom_music folder. Add .ogg files, then press Rescan."
				: "Could not open it. It is the custom_music folder inside your game folder.";
		}).bounds(left, actionY, 76, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Rescan"), b -> {
			int skipped = rescanCustom();
			status = "Found " + customTracks.size() + " track(s)."
				+ (skipped > 0 ? " Skipped " + skipped + " file(s) that are not Ogg Vorbis." : "");
			tab = Tab.YOURS;
			page = 0;
			artistOffset = 0;
			buildWidgets();
		}).bounds(left + 78, actionY, 76, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
			.bounds(left + 156, actionY, 76, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(left + 234, actionY, 76, 20).build());
	}

	/** The fixed row of artist buttons under the tabs: [<] [artist] [artist] ... [>]. */
	private void buildArtistRow() {
		List<ArtistGroup> artists = tab == Tab.YOURS ? shownCustomArtists : shownVanillaArtists;
		if (artists.isEmpty()) return;

		int rowLeft = this.width / 2 - listWidth() / 2;
		int available = listWidth() - 2 * (ARROW_WIDTH + GAP);
		int slots = Math.max(1, (available + GAP) / (MIN_ARTIST_BUTTON + GAP));
		int buttonWidth = (available - (slots - 1) * GAP) / slots;
		artistOffset = Math.max(0, Math.min(artistOffset, Math.max(0, artists.size() - slots)));

		Button prev = Button.builder(Component.literal("<"), b -> {
			artistOffset = Math.max(0, artistOffset - slots);
			buildWidgets();
		}).bounds(rowLeft, ARTIST_ROW_Y, ARROW_WIDTH, 20).build();
		prev.active = artistOffset > 0;
		addRenderableWidget(prev);

		for (int i = 0; i < slots && artistOffset + i < artists.size(); i++) {
			ArtistGroup group = artists.get(artistOffset + i);
			int x = rowLeft + ARROW_WIDTH + GAP + i * (buttonWidth + GAP);
			addRenderableWidget(Button.builder(Component.literal(artistLabel(group, buttonWidth - 8)), b -> {
				boolean everythingAllowed = true;
				for (String key : group.keys()) {
					if (!isAllowed(key)) everythingAllowed = false;
				}
				// Mixed or blocked: allow all of this artist's songs. Fully allowed: block them all.
				for (String key : group.keys()) {
					setAllowed(key, !everythingAllowed);
				}
				buildWidgets();
			}).bounds(x, ARTIST_ROW_Y, buttonWidth, 20).build());
		}

		Button next = Button.builder(Component.literal(">"), b -> {
			artistOffset = Math.min(artists.size() - 1, artistOffset + slots);
			buildWidgets();
		}).bounds(rowLeft + listWidth() - ARROW_WIDTH, ARTIST_ROW_Y, ARROW_WIDTH, 20).build();
		next.active = artistOffset + slots < artists.size();
		addRenderableWidget(next);
	}

	/** "check Artist (allowed/total)"; the count is dropped, then the name shortened, if it does not fit. */
	private String artistLabel(ArtistGroup group, int maxPixels) {
		int allowed = 0;
		for (String key : group.keys()) {
			if (isAllowed(key)) allowed++;
		}
		int total = group.keys().size();
		String mark = allowed == total ? GREEN_CHECK : (allowed == 0 ? RED_CROSS : YELLOW_MIXED);

		String withCount = mark + group.artist() + " " + GRAY + "(" + allowed + "/" + total + ")";
		if (this.font.width(withCount) <= maxPixels) return withCount;

		String name = group.artist();
		String plain = mark + name;
		if (this.font.width(plain) <= maxPixels) return plain;
		while (name.length() > 3) {
			name = name.substring(0, name.length() - 1);
			plain = mark + name + "...";
			if (this.font.width(plain) <= maxPixels) return plain;
		}
		return plain;
	}

	private void buildList() {
		int rows = rows();
		int listLeft = this.width / 2 - listWidth() / 2;
		int start = page * perPage();

		for (int i = 0; i < perPage() && start + i < itemCount(); i++) {
			int x = listLeft + (i / rows) * (rowWidth() + 10);
			int y = listTop() + (i % rows) * ROW_HEIGHT;
			switch (tab) {
				case YOURS -> addCustomSong(shownCustom.get(start + i), x, y);
				case VANILLA -> addVanillaSong(shownVanilla.get(start + i), x, y);
				case DISCS -> addDisc(shownDiscs.get(start + i), x, y);
			}
		}
	}

	private void addVanillaSong(VanillaSong song, int x, int y) {
		boolean allowed = !config.disabledVanilla.contains(song.name());
		addRenderableWidget(Button.builder(Component.literal(label(allowed, song.info(), song.info().seconds(), LIST_WIDTH - 14)), b -> {
			if (!config.disabledVanilla.remove(song.name())) {
				config.disabledVanilla.add(song.name());
			}
			buildWidgets();
		}).bounds(x, y, LIST_WIDTH, 20).build());
	}

	private void addCustomSong(CustomTracks.Found track, int x, int y) {
		MusicConfig.CustomEntry entry = config.custom.get(track.id());

		double seconds = track.info().known() ? track.info().seconds() : 0;
		addRenderableWidget(Button.builder(Component.literal(label(entry.enabled, TrackNames.forCustom(track.name()), seconds, 201)), b -> {
			entry.enabled = !entry.enabled;
			buildWidgets();
		}).bounds(x, y, 215, 20).build());

		addRenderableWidget(Button.builder(Component.literal(entry.where().label), b -> {
			entry.preset = entry.where().next().name();
			buildWidgets();
		}).bounds(x + 220, y, 90, 20).build());
	}

	/** One wide button per disc: "Cat - C418 (3:05)  >  Original". Clicking cycles through your tracks. */
	private void addDisc(DiscRow row, int x, int y) {
		CustomTracks.Found replacement = findCustom(config.discs.get(row.key()));
		addRenderableWidget(Button.builder(Component.literal(discLabel(row.disc(), replacement, rowWidth() - 14)), b -> cycleDisc(row))
			.bounds(x, y, rowWidth(), 20).build());
	}

	private CustomTracks.Found findCustom(String id) {
		if (id == null) return null;
		for (CustomTracks.Found track : customTracks) {
			if (track.id().equals(id)) return track;
		}
		return null;
	}

	/** Original, then each of your tracks in turn, then back to original. */
	private void cycleDisc(DiscRow row) {
		if (customTracks.isEmpty()) {
			status = "Add .ogg files on the Yours tab first, then pick them here.";
			buildWidgets();
			return;
		}

		String current = config.discs.get(row.key());
		int index = -1; // -1 is "Original"
		for (int i = 0; i < customTracks.size(); i++) {
			if (customTracks.get(i).id().equals(current)) index = i;
		}

		int next = index + 1;
		if (next >= customTracks.size()) {
			config.discs.remove(row.key());
			status = row.disc().title() + " plays its original music again.";
		} else {
			CustomTracks.Found track = customTracks.get(next);
			config.discs.put(row.key(), track.id());
			status = describeReplacement(row.disc(), track);
		}
		buildWidgets();
	}

	/** Warns about the two things that surprise people: the jukebox's time limit and stereo files. */
	private String describeReplacement(TrackNames.Disc disc, CustomTracks.Found track) {
		List<String> lines = new ArrayList<>();
		if (isCutOff(disc, track)) {
			lines.add("It is " + TrackNames.formatTime(track.info().seconds()) + " but " + disc.title()
				+ " stops at " + TrackNames.formatTime(disc.seconds()) + ", so the end is cut off.");
		}
		if (track.info().stereo()) {
			lines.add("Stereo plays at full volume everywhere; mono fades with distance.");
		}
		if (lines.isEmpty()) {
			lines.add(disc.title() + " will play " + TrackNames.forCustom(track.name()).title() + ".");
		}
		return String.join("\n", lines);
	}

	/** The jukebox stops when the original disc would have ended, whatever the replacement's length. */
	private static boolean isCutOff(TrackNames.Disc disc, CustomTracks.Found track) {
		return track.info().known() && disc.seconds() > 0 && track.info().seconds() > disc.seconds() + 1;
	}

	private String discLabel(TrackNames.Disc disc, CustomTracks.Found replacement, int maxPixels) {
		String time = disc.seconds() > 0 ? " (" + TrackNames.formatTime(disc.seconds()) + ")" : "";
		String artist = disc.info().hasArtist() ? " - " + disc.artist() : "";
		String arrow = GRAY + "  >  " + RESET;

		String replacementTitle = replacement == null ? null : TrackNames.forCustom(replacement.name()).title();
		String warning = replacement != null && isCutOff(disc, replacement) ? RED + "!" + RESET + " " : "";

		String discTitle = disc.title();
		while (true) {
			String left = discTitle + GRAY + artist + time + RESET;
			String right = replacement == null ? GRAY + "Original" : warning + GREEN + replacementTitle;
			String full = left + arrow + right;
			if (this.font.width(full) <= maxPixels) return full;

			// Shorten the replacement's name first, then the disc's own title.
			if (replacementTitle != null && replacementTitle.length() > 6) {
				replacementTitle = replacementTitle.substring(0, replacementTitle.length() - 1);
			} else if (discTitle.length() > 4) {
				discTitle = discTitle.substring(0, discTitle.length() - 1);
			} else {
				return full;
			}
		}
	}

	/**
	 * "check Title - Artist (3:05)", with the title shortened if it would not fit in {@code maxPixels}.
	 * The artist and length are never cut. A length of 0 (or less) means unknown and is left out.
	 */
	private String label(boolean allowed, TrackNames.Info info, double seconds, int maxPixels) {
		String mark = allowed ? GREEN_CHECK : RED_CROSS;
		String time = seconds > 0 ? " (" + TrackNames.formatTime(seconds) + ")" : "";
		String suffix = GRAY + (info.hasArtist() ? " - " + info.artist() : "") + time;

		String full = mark + info.title() + suffix;
		if (this.font.width(full) <= maxPixels) return full;

		String title = info.title();
		while (title.length() > 3) {
			title = title.substring(0, title.length() - 1);
			String shortened = mark + title + "..." + suffix;
			if (this.font.width(shortened) <= maxPixels) return shortened;
		}
		return mark + title + "...";
	}

	private void apply() {
		try {
			config.save();
			PackWriter.Result result = PackWriter.write(config, catalog, customTracks);
			if (result.usedFallback() && config.discs.isEmpty()) {
				status = "Could not read vanilla music, so only your tracks were added. See the log.";
			} else if (!Platform.enablePack(PackWriter.PACK_ID)) {
				status = "Applied, but enable the 'CustomMusic' pack in Options > Resource Packs.";
			} else if (Platform.reloadResources()) {
				status = "Applied " + result.tracksCopied() + " custom track(s). Reloading resources...";
			} else {
				status = "Applied. Press F3+T to reload resources.";
			}
		} catch (Exception e) {
			CustomMusicClient.LOGGER.error("Could not write the music resource pack", e);
			status = "Failed: " + e.getMessage();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (query.isEmpty() && !searchBox.isFocused()) {
			graphics.text(this.font, "Search songs or artists...", this.width / 2 - 155 + 6, SEARCH_Y + 5, 0xFF808080, false);
		}
		drawCentered(graphics, "Page " + (page + 1) + " / " + pageCount(), this.height - 48, 0xFFAAAAAA);

		int top = listTop();
		if (tab == Tab.YOURS && customTracks.isEmpty()) {
			drawCentered(graphics, "No .ogg files found yet.", top + 10, 0xFFFFFFFF);
			drawCentered(graphics, "Press 'Open folder', drop Ogg Vorbis files in, then press Rescan.", top + 24, 0xFFAAAAAA);
			drawCentered(graphics, "Tip: name files like 'Artist - Title.ogg' to sort and label them.", top + 38, 0xFFAAAAAA);
		}
		if (tab == Tab.VANILLA && vanillaSongs.isEmpty()) {
			drawCentered(graphics, "Could not read the vanilla music list (see the game log).", top + 10, 0xFFFF5555);
		}
		if (tab == Tab.DISCS && allDiscs.isEmpty()) {
			drawCentered(graphics, "Could not read the music disc list (see the game log).", top + 10, 0xFFFF5555);
		}
		boolean listHasItems = switch (tab) {
			case YOURS -> !customTracks.isEmpty();
			case VANILLA -> !vanillaSongs.isEmpty();
			case DISCS -> !allDiscs.isEmpty();
		};
		if (listHasItems && itemCount() == 0) {
			drawCentered(graphics, "Nothing matches your search.", top + 10, 0xFFFFFFFF);
		}

		int statusY = this.height - 70;
		if (!status.isEmpty()) {
			String[] lines = status.split("\n");
			for (int i = 0; i < lines.length; i++) {
				drawCentered(graphics, lines[i], statusY + i * 10, 0xFFFFFF55);
			}
		} else if (!packActive) {
			drawCentered(graphics, "Enable the 'CustomMusic' pack in Options > Resource Packs.", statusY, 0xFFFF5555);
		} else if (tab == Tab.DISCS) {
			drawCentered(graphics, "Click a disc to cycle its replacement. A red ! means it gets cut off.", statusY, 0xFFAAAAAA);
		} else {
			drawCentered(graphics, "Top: toggle a whole artist. Below: toggle one song. Apply saves.", statusY, 0xFFAAAAAA);
		}
	}

	private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int color) {
		graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, color, true);
	}

	@Override
	public void onClose() {
		config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
