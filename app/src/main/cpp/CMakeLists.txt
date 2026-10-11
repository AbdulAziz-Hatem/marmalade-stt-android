# Native build: sherpa-onnx from source with TTS off (docs/ARCHITECTURE.md D3).
#
# Produces libsherpa-onnx-jni.so (ASR, VAD and feature extraction). Every
# third-party dependency is compiled in statically except ONNX Runtime, which
# is linked dynamically against the libonnxruntime.so from the
# com.microsoft.onnxruntime:onnxruntime-android Maven AAR. app/build.gradle.kts
# extracts that AAR's headers and .so files and passes the directory as ORT_DIR.
#
# Fully offline: sherpa-onnx fetches its dependencies with FetchContent. Each
# one is a git submodule under third_party/, pinned to the exact tag or commit
# sherpa-onnx v1.13.8 asks for. FETCHCONTENT_FULLY_DISCONNECTED makes any
# dependency we missed fail the configure step instead of downloading it.
cmake_minimum_required(VERSION 3.22.1)
project(marmalade_stt_native C CXX)

if(NOT DEFINED THIRD_PARTY_DIR OR NOT DEFINED ORT_DIR)
  message(FATAL_ERROR "THIRD_PARTY_DIR and ORT_DIR must be passed in by app/build.gradle.kts")
endif()
if(NOT EXISTS "${THIRD_PARTY_DIR}/sherpa-onnx/CMakeLists.txt")
  message(FATAL_ERROR "Submodules missing. Run: git submodule update --init")
endif()

# sherpa-onnx's cmake/onnxruntime.cmake reads these two environment variables
# at configure time. Setting them skips its ONNX Runtime download.
set(ENV{SHERPA_ONNXRUNTIME_INCLUDE_DIR} "${ORT_DIR}/headers")
set(ENV{SHERPA_ONNXRUNTIME_LIB_DIR} "${ORT_DIR}/jni/${ANDROID_ABI}")

set(FETCHCONTENT_FULLY_DISCONNECTED ON CACHE BOOL "" FORCE)
# FetchContent name (as passed to FetchContent_Declare, upper-cased) -> submodule.
# kaldi-native-fbank is patched, so it is pointed at its patched copy below.
foreach(pair
    KISSFFT=kissfft
    KALDI_DECODER=kaldi-decoder
    KALDIFST=kaldifst
    OPENFST=openfst
    EIGEN=eigen
    JSON=json)
  string(REPLACE "=" ";" kv "${pair}")
  list(GET kv 0 name)
  list(GET kv 1 dir)
  set(FETCHCONTENT_SOURCE_DIR_${name} "${THIRD_PARTY_DIR}/${dir}" CACHE PATH "" FORCE)
endforeach()

# On Android, sherpa-onnx's cmake/simple-sentencepiece.cmake rewrites three
# simple-sentencepiece sources in place (it strips the thread pool). Give it a
# fresh copy in the build tree so the submodule's working tree stays clean.
set(_ssp_copy "${CMAKE_BINARY_DIR}/simple-sentencepiece-src")
file(REMOVE_RECURSE "${_ssp_copy}")
file(COPY "${THIRD_PARTY_DIR}/simple-sentencepiece/" DESTINATION "${_ssp_copy}"
     PATTERN ".git" EXCLUDE)
set(FETCHCONTENT_SOURCE_DIR_SIMPLE-SENTENCEPIECE "${_ssp_copy}" CACHE PATH "" FORCE)
# Reproducible builds: the copy sits under .cxx/<buildType>/<hash>/, and AGP
# derives that hash from the configure arguments, which hold absolute paths.
# Its __FILE__ strings (assert messages) would then differ between checkouts.
# Map the copy back to the submodule's repo-relative path. Clang tries the
# longer prefix first, so this wins over the repo-root map from Gradle.
add_compile_options("-ffile-prefix-map=${_ssp_copy}=third_party/simple-sentencepiece")

