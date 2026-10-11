package app.marmalade.stt.data

import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.speech.turn.SmartTurnAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Guards the in-app Open-source licenses screen against its silent
 * runtime-failure and compliance modes:
 *  1. a [LicenseCatalog.Component] referencing an unknown license id,
 *  2. a bundled text file that isn't actually present under
 *     `assets/licenses/` (the "view text" tap would fail at runtime),
 *  3. a component displaying a **shared** license body for a license that
 *     embeds the licensor's copyright (MIT/BSD), which would show the
 *     *wrong* copyright holder, and
 *  4. the catalog drifting from the repo-root NOTICE.md, and
 *  5. NOTICE.md's pinned facts (the bundled models' SHA-256s, the
 *     downloadable model files' SHA-256s, the ONNX Runtime release its
 *     notices belong to) drifting from what ships, and
 *  6. a downloadable engine linking to a license entry that doesn't exist.
 *
 * All of these would compile fine and only surface to a user or an auditor.
 *
 * Unit tests run with the module directory (`app/`) as the working dir, so
 * the asset files and the repo root are reachable on the filesystem.
 */
class LicenseCatalogTest {

    private val assetsDir = File("src/main/assets/licenses")
    private val repoRoot = File("..")

    /**
     * Licenses whose **text body contains no licensor copyright line**, so a
     * single shared body is safe to display for every component on them.
     * MIT and the BSD family are deliberately absent — their text embeds the
     * copyright, so each component must ship its own.
     */
    private val noEmbeddedCopyright =
        setOf(
            "GPL-3.0-or-later", "Apache-2.0", "MPL-2.0", "CC-BY-4.0", "CC-BY-SA-4.0", "CC0-1.0", "OFL-1.1",
            "CC-BY-2.0-FR",
            // Model licenses (#13): agreements with no licensor copyright line in them.
            "OpenMDW-1.1",
        )

    /**
     * License families whose per-component text is a compilation of many
     * notices (ONNX Runtime's ThirdPartyNotices.txt), each entry naming its
     * own holder. The catalog line describes the compilation rather than
     * quoting one holder, so it is exempt from the embedded-line check.
     */
    private val noticeCompilations = setOf("Third-party notices")

    @Test
    fun everyComponentMapsToAKnownLicense() {
        for (component in LicenseCatalog.components) {
            assertNotNull(
                "Component '${component.name}' references unknown license " +
                    "'${component.licenseId}'",
                LicenseCatalog.licenseFor(component.licenseId),
            )
        }
    }

    @Test
    fun licenseAndComponentIdsAreUnique() {
        val licenseIds = LicenseCatalog.licenses.map { it.id }
        assertEquals("Duplicate license ids", licenseIds.distinct().size, licenseIds.size)
        val keys = LicenseCatalog.components.map { it.key }
        assertEquals("Duplicate component keys", keys.distinct().size, keys.size)
    }

    @Test
    fun everyBundledTextExists() {
        for (license in LicenseCatalog.licenses) {
            val asset = license.sharedAsset ?: continue
            assertBundled(license.id, asset)
        }
        for (component in LicenseCatalog.components) {
            val asset = component.textAsset ?: continue
            assertBundled(component.name, asset)
        }
    }

    /** A license text nobody references is dead weight in the APK. */
    @Test
    fun everyBundledFileIsReferenced() {
        val referenced = LicenseCatalog.licenses.mapNotNull { it.sharedAsset }.toSet() +
            LicenseCatalog.components.mapNotNull { it.textAsset }
        val onDisk = assetsDir.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals("Unreferenced license texts in assets/licenses/", emptySet<String>(), onDisk - referenced)
    }

    @Test
    fun everyComponentHasAttribution() {
        for (component in LicenseCatalog.components) {
            assertTrue(
                "Component '${component.name}' has no copyright/attribution line",
                component.copyright.isNotEmpty(),
            )
        }
    }

