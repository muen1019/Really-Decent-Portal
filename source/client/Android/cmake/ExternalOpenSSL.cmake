# SPDX-License-Identifier: Apache-2.0
# Modified for the DeskQuay Android client; see the versioned source and change history.
# Distribution change notice: 2026-10-29.
# Upstream basis: FreeRDP 3.32.2 commit 97eff6ddfbbfaa8e42a126d362686bd001ff8bdc.
# Existing copyright and attribution notices are retained.

include(ExternalProject)
include(DepVersions)
find_package(Python3 REQUIRED COMPONENTS Interpreter)

# Map ANDROID_ABI to OpenSSL architecture names
if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(OSSL_ARCH "android-arm64")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
  set(OSSL_ARCH "android-arm")
elseif(ANDROID_ABI STREQUAL "x86_64")
  set(OSSL_ARCH "android-x86_64")
elseif(ANDROID_ABI STREQUAL "x86")
  set(OSSL_ARCH "android-x86")
elseif(ANDROID_ABI STREQUAL "riscv64")
  set(OSSL_ARCH "android-riscv64")
else()
  message(FATAL_ERROR "ExternalOpenSSL: unsupported ABI '${ANDROID_ABI}'")
endif()

set(NDK_TOOLCHAIN_BIN "${NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_PLATFORM}/bin")

if(CMAKE_HOST_WIN32)
  # OpenSSL's Android target needs a POSIX Perl/make environment even when the
  # NDK compiler is a Windows executable. Do not prepend a Unix ':' to PATH in
  # a native Windows process, or mix Strawberry Perl with MSYS makefile paths.
  set(FREERDP_OPENSSL_WINDOWS_HELPER "${CMAKE_CURRENT_LIST_DIR}/../tools/openssl_windows_host.py" CACHE FILEPATH "Windows MSYS2 OpenSSL host adapter")
  set(FREERDP_MSYS_ROOT "" CACHE PATH "Existing MSYS2 root (not modified by this build)")
  set(FREERDP_MSYS_MAKE "${FREERDP_MSYS_ROOT}/usr/bin/make.exe" CACHE FILEPATH "Verified MSYS GNU make")
  set(FREERDP_OPENSSL_BUILD_JOBS "2" CACHE STRING "OpenSSL host build parallelism")
  if(NOT EXISTS "${FREERDP_OPENSSL_WINDOWS_HELPER}" OR NOT EXISTS "${FREERDP_MSYS_ROOT}/usr/bin/bash.exe"
     OR NOT EXISTS "${FREERDP_MSYS_MAKE}")
    message(FATAL_ERROR "Windows Android OpenSSL requires FREERDP_OPENSSL_WINDOWS_HELPER, FREERDP_MSYS_ROOT and FREERDP_MSYS_MAKE. The adapter must use existing, verified MSYS2 tools; no tools are installed automatically.")
  endif()
  find_package(Python3 REQUIRED COMPONENTS Interpreter)
  set(OPENSSL_HOST_ARGS
      "${FREERDP_OPENSSL_WINDOWS_HELPER}" --source <SOURCE_DIR> --binary <BINARY_DIR>
      --root "${FREERDP_SOURCE_DIR}" --build-root "${CMAKE_BINARY_DIR}" --abi "${CMAKE_ANDROID_ARCH_ABI}"
      --ndk "${NDK_ROOT}" --msys-root "${FREERDP_MSYS_ROOT}" --make "${FREERDP_MSYS_MAKE}"
      --arch "${OSSL_ARCH}" --api "${NDK_API_LEVEL}" --prefix "${DEPS_INSTALL_DIR}"
      --libdir "${CMAKE_INSTALL_LIBDIR}" --jobs "${FREERDP_OPENSSL_BUILD_JOBS}")
  set(OPENSSL_CONFIGURE_COMMAND "${Python3_EXECUTABLE}" ${OPENSSL_HOST_ARGS} --stage configure)
  set(OPENSSL_BUILD_COMMAND "${Python3_EXECUTABLE}" ${OPENSSL_HOST_ARGS} --stage build)
  set(OPENSSL_INSTALL_COMMAND "${Python3_EXECUTABLE}" ${OPENSSL_HOST_ARGS} --stage install)
else()
  find_program(OPENSSL_POSIX_MAKE NAMES gmake make REQUIRED)
  set(OPENSSL_PUBLIC_RECIPE_ARGS
      "${FREERDP_SOURCE_DIR}/client/Android/tools/public_native_metadata.py"
      --kind openssl --root "${FREERDP_SOURCE_DIR}" --build-root "${CMAKE_BINARY_DIR}"
      --source <SOURCE_DIR> --binary <BINARY_DIR> --prefix "${DEPS_INSTALL_DIR}"
      --abi "${CMAKE_ANDROID_ARCH_ABI}" --arch "${OSSL_ARCH}" --api "${NDK_API_LEVEL}"
      --ndk "${NDK_ROOT}" --ndk-bin "${NDK_TOOLCHAIN_BIN}" --make "${OPENSSL_POSIX_MAKE}" --jobs 2)
  set(OPENSSL_CONFIGURE_COMMAND "${Python3_EXECUTABLE}" ${OPENSSL_PUBLIC_RECIPE_ARGS} --stage configure)
  set(OPENSSL_BUILD_COMMAND "${Python3_EXECUTABLE}" ${OPENSSL_PUBLIC_RECIPE_ARGS} --stage build)
  set(OPENSSL_INSTALL_COMMAND "${Python3_EXECUTABLE}" ${OPENSSL_PUBLIC_RECIPE_ARGS} --stage install)
endif()

ExternalProject_Add(
  openssl
  DOWNLOAD_EXTRACT_TIMESTAMP OFF
  SOURCE_DIR ${CMAKE_SOURCE_DIR}/external/openssl
  BINARY_DIR ${CMAKE_BINARY_DIR}/external/openssl
  URL https://github.com/openssl/openssl/releases/download/${OPENSSL_VERSION}/${OPENSSL_VERSION}.tar.gz
  URL_HASH ${OPENSSL_HASH}
  LIST_SEPARATOR |
  CONFIGURE_COMMAND ${OPENSSL_CONFIGURE_COMMAND}
  BUILD_COMMAND ${OPENSSL_BUILD_COMMAND}
  INSTALL_COMMAND ${OPENSSL_INSTALL_COMMAND}
)
