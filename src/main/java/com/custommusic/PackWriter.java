package com.custommusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates a normal folder resource pack at resourcepacks/CustomMusic.
 * <ul>
 *   <li>assets/custommusic_user/sounds/*.ogg: copies of the enabled custom tracks</li>
 *   <li>assets/minecraft/sounds.json: each vanilla music event rewritten with "replace": true
 *       so that it contains exactly the allowed vanilla tracks plus the custom tracks assigned to it</li>
 * </ul>
 * Because this is plain vanilla resource pack machinery, no mixins or engine internals are needed.
 */
public final class PackWriter {
	/** Namespace used for the copied tracks. Deliberately different from the mod id so the pack can be detected. */
	public static final String NAMESPACE = "custommusic_user";

	/** How the game identifies a folder pack inside resourcepacks/. */
	public static final String PACK_ID = "file/" + MusicConfig.PACK_FOLDER;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Only used if vanilla's sounds.json could not be read: custom tracks are added, vanilla is untouched. */
	private static final String[] FALLBACK_EVENTS = {
		"music.game", "music.creative", "music.menu", "music.under_water", "music.end", "music.dragon",
		"music.nether.nether_wastes", "music.nether.crimson_forest", "music.nether.soul_sand_valley",
		"music.nether.basalt_deltas", "music.nether.warped_forest"
	};

	public record Result(int tracksCopied, int eventsWritten, boolean usedFallback) {}

	private PackWriter() {}

