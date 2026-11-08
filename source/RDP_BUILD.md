<!-- SPDX-License-Identifier: Apache-2.0 -->
# Really Decent Portal build review

Distribution modification notice: 2026-10-29. Existing upstream attribution
and license notices are retained.

This is a pre-publication source candidate based on FreeRDP 3.32.2,
commit 97eff6ddfbbfaa8e42a126d362686bd001ff8bdc. The product name Really Decent Portal (RDP) was selected by the user on
2026-10-30. Its independent application ID is io.github.muen1019.reallydecentportal.
This naming layer requires a new APK build and runtime review before release.
The upstream release was rechecked by the maintainer on 2026-10-29;
this statement is not a promise that it remains the newest release.

## Source build

Use JDK 21, Gradle wrapper 9.6.1, Android Gradle Plugin 9.2.1,
Android SDK/build-tools 37/37.0.0, NDK 29.0.13113456 and CMake 4.1.2.
Install and authorize these tools yourself before invoking the build.
The project does not automatically download an SDK or enable device debugging.

From client/Android/Studio:

    ./gradlew -PdeskquayAbi=arm64-v8a :aFreeRDP:assembleDeskquayRelease

Use x86_64 for an emulator build. This reviewed configuration builds one ABI
at a time. On Windows use gradlew.bat and provide paths to an existing MSYS2
root, its POSIX GNU make, and pkg-config with deskquayMsysRoot,
deskquayMake and deskquayPkgConfig Gradle properties. The OpenSSL host helper
is shipped at client/Android/tools/openssl_windows_host.py; it does not install
MSYS2 or change global environment variables.

Gradle dependencies and CMake ExternalProject archives must be available.
The first normal source build may download declared dependencies; --offline
is appropriate only after the dependencies and pinned CMake archives have been
prepared locally. CMake archives retain their declared URL_HASH verification.
The normal full-source recipe compiles FreeRDP/JNI from the declared sources.
The separate controlled naming/assets repackage below permits only exact approved
source10 packaged bytes; earlier Input Lab native libraries are excluded.

The current tested native feature set enables OpenSSL and cJSON and disables
FFmpeg, OpenH264, Opus, WebP, JPEG and PNG. Other optional codec configurations
have not been validated for this fork.

New saved connections start with custom desktop scale 200 and device scale 100.
Existing saved values, legacy missing-key fallbacks, imported files without
scale values, Room migration defaults, and quick-connect/history defaults are
not rewritten. This is separate from the full-display resolution and hidden-bar
defaults. The resulting remote desktop appearance still requires integration
verification; a model test is not a Windows display observation.

The release output is deliberately UNSIGNED. It must be independently checked
for package/version, manifest, native provenance, 16 KiB alignment, complete
offline notices and absence of diagnostic components before signing. No default
debug signing key is used by the release variant. Signing is a separate
user-controlled operation with a separately retained private key.

## Tests and publication status

src/test contains public unit tests and is not a release runtime source set.
The integration review separately reruns the 12 remote-hover CPU cases,
keyboard ownership, pen controller and new-saved-display-default cases against
this production source.
Host sinks and Robolectric events are not physical device or Windows Ink tests.
Diagnostic receivers, synthetic-input helpers and test activities are excluded
from the product source and release APK.

The source-offer metadata in the App About assets still marks the public source
URL and exact release archive as pending. Do not distribute this unsigned review
build as a final release. Final branding, corresponding-source publication,
license review, signing, integration/device regression and the user's pre-publication
review remain mandatory. Two-clean-build bit-for-bit reproducibility is not yet
claimed; this recipe provides source and producer traceability.


## Public native producer and lookup policy

Distribution modification notice: 2026-10-29. Before the first Android CMake
configuration, the release builder supplies prefix maps through the optional
deskquayCFlags and deskquayCxxFlags Gradle properties. These become explicit
CMAKE_C_FLAGS:STRING and CMAKE_CXX_FLAGS:STRING cache arguments. The Android NDK
initializes these cache entries before CMake imports environment defaults, so
environment-only CFLAGS/CXXFLAGS do not establish the Android CMake options.

