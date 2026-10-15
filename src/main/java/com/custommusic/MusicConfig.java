package com.custommusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** User settings, saved to config/custommusic.json. */
public final class MusicConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch: when false, no vanilla music plays at all. */
	public boolean vanillaEnabled = true;

	/** Vanilla tracks the player turned off, as "namespace:path" (e.g. "minecraft:music/game/calm1"). */
	public Set<String> disabledVanilla = new LinkedHashSet<>();

	/** Settings for each custom track, keyed by track id (the sanitized file name). */
	public Map<String, CustomEntry> custom = new LinkedHashMap<>();

	public static final class CustomEntry {
		public boolean enabled = true;
		/** Name of a {@link Preset}; stored as a string so the JSON stays readable and forgiving. */
		public String preset = Preset.ALL_DIMENSIONS.name();

		public Preset where() {
			return Preset.parse(preset, Preset.ALL_DIMENSIONS);
		}
	}

	/** Folder the player drops .ogg files into. */
	public static Path musicDir() {
		return FabricLoader.getInstance().getGameDir().resolve("custom_music");
	}

	/** The generated resource pack folder (inside the normal resourcepacks folder). */
	public static Path packDir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve("CustomMusic");
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("custommusic.json");
	}

	public static MusicConfig load() {
		Path path = file();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				MusicConfig loaded = GSON.fromJson(reader, MusicConfig.class);
				if (loaded != null) {
					loaded.repairNulls();
					return loaded;
				}
			} catch (Exception e) {
				CustomMusicClient.LOGGER.warn("Could not read {}; starting with defaults", path, e);
			}
		}
		return new MusicConfig();
	}

	public void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			CustomMusicClient.LOGGER.warn("Could not save {}", path, e);
		}
	}

	private void repairNulls() {
		if (disabledVanilla == null) disabledVanilla = new LinkedHashSet<>();
		if (custom == null) custom = new LinkedHashMap<>();
		custom.replaceAll((id, entry) -> entry == null ? new CustomEntry() : entry);
	}
}
