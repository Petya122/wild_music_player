# Google Play Console checklist (Play version)

Everything here is about the **Play version** (`com.wildmusicplayer.play`): no internet permission,
no YouTube, no self-update, no donation link. Texts and images for the store listing are in this folder.

## 1. Upload key (once, on your PC)

Google keeps the real signing key (**Play App Signing**, accept it on the first upload). You only keep
an **upload key**. Create it outside the project folder:

```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

- Use the same password for the key as for the keystore (the CI uses one password for both).
- Keep `upload.jks` and its password somewhere safe and backed up. Never put it in the repository.

Turn it into text for GitHub (PowerShell):

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")) | Set-Content upload_base64.txt
```

GitHub → repository → Settings → Environments → `release` → add two secrets:

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | the contents of `upload_base64.txt` |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password |

Then delete `upload_base64.txt`. From the next release on, the release run also builds
`wild_music_player-play-vX.Y.Z.aab`; download it from the run's **Artifacts** (it's not attached to the
GitHub release) and upload it to Play.

## 2. Create the app

- App name: **Wild Music Player** · Default language: English (United States)
- App or game: **App** · Free or paid: **Free**
- Category: **Music & Audio**
- Contact email: your developer email (shown publicly on the store page)

## 3. App content (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | `https://github.com/Petya122/wild_music_player/blob/master/PRIVACY.md` |
| Ads | No, the app has no ads |
| App access | All functionality is available without special access (no login) |
| Content rating | See section 4 |
| Target audience | Ages **13–15, 16–17 and 18+** (not under 13, so the stricter children's rules don't apply) |
| News app | No |
| Data safety | See section 5 |
| Government app | No |
| Financial features | None |
| Health | None |
| Foreground service permissions | See section 6 |

## 4. Content rating questionnaire (IARC)

- Category: **All other app types** (utility / productivity / other)
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Users can interact or exchange content: **No**
- Shares the user's location: **No**
- Digital purchases: **No**

Expected result: the lowest age rating (for example PEGI 3 / Everyone).

## 5. Data safety

- Does the app collect or share any of the required user data types? **No**
- The app has no internet permission; nothing leaves the device. (Android's own backup of playlists and
  settings is done by Android, not by the app, and doesn't count as collection by the developer.)

Result shown on the store: "No data collected · No data shared".

## 6. Foreground service declaration

Type: **Media playback** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`)

Description to paste:

> The app plays the user's music in the background: when the screen is off, while another app is open,
> and when the user starts playback from the notification, the home screen widget, the Quick Settings
> tile or a Bluetooth car radio or headset. The service runs only while music is playing or paused in
> the notification, and it shows the standard media notification with playback controls.

Google may ask for a short video (a link to an unlisted YouTube video or a Google Drive file): record
the phone screen while you start a song, go to the home screen, and the music keeps playing with the
notification visible; then pause it from the notification.

## 7. Store listing (Grow → Store presence → Main store listing)

| Field | File |
|---|---|
| App name | `title.txt` |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` |
| App icon (512×512) | `images/icon.png` |
| Feature graphic (1024×500) | `images/featureGraphic.png` |
| Phone screenshots | `images/phoneScreenshots/` |

Don't mention YouTube, downloading, Ko-fi or the GitHub version anywhere in the listing.

## 8. Closed test (needed before the first public release)

New personal developer accounts must run a closed test first:

1. Testing → Closed testing → create a track, upload the `.aab`.
2. Testers: add an email list with **at least 12 people** (their Google account emails).
3. Share the opt-in link; each tester accepts it and installs the app from Play.
4. Keep at least 12 testers opted in for **14 days in a row**.
5. Then: Dashboard → **Apply for production** and answer the short questions about the test.

## 9. Optional: Android Auto

The app supports Android Auto. To show it on car screens for Play users, opt in under
**Advanced settings → Form factors → Android Auto** and pass Google's extra Auto review. This can wait
until after the first release.
