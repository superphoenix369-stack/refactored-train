package com.custommusic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Finds the player's own tracks in the custom_music folder. */
public final class CustomTracks {
	private CustomTracks() {}

	/** A usable track: {@code id} is a resource-safe name, {@code file} the original file. */
	public record Found(String id, Path file) {}

	public record Result(List<Found> tracks, List<String> skipped) {}

	public static Result scan(Path dir) {
		List<Found> found = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		if (!Files.isDirectory(dir)) return new Result(found, skipped);

		List<Path> files;
		try (Stream<Path> stream = Files.list(dir)) {
			files = stream
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
				.sorted(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
				.toList();
		} catch (IOException e) {
			CustomMusicClient.LOGGER.warn("Could not list {}", dir, e);
			return new Result(found, skipped);
		}

		Set<String> usedIds = new HashSet<>();
		for (Path file : files) {
			String name = file.getFileName().toString();
			if (!looksLikeOggVorbis(file)) {
				skipped.add(name);
				continue;
			}
			String id = uniqueId(sanitize(name.substring(0, name.length() - ".ogg".length())), usedIds);
			usedIds.add(id);
			found.add(new Found(id, file));
		}
		return new Result(found, skipped);
	}

	/** Resource names may only contain a-z, 0-9, '_' and '-' (plus '.' and '/'), so normalise aggressively. */
	static String sanitize(String baseName) {
		String s = baseName.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9_-]+", "_")
			.replaceAll("_+", "_")
			.replaceAll("^[_-]+|[_-]+$", "");
		return s.isEmpty() ? "track" : s;
	}

	private static String uniqueId(String id, Set<String> used) {
		if (!used.contains(id)) return id;
		int n = 2;
		while (used.contains(id + "_" + n)) n++;
		return id + "_" + n;
	}

	/** Minecraft only plays Ogg *Vorbis*. Catches renamed mp3s and Ogg Opus files before they cause silent failures. */
	private static boolean looksLikeOggVorbis(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] head = in.readNBytes(64);
			if (head.length < 35) return false;
			if (head[0] != 'O' || head[1] != 'g' || head[2] != 'g' || head[3] != 'S') return false;
			return new String(head, StandardCharsets.ISO_8859_1).contains("vorbis");
		} catch (IOException e) {
			return false;
		}
	}
}
