switfix # Splitscreen

Console-style local splitscreen for Minecraft, inside a **single game instance**. One window, one
process, up to four players, each with their own view, HUD, inventory screens and controller.

- Minecraft **26.1.2**, **NeoForge** 26.1.2.x, Java 25, client-side only
- The 1.21.1 version lives on the `master` branch
- **Requires [Legacy4J](https://modrinth.com/mod/legacy4j)** (1.9+, which in turn needs Factory API)

> **Status: experimental.** Joining, rendering up to four views, per-player inventories and pause menus
> are verified in a real client with scripted test players. Real controller input has not yet been
> play-tested. Expect rough edges and please report them.

## Usage

1. Open a **singleplayer** world (the integrated server is what extra players join).
2. Plug in a controller and press **Start** on it. It joins as the next player.
   - or run `/splitscreen join` (uses the next free controller).
   - **No controller?** Press **F9** (rebindable) or run `/splitscreen join dummy` to add a scripted
     test player that walks, looks around, jumps and swings by itself. **F10** removes the last player.
3. Hold **Back** for about two seconds to leave, or run `/splitscreen leave`.

The main player keeps using keyboard and mouse (or Legacy4J's controller, see below). Extra players use a controller.

### Layouts

| Players | Layout |
| --- | --- |
| 2 | top / bottom (or side by side, `/splitscreen layout vertical`) |
| 3 | one wide view on top, two below |
| 4 | quadrants |

## Legacy4J

Legacy4J is a required dependency.

Legacy4J drives the main player from one selected controller. This mod asks Legacy4J which controller
that is (via reflection, since its class names change between releases) and never offers it to a secondary
player. Every other controller is free to join. Legacy4J styles the HUD through Factory API
"UI definitions" stored on each `Gui`, so this mod copies the main player's definitions onto every extra player's
`Gui`. Each view is scaled so it keeps at least `minGuiWidth` x `minGuiHeight` GUI units (default 400x225);
raise those for a smaller, roomier UI or lower them for a bigger one.

If Legacy4J uses its SDL controller backend, its controller index is assumed to match the GLFW joystick
index. If that is wrong for your setup, use `/splitscreen join` after unplugging the extra controllers
first, or report it.

## How it works

Minecraft's client is a singleton with one level, one player and one renderer. Rather than running
several clients, each local player gets a `PlayerContext` holding their own:

- in-memory connection to the integrated server (so they are a real server-side player),
- `ClientLevel`, `LocalPlayer`, `MultiPlayerGameMode`, `LevelRenderer`, `GameRenderer`, `Gui`, screen and particles,
- off-screen render target sized to their viewport.

`SplitscreenManager` swaps one context at a time into the `Minecraft` fields while that player is
ticked, rendered, or handling a network packet. While scoped, the `Window` reports the player's
viewport size, so projection, GUI scaling and screens just work. After all views are rendered they
are blitted into the real window. With one player nothing is swapped and the game is vanilla.

Mixins live in `org.kylepitcock.splitscreen.mixin.client`; the logic is in `...splitscreen.client`.

## Known limitations

- Singleplayer / integrated server only (extra players are local).
- Sound is positioned around the main player's camera.
- Toasts and the loading overlay appear in every view.
- Chat and text input are main-player only (no on-screen keyboard).
- Each extra player has their own chunk renderer, so memory use and load grow with player count.
  Lower render distance if frame rate suffers.
- Only the main player's pause menu pauses the game; other players' pause menus are per-player.

## Config

`config/splitscreen-client.toml`: `maxPlayers`, `verticalSplit`, `joinWithStart`, `lookSpeed`,
`deadzone`, `invertLookY`, `minGuiWidth`, `minGuiHeight`.

## Testing without controllers

```
./gradlew runClient -Pautotest=4   # creates a fresh world in run-autotest/ and adds 3 scripted players
```

Positions of every player are logged every 5 seconds (`[autotest]` lines), a screenshot of the composed
window is saved to `run-autotest/screenshots/autotest.png`, then the game exits.

## Building

```
./gradlew build      # jar in build/libs
./gradlew runClient  # dev client
```

Requires JDK 25 for compiling (Gradle's toolchain download fetches it). Launch Gradle itself with JDK 21 or newer.
