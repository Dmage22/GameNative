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