	public static Result write(MusicConfig config, VanillaCatalog catalog, List<CustomTracks.Found> tracks) throws IOException {
		Path pack = MusicConfig.packDir();
		Path soundsDir = pack.resolve("assets").resolve(NAMESPACE).resolve("sounds");
		Path minecraftDir = pack.resolve("assets").resolve("minecraft");
		Files.createDirectories(soundsDir);
		Files.createDirectories(minecraftDir);

		writePackMeta(pack);
		clearOldTracks(soundsDir);

		Map<String, CustomTracks.Found> byId = new HashMap<>();
		for (CustomTracks.Found track : tracks) {
			byId.put(track.id(), track);
		}

		// A track is copied if it is in the music rotation, or if it replaces a disc.
		Set<String> usedByDiscs = new HashSet<>();
		for (Map.Entry<String, String> disc : config.discs.entrySet()) {
			if (catalog.discs.containsKey(disc.getKey()) && byId.containsKey(disc.getValue())) {
				usedByDiscs.add(disc.getValue());
			}
		}

		List<CustomTracks.Found> active = new ArrayList<>(); // plays in the normal rotation
		List<CustomTracks.Found> copied = new ArrayList<>(); // has its file in the pack
		for (CustomTracks.Found track : tracks) {
			MusicConfig.CustomEntry entry = config.custom.get(track.id());
			boolean inRotation = entry != null && entry.enabled;
			if (!inRotation && !usedByDiscs.contains(track.id())) continue;
			Files.copy(track.file(), soundsDir.resolve(track.id() + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
			copied.add(track);
			if (inRotation) active.add(track);
		}

		JsonObject root = new JsonObject();
		boolean fallback = catalog.events.isEmpty();
		if (!fallback) {
			for (Map.Entry<String, List<JsonElement>> event : catalog.events.entrySet()) {
				root.add(event.getKey(), buildEvent(event.getKey(), event.getValue(), true, config, active));
			}
		} else {
			for (String key : FALLBACK_EVENTS) {
				JsonObject event = buildEvent(key, List.of(), false, config, active);
				if (event.getAsJsonArray("sounds").size() > 0) root.add(key, event);
			}
		}

		for (Map.Entry<String, JsonObject> disc : catalog.discs.entrySet()) {
			String trackId = config.discs.get(disc.getKey());
			if (trackId != null && byId.containsKey(trackId)) {
				root.add(disc.getKey(), buildDisc(disc.getValue(), trackId, config.customVolume));
			}
		}

		Files.writeString(minecraftDir.resolve("sounds.json"), GSON.toJson(root), StandardCharsets.UTF_8);
		writeLang(pack, copied);
		return new Result(copied.size(), root.size(), fallback);
	}

	private static JsonObject buildEvent(String eventKey, List<JsonElement> vanillaEntries, boolean replace,
										 MusicConfig config, List<CustomTracks.Found> active) {
		JsonArray sounds = new JsonArray();

		if (replace && config.vanillaEnabled) {
			for (JsonElement raw : vanillaEntries) {
				if (VanillaCatalog.isEventReference(raw)) {
					sounds.add(raw); // keep references to other events untouched
					continue;
				}
				String name = VanillaCatalog.normalize(VanillaCatalog.nameOf(raw));
				if (config.disabledVanilla.contains(name)) continue;
				sounds.add(soundObject(name));
			}
		}

		Preset.Category category = Preset.Category.of(eventKey);
		for (CustomTracks.Found track : active) {
			MusicConfig.CustomEntry entry = config.custom.get(track.id());
			if (entry.where().categories.contains(category)) {
				sounds.add(customSoundObject(track.id(), config.customVolume));
			}
		}

		JsonObject event = new JsonObject();
		event.addProperty("replace", replace);
		event.add("sounds", sounds);
		return event;
	}

	/** The disc's original event (keeping extras such as "subtitle") with only its audio swapped. */
	private static JsonObject buildDisc(JsonObject original, String trackId, int volumePercent) {
		JsonObject event = new JsonObject();
		for (Map.Entry<String, JsonElement> property : original.entrySet()) {
			if (!property.getKey().equals("sounds") && !property.getKey().equals("replace")) {
				event.add(property.getKey(), property.getValue());
			}
		}
		JsonArray sounds = new JsonArray();
		sounds.add(customSoundObject(trackId, volumePercent));
		event.addProperty("replace", true);
		event.add("sounds", sounds);
		return event;
	}

	/**
	 * One of the player's tracks, with the custom volume applied. 100% writes no "volume" at all (the default).
	 * The game rejects a volume of 0 and may then discard the whole sounds.json, so 0% becomes a tiny positive value.
	 */
	private static JsonObject customSoundObject(String trackId, int volumePercent) {
		JsonObject sound = soundObject(NAMESPACE + ":" + trackId);
		if (volumePercent < 100) {
			sound.addProperty("volume", Math.max(0.001, volumePercent / 100.0));
		}
		return sound;
	}

	private static JsonObject soundObject(String name) {
		JsonObject sound = new JsonObject();
		sound.addProperty("name", name);
		sound.addProperty("stream", true); // music is streamed from disk instead of fully loaded into memory
		return sound;
	}

	/**
	 * Gives the "Now playing" toast a proper name for custom tracks. The game looks the name up under the
	 * sound's resource id with separators turned into dots, e.g. custommusic_user.my_song.
	 */
	private static void writeLang(Path pack, List<CustomTracks.Found> active) throws IOException {
		Path langDir = pack.resolve("assets").resolve(NAMESPACE).resolve("lang");
		Files.createDirectories(langDir);
		JsonObject lang = new JsonObject();
		for (CustomTracks.Found track : active) {
			lang.addProperty(NAMESPACE + "." + track.id(), TrackNames.forCustom(track.name()).title());
		}
		Files.writeString(langDir.resolve("en_us.json"), GSON.toJson(lang), StandardCharsets.UTF_8);
	}

	private static void writePackMeta(Path pack) throws IOException {
		// min_format/max_format is the pack.mcmeta scheme used since 1.21.9. The wide range keeps the
		// pack from being flagged as "made for another version" after game updates.
		JsonObject inner = new JsonObject();
		inner.addProperty("description", "Generated by the Custom Music mod. Edit it from the in-game screen.");
		inner.addProperty("min_format", 75);
		inner.addProperty("max_format", 9999);
		JsonObject meta = new JsonObject();
		meta.add("pack", inner);
		Files.writeString(pack.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);
	}

	private static void clearOldTracks(Path soundsDir) throws IOException {
		try (Stream<Path> old = Files.list(soundsDir)) {
			for (Path file : old.toList()) {
				if (Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
					Files.delete(file);
				}
			}
		}
	}
}