ExternalDeps.cmake forwards the outer CACHE values, rather than the normal
variables containing NDK-added target flags, as quoted typed CMake arguments to
the dependency subprojects. The NDK still supplies each subproject's target flags.
OpenSSL uses its separate Perl Configure build, so the builder also retains the
same options in process-local CFLAGS/CXXFLAGS for that path. The MSYS conversion
policy is unchanged. No global compiler environment setting is modified.

For a direct source build, provide equivalent Clang options for the resolved
source root, build/tool workspace and NDK root. Each physical native-Windows
forward-slash spelling and MSYS /drive spelling maps to stable public roots:

    -ffile-prefix-map=<source-root>=/src/deskquay
    -fdebug-prefix-map=<source-root>=/src/deskquay
    -ffile-prefix-map=<build-tool-workspace>=/build/deskquay
    -fdebug-prefix-map=<build-tool-workspace>=/build/deskquay
    -ffile-prefix-map=<ndk-root>=/toolchain/ndk
    -fdebug-prefix-map=<ndk-root>=/toolchain/ndk

The placeholders are derived from the local build location; no contributor's
home path belongs in the published metadata. For a direct source build, pass the option string in both
-PdeskquayCFlags="<mapped-options>" and -PdeskquayCxxFlags="<mapped-options>", and
also supply that string as process-local CFLAGS/CXXFLAGS for OpenSSL. These are
different build-system inputs; they are not substitutes for each other.
Actual outer/inner/OpenSSL compilation options and the
final uncompressed APK entries/native allocated sections must be checked;
setting an environment variable alone does not establish a sanitized result.

The generated FreeRDP/WinPR display CFLAGS and OpenSSL compiler_flags string
are replaced by a truthful pointer to these build instructions, not claimed as
the actual compiler command. Source templates and dependency archives remain
unchanged. The shared build recipe validates generated-header schemas and
refuses unexpected regeneration.

Runtime default lookup policy is WinPR install prefix / and sysconfdir /etc;
OpenSSL prefix /, libdir lib and openssldir /usr/local/ssl. These default lookup
directories can affect certificate/provider discovery. They are not a
metadata-only runtime-equivalence claim. Cryptographic algorithms and
certificate-verification switches are not changed by this recipe. Known and
unknown certificate behavior and the legacy provider must be regressed before
release readiness may be claimed.

OpenSSL installation always uses a new owned DESTDIR below its .cxx binary
directory, followed by validated copying to the original ABI dependency prefix.
It never installs into the host /lib or /usr/local tree. Windows MSYS conversion
exclusions are fixed exact target-macro/prefix-map prefixes, scoped to this
child process; ordinary source and include argument conversion remains enabled.

## Windows OpenSSL link argument transport

Distribution modification notice: 2026-10-29. The Windows host adapter moves
only the generated libcrypto.so recipe relative object arguments into an NDK
response file, in their original order. Flags stay outside the response file
so MSYS path conversion remains active; archives and link flags stay unchanged.
The generated Makefile and response file are checked before/after build and
install. Unexpected recipes, regeneration or existing foreign files stop the
build. OpenSSL sources, dependency archives, algorithms, certificate policy
and POSIX builds are unchanged. This corrects a Windows command-length failure;
host link success alone does not establish Android runtime or release readiness.

## Input follow-up and generated dependency checks

Distribution modification notice: 2026-10-29. The input follow-up changes three
Java files. Hardware key holds use their key-down/up path before local IME
consumption, preserving repeats and modifier lifetime. Physical horizontal
scroll gestures are translated through the existing RDP horizontal wheel path.
The merged source passed 11 keyboard and 114 pointer host controls and compiled
as an Android fixture. These constructed tests do not establish Book Cover
hardware or remote Windows behavior. CPU04 Java byte-equivalence is no longer
claimed for this source. Real Android fixture execution and physical remote
acceptance remain separate, recorded gates.

