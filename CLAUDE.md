# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Native Android client for [Larpnet](https://larpnet.pl), a Friendica-based (ActivityPub-federated)
social network for the Polish LARP community, built against the server's Mastodon-compatible
REST API (`friendica-larpnet`'s `src/Module/Api/Mastodon/`). See `README.md` for setup, full
v1 scope, and the history of real bugs found by driving the app live (Cloudflare User-Agent
blocking, HTML-parser block/inline handling, OAuth back-stack/cancellation fixes).

## Commands

- Build debug: `./gradlew assembleGithubDebug` (or `assemblePlaystoreDebug`) — two product
  flavors on the `distribution` dimension, no separate feature modules; flavor-specific source
  lives in `app/src/github/` / `app/src/playstore/` alongside `app/src/main/`.
- Install on a running emulator/device: `adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk`,
  or `./gradlew installGithubDebug`.
- Lint: `./gradlew lintDebug` (must stay at 0 errors/0 warnings).
- Compile-only check (fast): `./gradlew :app:compileGithubDebugKotlin`.
- There are no unit or instrumented tests in this repo — verification is done by running the
  app (see "Testing changes" below), plus `assembleDebug`/`lintDebug` passing clean.
- If `./gradlew` hangs/fails talking to `services.gradle.org` on this network, it's a TLS
  quirk, not a project issue: retry with
  `JAVA_OPTS="-Dhttp.keepAlive=false -Dhttps.protocols=TLSv1.2"` (only needed before the
  Gradle distribution is cached locally).

## Architecture

- Kotlin + Jetpack Compose + Material3 throughout, single Activity (`MainActivity`) +
  Navigation-Compose (`ui/nav/NavGraph.kt`). No XML views.
- State management is plain `ViewModel` + Compose `mutableStateOf` (not StateFlow/LiveData),
  exposed as `var uiState by mutableStateOf(...)`.
- **Manual dependency injection** (`di/AppContainer.kt`) — no Hilt, deliberately (see README).
  `AppContainer` is a plain object graph built once in `App.onCreate()`; screens reach it via
  `rememberAppContainer()`.
- **Retrofit + OkHttp + kotlinx.serialization** (`network/`) against the Mastodon-compatible API
  surface. Paginated calls return `Response<List<T>>` so `Link`-header cursors stay reachable
  (`network/LinkHeaderPaging.kt`) — the JSON body alone has no pagination info on this server.
- **OAuth2 authorization-code flow**, hand-rolled (`data/auth/OAuthFlow.kt`), no refresh tokens
  (server-confirmed access tokens don't expire; revocation surfaces as 401/403 via
  `network/AuthInterceptor.kt`, forcing logout).
- **Domain layer** (`domain/`): `thread/ThreadBuilder.kt` reconstructs Friendica's flat
  `context.descendants` reply list into a tree — a parent can appear *later* in that list than
  its own child, see the doc comment there. `html/HtmlParser.kt` turns a post's raw HTML into a
  small block-level `HtmlNode` tree, rendered natively by `ui/common/HtmlContent.kt` (no
  WebView-per-post).
- No working streaming endpoints server-side: timelines poll on an interval while visible and
  surface new posts behind a tappable banner (`ui/timeline/NewPostsBanner.kt`) rather than
  reflowing the list under the reader; pull-to-refresh merges immediately.
- `StatusCard` (`ui/timeline/StatusCard.kt`) is the single shared post-rendering composable used
  by every timeline, the thread screen, and profile — action-row/icon changes there apply
  everywhere at once.

## Versioning

`versionName` in `app/build.gradle.kts` is bumped by hand on every PR that ships a change:
- Bump the **patch** number (last one) for a fix or change users can't see.
- Bump the **minor** number (middle one, resetting patch to 0) for anything users *can* see —
  a new feature, a UI change, new visible behavior.

`versionCode` is separate and CI-derived (`GITHUB_RUN_NUMBER`) — never hand-bump it.

Do this proactively as part of the same PR/commit that makes the change, without waiting to be
asked.

## Testing changes

Before reporting a UI/behavior change as done, build and run the app on the Android emulator
(`app-github-debug` flavor) and actually exercise the change (tap through it, take screenshots)
rather than relying on compile success or type-checking alone. Only skip this and say so
explicitly if the emulator genuinely can't be used for the change at hand.

Point the emulator at `test.larpnet.pl` (not production `larpnet.pl`) for this — same test
account as used elsewhere against this server. Prefer this even for read-only checks; it
matters most before any action that writes data (favouriting, replying, following, etc.),
since those hit the account/server for real.

When a change is visual (layout, colors, icons, spacing, new UI element), attach a screenshot
showing it to the PR (`gh pr comment <number> --attach <path>#<alt text>`) — don't just describe
it in words.
