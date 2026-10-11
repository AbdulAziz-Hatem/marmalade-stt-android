// CPU affinity for the calling thread (#31), for app.marmalade.stt.speech.CpuAffinity.
//
// ONNX Runtime can't pin its own threads on Android: the code behind the
// session.intra_op_thread_affinities option is compiled out there (ORT 1.28,
// core/platform/posix/env.cc). Threads inherit their creator's affinity,
// though, so pinning the thread that creates a session pins the ORT and
// XNNPACK thread pools created with it.
#define _GNU_SOURCE
#include <errno.h>
#include <jni.h>
#include <sched.h>

// Pins the calling thread to the CPUs in `cpus`. Returns 0, or errno.
JNIEXPORT jint JNICALL
Java_app_marmalade_stt_speech_CpuAffinity_nativeSet(JNIEnv *env, jclass cls, jintArray cpus) {
  (void)cls;
  cpu_set_t set;
  CPU_ZERO(&set);
  jsize n = (*env)->GetArrayLength(env, cpus);
  jint *ids = (*env)->GetIntArrayElements(env, cpus, NULL);
  if (ids == NULL) return ENOMEM;
  for (jsize i = 0; i < n; i++) {
    if (ids[i] >= 0 && ids[i] < CPU_SETSIZE) CPU_SET(ids[i], &set);
  }
  (*env)->ReleaseIntArrayElements(env, cpus, ids, JNI_ABORT);
  return sched_setaffinity(0, sizeof(set), &set) == 0 ? 0 : errno;
}

// The CPUs the calling thread may run on, or null on error.
JNIEXPORT jintArray JNICALL
Java_app_marmalade_stt_speech_CpuAffinity_nativeGet(JNIEnv *env, jclass cls) {
  (void)cls;
  cpu_set_t set;
  CPU_ZERO(&set);
  if (sched_getaffinity(0, sizeof(set), &set) != 0) return NULL;
  jint ids[CPU_SETSIZE];
  jsize n = 0;
  for (int cpu = 0; cpu < CPU_SETSIZE; cpu++) {
    if (CPU_ISSET(cpu, &set)) ids[n++] = cpu;
  }
  jintArray out = (*env)->NewIntArray(env, n);
  if (out != NULL) (*env)->SetIntArrayRegion(env, out, 0, n, ids);
  return out;
}
