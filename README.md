# Custom Music (Fabric, Minecraft 26.3)

Client-side mod that lets you:

- **Block or allow individual vanilla music tracks**, or switch all vanilla music off.
- **Add your own `.ogg` tracks** and choose where each one plays (overworld, all dimensions, main menu, everywhere, Nether, End, dragon fight).

Your tracks join vanilla's own music rotation, so menu/creative/dimension/biome logic keeps working.

Use

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
