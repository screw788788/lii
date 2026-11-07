# Lumen Launcher 2

A minimal, fast Android launcher with a ColorOS-inspired look and lots of motion. Jetpack Compose, no ads, no tracking, no internet permission.

## What's in v2
**Look**
- Uniform squircle icons with soft baked-in shadows; old-style icons get a clean tile instead of floating on grey
- Glass dock and search bar (gradient + hairline border), legibility shading under the status bar and dock
- 6 built-in wallpapers (soft gradient meshes), applied in one tap. Long-press empty home space.
- Android 12+: the wallpaper really blurs behind the app drawer as you drag it

**Motion**
- Home springs/zooms in every time you return from an app
- Drawer follows your finger, then springs open/closed with fling velocity; wallpaper blur + home scale follow it
- Wallpaper parallax as you swipe between home pages; pages scale/fade as they slide
- Springy icon press, zoom-from-icon app launch, pop-in long-press menu, bottom-sheet settings, A-Z bubble

**Easier to use**
- Smart first-run layout: dock = phone/messages/browser/camera; home = gallery, calendar, settings, clock, maps, etc. (whatever your phone has)
- A-Z scrubber in the drawer (drag along the right edge)
- Visible swipe-up handle above the dock (tap it too)
- Long-press empty space: wallpapers, "show all apps on home" switch, reset layout
- Long-press an icon: add/remove Home or Dock, App info, Uninstall
- Swipe down on home: notification shade (best effort)

## Get the APK from GitHub
1. Upload this folder's contents to your repo (Add file -> Upload files) and commit to `main`.
2. Actions tab -> wait for "Build APK" to go green.
3. Releases -> "Lumen Launcher (latest build)" -> download `Lumen-Launcher.apk`.

## Install / update
- This version is signed with a fixed key (`app/lumen.keystore`), so future builds install over the top of each other.
- **First time only:** uninstall any older Lumen build (it was signed with a different, throwaway key).
- Press Home -> choose Lumen -> Always.

## Notes
- The keystore in this repo is a personal-use key with a public password. Make your own before publishing anywhere.
- Not included yet: folders, drag-to-rearrange, widgets, icon packs.
