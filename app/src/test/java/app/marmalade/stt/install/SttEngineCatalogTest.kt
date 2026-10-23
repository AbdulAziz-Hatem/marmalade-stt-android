package app.marmalade.stt.install

import app.marmalade.stt.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped catalog is what the installer trusts blindly: a typo in a hash,
 * an unpinned `/main/` URL or a file name that escapes its directory would
 * only show up on a user's phone.
 */
class SttEngineCatalogTest {

    private val engines = SttEngineCatalog.engines

    private companion object {
        const val OUR_RELEASES = "https://github.com/maxwhipw/marmalade-stt-engines/releases/download/"
        val GRAPH_ROLES = setOf(FileRole.ENCODER, FileRole.DECODER, FileRole.JOINER, FileRole.CONV_FRONTEND)
    }

    @Test
    fun idsAreUniqueAndSafeAsDirectoryNames() {
        assertTrue(engines.isNotEmpty())
        assertEquals(engines.size, engines.map { it.id }.toSet().size)
        engines.forEach { assertTrue("bad id ${it.id}", it.id.matches(Regex("[a-z0-9][a-z0-9-]*"))) }
        assertTrue(SttEngineCatalog.default in engines)
        engines.forEach { assertEquals(it, SttEngineCatalog.byId(it.id)) }
    }

    /** An engine's file sets: its download, and the previous set older versions installed (#78) if any. */
    private fun SttEngineDescriptor.fileSets(): List<List<ModelFile>> = listOfNotNull(files, previousFiles.ifEmpty { null })

    private fun tagOf(f: ModelFile) = f.url.substringAfter(OUR_RELEASES).substringBefore('/')

