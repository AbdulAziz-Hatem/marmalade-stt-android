package app.marmalade.stt

import app.marmalade.stt.data.SpeechSettings
import app.marmalade.stt.install.ModelInstaller
import app.marmalade.stt.speech.SpeedCalibrator
import kotlinx.coroutines.Job

/**
 * Cleans up after engines that left the app (#45), at startup: deletes their
 * files ([ModelInstaller.removeUnknownModels]), forgets stored ids of them
 * ([SpeechSettings.forgetUnknownEngines]) and, when the selection or the
 * recommendation was one of them, recommends again
 * ([SpeedCalibrator.recommendAgain], which pins an installed engine that is
 * running rather than switching to one that isn't downloaded). Also
 * recommends again when the stored recommendation was chosen under older
 * rules ([app.marmalade.stt.data.SpeechPrefs.recommendationOutdated], #50).
 * Returns that recommendation's job, or null when none was needed.
 */
internal suspend fun cleanUpRemovedEngines(
    installer: ModelInstaller,
    settings: SpeechSettings,
    calibrator: SpeedCalibrator,
): Job? {
    installer.removeUnknownModels()
    val choiceGone = settings.forgetUnknownEngines()
    val outdated = settings.current().recommendationOutdated
    return if (choiceGone || outdated) calibrator.recommendAgain() else null
}
