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
 * Two paged lists, both sorted by artist: your own tracks (toggle + where each plays) and vanilla
 * tracks (toggle). A green check means the song may play, a red cross means it is blocked.
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
	private static final String GRAY = "\u00a77";

	private record VanillaRow(String name, TrackNames.Info info) {}

	private final Screen parent;
	private final MusicConfig config = MusicConfig.load();

	private VanillaCatalog catalog = new VanillaCatalog();
	private List<VanillaRow> vanillaRows = List.of();
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

		List<VanillaRow> rows = new ArrayList<>();
		for (String name : catalog.tracks) {
			rows.add(new VanillaRow(name, TrackNames.forVanilla(name)));
		}
		rows.sort(Comparator.comparing(VanillaRow::info, TrackNames.ORDER).thenComparing(VanillaRow::name));
		this.vanillaRows = rows;

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

	private int pageCount() {
		int total = showCustom ? customTracks.size() : vanillaRows.size();
		return Math.max(1, (total + perPage() - 1) / perPage());
	}

	private void buildWidgets() {
		this.clearWidgets();
		page = Math.max(0, Math.min(page, pageCount() - 1));

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

		if (showCustom) {
			buildCustomRows(left);
		} else {
			buildVanillaRows();
		}

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
			buildWidgets();
		}).bounds(left + 78, actionY, 76, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
			.bounds(left + 156, actionY, 76, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(left + 234, actionY, 76, 20).build());
	}

	private void buildCustomRows(int left) {
		int start = page * perPage();
		for (int i = 0; i < perPage() && start + i < customTracks.size(); i++) {
			CustomTracks.Found track = customTracks.get(start + i);
			MusicConfig.CustomEntry entry = config.custom.get(track.id());
			int y = LIST_TOP + i * ROW_HEIGHT;

			addRenderableWidget(Button.builder(Component.literal(label(entry.enabled, TrackNames.forCustom(track.name()), 186)), b -> {
				entry.enabled = !entry.enabled;
				buildWidgets();
			}).bounds(left, y, 200, 20).build());

			addRenderableWidget(Button.builder(Component.literal(entry.where().label), b -> {
				entry.preset = entry.where().next().name();
				buildWidgets();
			}).bounds(left + 205, y, 105, 20).build());
		}
	}

	private void buildVanillaRows() {
		int rows = rows();
		int columns = columns();
		int totalWidth = columns * LIST_WIDTH + (columns - 1) * 10;
		int listLeft = this.width / 2 - totalWidth / 2;

		int start = page * perPage();
		for (int i = 0; i < perPage() && start + i < vanillaRows.size(); i++) {
			VanillaRow row = vanillaRows.get(start + i);
			boolean allowed = !config.disabledVanilla.contains(row.name());
			int column = i / rows;
			int line = i % rows;

			addRenderableWidget(Button.builder(Component.literal(label(allowed, row.info(), LIST_WIDTH - 14)), b -> {
				if (!config.disabledVanilla.remove(row.name())) {
					config.disabledVanilla.add(row.name());
				}
				buildWidgets();
			}).bounds(listLeft + column * (LIST_WIDTH + 10), LIST_TOP + line * ROW_HEIGHT, LIST_WIDTH, 20).build());
		}
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
		if (!showCustom && vanillaRows.isEmpty()) {
			drawCentered(graphics, "Could not read the vanilla music list (see the game log).", LIST_TOP + 10, 0xFFFF5555);
		}

		int statusY = this.height - 70;
		if (!status.isEmpty()) {
			drawCentered(graphics, status, statusY, 0xFFFFFF55);
		} else if (!packActive) {
			drawCentered(graphics, "Enable the 'CustomMusic' pack: Options > Resource Packs (after pressing Apply).", statusY, 0xFFFF5555);
		} else {
			drawCentered(graphics, "Sorted by artist. Click a song to allow (\u2714) or block (\u2718) it. Apply saves.", statusY, 0xFFAAAAAA);
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
