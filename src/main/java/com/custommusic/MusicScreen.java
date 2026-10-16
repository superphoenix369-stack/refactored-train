package com.custommusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layout, top to bottom: tabs, a fixed row of artist buttons (arrows scroll through the artists),
 * then the song list, sorted by artist. An artist button allows or blocks all of that artist's songs.
 * A green check means the song may play, a red cross means it is blocked.
 * "Apply" writes the resource pack and asks the game to reload resources.
 */
public final class MusicScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int ARTIST_ROW_Y = 54;
	private static final int LIST_TOP = 80;
	private static final int MAX_ROWS = 7;
	private static final int LIST_WIDTH = 310;
	private static final int ARROW_WIDTH = 20;
	private static final int GAP = 5;
	private static final int MIN_ARTIST_BUTTON = 127;

	// Legacy formatting codes are written as escapes so the source file never depends on its encoding.
	private static final String GREEN_CHECK = "\u00a7a\u2714\u00a7r ";
	private static final String RED_CROSS = "\u00a7c\u2718\u00a7r ";
	private static final String YELLOW_MIXED = "\u00a7e~\u00a7r ";
	private static final String GRAY = "\u00a77";

	private record VanillaSong(String name, TrackNames.Info info) {}

	private final Screen parent;
	private final MusicConfig config = MusicConfig.load();

	private VanillaCatalog catalog = new VanillaCatalog();
	private List<VanillaSong> vanillaSongs = List.of();
	private List<ArtistGroup> vanillaArtists = List.of();
	private List<CustomTracks.Found> customTracks = List.of();
	private List<ArtistGroup> customArtists = List.of();
	private boolean packActive = true;

	private boolean showCustom = true;
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
		this.vanillaArtists = ArtistGroup.group(songs.stream()
			.map(song -> new ArtistGroup.Member(song.name(), song.info()))
			.toList());

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
		this.customArtists = ArtistGroup.group(sorted.stream()
			.map(track -> new ArtistGroup.Member(track.id(), TrackNames.forCustom(track.name())))
			.toList());

		Map<String, MusicConfig.CustomEntry> merged = new LinkedHashMap<>();
		for (CustomTracks.Found track : customTracks) {
			merged.put(track.id(), config.custom.getOrDefault(track.id(), new MusicConfig.CustomEntry()));
		}
		config.custom = merged;
		return result.skipped().size();
	}

	@Override
	protected void init() {
		buildWidgets();
	}

	private int rows() {
		// Keep the list clear of the status line and the two button rows at the bottom.
		return Math.max(3, Math.min(MAX_ROWS, (this.height - LIST_TOP - 76) / ROW_HEIGHT));
	}

	/** Wide windows get two columns for the (long) vanilla list. */
	private int columns() {
		return !showCustom && this.width >= 660 ? 2 : 1;
	}

	private int perPage() {
		return rows() * columns();
	}

	private int songCount() {
		return showCustom ? customTracks.size() : vanillaSongs.size();
	}

	private int pageCount() {
		return Math.max(1, (songCount() + perPage() - 1) / perPage());
	}

	private int listWidth() {
		return columns() * LIST_WIDTH + (columns() - 1) * 10;
	}

	private boolean isAllowed(String key) {
		return showCustom ? config.custom.get(key).enabled : !config.disabledVanilla.contains(key);
	}

	private void setAllowed(String key, boolean allowed) {
		if (showCustom) {
			config.custom.get(key).enabled = allowed;
		} else if (allowed) {
			config.disabledVanilla.remove(key);
		} else {
			config.disabledVanilla.add(key);
		}
	}

	private void buildWidgets() {
		this.clearWidgets();
		page = Math.max(0, Math.min(page, pageCount() - 1));

		int left = this.width / 2 - 155;

		// Tabs and the master switch for vanilla music.
		addRenderableWidget(Button.builder(Component.literal(showCustom ? "[Your tracks]" : "Your tracks"), b -> {
			showCustom = true;
			page = 0;
			artistOffset = 0;
			buildWidgets();
		}).bounds(left, 30, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal(showCustom ? "Vanilla tracks" : "[Vanilla tracks]"), b -> {
			showCustom = false;
			page = 0;
			artistOffset = 0;
			buildWidgets();
		}).bounds(left + 105, 30, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Vanilla: " + (config.vanillaEnabled ? "ON" : "OFF")), b -> {
			config.vanillaEnabled = !config.vanillaEnabled;
			buildWidgets();
		}).bounds(left + 210, 30, 100, 20).build());

		buildArtistRow();
		buildSongList();

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
			showCustom = true;
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
		List<ArtistGroup> artists = showCustom ? customArtists : vanillaArtists;
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

	private void buildSongList() {
		int rows = rows();
		int listLeft = this.width / 2 - listWidth() / 2;
		int start = page * perPage();

		for (int i = 0; i < perPage() && start + i < songCount(); i++) {
			int x = listLeft + (i / rows) * (LIST_WIDTH + 10);
			int y = LIST_TOP + (i % rows) * ROW_HEIGHT;
			if (showCustom) {
				addCustomSong(customTracks.get(start + i), x, y);
			} else {
				addVanillaSong(vanillaSongs.get(start + i), x, y);
			}
		}
	}

	private void addVanillaSong(VanillaSong song, int x, int y) {
		boolean allowed = !config.disabledVanilla.contains(song.name());
		addRenderableWidget(Button.builder(Component.literal(label(allowed, song.info(), LIST_WIDTH - 14)), b -> {
			if (!config.disabledVanilla.remove(song.name())) {
				config.disabledVanilla.add(song.name());
			}
			buildWidgets();
		}).bounds(x, y, LIST_WIDTH, 20).build());
	}

	private void addCustomSong(CustomTracks.Found track, int x, int y) {
		MusicConfig.CustomEntry entry = config.custom.get(track.id());

		addRenderableWidget(Button.builder(Component.literal(label(entry.enabled, TrackNames.forCustom(track.name()), 186)), b -> {
			entry.enabled = !entry.enabled;
			buildWidgets();
		}).bounds(x, y, 200, 20).build());

		addRenderableWidget(Button.builder(Component.literal(entry.where().label), b -> {
			entry.preset = entry.where().next().name();
			buildWidgets();
		}).bounds(x + 205, y, 105, 20).build());
	}

	/** "check Title - Artist", with the title shortened if it would not fit in {@code maxPixels}. */
	private String label(boolean allowed, TrackNames.Info info, int maxPixels) {
		String mark = allowed ? GREEN_CHECK : RED_CROSS;
		String suffix = info.hasArtist() ? " " + GRAY + "- " + info.artist() : "";

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
			if (result.usedFallback()) {
				status = "Could not read vanilla music, so only your tracks were added. See the log.";
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

		drawCentered(graphics, this.title.getString(), 12, 0xFFFFFFFF);
		drawCentered(graphics, "Page " + (page + 1) + " / " + pageCount(), this.height - 48, 0xFFAAAAAA);

		if (showCustom && customTracks.isEmpty()) {
			drawCentered(graphics, "No .ogg files found yet.", LIST_TOP + 10, 0xFFFFFFFF);
			drawCentered(graphics, "Press 'Open folder', drop Ogg Vorbis files in, then press Rescan.", LIST_TOP + 24, 0xFFAAAAAA);
			drawCentered(graphics, "Tip: name files like 'Artist - Title.ogg' to sort and label them.", LIST_TOP + 38, 0xFFAAAAAA);
		}
		if (!showCustom && vanillaSongs.isEmpty()) {
			drawCentered(graphics, "Could not read the vanilla music list (see the game log).", LIST_TOP + 10, 0xFFFF5555);
		}

		int statusY = this.height - 70;
		if (!status.isEmpty()) {
			drawCentered(graphics, status, statusY, 0xFFFFFF55);
		} else if (!packActive) {
			drawCentered(graphics, "Enable the 'CustomMusic' pack in Options > Resource Packs.", statusY, 0xFFFF5555);
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