    /**
     * The compliance invariant: a component may only fall back to a license's
     * *shared* body when that license carries no embedded copyright. Otherwise
     * it must supply its own [LicenseCatalog.Component.textAsset] with the
     * correct holder.
     */
    @Test
    fun noComponentShowsASharedBodyThatEmbedsCopyright() {
        for (component in LicenseCatalog.components) {
            val usesSharedBody = component.textAsset == null &&
                LicenseCatalog.licenseFor(component.licenseId)?.sharedAsset != null
            if (usesSharedBody) {
                assertTrue(
                    "Component '${component.name}' would display the shared " +
                        "'${component.licenseId}' body, but that license embeds a " +
                        "copyright holder — give it a per-component textAsset.",
                    component.licenseId in noEmbeddedCopyright,
                )
            }
        }
    }

    /**
     * Setting [LicenseCatalog.Component.textAsset] to a family's shared body
     * (e.g. a font pointing at `OFL-1.1.txt`) flags the body as embedding the
     * copyright, so the text screen hides the component's attribution — the
     * holder would appear nowhere in the license view.
     */
    @Test
    fun noComponentClaimsASharedBodyAsItsOwnText() {
        val sharedBodies = LicenseCatalog.licenses.mapNotNull { it.sharedAsset }.toSet()
        for (component in LicenseCatalog.components) {
            assertTrue(
                "Component '${component.name}' sets textAsset to the shared body " +
                    "'${component.textAsset}' — drop it so the attribution shows.",
                component.textAsset !in sharedBodies,
            )
        }
    }

    /**
     * Per-component texts embed one holder's copyright, so two components
     * sharing one means at least one shows the wrong holder.
     */
    @Test
    fun noTwoComponentsShareAPerComponentText() {
        val texts = LicenseCatalog.components.mapNotNull { it.textAsset }
        assertEquals(
            "A per-component license text is reused: " +
                texts.groupBy { it }.filterValues { it.size > 1 }.keys,
            texts.distinct().size,
            texts.size,
        )
    }

    /**
     * An embedded-copyright text must actually contain the holder the catalog
     * shows for it — catches a copied MIT.txt that still names another project.
     */
    @Test
    fun perComponentTextsEmbedTheCatalogCopyright() {
        for (component in LicenseCatalog.components) {
            val asset = component.textAsset ?: continue
            if (component.licenseId in noticeCompilations) continue
            val text = File(assetsDir, asset).readText()
            for (line in component.copyright) {
                assertTrue(
                    "licenses/$asset does not contain '${component.name}' copyright line '$line'",
                    text.contains(line),
                )
            }
        }
    }

    /** The app's own in-APK MIT text must match the repo LICENSE verbatim. */
    @Test
    fun appLicenseTextMatchesRepoLicense() {
        assertEquals(
            File(repoRoot, "LICENSE").readText(),
            File(assetsDir, "MIT.txt").readText(),
        )
    }

    @Test
    fun groupingCoversEveryComponent() {
        val grouped = LicenseCatalog.groupedByLicense()
        val groupedCount = grouped.sumOf { (_, members) -> members.size }
        assertEquals(
            "groupedByLicense() dropped or duplicated components",
            LicenseCatalog.components.size,
            groupedCount,
        )
    }

    @Test
    fun everyComponentShipsABundledText() {
        // Every component's full license text must be readable offline from the
        // APK — none may be online-only. (The license URLs remain only as the
        // "view online" affordance / metadata.)
        for (component in LicenseCatalog.components) {
            assertNotNull(
                "Component '${component.name}' has no bundled license text — " +
                    "all license texts must ship in the APK, not online-only.",
                component.resolvedAsset(),
            )
        }
    }

    /**
     * NOTICE.md is the repo-side mirror of this catalog. Each component and
     * its license must appear in it, so adding a component to one surface and
     * not the other fails here rather than in a license audit.
     */
    @Test
    fun noticeMentionsEveryComponentAndLicense() {
        val notice = File(repoRoot, "NOTICE.md").readText()
        for (component in LicenseCatalog.components) {
            assertTrue(
                "NOTICE.md does not mention component '${component.name}'",
                notice.contains(component.name),
            )
            assertTrue(
                "NOTICE.md does not mention license '${component.licenseId}' " +
                    "(used by '${component.name}')",
                notice.contains(component.licenseId),
            )
        }
    }

