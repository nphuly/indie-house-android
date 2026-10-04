# Indie House for BlackBerry Leap

**English** · [Tiếng Việt](README.vi.md)

The Android client for [Indie House](https://github.com/nphuly/indie-house-server), a self-hosted, invite-only music streaming service.

| Repository | What |
| --- | --- |
| [indie-house-server](https://github.com/nphuly/indie-house-server) | Backend: Laravel control plane, Go media worker and stream gateway, documentation |
| [indie-house-ios](https://github.com/nphuly/indie-house-ios) | Native iOS client in SwiftUI |
| **indie-house-android** (this one) | Android 4.3 (API 18) client for the BlackBerry Leap |

This is an Android Java client for the BlackBerry Leap Android 4.3 runtime (API 18). It uses Android Views throughout; it has no WebView or Flutter dependency. It discovers an Indie House instance from its HTTPS address or by scanning the same manifest QR code as the iPhone client. The app confirms the instance name and hostname before connecting, checks the instance ID on sign-in, and uses the [mobile JSON API](https://github.com/nphuly/indie-house-server/blob/main/docs/en/mobile-api.md) with rotating access and refresh tokens.

Implemented screens and actions: connect by address or camera QR, sign in or redeem an invite, Home, Search, Khám phá (the full catalogue), Library, album, artist, playlist and listening-history pages, and Settings (library, audio quality, autoplay, listening history, devices, sign out). Users can create, edit and delete playlists, add songs to or remove them from playlists, queue a song to play next, and revoke devices. Playback uses Android `MediaPlayer` for MP3 and FLAC streams (a device that cannot decode FLAC falls back to 320 kbps), with shuffle, repeat (off/all/one), a queue, and the account's autoplay setting. Tokens are stored with an Android Keystore wrapped AES key when supported; if the BlackBerry runtime does not provide a working keystore, a session works only until the app process ends. No token is stored as plain text.

The interface is in Vietnamese (default) or English and follows the listener web app and the ["Biển hiệu" design system](https://github.com/nphuly/indie-house-server/blob/main/docs/en/design-system.md) described in the server repository. Strings live in `res/values/strings.xml` (Vietnamese) and `res/values-en/strings.xml`; keep both files on the same keys. The language is switched with the VI | EN toggle on the connection and sign-in screens or in Settings, stored per device by `Locales`, and sent to the server as `Accept-Language`. Switching rebuilds the views in place, so music keeps playing.

The app uses the same "Biển hiệu" tokens, the Chakra Petch and JetBrains Mono typefaces bundled in `assets/fonts` (subset to Latin and Vietnamese, SIL Open Font License files alongside), and the web's own icon set. Play buttons, pill buttons and artwork have cut corners (`Widgets.Cut`, and `clipPath` in `ArtView`). In [indie-house-server](https://github.com/nphuly/indie-house-server), `python3 scripts/generate-legacy-android-assets.py` renders the icons from `src/control-plane/app/Support/Icon.php` into per-density PNGs, because Android 4.3 has no vector drawables, and crops the record-shop hero (`scripts/generate-record-shop-hero.py`) for the connection screens and the home spotlight. It writes here when this repository is checked out next to it as `../indie-house-android`, or when `INDIE_HOUSE_ANDROID` points here. `ArtView` draws the web's hologram layer over loaded artwork, and `ArtworkLoader` decodes in full colour at 720px or more.

The app fetches track, artist and album artwork through authenticated HTTPS endpoints, keeps a small memory cache, remembers addresses that have no image, and shows a tinted placeholder with the item's initial when no artwork exists. Album and artist shelves are derived from track data. Recently played songs show each track once, ordered by its latest listen; the server retains every play log for account history and statistics. As on the web, a listen is logged after ten seconds or when the song ends.

On a cold launch, a native loading screen stays visible while the saved session and the Home data are checked. A valid session opens Home directly; a missing or expired session opens sign-in. Connection and instance errors stay on the loading screen with Retry and Change library actions.

The four main sections use a bottom navigation bar: Trang chủ, Tìm kiếm, Khám phá and Thư viện. Each tab keeps its own back stack and scroll position; tapping the active tab returns to its first page, then scrolls to the top, then refreshes. Settings open from the avatar in the top bar. Menus and forms are bottom sheets; destructive actions ask for confirmation in a dialog.

Android 4.3 does not include the ISRG Root X1 certificate needed by the current Indie House HTTPS chain. For API levels below 25, the client checks against the device's normal trust authorities and the bundled [official Let's Encrypt root certificate](https://letsencrypt.org/certs/isrgrootx1.pem). Certificate-chain and hostname checks remain enabled. Android 4.1–4.4 leave TLS 1.1 and 1.2 switched off for client sockets and still offer SSLv3, so the API client enables TLS 1.2, 1.1 and 1.0 and drops SSLv3.

`MediaPlayer` streams through Android's own media stack, not through that client. On the Android 4.3 emulator this stack only negotiates TLS 1.0 with ECDHE-RSA CBC ciphers and trusts only the system certificate store. Audio therefore plays only if the edge still accepts TLS 1.0 (Cloudflare's default minimum) and the certificate chains to a root the device already trusts. Check both on the Leap before raising the minimum TLS version. The bundled certificate has SHA-256 fingerprint `96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6`.

The QR scanner uses the device camera and ZXing Core 3.3.2, packaged in `libs/` with its Apache 2.0 license. It needs no Google Play services or external scanner app. A camera is optional: users can always enter the HTTPS address manually.

## Develop in Android Studio

Open this repository's root directory as the project, then allow Gradle to sync. Select the Android Studio bundled JDK (JBR 21 works) as the Gradle JDK. Install Android SDK Platform 35 and Build Tools 36.0.0 in SDK Manager; Platform 18 is also needed for the original `build.sh`. Android Studio creates a machine-specific `local.properties` with the SDK path. The Gradle wrapper downloads Gradle 9.1.0 on first use. The project uses Android Gradle Plugin 9.0.1 and compiles Java 8 source for Android 4.3 (API 18).

The existing `src`, `res`, `AndroidManifest.xml`, and bundled ZXing JAR are used directly by the `app` module. Choose the `app` run configuration and use **Run** or **Build > Build APK(s)**. The Gradle APK is written to `gradle-build/app/outputs/apk/debug/app-debug.apk`. A physical Leap is needed to check BlackBerry-specific playback, TLS, and camera behavior.

### UI structure and preview

- `MainActivity` owns the session, the signed-in shell (top bar, content, mini player, navigation), per-tab back stacks, requests and actions. `Player` wraps `MediaPlayer` with the queue, shuffle, repeat and listen logging; `PlayerScreen` is the full-screen Now Playing view. `QrScanActivity` owns camera scanning.
- `Screens` builds every page from cached `Page` data. `Theme` holds the tokens and fonts, `StyledActivity` the shared builders, and `Sheet` the bottom sheets and confirmation dialogs. `ArtView`, `TrackRow`, `IconButton`, `NavigationBar` and the small views in `Widgets` (equalizer, switch, loading strip, pill button) are reusable Views.
- `res/layout/screen_connect.xml` and `screen_login.xml` are the real layouts for connection and sign-in. Open either file and select **Split** or **Design** in Android Studio to see the Layout Editor preview while editing. Colours, text styles and buttons live in `res/values/colors.xml`, `res/values/styles.xml` and `res/drawable/`. A TextView's `android:tag` (`mono`, `semibold`, `bold`, `extrabold`) selects the bundled font at runtime; the preview shows the system font. `tools:` attributes affect preview only.
- `res/layout/preview_navigation.xml` and `preview_track_row.xml` show the two custom Views with sample content. Their data, selected tab and callbacks are supplied by Java at runtime; test dynamic screens on an emulator or device and inspect them with **Layout Inspector**.

Keep the preview renderer on API 18 when checking Leap layouts, then run on the Leap to verify its actual font, touch targets and playback. Programmatically built catalogue and player screens cannot be previewed as complete XML screens until their layout code is moved to resources.

If Android Studio still shows an older `IndieHouseLeap` run configuration and reports that its APK path is missing, sync Gradle, then select or create an **Android App** run configuration with module `app` and launch option **Default Activity**. The older configuration targets the project root, which does not produce an APK.

If `build/debug.keystore` exists, Gradle signs debug APKs with the same local key as `build.sh`, allowing upgrades over earlier test builds. Keep that key when moving development to another computer. On a fresh checkout without it, Gradle uses the normal Android Studio debug key; that APK cannot upgrade an app signed with the old key.

## Original command-line build

Build with JDK and Android SDK Platform 18 and 35/Build Tools 36.0.0:

```bash
./build.sh
```

The signed local-test APK is `dist/indie-house-leap-api18.apk`. The build uses the existing key under ignored `build/`; preserve it to install future builds as upgrades. This is a private test build, not an app-store release.

The APK was compiled against Android API 18, verified with a v1 signature and installed on a newer Android emulator with its old-target test override. The connection and camera preview screens opened. **QR decoding, HTTPS/TLS, login, audio streaming and seeking still need testing on a physical BlackBerry Leap.** The BlackBerry 10 Android runtime has its own camera, TLS and audio compatibility limits. The app rejects HTTP, certificate errors and cross-origin API redirects; do not disable those checks to work around an old trust store. The app only talks to the Indie House instance the user confirmed.

## License

[PolyForm Noncommercial License 1.0.0](LICENSE): noncommercial use only. See also [DISCLAIMER.md](DISCLAIMER.md). ZXing Core is under the Apache 2.0 license (`libs/`), and the bundled fonts are under the SIL Open Font License (`assets/fonts`).
