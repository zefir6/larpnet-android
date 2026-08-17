# Larpnet Android

Native Android client for [Larpnet](https://larpnet.pl), a Friendica-based (ActivityPub-federated)
social network for the Polish LARP community. Built against the server's Mastodon-compatible
REST API (`friendica-larpnet`'s `src/Module/Api/Mastodon/`).

## Status

Scaffolded end-to-end and **fully exercised against the real production server** with a test
account, not just compile-checked. `./gradlew clean assembleDebug` and `./gradlew lintDebug`
both pass with 0 errors and 0 warnings (JDK 17, AGP 8.7.2, Kotlin 2.0.21, compileSdk/targetSdk
35). On an API 34 emulator, a complete OAuth2 login against `larpnet.pl` succeeded (app
registration → Custom Tab → real sign-in page → consent screen → token exchange →
`verify_credentials`), and Home, Public, Notifications, Profile, and Thread screens were all
driven against live data with a real account.

**Real bugs found and fixed this way, not by reading code:**
- **Cloudflare blocks generic HTTP User-Agents.** `larpnet.pl`'s bot-protection returns a bare
  `403` for requests carrying OkHttp's/curl's default User-Agent (confirmed directly with
  `curl`), which hit both the API (`AppContainer`'s `OkHttpClient`s) *and* image loading (Coil
  builds its own default client unless told otherwise — avatars and media were silently
  blank until `App.kt` wired Coil's `SingletonImageLoader` to a client carrying the same
  header). Both now send `larpnet-android/<version>`, matching the sibling macOS app's
  workaround.
- **HTML parser mis-rendered un-wrapped inline content.** A real federated post whose body was
  just a run of `#hashtag` links (not wrapped in a `<p>`) rendered one hashtag per line instead
  of flowing as one paragraph — `HtmlParser` treated every top-level node as its own block. It
  now buffers consecutive inline nodes (text, `<a>`, `<span>`, etc.) and only starts a new block
  on an actual block-level tag.
- **Login screen got stuck after backing out of the browser.** Custom Tabs' simple launch API
  gives no cancellation callback, so backing out left the login button permanently disabled on
  "Opening browser…". Fixed with a resume-based reset (`LoginViewModel.onScreenResumed`).
- **Back-stack left a stale Chrome activity after login.** The OAuth redirect's relaunch of
  `MainActivity` didn't clear the Custom Tab activity above it, so a system Back press from a
  top-level screen could resurface the spent browser page (and eventually exit the app
  entirely). Fixed with `FLAG_ACTIVITY_CLEAR_TOP` in `OAuthRedirectActivity`.

**Not yet done**: posting, replying, favouriting/reblogging, and profile editing were verified
to hit the right endpoints (reblog/unreblog were exercised, accidentally then deliberately, and
round-tripped correctly) but composing and actually publishing a new post was deliberately not
exercised live, to avoid posting test content to the real community.

## Setup

1. Install JDK 17 if you want to build from the command line (Android Studio bundles its own
   JBR and doesn't need this): `brew install openjdk@17` (formula, no sudo needed) or
   `brew install --cask temurin17` (cask, needs sudo for the system-wide symlink).
2. Install Android Studio: `brew install --cask android-studio`, or from
   [developer.android.com](https://developer.android.com/studio).
3. On first launch, run through the Setup Wizard and accept SDK licenses. Via SDK Manager,
   make sure an **Android SDK Platform** matching `compileSdk` (currently 35) and a current
   **Build-Tools** version are installed.
4. Open this directory in Android Studio (`File > Open`). Let Gradle sync run — the wrapper
   (`gradlew`/`gradle/wrapper/`) is already committed and pinned to Gradle 8.10.2, so this
   should just work. If Studio flags AGP/Kotlin/Compose-BOM version mismatches, use the
   **Upgrade Assistant** it offers rather than hand-editing `gradle/libs.versions.toml`.
5. Create or select an emulator (Pixel-class, API 34+) via Device Manager, or connect a
   physical device with USB debugging enabled.
6. Run the `app` configuration (green ▶), or from the command line:
   `JAVA_HOME=$(brew --prefix openjdk@17) ./gradlew installDebug`. First launch shows the
   login screen with the instance field prefilled `larpnet.pl`; logging in opens a Chrome
   Custom Tab for the server's login/consent page and lands on the home timeline on success.
7. To point at a different instance (local dev stack, staging), just change the URL field
   at login — nothing about the instance is hardcoded beyond that default.

**Note on this network**: if `./gradlew` hangs or fails with `SocketException: Unexpected end
of file from server` while talking to `services.gradle.org`, it's a TLS negotiation quirk with
this specific network path, not a project issue — set
`JAVA_OPTS="-Dhttp.keepAlive=false -Dhttps.protocols=TLSv1.2"` and retry. Not needed once the
Gradle distribution is cached locally (`~/.gradle/wrapper/dists`).

## Architecture

- **Kotlin + Jetpack Compose + Material3**, single Activity (`MainActivity`) +
  Navigation-Compose (`ui/nav/NavGraph.kt`).
- **Retrofit + OkHttp + kotlinx.serialization** for networking (`network/`), with a
  `Response<List<T>>` return type on paginated calls so the Mastodon-API-style `Link` header
  cursors stay reachable (`network/LinkHeaderPaging.kt`) — the JSON body alone doesn't carry
  pagination info on this server.
- **OAuth2 authorization-code flow**, hand-rolled (`data/auth/OAuthFlow.kt`) rather than via
  AppAuth, since the server has no OIDC discovery document and no PKCE support. Login opens
  a Chrome Custom Tab; the redirect lands on `ui/login/OAuthRedirectActivity`, which forwards
  the callback through `AppContainer` back to the login screen.
- **No refresh tokens**: confirmed server-side that access tokens don't expire and none is
  ever issued. The only token lifecycle event is server-side revocation, surfaced as a
  401/403 that `network/AuthInterceptor.kt` turns into a forced-logout event.
- **Manual dependency injection** (`di/AppContainer.kt`) — no Hilt, deliberately, to avoid an
  unverifiable KSP/AGP/Kotlin alignment failure mode while this was written without a build
  environment. `AppContainer` is a plain object graph built once in `App.onCreate()`.
- **Domain layer** (`domain/`): `thread/ThreadBuilder.kt` reconstructs Friendica's flat
  `context.descendants` reply list into a tree (a parent can appear later in that list than
  its own child — see the doc comment there); `html/HtmlParser.kt` turns a post's raw HTML
  `content` into a small block-level tree (`HtmlNode`) that `ui/common/HtmlContent.kt` renders
  natively, since Friendica's HTML is richer than plain Mastodon text (images, blockquotes,
  lists, code) and a WebView-per-post wouldn't scale in a timeline.
- Since the server's streaming endpoints are all unimplemented, timelines poll on an interval
  while visible and surface new posts behind a tappable "N new posts" banner
  (`ui/timeline/NewPostsBanner.kt`) rather than reflowing the list under the reader.
  Pull-to-refresh merges immediately since it's a direct user action.

See the plan this was built from for the full rationale behind each of these choices:
`~/.claude/plans/create-larpnet-android-app-unified-noodle.md` (Claude Code plan file, not
part of this repo).

## v1 scope

Implemented: OAuth2 login, home/local/public timelines (local via `/api/v1/timelines/public?local=true`,
a fifth bottom-nav tab), thread view with reply-tree reconstruction, compose (new post + reply,
visibility selector, content warning, sensitive flag, media attachments), favourite/reblog/bookmark,
notifications list, own + others' profile view, account search (`/api/v1/accounts/search`) with
follow/unfollow from the results list, profile editing (display name, bio, manually-approve-followers,
discoverable, bot flag -- all via `update_credentials`).

Explicitly **not** in v1 (see doc comments at the relevant call sites for why):
- **Streaming/live updates** — server has no working `/api/v1/streaming/*`; everything is
  pull-to-refresh + polling.
- **Push notifications** — no client-side implementation; `push.larpnet.pl` (self-hosted
  ntfy/UnifiedPush) is the natural v2 integration point.
- **Larpnet "local-only" (SERVER_ONLY) post visibility** — not exposed by the Mastodon API
  layer server-side; only `public`/`unlisted`/`private`/`direct` are supported.
- **Mobilizon federated events** — out of scope entirely.
- **Poll voting, featured tags, filters, domain blocks, admin endpoints** — all
  `Module\Api\Mastodon\Unimplemented` server-side.
- **2FA-specific UI** — handled implicitly by the server's own browser-based login form.

## Verification

Done, on an API 34 (`google_apis`, arm64) emulator against the real `larpnet.pl` with a test
account:
- `./gradlew clean assembleDebug` and `./gradlew lintDebug` — 0 errors, 0 warnings.
- Full OAuth2 login: registration → Custom Tab → real sign-in page → consent screen → token
  exchange → `verify_credentials`, all 200s.
- Backing out of the Custom Tab mid-flow returns to a usable (not stuck) login screen; after a
  successful login, system Back from a top-level screen no longer resurfaces the spent browser
  activity (see "Status" for both fixes).
- Home, Public, Notifications, and Profile tabs all load real data with correct empty/populated
  states; avatars and post media render (see "Status" for the Coil User-Agent fix this needed).
- Opened a real federated post's thread (ancestors/context endpoint), confirmed correct
  rendering including a real multi-hashtag post (see "Status" for the HTML-parser fix this
  needed) and multi-image media attachments.
- Reblog/unreblog round-tripped correctly against a live post (unintentionally at first, then
  deliberately verified and cleaned up).
- Compose screen renders correctly (visibility dropdown, CW field, sensitive checkbox, media
  picker, disabled Publish while empty) — not actually submitted, to avoid posting test content
  to the real community.

Additionally verified live (2026-08-16): Local timeline tab (distinct content from Home/Public,
confirmed against real instance-only posts), account search for a real federated handle
(`@metin@graphics.social`) followed by follow/unfollow round-tripping correctly (both cleaned up
afterwards to avoid leaving a stray follow on the test account), and Edit Profile's new
locked/discoverable/bot switches correctly reflecting the test account's real server-side state
on load (not saved, to avoid changing real account settings unprompted).

Still to do:
- Actually publish a post/reply (needs the user's go-ahead, to avoid posting to their community
  unprompted) and confirm it round-trips into the timeline.
- Force a 401 (e.g. revoke the app's OAuth grant server-side) and confirm the app returns to
  the login screen.
- Actually save an edit-profile change (display name/bio/locked/discoverable/bot) and confirm
  it persists server-side (only load was verified live, not save).
- Pagination (load-more) on a timeline with more than one page of content.
