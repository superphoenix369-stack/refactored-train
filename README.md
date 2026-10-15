# Custom Music (Fabric, Minecraft 26.3)

Client-side mod that lets you:

- **Block or allow individual vanilla music tracks**, or switch all vanilla music off.
- **Add your own `.ogg` tracks** and choose where each one plays (overworld, all dimensions, main menu, everywhere, Nether, End, dragon fight).

Your tracks join vanilla's own music rotation, so menu/creative/dimension/biome logic keeps working.

## Build the jar on GitHub (no local setup)

1. Create a new repository on github.com and upload the **contents** of this folder
   (so `build.gradle`, `src/` and `.github/` are at the top level of the repo).
   Make sure the hidden `.github` folder is included.
2. Open the repo's **Actions** tab. The build starts automatically on every push to `main`
   (or press **Run workflow** under "Build mod jar").
3. When the run finishes (a few minutes), open it and download **custommusic-jar** from the
   *Artifacts* section at the bottom. Unzip it to get `custommusic-1.0.0.jar`.
4. Optional: push a tag such as `v1.0.0` and the jar is also attached to a GitHub **Release**.

If the run fails, open it and click the red step. The error text is what to send me.
The workflow installs Java 25 and the latest Gradle itself, so no Gradle wrapper is needed.

## Build locally

Requirements: **JDK 25**, internet access for Gradle.

```
gradle wrapper          # once, only if there is no ./gradlew yet (needs Gradle 9.x installed)
./gradlew build         # Windows: gradlew.bat build
```

The jar ends up in `build/libs/custommusic-1.0.0.jar`.

No Gradle wrapper is bundled. Easiest alternative: generate a fresh project at
<https://fabricmc.net/develop/template/> (Minecraft 26.3), then copy this project's `src/` folder
over the generated `src/` and copy the values from `gradle.properties` if the template differs.

## Install

Put the jar in `.minecraft/mods/` with **Fabric Loader 0.19.5+** and **Fabric API for 26.3**
(Java 25).

## Use

1. Launch the game and press **M** (rebindable under Controls > Custom Music). It also works on the
   title screen and the pause menu.
2. Press **Open folder** and drop `.ogg` files into `custom_music/` (inside your game folder).
   Files must be **Ogg Vorbis**. Convert anything else with
   `ffmpeg -i song.mp3 -c:a libvorbis -q:a 5 song.ogg`. Ogg Opus will not play.
3. Press **Rescan**. Click a track to enable/disable it; click the button on its right to cycle where it plays.
4. Open the **Vanilla tracks** tab to block individual vanilla songs, or use **Vanilla: ON/OFF** for all of them.
5. Press **Apply**. The mod writes a resource pack and reloads resources.
6. **First time only:** Options > Resource Packs > enable the pack called **CustomMusic**.
   (The screen shows a red hint until the pack is active.)

Settings live in `config/custommusic.json`.

## How it works

No mixins. Apply generates an ordinary folder resource pack at `resourcepacks/CustomMusic/`:

- `assets/custommusic_user/sounds/*.ogg`: copies of your enabled tracks
- `assets/minecraft/sounds.json`: each vanilla `music.*` event rewritten with `"replace": true`, listing
  the vanilla tracks you allow plus the custom tracks assigned to that event

Because it relies only on standard resource pack behaviour, it needs almost no Minecraft internals.

## Notes and limitations

- Changes take effect after Apply (which triggers the same reload as F3+T). If the automatic reload
  isn't possible, the screen tells you to press F3+T.
- If every track for a situation is blocked, that situation is silent. The game may log a warning
  about an empty sound event each time it tries to start music.
- Custom tracks assigned to "Overworld" are added to every `music.overworld.*` biome event too, so
  they can appear in biomes that normally have their own small playlists.
- Vanilla's "Now playing" toast may show an unpolished name for custom tracks.
- Only vanilla (`minecraft:`) tracks are listed for toggling, not tracks added by other mods or packs.
- This was written against the Minecraft 26.2/26.3 API surface (Mojang names, `GuiGraphicsExtractor`,
  `KeyMappingHelper`, `Identifier`) but could not be compiled against the real game in the environment it was
  written in. See the project notes below for the lines most likely to need a tweak.

## If the build fails

Most likely spots, in order:

1. `MusicScreen.java`: the `GuiGraphicsExtractor` import (expected `net.minecraft.client.gui.GuiGraphicsExtractor`).
2. `CustomMusicClient.java`: `ScreenEvents` / `ScreenKeyboardEvents` imports (expected `net.fabricmc.fabric.api.client.screen.v1`).
3. `VanillaCatalog.java` / `Platform.java`: `getResourceStack`, `openAsReader`, `getNamespaces` on `ResourceManager`/`Resource`.
4. `gradle.properties`: `loom_version` and `fabric_api_version`. Copy the current values from the template generator.

Each is a one-line fix; the logic around them does not depend on version details.
