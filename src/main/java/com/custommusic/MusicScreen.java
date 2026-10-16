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
 * Two paged lists, both sorted by artist, with a clickable header above each artist's songs:
 * your own tracks (toggle + where each plays) and vanilla tracks (toggle).
 * A green check means the song may play, a red cross means it is blocked.
 * "Apply" writes the resource pack and asks the game to reload resources.
 */
public final class MusicScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int LIST_TOP = 58;
	private static final int MAX_ROWS = 7;
	private static final int LIST_WIDTH = 310;

	// Legacy formatting codes are written as escapes so the source file never depends on its encoding.
	private static final String GREEN_CHECK = "\u00a7a\u2714\u00a7r ";
	private static final String RED_CROSS = "\u00a7c\u2718\u00a7r ";
	private static final String YELLOW_MIXED = "\u00a7e~\u00a7r ";
	private static final String GRAY = "\u00a77";
	private static final String BOLD = "\u00a7l";
	private static final String RESET = "\u00a7r";

	/** One line in a list: an artist header or a song. */
	private interface Entry {}

	/** Clicking it allows (or, if everything is already allowed, blocks) every song by this artist. */
	private record Header(String artist, List<String> keys, boolean custom) implements Entry {}

	private record VanillaSong(String name, TrackNames.Info info) implements Entry {}

	private record CustomSong(CustomTracks.Found track) implements Entry {}

	/** Sorted item before headers are added. {@code key} is the vanilla track name or the custom track id. */
	private record Item(String key, TrackNames.Info info, Entry entry) {}

	private final Screen parent;
	private final MusicConfig config = MusicConfig.load();

	private VanillaCatalog catalog = new VanillaCatalog();
	private List<Entry> vanillaEntries = List.of();
	private List<Entry> customEntries = List.of();
	private List<CustomTracks.Found> customTracks = List.of();
	private boolean packActive = true;

	private boolean showCustom = true;
	private int page = 0;
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

		List<Item> items = new ArrayList<>();
		for (String name : catalog.tracks) {
			TrackNames.Info info = TrackNames.forVanilla(name);
			items.add(new Item(name, info, new VanillaSong(name, info)));
		}
		items.sort(Comparator.comparing(Item::info, TrackNames.ORDER).thenComparing(Item::key));
		this.vanillaEntries = withHeaders(items, false);

		rescanCustom();
	}

	/** Reads the custom_music folder and keeps saved settings for tracks that are still there. */
	private int rescanCustom() {
		CustomTracks.Result result = CustomTracks.scan(MusicConfig.musicDir());
		this.customTracks = result.tracks();

		Map<String, MusicConfig.CustomEntry> merged = new LinkedHashMap<>();
		List<Item> items = new ArrayList<>();
		for (CustomTracks.Found track : customTracks) {
			merged.put(track.id(), config.custom.getOrDefault(track.id(), new MusicConfig.CustomEntry()));
			items.add(new Item(track.id(), TrackNames.forCustom(track.name()), new CustomSong(track)));
		}
		config.custom = merged;

		items.sort(Comparator.comparing(Item::info, TrackNames.ORDER).thenComparing(Item::key));
		this.customEntries = withHeaders(items, true);
		return result.skipped().size();
	}

	/** Puts an artist header in front of each run of songs by the same artist. Items must already be sorted. */
	private static List<Entry> withHeaders(List<Item> items, boolean custom) {
		List<Entry> out = new ArrayList<>();
		int i = 0;
		while (i < items.size()) {
			String artist = items.get(i).info().artist();
			List<String> keys = new ArrayList<>();
			int j = i;
			while (j < items.size() && items.get(j).info().artist().equalsIgnoreCase(artist)) {
				keys.add(items.get(j).key());
				j++;
			}
			out.add(new Header(artist, keys, custom));
			for (int k = i; k < j; k++) {
				out.add(items.get(k).entry());
			}
			i = j;
		}
		return out;
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

	private List<List<ListLayout.Placed<Entry>>> pages() {
		List<Entry> entries = showCustom ? customEntries : vanillaEntries;
		return ListLayout.pages(entries, e -> e instanceof Header, rows(), rows() * columns());
	}

	private boolean isAllowed(boolean custom, String key) {
		return custom ? config.custom.get(key).enabled : !config.disabledVanilla.contains(key);
	}

	private void setAllowed(boolean custom, String key, boolean allowed) {
		if (custom) {
			config.custom.get(key).enabled = allowed;
		} else if (allowed) {
			config.disabledVanilla.remove(key);
		} else {
			config.disabledVanilla.add(key);
		}
	}

	private void buildWidgets() {
		this.clearWidgets();
		List<List<ListLayout.Placed<Entry>>> pages = pages();
		page = Math.max(0, Math.min(page, pages.size() - 1));

		int left = this.width / 2 - 155;

		// Tabs and the master switch for vanilla music.
		addRenderableWidget(Button.builder(Component.literal(showCustom ? "[Your tracks]" : "Your tracks"), b -> {
			showCustom = true;
			page = 0;
			buildWidgets();
		}).bounds(left, 30, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal(showCustom ? "Vanilla tracks" : "[Vanilla tracks]"), b -> {
			showCustom = false;
			page = 0;
			buildWidgets();
		}).bounds(left + 105, 30, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Vanilla: " + (config.vanillaEnabled ? "ON" : "OFF")), b -> {
			config.vanillaEnabled = !config.vanillaEnabled;
			buildWidgets();
		}).bounds(left + 210, 30, 100, 20).build());

		buildList(pages.get(page));

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
		next.active = page < pages.size() - 1;
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
			buildWidgets();
		}).bounds(left + 78, actionY, 76, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
			.bounds(left + 156, actionY, 76, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(left + 234, actionY, 76, 20).build());
	}

	private void buildList(List<ListLayout.Placed<Entry>> placed) {
		int rows = rows();
		int columns = columns();
		int totalWidth = columns * LIST_WIDTH + (columns - 1) * 10;
		int listLeft = this.width / 2 - totalWidth / 2;

		for (ListLayout.Placed<Entry> p : placed) {
			int x = listLeft + (p.slot() / rows) * (LIST_WIDTH + 10);
			int y = LIST_TOP + (p.slot() % rows) * ROW_HEIGHT;

			if (p.item() instanceof Header header) {
				addHeader(header, x, y);
			} else if (p.item() instanceof VanillaSong song) {
				addVanillaSong(song, x, y);
			} else if (p.item() instanceof CustomSong song) {
				addCustomSong(song, x, y);
			}
		}
	}

	private void addHeader(Header header, int x, int y) {
		int allowed = 0;
		for (String key : header.keys()) {
			if (isAllowed(header.custom(), key)) allowed++;
		}
		int total = header.keys().size();
		String mark = allowed == total ? GREEN_CHECK : (allowed == 0 ? RED_CROSS : YELLOW_MIXED);
		String text = mark + BOLD + header.artist() + RESET + " " + GRAY + "(" + allowed + "/" + total + " allowed)";

		addRenderableWidget(Button.builder(Component.literal(text), b -> {
			boolean everythingAllowed = true;
			for (String key : header.keys()) {
				if (!isAllowed(header.custom(), key)) everythingAllowed = false;
			}
			// Mixed or blocked: allow all of this artist's songs. Fully allowed: block them all.
			for (String key : header.keys()) {
				setAllowed(header.custom(), key, !everythingAllowed);
			}
			buildWidgets();
		}).bounds(x, y, LIST_WIDTH, 20).build());
	}

	private void addVanillaSong(VanillaSong song, int x, int y) {
		boolean allowed = isAllowed(false, song.name());
		addRenderableWidget(Button.builder(Component.literal(label(allowed, song.info(), LIST_WIDTH - 14)), b -> {
			setAllowed(false, song.name(), !isAllowed(false, song.name()));
			buildWidgets();
		}).bounds(x, y, LIST_WIDTH, 20).build());
	}

	private void addCustomSong(CustomSong song, int x, int y) {
		MusicConfig.CustomEntry entry = config.custom.get(song.track().id());

		addRenderableWidget(Button.builder(Component.literal(label(entry.enabled, TrackNames.forCustom(song.track().name()), 186)), b -> {
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
		drawCentered(graphics, "Page " + (page + 1) + " / " + pages().size(), this.height - 48, 0xFFAAAAAA);

		if (showCustom && customTracks.isEmpty()) {
			drawCentered(graphics, "No .ogg files found yet.", LIST_TOP + 10, 0xFFFFFFFF);
			drawCentered(graphics, "Press 'Open folder', drop Ogg Vorbis files in, then press Rescan.", LIST_TOP + 24, 0xFFAAAAAA);
			drawCentered(graphics, "Tip: name files like 'Artist - Title.ogg' to sort and label them.", LIST_TOP + 38, 0xFFAAAAAA);
		}
		if (!showCustom && vanillaEntries.isEmpty()) {
			drawCentered(graphics, "Could not read the vanilla music list (see the game log).", LIST_TOP + 10, 0xFFFF5555);
		}

		int statusY = this.height - 70;
		if (!status.isEmpty()) {
			drawCentered(graphics, status, statusY, 0xFFFFFF55);
		} else if (!packActive) {
			drawCentered(graphics, "Enable the 'CustomMusic' pack in Options > Resource Packs.", statusY, 0xFFFF5555);
		} else {
			drawCentered(graphics, "Click a song or an artist to allow (\u2714) or block (\u2718). Apply saves.", statusY, 0xFFAAAAAA);
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