# Static sherpa-onnx and dependencies inside one JNI library.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
# TTS OFF is the point of this build: it drops espeak-ng (GPL-3.0) and
# piper-phonemize. The other options are features and tools the app doesn't use.
foreach(opt TTS SPEAKER_DIARIZATION C_API BINARY WEBSOCKET PORTAUDIO PYTHON TESTS CHECK)
  set(SHERPA_ONNX_ENABLE_${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(SHERPA_ONNX_ENABLE_JNI ON CACHE BOOL "" FORCE)

# Patches not yet in a release (third_party/patches/<name>/README.md) are
# applied to a copy of the sources in the build tree, so each submodule stays
# at its tag. patched_copy(<name> <copy dir> <paths...>) copies the given
# paths of third_party/<name> to <copy dir> and applies
# third_party/patches/<name>/*.patch in file-name order.
#
# The copy is only refreshed when this configure step runs, so every input of
# it is a configure dependency: the patches and the copied sources (a
# submodule bump or a local edit). CONFIGURE_DEPENDS re-runs the step when a
# file is added or removed; the property list when one changes.
#
# Reproducible builds, as for the simple-sentencepiece copy above: __FILE__
# strings (log macros) become repo-relative third_party/<name>/ paths, the
# same in every checkout. A patched file's strings name the submodule's file,
# but its __LINE__ numbers are the patched copy's.
find_program(PATCH_EXECUTABLE patch REQUIRED)
function(patched_copy name copy)
  set(src "${THIRD_PARTY_DIR}/${name}")
  file(GLOB patches CONFIGURE_DEPENDS "${THIRD_PARTY_DIR}/patches/${name}/*.patch")
  list(SORT patches)
  set(globs "")
  set(paths "")
  foreach(rel ${ARGN})
    list(APPEND paths "${src}/${rel}")
    if(IS_DIRECTORY "${src}/${rel}")
      list(APPEND globs "${src}/${rel}/*")
    else()
      list(APPEND globs "${src}/${rel}")
    endif()
  endforeach()
  file(GLOB_RECURSE sources CONFIGURE_DEPENDS ${globs})
  set_property(DIRECTORY "${CMAKE_SOURCE_DIR}" APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${patches} ${sources})
  file(REMOVE_RECURSE "${copy}")
  file(COPY ${paths} DESTINATION "${copy}")
  foreach(p ${patches})
    # --fuzz=0: a patch whose context no longer matches exactly fails here
    # instead of landing a few lines off.
    execute_process(
      COMMAND "${PATCH_EXECUTABLE}" -p1 --forward --batch --fuzz=0 --no-backup-if-mismatch --quiet -i "${p}"
      WORKING_DIRECTORY "${copy}" RESULT_VARIABLE rc)
    if(NOT rc EQUAL 0)
      message(FATAL_ERROR "${name} patch ${p} does not apply to third_party/${name}")
    endif()
  endforeach()
  add_compile_options("-ffile-prefix-map=${copy}=third_party/${name}")
endfunction()

# Only the parts of sherpa-onnx that CMake reads are copied.
set(_sherpa_copy "${CMAKE_BINARY_DIR}/sherpa-onnx-src")
patched_copy(sherpa-onnx "${_sherpa_copy}" CMakeLists.txt cmake sherpa-onnx)
# kaldi-native-fbank (#53: the Whisper log-mel FFT), the parts CMake reads.
set(_knf_copy "${CMAKE_BINARY_DIR}/kaldi-native-fbank-src")
patched_copy(kaldi-native-fbank "${_knf_copy}" CMakeLists.txt cmake kaldi-native-fbank LICENSE)
set(FETCHCONTENT_SOURCE_DIR_KALDI_NATIVE_FBANK "${_knf_copy}" CACHE PATH "" FORCE)

add_subdirectory("${_sherpa_copy}" sherpa-onnx)

# Our own ~40-line helper (#31): pins a thread to chosen CPUs so the ONNX
# Runtime thread pools created on it run on the fast cores (cpu_affinity.c).
add_library(marmalade-cpu SHARED cpu_affinity.c)
