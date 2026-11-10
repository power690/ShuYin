cmake_minimum_required(VERSION 3.10.2)
project("native-blur")

add_library(native-blur SHARED blur.cpp)

target_link_libraries(native-blur
    android
    log
    jnigraphics)

target_link_options(native-blur PRIVATE "-Wl,-z,max-page-size=16384")

set(ape_root ${CMAKE_CURRENT_SOURCE_DIR}/ape)
set(maclib_root ${ape_root}/maclib)

set(maclib_sources
    ${maclib_root}/Source/MACLib/APECompress.cpp
    ${maclib_root}/Source/MACLib/APECompressCore.cpp
    ${maclib_root}/Source/MACLib/APECompressCreate.cpp
    ${maclib_root}/Source/MACLib/APEDecompress.cpp
    ${maclib_root}/Source/MACLib/APEDecompressCore.cpp
    ${maclib_root}/Source/MACLib/APEHeader.cpp
    ${maclib_root}/Source/MACLib/APEInfo.cpp
    ${maclib_root}/Source/MACLib/APELink.cpp
    ${maclib_root}/Source/MACLib/APETag.cpp
    ${maclib_root}/Source/MACLib/BitArray.cpp
    ${maclib_root}/Source/MACLib/FloatTransform.cpp
    ${maclib_root}/Source/MACLib/Interim.cpp
    ${maclib_root}/Source/MACLib/MACLib.cpp
    ${maclib_root}/Source/MACLib/MACProgressHelper.cpp
    ${maclib_root}/Source/MACLib/MD5.cpp
    ${maclib_root}/Source/MACLib/NewPredictor.cpp
    ${maclib_root}/Source/MACLib/NNFilter.cpp
    ${maclib_root}/Source/MACLib/NNFilterGeneric.cpp
    ${maclib_root}/Source/MACLib/Prepare.cpp
    ${maclib_root}/Source/MACLib/UnBitArray.cpp
    ${maclib_root}/Source/MACLib/UnBitArrayBase.cpp
    ${maclib_root}/Source/MACLib/WAVInputSource.cpp
    ${maclib_root}/Source/MACLib/Old/APEDecompressCoreOld.cpp
    ${maclib_root}/Source/MACLib/Old/APEDecompressOld.cpp
    ${maclib_root}/Source/MACLib/Old/AntiPredictorExtraHighOld.cpp
    ${maclib_root}/Source/MACLib/Old/AntiPredictorFastOld.cpp
    ${maclib_root}/Source/MACLib/Old/AntiPredictorHighOld.cpp
    ${maclib_root}/Source/MACLib/Old/AntiPredictorNormalOld.cpp
    ${maclib_root}/Source/MACLib/Old/AntiPredictorOld.cpp
    ${maclib_root}/Source/MACLib/Old/NewPredictorOld.cpp
    ${maclib_root}/Source/MACLib/Old/UnBitArrayOld.cpp
    ${maclib_root}/Source/MACLib/Old/UnMACOld.cpp
    ${maclib_root}/Source/Shared/BufferIO.cpp
    ${maclib_root}/Source/Shared/CharacterHelper.cpp
    ${maclib_root}/Source/Shared/CircleBuffer.cpp
    ${maclib_root}/Source/Shared/CPUFeatures.cpp
    ${maclib_root}/Source/Shared/CRC.cpp
    ${maclib_root}/Source/Shared/GlobalFunctions.cpp
    ${maclib_root}/Source/Shared/MemoryIO.cpp
    ${maclib_root}/Source/Shared/Semaphore.cpp
    ${maclib_root}/Source/Shared/StdLibFileIO.cpp
    ${maclib_root}/Source/Shared/Thread.cpp
    ${maclib_root}/Source/Shared/WholeFileIO.cpp)

if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    list(APPEND maclib_sources ${maclib_root}/Source/MACLib/NNFilterNeon.cpp)
    if(ANDROID_ABI STREQUAL "armeabi-v7a")
        set_source_files_properties(${maclib_root}/Source/MACLib/NNFilterNeon.cpp
            PROPERTIES COMPILE_FLAGS "-mfpu=neon")
    endif()
elseif(ANDROID_ABI STREQUAL "x86_64" OR ANDROID_ABI STREQUAL "x86")
    list(APPEND maclib_sources
        ${maclib_root}/Source/MACLib/NNFilterSSE2.cpp
        ${maclib_root}/Source/MACLib/NNFilterSSE4.1.cpp
        ${maclib_root}/Source/MACLib/NNFilterAVX2.cpp
        ${maclib_root}/Source/MACLib/NNFilterAVX512.cpp)
    set_source_files_properties(${maclib_root}/Source/MACLib/NNFilterSSE2.cpp
        PROPERTIES COMPILE_FLAGS "-msse2")
    set_source_files_properties(${maclib_root}/Source/MACLib/NNFilterSSE4.1.cpp
        PROPERTIES COMPILE_FLAGS "-msse4.1")
    set_source_files_properties(${maclib_root}/Source/MACLib/NNFilterAVX2.cpp
        PROPERTIES COMPILE_FLAGS "-mavx2")
    set_source_files_properties(${maclib_root}/Source/MACLib/NNFilterAVX512.cpp
        PROPERTIES COMPILE_FLAGS "-mavx512dq -mavx512bw")
endif()

set_source_files_properties(${maclib_sources} PROPERTIES
    COMPILE_OPTIONS "-Wno-inconsistent-missing-override")

add_library(apeJNI SHARED
    ${ape_root}/ape_jni.cc
    ${ape_root}/ape_io_bridge.cc
    ${maclib_sources})

target_compile_options(apeJNI PRIVATE -fexceptions -frtti)
target_link_options(apeJNI PRIVATE "-Wl,-z,max-page-size=16384")

target_include_directories(apeJNI PRIVATE
    ${maclib_root}/Source/Shared
    ${maclib_root}/Source/MACLib)

find_library(android_log_lib log)
target_link_libraries(apeJNI PRIVATE ${android_log_lib})
set_target_properties(apeJNI PROPERTIES CXX_STANDARD 11)

set(flac_root ${CMAKE_CURRENT_SOURCE_DIR}/flac)

set(BUILD_CXXLIBS OFF CACHE BOOL "" FORCE)
set(BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(INSTALL_MANPAGES OFF CACHE BOOL "" FORCE)
set(INSTALL_PKGCONFIG_MODULES OFF CACHE BOOL "" FORCE)
set(INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(WITH_OGG OFF CACHE BOOL "" FORCE)
set(WITH_FORTIFY_SOURCE OFF CACHE BOOL "" FORCE)
set(WITH_STACK_PROTECTOR OFF CACHE BOOL "" FORCE)
add_subdirectory(${flac_root}/libflac EXCLUDE_FROM_ALL)

add_library(flacJNI SHARED
    ${flac_root}/flac_jni.cc
    ${flac_root}/flac_parser.cc)

target_include_directories(flacJNI PRIVATE
    ${flac_root}/include
    ${flac_root}/libflac/include)

target_link_libraries(flacJNI PRIVATE
    android
    FLAC
    ${android_log_lib})

target_link_options(flacJNI PRIVATE "-Wl,-z,max-page-size=16384")
set_target_properties(flacJNI PROPERTIES CXX_STANDARD 11)
