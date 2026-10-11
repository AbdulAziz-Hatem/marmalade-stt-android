package app.marmalade.stt.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons drawn in-tree rather than pulled from `material-icons-extended`.
 *
 * None of these glyphs are in `material-icons-core`, which is all
 * `material3` brings in. Adding the extended artifact for a handful of
 * glyphs would pull the whole Material catalogue into the build just to have
 * R8 throw ~99% of it away again. The path data is Material Icons' own
 * (Apache-2.0, covered by the AndroidX entry in the licenses catalog); the
 * Bug / LicenseDoc / Privacy glyphs match marmalade-tts-android's.
 */
object MarmaladeIcons {

    /** Microphone (material `mic` filled) — the Dictate tab. */
    val Mic: ImageVector by lazy {
        filled24(
            "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 " +
                "5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 " +
                "11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
        )
    }

    /** Stop (material `stop` filled) — the Dictate mic button while listening. */
    val Stop: ImageVector by lazy {
        filled24("M6 6h12v12H6z")
    }

    /** Copy (material `content_copy` filled) — the Dictate "Copy" button. */
    val Copy: ImageVector by lazy {
        filled24(
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 " +
                "1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
        )
    }

    /** Bug (material `bug_report` outline) — the About "Report a bug" row. */
    val Bug: ImageVector by lazy {
        filled24(
            "M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 " +
                "2.17C12.96 5.06 12.49 5 12 5c-.49 0-.96.06-1.41.17L8.41 3 " +
                "7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33" +
                "-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 " +
                "2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09" +
                "-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0" +
                "-4h-4v-2h4v2z",
        )
    }

    /**
     * Document with text lines (material `description` filled) — the About
     * "Open-source licenses" row.
     */
    val LicenseDoc: ImageVector by lazy {
        filled24(
            "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2h12.01c1.1 " +
                "0 1.99-.9 1.99-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V" +
                "3.5L18.5 9H13z",
        )
    }

    /**
     * About "Privacy policy" row. A plain shield — reads as protection /
     * policy without implying account security the way a padlock would.
     */
    val Privacy: ImageVector by lazy {
        filled24("M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z")
    }

    /** Download (material `download` filled) — the Engines rows' download disc. */
    val Download: ImageVector by lazy {
        filled24("M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z")
    }

    private fun filled24(pathData: String): ImageVector =
        ImageVector.Builder(
            name = "MarmaladeIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addFilled(pathData).build()

    /**
     * Fills with black, not with the theme colour: `Icon` tints the whole
     * vector with `LocalContentColor`, so the authored colour only has to
     * be opaque. Using the theme here would double-tint.
     */
    private fun ImageVector.Builder.addFilled(pathData: String) = apply {
        addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
    }
}
