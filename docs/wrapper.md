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