    /**
     * The bundled models (the VAD, the Granite formatter and Smart Turn) are pinned by
     * hash in NOTICE.md (their provenance statements). Swapping a file
     * without updating NOTICE.md fails here.
     */
    @Test
    fun noticePinsTheBundledModelHashes() {
        val notice = File(repoRoot, "NOTICE.md").readText()
        for (path in listOf("vad/silero_vad.onnx", "granite/formatter.int8.onnx", SmartTurnAsset.PATH)) {
            val model = File("src/main/assets/$path")
            assertTrue("Bundled model missing: ${model.path}", model.isFile)
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(model.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertTrue("NOTICE.md does not state the bundled $path SHA-256 $sha256", notice.contains(sha256))
        }
    }

    /**
     * The Engines screen opens a model's license via its
     * `SttEngineDescriptor.licenseKey`; an unknown key would crash that tap,
     * and a mismatched license id would show one license and open another.
     */
    @Test
    fun everyEngineLicenseKeyIsACatalogComponent() {
        for (engine in SttEngineCatalog.engines) {
            val component = LicenseCatalog.componentFor(engine.licenseKey)
            assertNotNull(
                "Engine '${engine.id}' licenseKey '${engine.licenseKey}' is not a LicenseCatalog component",
                component,
            )
            assertEquals(
                "Engine '${engine.id}' licenseId disagrees with its LicenseCatalog component",
                component!!.licenseId,
                engine.licenseId,
            )
        }
    }

    /**
     * NOTICE.md pins every downloadable model file by hash, like the bundled
     * VAD model. Re-pinning a model in [SttEngineCatalog] without updating
     * NOTICE.md fails here.
     */
    @Test
    fun noticePinsEveryDownloadableModelFileHash() {
        val notice = File(repoRoot, "NOTICE.md").readText()
        for (file in SttEngineCatalog.engines.flatMap { it.files }) {
            assertTrue(
                "NOTICE.md does not state ${file.name}'s SHA-256 ${file.sha256}",
                notice.contains(file.sha256),
            )
        }
    }

    /**
     * The Smart Turn end-of-dictation model (#43, bundled since #44) is not an
     * engine. NOTICE.md must state the pinned file, its hash and its Hugging
     * Face revision (the hash of the committed file is checked above), and
     * both texts must keep their own years: the model repo's LICENSE says
     * 2024–2025, pipecat's (the ported feature code) 2024–2026.
     */
    @Test
    fun noticePinsTheSmartTurnModel() {
        val notice = File(repoRoot, "NOTICE.md").readText()
        // The asset's pin (SmartTurnAsset) and NOTICE must name the same file.
        for (fact in listOf(SmartTurnAsset.fileName, SmartTurnAsset.SHA256, SmartTurnAsset.REVISION)) {
            assertTrue("NOTICE.md does not state Smart Turn's '$fact'", notice.contains(fact))
        }
        assertEquals("smart-turn.txt", LicenseCatalog.componentFor("smart-turn-v3")?.textAsset)
        assertEquals("pipecat.txt", LicenseCatalog.componentFor("pipecat-whisper-features")?.textAsset)
    }

    /**
     * The ONNX Runtime texts are per release. Bumping the Maven version in
     * app/build.gradle.kts without refreshing them fails here.
     */
    @Test
    fun onnxRuntimeNoticesMatchTheBuiltVersion() {
        val version = Regex("""val onnxRuntimeVersion = "([^"]+)"""")
            .find(File("build.gradle.kts").readText())
            ?.groupValues?.get(1)
        assertNotNull("onnxRuntimeVersion not found in app/build.gradle.kts", version)
        val notices = LicenseCatalog.componentFor("onnxruntime-notices")
        assertNotNull(notices)
        assertTrue(
            "ONNX Runtime notices entry is not for $version",
            notices!!.copyright.any { it.contains("ONNX Runtime $version") },
        )
        assertTrue(
            "Third-party notices URL is not for v$version",
            LicenseCatalog.licenseFor("Third-party notices")!!.url.contains("/v$version/"),
        )
        assertTrue(
            "NOTICE.md does not name ONNX Runtime $version",
            File(repoRoot, "NOTICE.md").readText().contains("ONNX Runtime $version"),
        )
    }

    private fun assertBundled(owner: String, asset: String) {
        val file = File(assetsDir, asset)
        assertTrue("Bundled license text missing for '$owner': ${file.path}", file.isFile)
        assertTrue("Bundled license text empty for '$owner': ${file.path}", file.length() > 0)
    }
}