The OpenSSL link check keeps every recipe and flag byte exact. It permits only
bounded generated object-dependency rules after the upstream make depend marker;
commands, make expansions and flag assignments in that suffix are rejected.
Old generated receipts are retained under their original recipe hash during
reversible recovery, never rewritten to pretend a newer producer ran.

## Android resource lookup and staged installation

Distribution modification notice: 2026-10-29. Android compiles the optional
WinPR resource lookup root as /share/WinPR. This runtime location is separate
from the host ABI resource installation destination, which remains at the
existing staged prefix. The upstream CMake variable used by install rules is
not changed, and other platforms retain their prior compile definition. This
removes a contributor-specific host path from the Android library. It is an
explicit runtime resource lookup change; keyboard/timezone optional-resource
behavior requires native regression. Algorithms, verification switches, C/H
source, OpenSSL metadata producer and successful OpenSSL outputs are unchanged.
Historical host193 / constructed Android ART28 cover their then-targeted input
sources. Source14 changes SessionActivity quick-connect/certificate methods, so
those results do not validate its new whole-file bytes or default/certificate
behavior. Existing keyboard, mouse-navigation and generic-motion dispatch method
bodies are independently byte-compared to canonical source13 in the new layer.
Source13 separately changed AppDatabase; neither earlier input tests nor method
digests establish DB safety or native resource runtime behavior.

## Internal compatibility names

The deskquay Gradle flavor, deskquay* build properties, assets/deskquay path
and deskquay_launcher resource are historical internal names retained to avoid
changing tested native/build routing. The App title and package are Really Decent
Portal and io.github.muen1019.reallydecentportal. Java/JNI namespaces remain
upstream-compatible. Signing uses the existing private distribution identity;
renaming does not require a new key. No existing App data is migrated.
The suggested repository name is Really-Decent-Portal; no public URL or name
availability is asserted by this source preparation.

The existing distribution signing certificate has historical subject CN=DeskQuay.
Its public SHA-256 fingerprint is
51cf6d51e0f5e97c9c482a5afab7e314bf7bfbf67347bc9857f284c732621216.
The key and certificate are preserved; this layer neither reads private key
material nor creates a new signing identity. Certificate subjects do not set
the Android launcher label. The Swedish accessibility-resource qualifier gets
an App-level override so its upstream label does not mask the selected brand.

## Historical source12 naming/assets packaging provenance

Distribution modification notice: 2026-10-30. For source12, Java, C/H, CMake,
manifests, crypto policy and native build inputs were byte-identical to source10.
The current source13 AppDatabase Java difference is described below.
The normal source-build recipe above remains available and unchanged. For the
upcoming controlled brand/assets-only repackage, the policy is
REUSED_APPROVED_SOURCE10_PACKAGED_BYTES and native_recompiled=false. This
source preparation has not packaged, signed, installed or executed that App.
Every reused native member must match the recorded source10 APK member hash;
native input changes invalidate this limited reuse proof. No native binary is
byte-patched and no earlier Input Lab JNI/FreeRDP library is admitted.

The x86 source10 owned native command completed with exit 0. Its complete
receipt was BLOCKED later by unsigned APK assembly, and it reused unchanged
source09 partial objects; it was not a fresh clean native build. The subsequent
separate source10 APK static gate passed. ARM64 source10 was a fresh owned
native build and passed the APK static gate. Nine production native libraries
per ABI come from those owned source10 builds; SQLCipher is the tenth member
from the declared verified vendor AAR. Exact public receipt and member hashes
are recorded in source-provenance.json without host paths or raw diagnostics.

Earlier engineering sections describe changes and requirements when introduced;
their original prebuild wording is historical context. Current source10 compile
evidence and this planned source12 reuse scope are recorded here and in the
metadata. Prior static/primitive/input evidence does not establish new App RDP,
tablet, Windows Ink internals or long-term stability acceptance.

Own generated JSON metadata, original notice arrangement and HTML markup are
explicitly MPL-2.0. Reproduced third-party text retains its original rights and
terms. All 25 original license/notice text files remain byte-identical; rendered
HTML preserves its complete underlying text after unescape. This is not a
whole-project or third-party relicense. Final public source URL/archive and
APK-source/dependency pairing remain required before publication review.

