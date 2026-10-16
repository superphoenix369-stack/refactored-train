package com.custommusic;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

import java.lang.reflect.Method;
import java.util.Collection;
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

	/**
	 * Makes sure the generated pack is selected, so the player never has to touch the Resource Packs screen.
	 * Equivalent to moving it to the "selected" side there, then saving options.txt. Everything goes through
	 * reflection so a renamed method can never break compilation; on any failure this returns false and
	 * the screen tells the player to enable the pack by hand.
	 */
	@SuppressWarnings("unchecked")
	public static boolean enablePack(String packId) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			Object repository = Minecraft.class.getMethod("getResourcePackRepository").invoke(minecraft);
			Class<?> repositoryClass = repository.getClass();

			repositoryClass.getMethod("reload").invoke(repository); // pick up the pack we just wrote
			Collection<String> selected = (Collection<String>) repositoryClass.getMethod("getSelectedIds").invoke(repository);
			if (selected.contains(packId)) return true;

			Object added = repositoryClass.getMethod("addPack", String.class).invoke(repository, packId);
			if (!Boolean.TRUE.equals(added)) {
				CustomMusicClient.LOGGER.info("The game did not accept pack {}", packId);
				return false;
			}
			Object options = Minecraft.class.getField("options").get(minecraft);
			options.getClass().getMethod("updateResourcePacks", repositoryClass).invoke(options, repository);
			return true;
		} catch (Throwable t) {
			CustomMusicClient.LOGGER.info("Could not enable the pack automatically ({})", t.toString());
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
