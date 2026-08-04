<h1 align="center">jellyfin-androidtv — Browse Modes fork</h1>

<p align="center">
A fork of the official Jellyfin Android TV app that adds a <strong>tile grid</strong> when you
open a Movies or TV library, instead of dropping straight into the item grid.
</p>

---

## What this fork changes

Opening a Movies or TV library shows a grid of tiles, each a different way in:

**All items** · **Unwatched** · **Just Added** · **Best Unseen** · **Random** · **Favorites** ·
**Genres** · **Highest Rated** · **Top Rated** · **Trending** · **New Releases** ·
**Studios / Networks** · **Recently Played** · **Critics' Picks** *(films only)* · **Longest**

Every tile carries its own coloured icon. **Trending** and **Top Rated** additionally badge each
poster with its position in TMDb's worldwide ranking.

Nothing is hidden — the first tile, **All items**, is the ordinary grid exactly as it was. Each
preset keeps its own display preferences, so changing the sort inside "Just Added" leaves your
plain library view alone.

That is 20 changed files against upstream, on the [`browse-modes`](../../tree/browse-modes)
branch. Everything else is stock Jellyfin Android TV.

> **Why this needs a separate install.** The Jellyfin TV app is native Kotlin and shares no code
> with the web client, so changes made there can never appear here. If you also want tiles in a
> browser or on your phone, that needs the
> [web fork](https://github.com/AvonWilliams/jellyfin-web).

## Install

**1. Download** `jellyfin-androidtv-browse-modes-*.apk` from the
[Browse Modes releases](https://github.com/AvonWilliams/jellyfin-browse-modes/releases).

Or use the Downloder app by AFTVnews. and enter any of the following:
...
8953482
...
or
...
http://aftv.news/8953482
...
or
...
avonwilliams.github.io/jellyfin-browse-modes/tv
...

**2. Enable network debugging on the TV.** Settings → Device Preferences → About, click **Build**
seven times, then Settings → Device Preferences → Developer options → **Network debugging**.
Note the TV's IP from Settings → Network & Internet.

**3. Sideload it:**

```bash
adb connect <TV-IP>:5555
adb install -r jellyfin-androidtv-browse-modes-1.0.0-debug.apk
```

The TV shows an **"Allow debugging from this computer?"** prompt the first time — accept it, and
tick *Always allow*. The install fails until you do.

**4. Open it.** A second Jellyfin icon appears. Enter your server address and sign in.

### It installs alongside the official app

These builds carry the `.debug` application id suffix, so they land as a **separate app** and
never touch your existing Jellyfin install. You can run both and remove this one at any time.

They are debug-signed, which is why sideloading is required — they are not Play Store builds.

### Optional: the server plugin

Two tiles — **Trending** and **Top Rated** — read curated TMDb lists and need the
[Browse Modes plugin](https://github.com/AvonWilliams/jellyfin-browse-modes) on your server.
Every other tile works without it; those two show an empty-state message.

Your Jellyfin server stays completely standard — there is no custom server build.

### Building from source

```bash
git clone -b browse-modes https://github.com/AvonWilliams/jellyfin-androidtv.git
cd jellyfin-androidtv
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/
```

Needs JDK 21 and the Android SDK with platform 36.

## Caveats

- Built against **Jellyfin Android TV 0.19.9** and Jellyfin server **12.0-rc3**.
- **Will not auto-update.** New upstream releases need the changes re-applied and a rebuild.
- **Decades** and **Age Rating** exist in the web client but are not implemented here yet.
- Browse modes replace the "smart screen" (Continue Watching / Next Up rows) as the library
  landing screen. Turn them off per library in that library's display preferences to get it back.

## Troubleshooting

**`adb connect` finds nothing.** Check whether a VPN is running on your computer — it hides the
TV completely. Also confirm the TV is awake and network debugging is still enabled; some TVs
reset it after a system update. The TV only answers on port 5555, so a plain `ping` will not
find it.

## Documentation

- [User guide](https://github.com/AvonWilliams/jellyfin-browse-modes/blob/main/docs/USER-GUIDE.md)
- [Technical reference](https://github.com/AvonWilliams/jellyfin-browse-modes/blob/main/docs/TECHNICAL.md)

## Upstream

This is a fork of [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv)
and remains under **GPL-2.0**. All credit for Jellyfin itself goes to its maintainers and
contributors; the browse modes work is an unaffiliated addition.