## Historical source13 Java database failure preservation candidate

Distribution modification notice: 2026-10-30. Only the AppDatabase Java source
changes. Detection opens use a nondeleting DatabaseErrorHandler that rethrows
the received SQLiteException. If both keyed and plaintext detection fail, the
method reports failure instead of deleting the existing database and sidecars.
The normal successful plaintext export/tmp/swap path and Room schema version,
Keystore/key creation and native APIs are unchanged. This is a private source
candidate; whole-App error handling and persistence still require integration.

The actual original compiled production method was exercised in isolated own-file
Android fixtures: correct-key closed-WAL and plaintext migration passed, while
wrong-key/corrupt cases deleted all owned DB files without throwing. The raw run
receipt is BLOCKED because a separate vendor-characterization assumption was not
met. Those product preservation failures are observed RED; that does not prove
the root cause of real missing bookmarks. GREEN is pending and no source13
runtime, whole-App, tablet or long-duration acceptance is claimed.

All C/H/JNI/CMake/config/native inputs, manifests, input controllers and vendor
assets remain unchanged. Exact approved source10 native bytes remain eligible
under the existing reuse policy; this Java-only safety change does not require
a native rebuild. Source13 package/runtime/signing are not executed here.

production-java59.json retains the historical source10 worktree-reference hashes
except its AppDatabase row, now the candidate hash. That row formerly represented
a CRLF file; its canonical LF content equals the source12 archived base. The
other 58 rows are retained references, not a new complete raw source13 stage map.
The unchanged host193/ART28 input targets do not supply DB regression coverage.
The original AppDatabase MPL-2.0/copyright notice is preserved. Existing limited
test certificate defaults and all public release gates remain unchanged.

## Historical source14 quick-connect display and certificate defaults

Distribution modification notice: 2026-10-30. Two tracked Java files change.
Hostname quick connections now use BookmarkBase.createNewConnection(), matching
the saved-bookmark factory's existing 200% display default. Resolution/fullscreen
defaults, existing bookmarks, branding, version and application ID do not change.

LibFreeRDP no longer adds unconditional /cert:ignore for bookmark connections.
The existing accept-all-certificate preference is GLOBAL for this App, defaults
to false, and is not per-host or per-bookmark. When explicitly true, both Java
certificate callbacks return native contract 2: temporary acceptance without a
new persistent pin. False retains the existing dialog Yes=1 (save)/No=0 (reject).
An explicitly supplied URI query such as ?cert=ignore still has its pre-existing
CLI override behavior; source14 does not remove that optional URI capability.

This is a source-only private candidate. Compilation, hostname quick-connect GUI,
certificate dialogs/known-host behavior, RDP and hardware are NOT_RUN for source14.
No preference or device setting is changed by preparation. The source13 database
patch remains byte-identical. All native C/H/JNI/CMake/config, manifests, schema,
input-controller targets, approved native reuse policy and raw legal assets remain
 unchanged. SessionActivity whole-file bytes change; only its selected existing
 input-dispatch method bodies are independently compared to source13 by digest.
 Earlier source13/host193/ART28 results do not validate the new whole file or paths.

production-java59.json changes only the SessionActivity and LibFreeRDP rows from
the source13 historical map; other 57 references remain as recorded, including the
AppDatabase candidate row. These two previous rows match canonical LF source13;
the mixed historical map is not a fresh complete raw source14 stage map. Original
MPL/copyright lines remain intact with dated modification notices. Public source
offer, actual integration, formal signing and publication review remain pending.

## Historical source15 physical input compatibility and fine two-axis wheel layer

Distribution modification notice: 2026-10-30. SessionView and SessionInputManager
change; new own TouchpadWheelAccumulator is MPL-2.0. Original upstream MPL and
copyright notices are retained. No native/JNI/CMake/config/manifest, branding,
version, namespace, certificate policy or stored-bookmark behavior changes.

