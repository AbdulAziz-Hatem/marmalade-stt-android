package app.marmalade.stt.speech.turn

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The committed Smart Turn asset is the pinned file (#44): the hash and size
 * in [SmartTurnAsset], which NOTICE.md and the goldens in
 * [OnnxTurnDetectorTest] were made against. Swapping the file fails here.
 */
class SmartTurnAssetTest {

    @Test
    fun `the bundled model is the pinned file`() {
        val file = File("src/main/assets/${SmartTurnAsset.PATH}")
        assertEquals(SmartTurnAsset.SIZE_BYTES, file.length())
        val sha256 = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(SmartTurnAsset.SHA256, sha256)
    }
}
