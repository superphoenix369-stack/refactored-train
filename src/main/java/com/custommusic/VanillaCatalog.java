package com.custommusic;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Everything vanilla (and other packs) define under "music.*" in sounds.json.
 * <p>
 * The generated pack rewrites these events with {@code "replace": true}, so we need the complete
 * original list. Entries added by our own generated pack are ignored when scanning, which means
 * running this again after the pack is active still finds every vanilla track (even ones we turned off).
 */
public final class VanillaCatalog {
	/** music event key (e.g. "music.game") to its raw sound entries. */
	public final Map<String, List<JsonElement>> events = new TreeMap<>();

	/** Every individually toggleable track as "namespace:path". */
	public final SortedSet<String> tracks = new TreeSet<>();

	public static VanillaCatalog scan(ResourceManager manager) {
		VanillaCatalog catalog = new VanillaCatalog();
		Identifier soundsJson = Identifier.fromNamespaceAndPath("minecraft", "sounds.json");

		List<Resource> stack;
		try {
			stack = manager.getResourceStack(soundsJson);
		} catch (Exception e) {
			CustomMusicClient.LOGGER.warn("Could not read sounds.json", e);
			return catalog;
		}

		Map<String, Set<String>> seen = new HashMap<>();
		for (Resource resource : stack) {
			try (BufferedReader reader = resource.openAsReader()) {
				JsonElement root = JsonParser.parseReader(reader);
				if (!root.isJsonObject()) continue;
				for (Map.Entry<String, JsonElement> event : root.getAsJsonObject().entrySet()) {
					if (!event.getKey().startsWith("music.") || !event.getValue().isJsonObject()) continue;
					JsonElement sounds = event.getValue().getAsJsonObject().get("sounds");
					if (sounds == null || !sounds.isJsonArray()) continue;
					for (JsonElement entry : sounds.getAsJsonArray()) {
						catalog.addEntry(event.getKey(), entry, seen);
					}
				}
			} catch (Exception e) {
				CustomMusicClient.LOGGER.warn("Skipping an unreadable sounds.json", e);
			}
		}
		return catalog;
	}

	private void addEntry(String eventKey, JsonElement entry, Map<String, Set<String>> seen) {
		String name = nameOf(entry);
		if (name == null) return;

		boolean eventReference = isEventReference(entry);
		String normalized = normalize(name);
		if (!eventReference && normalized.startsWith(PackWriter.NAMESPACE + ":")) return; // our own tracks

		String key = (eventReference ? "event:" : "sound:") + normalized;
		if (!seen.computeIfAbsent(eventKey, k -> new HashSet<>()).add(key)) return;

		events.computeIfAbsent(eventKey, k -> new ArrayList<>()).add(entry);
		if (!eventReference) tracks.add(normalized);
	}

	/** sounds.json entries are either a plain string or an object with a "name". */
	static String nameOf(JsonElement entry) {
		if (entry.isJsonPrimitive()) return entry.getAsString();
		if (entry.isJsonObject()) {
			JsonElement name = entry.getAsJsonObject().get("name");
			if (name != null && name.isJsonPrimitive()) return name.getAsString();
		}
		return null;
	}

	/** An entry with "type": "event" points at another sound event instead of a file. */
	static boolean isEventReference(JsonElement entry) {
		if (!entry.isJsonObject()) return false;
		JsonElement type = entry.getAsJsonObject().get("type");
		return type != null && type.isJsonPrimitive() && "event".equals(type.getAsString());
	}

	public static String normalize(String name) {
		return name.contains(":") ? name : "minecraft:" + name;
	}

	/** "minecraft:music/game/calm1" becomes "game/calm1". */
	public static String displayName(String normalized) {
		String s = normalized;
		if (s.startsWith("minecraft:")) s = s.substring("minecraft:".length());
		if (s.startsWith("music/")) s = s.substring("music/".length());
		return s;
	}
}
