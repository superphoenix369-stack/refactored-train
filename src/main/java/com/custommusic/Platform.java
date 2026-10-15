package com.custommusic;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Helpers that deliberately avoid depending on version-specific game internals. */
public final class Platform {
	private Platform() {}

	/** Opens a folder in the system file manager. Returns false if that failed. */
	public static boolean openFolder(Path dir) {
		try {
			Files.createDirectories(dir);
			String path = dir.toAbsolutePath().toString();
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			ProcessBuilder builder;
			if (os.contains("win")) {
				builder = new ProcessBuilder("explorer.exe", path);
			} else if (os.contains("mac")) {
				builder = new ProcessBuilder("open", path);
			} else {
				builder = new ProcessBuilder("xdg-open", path);
			}
			builder.start();
			return true;
		} catch (Exception e) {
			CustomMusicClient.LOGGER.warn("Could not open {}", dir, e);
			return false;
		}
	}

	/**
	 * Same thing F3+T does. Called through reflection so a renamed method can never break compilation;
	 * if it fails the screen simply tells the player to press F3+T.
	 */
	public static boolean reloadResources() {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			Method reload = Minecraft.class.getMethod("reloadResourcePacks");
			reload.invoke(minecraft);
			return true;
		} catch (Throwable t) {
			CustomMusicClient.LOGGER.info("Could not trigger a resource reload automatically ({}); press F3+T", t.toString());
			return false;
		}
	}

	/** True when the generated resource pack is enabled (its namespace is visible to the resource manager). */
	public static boolean packActive(ResourceManager manager) {
		try {
			return manager.getNamespaces().contains(PackWriter.NAMESPACE);
		} catch (Exception e) {
			return true; // don't nag if we can't tell
		}
	}
}
