cmake_minimum_required(VERSION 3.22)
project(dictator_whisper C CXX)

# whisper.cpp is pinned to a release tarball and verified by SHA-256 (ADR 0002). Bump the URL and
# the hash together; THIRD_PARTY_NOTICES.md names the same version.
include(FetchContent)
FetchContent_Declare(
    whisper
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz
    URL_HASH SHA256=ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
)

# Whisper is unusably slow unoptimised, so debug builds compile it optimised too.
set(CMAKE_C_FLAGS_DEBUG "-O3 -g0")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -g0")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
# Baseline ARMv8 / x86-64 code only, so the app runs on every supported phone (no SIGILL on
# Cortex-A53). Faster per-CPU variants are a measured follow-up (ADR 0002).
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(dictator_whisper SHARED whisper_jni.cpp)
target_link_libraries(dictator_whisper PRIVATE whisper android log)
target_compile_options(dictator_whisper PRIVATE -Wall -Wextra -O3)
target_link_options(dictator_whisper PRIVATE -Wl,--gc-sections "-Wl,-z,max-page-size=16384")
