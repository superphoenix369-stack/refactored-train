package com.custommusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two paged lists: your own tracks (toggle + where each plays) and vanilla tracks (toggle).
 * "Apply" writes the resource pack and asks the game to reload resources.
 */
public final class MusicScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int LIST_TOP = 58;
	private static final int MAX_ROWS = 7;

	private final Screen parent;
	private final MusicConfig config = MusicConfig.load();

	private VanillaCatalog catalog = new VanillaCatalog();
	private List<String> vanillaTracks = List.of();
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
		this.vanillaTracks = new ArrayList<>(catalog.tracks);
		this.packActive = Platform.packActive(resources);
		rescanCustom();
	}

	/** Reads the custom_music folder and keeps saved settings for tracks that are still there. */
	private int rescanCustom() {
		CustomTracks.Result result = CustomTracks.scan(MusicConfig.musicDir());
		this.customTracks = result.tracks();

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

	private int perPage() {
		return showCustom ? rows() : rows() * 2;
	}

	private int pageCount() {
		int total = showCustom ? customTracks.size() : vanillaTracks.size();
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
			buildVanillaRows(left);
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

			addRenderableWidget(Button.builder(Component.literal((entry.enabled ? "[x] " : "[ ] ") + shorten(track.id(), 26)), b -> {
				entry.enabled = !entry.enabled;
				buildWidgets();
			}).bounds(left, y, 190, 20).build());

			addRenderableWidget(Button.builder(Component.literal(entry.where().label), b -> {
				entry.preset = entry.where().next().name();
				buildWidgets();
			}).bounds(left + 195, y, 115, 20).build());
		}
	}

	private void buildVanillaRows(int left) {
		int rows = rows();
		int start = page * perPage();
		for (int i = 0; i < perPage() && start + i < vanillaTracks.size(); i++) {
			String name = vanillaTracks.get(start + i);
			boolean off = config.disabledVanilla.contains(name);
			int column = i / rows;
			int row = i % rows;

			addRenderableWidget(Button.builder(Component.literal((off ? "[ ] " : "[x] ") + shorten(VanillaCatalog.displayName(name), 20)), b -> {
				if (!config.disabledVanilla.remove(name)) {
					config.disabledVanilla.add(name);
				}
				buildWidgets();
			}).bounds(left + column * 160, LIST_TOP + row * ROW_HEIGHT, 150, 20).build());
		}
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
		}
		if (!showCustom && vanillaTracks.isEmpty()) {
			drawCentered(graphics, "Could not read the vanilla music list (see the game log).", LIST_TOP + 10, 0xFFFF5555);
		}

		int statusY = this.height - 70;
		if (!status.isEmpty()) {
			drawCentered(graphics, status, statusY, 0xFFFFFF55);
		} else if (!packActive) {
			drawCentered(graphics, "Enable the 'CustomMusic' pack: Options > Resource Packs (after pressing Apply).", statusY, 0xFFFF5555);
		} else {
			drawCentered(graphics, "Click a track to allow or block it. Apply saves and reloads.", statusY, 0xFFAAAAAA);
		}
	}

	private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int color) {
		graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, color, true);
	}

	private static String shorten(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max - 3) + "...";
	}

	@Override
	public void onClose() {
		config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
