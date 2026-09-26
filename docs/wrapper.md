# Single-game wrapper

A build of GameNative that runs exactly one game with a known-good, locked-down setup.
The wrapper code is generic; everything game-specific lives in a **preset** under
`app/src/wrapper/assets/wrapper/` (build type `wrapper`).

## Preset (`preset.json`)
```jsonc
{
  "id": "maplelegends",                 // used for the custom-game folder name
  "name": "MapleLegends",
  "install": {
    "type": "wineskin-pkg",             // installer: "folder" | "wineskin-pkg" (xar -> gzip cpio -> drive_c/<gameDir>)
    "gameDir": "MapleLegends",          // folder inside drive_c (wineskin-pkg) to keep
    "exe": "MapleLegends.exe"           // relative to the game folder
  },
  "components": ["fexcore-2609-maple1.wcp"],   // .wcp files bundled in the preset, installed on first run
  "container": {                        // keys understood by ContainerUtils.applyBestConfigMapToContainerData
    "emulator": "...", "fexcoreVersion": "2609-maple1", "wineVersion": "...", "dxwrapper": "...",
    "envVars": "...", "graphicsDriver": "..."
  },
  "screenSize": "1366x768",
  "registry": [ { "key": "Software\\Wine", "name": "Version", "value": "win98" } ],   // written to the prefix's user.reg
  "controlsProfile": "controls.icp"     // default on-screen layout, imported on first run
}
```

## Flow
1. First start (no login screen): `WrapperHomeScreen` shows the game name and status.
2. **Import game**: user picks the downloaded package (or folder); the preset's installer
   unpacks it into the app's game folder, which is registered as a custom game
   (`PrefManager.customGameManualFolders`, `CustomGameScanner`) -> appId `CUSTOM_GAME_<n>`.
3. **Setup** (idempotent): install bundled components (ContentsManager), create the container
   (`ContainerUtils.getOrCreateContainer`, maps A: to the game folder), apply
   `preset.container` via `applyBestConfigMapToContainerData` + `applyToContainer`, set
   screen size and executable, write registry values, import the controls profile.
4. **Play**: same path as `CustomGameAppScreen` Play (`preLaunchApp` -> XServer screen).
5. Settings the user may change are limited (controls, maybe resolution); container settings are locked.

## Phases
- A: build type + preset parsing + home screen skeleton + skip login
- B: setup + play with an already-extracted folder
- C: installers (folder, wineskin-pkg) with progress; update import (event patch keeps the exe)
- D: lock down settings / quick menu

## Home screen (user spec)
Start game · GPU (driver choice) · Resolution (container screen size; must cover the in-game
resolution, e.g. 1366x768) · legends.ini (opens the game's own config file in an in-app text
editor, since the game folder may be app-private) · Controls.

## Preset files (MapleLegends)
- `container.json`: GameNative "Export config" of the working container, machine-specific keys removed
  (id, drives, installPath, session stats, controls profile id) and FEX_SILENTLOG dropped.
- `fexcore-preset.json`: the FEX preset the container used (user's "D2R" preset: TSO+vector TSO,
  multiblock, MAXINST 5000, small TSC scale, SMC mtrack, volatile metadata, mono hacks).
- Components to bundle: proton-11.0-2-arm64ec-6, FEXCore 2609-maple1, DXVK 2.7.1-1-gplasync,
  graphics driver "Wrapper" + Turnip Adreno T30 (@Mr_Purple_666).

## TODO before publishing
- Per-GPU driver selection: the bundled Turnip T30 is only verified on Adreno 840 (Galaxy Z Fold 7).
  Other Adreno -> system Qualcomm driver or another Turnip build; Mali/Xclipse -> system driver via
  Wrapper-gamenative. Testing is on the Fold 7 only for now.

## Multi-client (later phase, user design)
- Rules: MapleLegends allows several clients open at once, but controlling several at once is botting.
  Input only ever goes to the one client in front; no broadcast, no per-client automation.
- No Wine desktop ever shown. All clients run in the same container/Wine session.
- UI: a vertical tab bar on either screen edge (Tab 1, 2, 3 ... + "next tab" button, "+" to start another
  client). A tab brings that client's window to the front, full screen. Later: optional 2x2 grid view, max 4.
- Existing hooks (com.winlator.winhandler.WinHandler, talks to winhandler.exe inside Wine):
  `exec(cmd)` to start another client, `listProcesses()` (pid, name, memoryUsage per process),
  `bringToFront(processName, hwnd)`. Missing: a way to list window handles per pid, since all clients
  share one process name -> extend winhandler.exe with a "list windows" request.
- Grid view idea: run the virtual desktop at 2x the client size with each client window in a fixed
  quadrant; the tab view zooms the renderer onto one quadrant, the grid view shows the whole desktop.
  Touch coordinates must be mapped to the zoomed quadrant.

## In-game side column (replaces GameNative's quick menu + drag-out side panel in the wrapper)
- The back key / drag-out panel no longer open GameNative's Quick Menu; users never see it.
- One slim column at the very LEFT edge, split in half:
  - Top half, clients: Tab 1..N, "next tab", "+" start another client (max 4).
  - Bottom half, tools: Edit on-screen controls, show/hide on-screen controls (also for clean
    screenshots), Keyboard (chat), Exit game.
- FPS limiter moves to the home screen's GPU section instead.
- Physical input: GameNative already auto-hides the on-screen controls ONCE per session when an external
  keyboard, a captured mouse, or a game controller is detected (XServerScreen.evaluateDevice ->
  hideInputControls; flag hasUpdatedScreenGamepad). They don't come back on unplug - the show/hide tool
  covers that. Keep this behaviour.
