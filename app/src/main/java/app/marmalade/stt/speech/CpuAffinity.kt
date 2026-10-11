package app.marmalade.stt.speech

import android.util.Log

/**
 * Pins the calling thread to chosen CPUs (#31), through our own small native
 * library (`app/src/main/cpp/cpu_affinity.c`).
 *
 * ONNX Runtime ignores `session.intra_op_thread_affinities` on Android, but
 * new threads inherit their creator's affinity. So [pinned] around a
 * recognizer's construction pins its ORT (and XNNPACK) thread pools, and
 * [pinned] around each decode pins the calling thread, which ORT uses as one
 * of the pool's workers.
 *
 * Android may reset a thread's affinity when it moves the app between cpusets
 * (foreground, background), so this is best effort.
 */
internal object CpuAffinity {
    private const val TAG = "CpuAffinity"

    private val loaded: Boolean by lazy {
        runCatching { System.loadLibrary("marmalade-cpu") }
            .onFailure { Log.w(TAG, "No affinity library; threads stay unpinned", it) }
            .isSuccess
    }

    /** The CPUs the calling thread may run on, or null if unknown. */
    fun get(): List<Int>? = if (loaded) nativeGet()?.toList() else null

    /** Pins the calling thread to [cpus]; false (and logged) when that fails. */
    fun set(cpus: List<Int>): Boolean {
        if (!loaded) return false
        val err = nativeSet(cpus.toIntArray())
        if (err != 0) Log.w(TAG, "sched_setaffinity($cpus) failed: errno $err")
        return err == 0
    }

    /**
     * Runs [block] with the calling thread pinned to [cpus], then restores its
     * previous affinity. With [cpus] null or empty, just runs [block].
     */
    inline fun <T> pinned(cpus: List<Int>?, block: () -> T): T {
        if (cpus.isNullOrEmpty()) return block()
        val before = get()
        val ok = before != null && set(cpus)
        try {
            return block()
        } finally {
            if (ok) set(before!!)
        }
    }

    @JvmStatic
    private external fun nativeSet(cpus: IntArray): Int

    @JvmStatic
    private external fun nativeGet(): IntArray?
}
