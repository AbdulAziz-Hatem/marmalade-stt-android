package app.marmalade.stt.debug

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.marmalade.stt.recognizer.MarmaladeRecognitionService
import app.marmalade.stt.ui.theme.MarmaladeSttTheme

/**
 * Debug-only caller for the speech entry points (#5): drives
 * `SpeechRecognizer` against our RecognitionService (by component, or the
 * device default) and fires `ACTION_RECOGNIZE_SPEECH`, logging every callback
 * and result on screen and in logcat (tag SpeechTest). Its text field is
 * somewhere to dictate into with the voice keyboard.
 */
class SpeechTestActivity : ComponentActivity() {

    private val log = mutableStateListOf<String>()
    private var recognizer: SpeechRecognizer? = null

    // One recognizer per target (key: ours?), reused: destroying one and
    // starting another at once races the system's unbinding
    // (ERROR_SERVER_DISCONNECTED).
    private val recognizers = mutableMapOf<Boolean, SpeechRecognizer>()

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        add("RECORD_AUDIO granted=$it")
    }

    private val recognizeSpeech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val results = it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        add("RECOGNIZE_SPEECH resultCode=${it.resultCode} results=$results")
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        setContent {
            MarmaladeSttTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
                        FlowRow {
                            Button(onClick = { listen(ourService = true) }, Modifier.padding(4.dp)) { Text("Listen: ours") }
                            Button(onClick = { listen(ourService = false) }, Modifier.padding(4.dp)) { Text("Listen: default") }
                            Button(onClick = { recognizer?.stopListening() }, Modifier.padding(4.dp)) { Text("Stop") }
                            Button(onClick = { recognizer?.cancel() }, Modifier.padding(4.dp)) { Text("Cancel") }
                            Button(onClick = ::recognizeIntent, Modifier.padding(4.dp)) { Text("RECOGNIZE_SPEECH") }
                        }
                        // A plain EditText, like most apps' text fields, to dictate
                        // into with the voice keyboard.
                        AndroidView(
                            factory = { context ->
                                EditText(context).apply {
                                    hint = "Type or dictate here"
                                    minLines = 3
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }

    private fun listen(ourService: Boolean) {
        recognizer?.cancel()
        val r = recognizers.getOrPut(ourService) {
            val created = if (ourService) {
                SpeechRecognizer.createSpeechRecognizer(this, ComponentName(this, MarmaladeRecognitionService::class.java))
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            created.apply { setRecognitionListener(listener) }
        }
        recognizer = r
        add("startListening (ours=$ourService)")
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
    }

    private fun recognizeIntent() {
        add("RECOGNIZE_SPEECH launched")
        recognizeSpeech.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .setPackage(packageName)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something to the test"),
        )
    }

    private val listener = object : RecognitionListener {
        private var rmsCount = 0
        override fun onReadyForSpeech(params: Bundle?) = add("onReadyForSpeech")
        override fun onBeginningOfSpeech() = add("onBeginningOfSpeech")
        override fun onRmsChanged(rmsdB: Float) {
            // Ten a second: log only every tenth.
            if (rmsCount++ % 10 == 0) add("onRmsChanged $rmsdB")
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = add("onEndOfSpeech")
        override fun onError(error: Int) = add("onError $error")
        override fun onResults(results: Bundle?) =
            add("onResults ${results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)}")
        override fun onPartialResults(partialResults: Bundle?) =
            add("onPartialResults ${partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)}")
        override fun onEvent(eventType: Int, params: Bundle?) = add("onEvent $eventType")
    }

    private fun add(line: String) {
        android.util.Log.i("SpeechTest", line)
        log += line
    }

    override fun onDestroy() {
        recognizers.values.forEach { it.destroy() }
        super.onDestroy()
    }
}