Classified distance and generic HSCROLL/VSCROLL keep fractional wheel units down
to one unit. Adjacent H/V packets retain vector totals; each chunk is within
[-255,255] so the existing invert preference remains safe. Historical samples,
cross-callback duplicates, per-device owners and cancellation are handled without
inventing a remote Precision Touchpad path. Physical wheel remains independent
of the screen-finger active gate. Lifecycle resets clear fractions/stale samples;
true classified terminal events retain their gesture tombstones. Oversized or
nonfinite samples and excess work beyond64 pairs/callback are discarded rather
than deferred. This work limit is an implementation policy, not a protocol limit.

Legacy logical tap-drag is covered by constructed AOSP-reference input, not an
observed Book Cover OEM stream. Browser-history navigation gestures remain
unresolved; horizontal scrolling is not automatically turned into Back/Forward.
No extra remote program/channel, native PTP semantics or finger emulation is added.
Remote apps may still accumulate fine wheel units until120; pixel-accurate pan,
identical native-touchpad behavior and arbitrary remote-app support are not proven.

The exact merged three-file target passed97 host integration contracts with
Android/Robolectric and JNI boundary fixtures, using explicitly documented new
test adapters. The original92 result85PASS/7FAIL remains historical evidence.
This is not whole-App validation. Source15 full compile, Android/App runtime,
RDP and tablet/hardware regressions are NOT_RUN. Native reuse remains the exact
approved source10 packaged-bytes policy, without a new native rebuild claim.

API29/33 checks use an API37 external-version boundary fixture, not actual old
Android runtimes. Below API34 timestamps are milliseconds times1e6 for units only;
same-ms same-vector distinct real samples can be indistinguishable from duplicate
callbacks. The canonical-core-java60 review map comes from the exact complete
source14 ZIP's59 core main Java files plus the three merged inputs. It is not the
mixed-EOL historical Java59 map or a raw materialized-stage map. Public source
offer, current126-file license mapping, release signing and user review remain
pending. No public archive is produced by this source-only preparation.

## Historical source16 touchpad compatibility and opt-in navigation

Distribution modification notice: 2026-10-31. Three existing product Java files
(SessionView, SessionInputManager, KeyboardMapper) and three existing settings /
localized string resources change. Original copyright and individual licenses
are retained. The new navigation option defaults to false; no gesture behavior
is silently enabled. Exact final source hashes and limited host evidence are
recorded in source16 provenance. Prior source15/110/46 results remain history.

This source preparation is not full-App, JNI, RDP, remote-browser, OEM or hardware
acceptance. Native/JNI/CMake/manifests, source13 database preservation, source14
TLS/certificate defaults, branding/version, S Pen and all other canonical files
remain byte-identical to source15. No remote helper/channel is installed. Public
source offer, current license mapping, static APK audit, Lab signing and user
review are still required. The private additive .pc16/.lab16 build identities are
packaging overlays only and do not change the canonical production identity.

## Current source17 wheel-axis dispatch compatibility

Distribution modification notice: 2026-11-01. Only the exact existing product
Java files listed in source17 provenance change. Original copyrights and
individual licenses are retained. Vertical-before-horizontal dispatch is a
limited compatibility workaround. Within-callback timing behavior passed only
two constructed host-model cases. The final 120-case host regression uses eight
explicit cross-axis-order contract test copies; the original 119-case V7 run
remains FAIL43, preserved as evidence rather than unchanged-test PASS. Cross-
callback native message timestamps and actual RDP behavior remain NOT_RUN.
Source16 host117 and prior runtime results remain history.

This source preparation is not full-App, JNI, RDP, remote-browser, OEM or hardware
acceptance. UI resources, opt-in defaults, native/JNI/CMake/manifests, source13
database preservation, source14 TLS/certificate defaults, branding/version,
S Pen and all other canonical files remain byte-identical to source16. No remote
helper/channel is installed. Public source offer, current license mapping, static
APK audit, Lab signing and user review are still required. The private additive
.pc17/.lab17 identities are packaging overlays only; canonical production identity
and version stay unchanged.
