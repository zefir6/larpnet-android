package pl.larpnet.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors lifted directly from the production web theme (`friendica-larpnet`'s
 * `view/theme/larpnet_notifications`, fetched live from `larpnet.pl`'s computed `style.pcss`),
 * not guessed -- same source of truth as the sibling iOS app's `UI/Theme/Theme.swift`, so both
 * clients (and the site itself) actually look like the same product:
 *   - accent / link color:      #a54bad  (link color, hover #94439b)
 *   - top bar / prominent:      #833c89  (`#topbar-first`, `.btn-primary` background)
 *   - page background:          #ededed  (`body { background-color }`)
 *   - card/panel background:    #ffffff  (`.panel { background-color }`, 4dp radius, subtle shadow)
 *   - body text:                #444444  (`body { color }`)
 */
val LarpnetAccent = Color(0xFFA54BAD)
val LarpnetAccentHover = Color(0xFF94439B)
val LarpnetNavBar = Color(0xFF833C89)
val LarpnetPageBackground = Color(0xFFEDEDED)
val LarpnetCardBackground = Color(0xFFFFFFFF)
val LarpnetBodyText = Color(0xFF444444)

/** Muted/secondary text (handles, timestamps, hints) -- iOS uses the system `.secondary` gray
 * for this, distinct from the explicit `bodyText` color used for primary content. */
val LarpnetMutedText = Color(0xFF767676)

/** Subtle accent-tinted wash for "this is a distinct sub-region" surfaces: blockquotes/code
 * blocks, incoming message bubbles, the focused post in a thread. Distinct from both the white
 * card background and the gray page background. */
val LarpnetHighlight = Color(0xFFF3E1F5)

val LarpnetError = Color(0xFFB00020)