    @Test
    fun everyFileIsPinnedHashedAndSized() {
        for (engine in engines) for (set in engine.fileSets()) {
            assertTrue(set.isNotEmpty())
            assertEquals(set.size, set.map { it.name }.toSet().size)
            for (f in set) {
                val where = "${engine.id}/${f.name}"
                assertTrue("$where name", f.name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")))
                assertTrue("$where sha256", f.sha256.matches(Regex("[0-9a-f]{64}")))
                assertTrue("$where size", f.sizeBytes > 0)
                assertTrue("$where https", f.url.startsWith("https://"))
                assertTrue("$where not /main/", !f.url.contains("/main/"))
                // Every file is a release asset of our engines repo (#39, #75): pinned by
                // release tag, named `<engine id>-<name>`. No download goes to Hugging Face.
                assertTrue("$where is on our engines repo", f.url.startsWith(OUR_RELEASES))
                assertTrue("$where pinned tag", f.url.contains(Regex("/download/v[0-9]+/")))
                assertTrue("$where url names the file", f.url.endsWith("/${engine.id}-${f.name}"))
                // ONNX Runtime graphs: the .onnx exports, or pre-optimised ORT-format files (#78).
                if (f.role in GRAPH_ROLES) assertTrue("$where is .onnx or .ort", f.name.matches(Regex(".*\\.(onnx|ort)")))
            }
        }
    }

    /**
     * Previous files (#78) keep an older version's install running. They must
     * be a set the catalog really shipped in another release, and a name in
     * both sets must be the same file: install trusts a verified file already
     * in staging by its name and size.
     */
    @Test
    fun previousFilesAreAnotherReleaseAndShareOnlyIdenticalFiles() {
        for (engine in engines.filter { it.previousFiles.isNotEmpty() }) {
            val previous = engine.previousFiles
            assertTrue(engine.id, previous.toSet() != engine.files.toSet())
            assertTrue("${engine.id}: new files get a new tag", previous.map(::tagOf).toSet() != engine.files.map(::tagOf).toSet())
            for (p in previous) {
                val current = engine.files.firstOrNull { it.name == p.name } ?: continue
                assertEquals("${engine.id}/${p.name} sha256", p.sha256, current.sha256)
                assertEquals("${engine.id}/${p.name} size", p.sizeBytes, current.sizeBytes)
            }
        }
    }

    @Test
    fun replacingFilesKeepsTheOldSetAsPrevious() {
        val ultra = SttEngineCatalog.default
        val ort = ultra.files.map { f ->
            if (f.role == FileRole.TOKENS) f
            else f.copy(name = f.name.replace(".onnx", ".ort"), url = f.url.replace("/v1/", "/v99/").replace(".onnx", ".ort"))
        }
        val switched = with(SttEngineCatalog) { ultra.replacingFiles(ort) }
        assertEquals(ort, switched.files)
        assertEquals(ultra.files, switched.previousFiles)
        assertEquals(ultra.copy(files = ort, previousFiles = ultra.files), switched)
    }

    /**
     * One release per engine (#75), so an engine's files can change without touching
     * another's tag. Qwen3-ASR's developer variant shares the user entry's release.
     */
    @Test
    fun eachEngineHasItsOwnRelease() {
        val tags = engines.associate { e -> e.id to e.files.map(::tagOf).toSet() }
        tags.forEach { (id, t) -> assertEquals("$id tags", 1, t.size) }
        assertEquals(engines.size, tags.values.map { it.single() }.toSet().size)
        // An engine's previous files (#78) are one release too, and no other engine's.
        val current = tags.values.map { it.single() }.toSet()
        for (engine in engines.filter { it.previousFiles.isNotEmpty() }) {
            val previous = engine.previousFiles.map(::tagOf).toSet()
            assertEquals("${engine.id} previous tags", 1, previous.size)
            assertTrue("${engine.id} previous tag is no engine's current one", previous.single() !in current)
        }
    }

    /** The roles each family's loader (SherpaBackends.kt, GraniteTranscriber.kt) looks up; a missing one would fail on device. */
    @Test
    fun everyEngineHasExactlyTheFilesItsFamilyNeeds() {
        val needs = mapOf(
            EngineFamily.WHISPER to listOf(FileRole.ENCODER, FileRole.DECODER, FileRole.TOKENS),
            EngineFamily.NEMO_TRANSDUCER to listOf(FileRole.ENCODER, FileRole.DECODER, FileRole.JOINER, FileRole.TOKENS),
            EngineFamily.NEMO_CTC to listOf(FileRole.ENCODER, FileRole.TOKENS),
            EngineFamily.CANARY to listOf(FileRole.ENCODER, FileRole.DECODER, FileRole.TOKENS),
            EngineFamily.QWEN3_ASR to listOf(
                FileRole.ENCODER, FileRole.DECODER, FileRole.CONV_FRONTEND,
                FileRole.TOKENIZER_VOCAB, FileRole.TOKENIZER_MERGES, FileRole.TOKENIZER_CONFIG,
            ),
            EngineFamily.STREAMING_TRANSDUCER to
                listOf(FileRole.ENCODER, FileRole.DECODER, FileRole.JOINER, FileRole.TOKENS),
            EngineFamily.GRANITE_CTC to listOf(FileRole.ENCODER, FileRole.TOKENS),
        )
        assertEquals(EngineFamily.entries.toSet(), needs.keys)
        for (engine in engines) for (set in engine.fileSets()) {
            assertEquals(engine.id, needs.getValue(engine.family).sorted(), set.map { it.role }.sorted())
        }
    }

    /** sherpa's Qwen3-ASR tokenizer is a directory holding these exact names; they are installed flat. */
    @Test
    fun qwenTokenizerFilesKeepTheirUpstreamNames() {
        for (engine in engines.filter { it.family == EngineFamily.QWEN3_ASR }) for (set in engine.fileSets()) {
            val names = set.associate { it.role to it.name }
            assertEquals("vocab.json", names[FileRole.TOKENIZER_VOCAB])
            assertEquals("merges.txt", names[FileRole.TOKENIZER_MERGES])
            assertEquals("tokenizer_config.json", names[FileRole.TOKENIZER_CONFIG])
        }
    }

    /**
     * Max's v1 lineup (2026-10-02, #45), in list order: everyday engines first, experimental last.
     * Nemotron 3.5's 560 ms export left in #37.
     */
    @Test
    fun theCatalogIsTheV1Lineup() {
        assertEquals(
            listOf(
                SttEngineCatalog.PARAKEET_ULTRA, "parakeet-tdt-110m-en", SttEngineCatalog.PARAKEET_JA,
                "canary-180m-flash", SttEngineCatalog.NEMOTRON_35_1120MS, "whisper-turbo",
                "granite-speech-5-470m", "qwen3-asr-0-6b",
            ),
            engines.map { it.id },
        )
    }

    @Test
    fun theUserLineup() {
        // Parakeet Ultra is the main engine, Parakeet 110M the small option, Parakeet-ja the Japanese
        // one (#60), Canary the tiny four-language one, Nemotron 3.5 streams Hindi, Arabic and
        // Vietnamese (#37), and Whisper turbo covers the rest. Qwen3-ASR joined them for Chinese and Korean (#50),
        // and for all 30 of its languages in #88.
        assertEquals(SttEngineCatalog.PARAKEET_ULTRA, SttEngineCatalog.default.id)
        val nonDeveloper = setOf(
            SttEngineCatalog.PARAKEET_ULTRA,
            "parakeet-tdt-110m-en",
            SttEngineCatalog.PARAKEET_JA,
            "canary-180m-flash",
            SttEngineCatalog.NEMOTRON_35_1120MS,
            "whisper-turbo",
            SttEngineCatalog.QWEN3,
        )
        assertEquals(nonDeveloper, engines.filterNot { it.developer }.map { it.id }.toSet())
        assertEquals(
            setOf("granite-speech-5-470m"),
            engines.filter { it.developer }.map { it.id }.toSet(),
        )
        assertEquals(nonDeveloper, SttEngineCatalog.visibleTo(showDeveloper = false).map { it.id }.toSet())
        assertEquals(engines, SttEngineCatalog.visibleTo(showDeveloper = true))
    }

    /** #67 (Max's v1 review): clean names, no underscores or "(English)", all distinct. */
    @Test
    fun displayNamesAreCleanAndDistinct() {
        engines.forEach { engine ->
            val name = engine.displayName
            assertTrue("$name: no underscore", '_' !in name)
            assertTrue("$name: no parenthetical", '(' !in name && ')' !in name)
            assertEquals("$name: trimmed, single spaces", name.trim().replace(Regex("\\s+"), " "), name)
        }
        assertEquals(engines.size, engines.map { it.displayName.lowercase() }.distinct().size)
        assertEquals(
            listOf(
                "Parakeet Ultra", "Parakeet 110M", "Parakeet Japanese", "Canary 180M Flash", "Nemotron 3.5",
                "Whisper Large v3 Turbo", "Granite Speech", "Qwen3-ASR",
            ),
            engines.map { it.displayName },
        )
    }

    /** One Nemotron 3.5 export since #37 (Max, 2026-10-02): 1120 ms, for Hindi, Arabic and Vietnamese. */
    @Test
    fun nemotron35IsThe1120msExportForHindiArabicAndVietnamese() {
        val nemotron = SttEngineCatalog.byId(SttEngineCatalog.NEMOTRON_35_1120MS)!!
        assertEquals("Nemotron 3.5", nemotron.displayName)
        assertEquals(1120, nemotron.chunkMs)
        assertEquals(false, nemotron.developer)
        assertEquals(R.string.engine_desc_nemotron_35, nemotron.description)
        assertEquals(setOf("hi", "ar", "vi"), EngineLanguages.of(nemotron))
        assertEquals(listOf(LanguageOptions.AUTO, "ar", "hi", "vi"), nemotron.languageOptions?.codes)
        assertEquals(listOf(nemotron), engines.filter { it.displayName.startsWith("Nemotron") })
        assertEquals(nemotron, SttEngineCatalog.effective(nemotron.id, showDeveloper = false))
    }

    /** #89: a developer engine runs only with Developer engines on, even as a stored recommendation. */
    @Test
    fun aDeveloperEngineNeverRunsWithDeveloperEnginesOff() {
        val granite = SttEngineCatalog.byId("granite-speech-5-470m")!! // a developer engine
        assertEquals(false, granite in SttEngineCatalog.visibleTo(showDeveloper = false))
        assertEquals(SttEngineCatalog.default, SttEngineCatalog.effective(granite.id, showDeveloper = false))
        assertEquals(SttEngineCatalog.default, SttEngineCatalog.effective(granite.id, showDeveloper = false, recommendedId = granite.id))
        assertEquals(SttEngineCatalog.default, SttEngineCatalog.effective(null, showDeveloper = false, recommendedId = granite.id))
        assertEquals(granite, SttEngineCatalog.effective(null, showDeveloper = true, recommendedId = granite.id))
    }

    @Test
    fun withNothingSelectedTheRecommendedEngineRuns() {
        val nemotron = SttEngineCatalog.byId(SttEngineCatalog.NEMOTRON_35_1120MS)!!
        assertEquals(nemotron, SttEngineCatalog.effective(null, showDeveloper = false, recommendedId = nemotron.id))
        // A hidden developer pick falls back to the recommendation too.
        assertEquals(nemotron, SttEngineCatalog.effective("granite-speech-5-470m", showDeveloper = false, recommendedId = nemotron.id))
        // The user's own visible pick wins.
        assertEquals(
            SttEngineCatalog.byId("parakeet-tdt-110m-en"),
            SttEngineCatalog.effective("parakeet-tdt-110m-en", showDeveloper = false, recommendedId = nemotron.id),
        )
        assertEquals(SttEngineCatalog.default, SttEngineCatalog.effective(null, showDeveloper = false, recommendedId = "gone"))
    }

    @Test
    fun effectiveEngineHidesDeveloperEnginesWhenTheToggleIsOff() {
        val dev = engines.first { it.developer }
        assertEquals(dev, SttEngineCatalog.effective(dev.id, showDeveloper = true))
        assertEquals(SttEngineCatalog.default, SttEngineCatalog.effective(dev.id, showDeveloper = false))
        assertEquals(SttEngineCatalog.default, SttEngineCatalog.effective("no-such-engine", showDeveloper = true))
        assertEquals(SttEngineCatalog.default, SttEngineCatalog.effective(null, showDeveloper = true))
    }

    @Test
    fun parakeet110mIsTheSmallEnglishEngine() {
        val engine = SttEngineCatalog.byId("parakeet-tdt-110m-en")!!
        assertEquals(false, engine.developer)
        assertEquals(listOf("en"), engine.languages)
        assertEquals(null, engine.languageOptions)
    }

    /** Parakeet Ultra (#25) is our own conversion, hosted on our engines repo (#39), and the default (#45). */
    @Test
    fun parakeetUltraIsTheDefaultFromOurRelease() {
        val ultra = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA)!!
        assertEquals(false, ultra.developer)
        assertTrue(ultra in SttEngineCatalog.visibleTo(showDeveloper = false))
        assertEquals(EngineFamily.NEMO_TRANSDUCER, ultra.family)
        for (f in ultra.files) assertEquals("${OUR_RELEASES}v1/parakeet-ultra-${f.name}", f.url)
    }

