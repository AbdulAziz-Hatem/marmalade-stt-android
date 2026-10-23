package app.marmalade.stt.recognizer

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * #96: Android's RecognitionService checks the calling app's mic permission
 * from our process before our code runs, and refuses the caller
 * (ERROR_INSUFFICIENT_PERMISSIONS) when our app can't see it (Android 11+
 * package visibility). The manifest must keep apps with a launcher icon,
 * keyboards and accessibility services visible. Checked on a Pixel 6a with
 * tools/recognizer-probe; the visibility rules themselves aren't modelled on
 * the JVM.
 */
class CallerVisibilityTest {

    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.names(tag: String) = children(tag).map { it.getAttributeNS(ANDROID, "name") }

    private val queryIntents = manifest.children("queries").flatMap { it.children("intent") }

    private fun queried(action: String) = queryIntents.any { action in it.names("action") }

    @Test
    fun `apps with a launcher icon are visible, so their recognizer requests pass Android's check`() {
        assertTrue(
            queryIntents.any { "android.intent.action.MAIN" in it.names("action") && "android.intent.category.LAUNCHER" in it.names("category") },
        )
    }

    @Test
    fun `keyboards and accessibility services without a launcher icon are visible too`() {
        assertTrue(queried("android.view.InputMethod"))
        assertTrue(queried("android.accessibilityservice.AccessibilityService"))
    }

    @Test
    fun `the recognition service is still declared`() {
        val services = manifest.children("application").flatMap { it.children("service") }
        assertTrue(services.any { it.getAttributeNS(ANDROID, "name").endsWith(".MarmaladeRecognitionService") })
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
