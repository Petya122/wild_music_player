# Privacy Policy

Wild Music Player · last updated 6 November 2026

Wild Music Player is a music player made by one independent developer (Petya122). It has **no accounts,
no ads, no analytics and no tracking**, and the developer receives **no data** from the app.

The app comes in two versions:

- **Google Play version** (`com.wildmusicplayer.play`)
- **GitHub version** (`com.wildmusicplayer`), installed from this repository's releases

## What the app uses on your phone

Both versions read the music files on your phone (Android's "Music and audio" permission) to show and
play them. Your library, playlists, favorites, play counts, listening stats, lyrics files and settings
are stored **only on your phone**. They are never sent to the developer or anyone else.

If you turn on Android's own backup, Android may save the app's playlists and settings to your Google
account. That backup is handled by Android and Google, not by this app.

## Google Play version: nothing leaves your phone

The Google Play version has no internet permission, so it can't connect to the internet at all. It
collects nothing and shares nothing.

## GitHub version: the services it connects to

The GitHub version connects to these services, only when you use the matching feature:

| Service | When | What is sent |
|---|---|---|
| YouTube (through NewPipeExtractor) | You search, stream or download | Your search words and the video you picked |
| LRCLIB (lrclib.net) | A song's lyrics are shown for the first time | The song's title, artist, album and length |
| GitHub | You check for or download an update | A normal request for this repository's releases |
| Image hosts (YouTube / Google) | Covers of YouTube songs are shown | The cover's address |

Each service sees your IP address, as with any website, and handles it under its own privacy policy.
Offline Mode stops streaming, downloads and new lyrics lookups.

## Permissions

| Permission | Why |
|---|---|
| Music and audio | To find and play the songs on your phone |
| Notifications | The player controls (and, in the GitHub version, download progress) |
| Foreground service | Keeps the music playing when the app is in the background |
| Network state | Noticing whether you're connected (used by the GitHub version's downloads) |
| Internet (GitHub version only) | YouTube, lyrics and updates |
| Install packages (GitHub version only) | Installing updates you downloaded from this repository |

## Children

The app isn't aimed at children and collects no information from anyone.

## Changes

Changes to this policy are published in this file; its history is visible on GitHub.

## Contact

Questions: open an issue at <https://github.com/Petya122/wild_music_player/issues>. For security
problems, see [SECURITY.md](SECURITY.md).
