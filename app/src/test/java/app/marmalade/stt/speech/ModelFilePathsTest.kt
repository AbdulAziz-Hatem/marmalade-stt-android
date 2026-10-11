package app.marmalade.stt.speech

import app.marmalade.stt.install.FileRole
import app.marmalade.stt.install.SttEngineCatalog
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [modelFilePaths]: the check that runs before any native model load. */
class ModelFilePathsTest {

    @get:Rule
    val dir = TemporaryFolder()

    private val descriptor = SttEngineCatalog.default

    @Test
    fun `every file present - absolute paths by role`() {
        descriptor.files.forEach { dir.newFile(it.name) }

        val paths = modelFilePaths(descriptor, dir.root)

        assertEquals(descriptor.files.map { it.role }.toSet(), paths.keys)
        paths.forEach { (role, file) ->
            assertTrue(file.isAbsolute)
            assertEquals(descriptor.file(role).name, file.name)
        }
    }

    @Test
    fun `missing files are all named in the error`() {
        dir.newFile(descriptor.file(FileRole.TOKENS).name)

        try {
            modelFilePaths(descriptor, dir.root)
            fail("expected FileNotFoundException")
        } catch (e: FileNotFoundException) {
            val message = e.message.orEmpty()
            assertTrue(message, descriptor.file(FileRole.ENCODER).name in message)
            assertTrue(message, descriptor.file(FileRole.DECODER).name in message)
            assertFalse(message, descriptor.file(FileRole.TOKENS).name in message)
        }
    }
}