    @Test
    fun streamingEnginesAreTheStreamingFamilyAndOnlyTheHeavyDecodersSkipLivePartials() {
        for (engine in engines) {
            assertEquals(engine.id, engine.id !in setOf("whisper-turbo", "qwen3-asr-0-6b"), engine.livePartials)
            assertEquals(engine.id, engine.id.contains("streaming"), engine.family.streaming)
        }
    }

    @Test
    fun languageOptionsDefaultToAutoWhereTheModelDetectsAndEnglishWhereItCannot() {
        for (engine in engines) {
            val options = engine.languageOptions ?: continue
            // ISO 639 codes: two letters, or three where there is no two-letter one (Qwen3's yue and fil, #88).
            assertTrue(engine.id, options.codes.all { it == LanguageOptions.AUTO || it.matches(Regex("[a-z]{2,3}")) })
        }
        assertEquals("en", SttEngineCatalog.byId("canary-180m-flash")!!.languageOptions!!.default)
        assertEquals(LanguageOptions.AUTO, SttEngineCatalog.byId("whisper-turbo")!!.languageOptions!!.default)
        // English-only models take no hint.
        assertEquals(null, SttEngineCatalog.byId("parakeet-tdt-110m-en")!!.languageOptions)
        assertEquals(null, SttEngineCatalog.byId("granite-speech-5-470m")!!.languageOptions)
    }
}
