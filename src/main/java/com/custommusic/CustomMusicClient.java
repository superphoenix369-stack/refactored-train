package com.custommusic;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomMusicClient implements ClientModInitializer {
	public static final String MOD_ID = "custommusic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		prepareMusicFolder();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.custommusic.open",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_M,
			category
		));

		// In a world with no screen open.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new MusicScreen(null));
				}
			}
		});

		// From the title screen and the pause menu, where the tick handler above never sees the key.
		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;

			ScreenKeyboardEvents.beforeKeyPress(screen).register((s, keyEvent) -> {
				if (openKey.matches(keyEvent)) {
					client.gui.setScreen(new MusicScreen(s));
				}
			});
		});
	}

	private static void prepareMusicFolder() {
		Path dir = MusicConfig.musicDir();
		try {
			Files.createDirectories(dir);
			Path readme = dir.resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, String.join(System.lineSeparator(),
					"Put your own music in this folder as .ogg (Ogg Vorbis) files.",
					"",
					"Then, in Minecraft:",
					"  1. Press M (or whatever you bound it to) to open the Custom Music screen.",
					"  2. Press Rescan, choose where each track may play, and press Apply.",
					"  3. First time only: Options > Resource Packs > enable the pack named CustomMusic.",
					"",
					"Convert other formats with ffmpeg, for example:",
					"  ffmpeg -i song.mp3 -c:a libvorbis -q:a 5 song.ogg",
					"(Ogg Opus files will not work, it has to be Vorbis.)",
					""));
			}
		} catch (IOException e) {
			LOGGER.warn("Could not prepare {}", dir, e);
		}
	}
}
