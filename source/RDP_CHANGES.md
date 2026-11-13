<!-- SPDX-License-Identifier: Apache-2.0 -->
# Really Decent Portal changes from FreeRDP 3.32.2

Distribution modification notice: 2026-10-29. Existing copyright and license
notices are retained, including file-specific MPL-2.0 notices.

This source candidate carries the tested Android input changes: physical
modifier ownership, separate screen touch and screen touchpad modes, external
mouse/trackpad routing and side buttons, Ctrl-wheel content zoom, RDPEI touch
and pen negotiation, pen pressure/barrel/range lifecycle, remote-pointer-only
hover display, compact toolbar styling, full-display defaults and masked native
credential fields. It does not install a companion on Windows.

Historical Input Lab flavors and debug diagnostic components are not included.
The production native CMake/Gradle source build is restored; optional codecs
remain at the reviewed reduced feature set. The application ID is separate
from aFreeRDP; Java/JNI namespaces are retained to preserve native interfaces.

Fresh saved connections use an explicit factory for custom desktop scale 200
and device scale 100. Legacy constructors/missing-key fallbacks, file import,
existing saved profiles and Room schema defaults retain their prior behavior;
the three-file Java layer does not change native input code or migrate data.

The offline About page includes complete third-party texts and a source-provenance
manifest. Fixed file-specific legal prefixes were inserted as a separate layer;
the source provenance records their exact inputs and outputs. The original legal
plan contains four native/CMake header exceptions; the OpenSSL file also has a
separately recorded host-helper path change. ExternalFreeRDP.cmake is a fifth
native/CMake distribution delta: it exports compile commands and carries its own
Apache modification notice. These are not described as byte-identical to the old
experimental source.
The normal full-source build rebuilds the nine FreeRDP/JNI/dependency production
libraries. The planned naming/assets-only package may reuse exact approved
source10 packaged bytes under its recorded policy; SQLCipher remains the separately
verified dependency-AAR member. This is not a new native compile claim.

The Really Decent Portal (RDP) product name was selected by the user on
2026-10-30. The independent quay/beacon vector mark remains a review candidate;
this naming source layer has not yet produced or validated a new APK. This source has not been committed, pushed or published
by the preparation tool. Public source URL/archive identity must be finalized
before the corresponding release can be distributed.

## Android 0.1.10

The screen trackpad uses lower gain for slow pointer movement and smoothly
decelerates after a fast swipe. A new touch interrupts the glide. The Android
viewport follows the trackpad pointer near the edge of the visible desktop
when zoomed in or when the on-screen keyboard reduces the viewport. The
launcher icon leaves more space around the monitor and tablet artwork.


## ABI evaluation and native public-path producer

Distribution modification notice: 2026-10-29. Restore the upstream String[]
type for ABI filters and split architectures; the App/Core varargs DSL remains
unchanged. Preserve the original Java/input source and host test harnesses.

Generated native metadata and explicit default lookup directories use the public
producer described in RDP_BUILD.md. Source templates/archives, algorithms
and certificate-verification switches are retained. The runtime lookup policy
is a separate behavior delta requiring certificate/provider/native regression.
The earlier 173 CPU cases and 13 display static checks may be inherited only
after exact Java/harness equivalence; they are not rerun or relabeled as native
or runtime-certificate coverage.

## Explicit native compiler option propagation

Distribution modification notice: 2026-10-29. Android CMake now accepts the
optional deskquayCFlags and deskquayCxxFlags properties as typed C/C++ cache
arguments before its first configuration. Dependency CMake projects receive
the outer CACHE values explicitly; NDK-added normal-variable flags are not
duplicated. OpenSSL continues to receive its process-local CFLAGS/CXXFLAGS.
This corrects the environment-only wiring that produced a real outer
configuration with none of the required prefix maps. The failed configuration
is retained as evidence; successful compilation or private-path removal is
not inferred from the source change. Actual compile records and final artifact
scans remain required. No Java/input logic, crypto/verification switches,
lookup policy, existing profiles or device settings change in this layer.

## Windows OpenSSL response-file transport

Distribution modification notice: 2026-10-29. The Windows-only host build recipe
uses an object-only response file for libcrypto.so, preserving the exact object
order, flags and archives. This corrects the observed NDK wrapper argument-list
limit without changing OpenSSL C/H source or runtime policy. The earlier build
failure is retained. Continuing owned partial native objects must be reported
as a resume, not a fresh clean build. Android/provider/certificate tests remain
separate gates. No Java or input behavior changes in this source08 layer.

## Held keys, horizontal touchpad scrolling and build readback

Distribution modification notice: 2026-10-29. This increment changes the existing
hardware keyboard routing and physical touchpad horizontal-scroll handling.
The 11 keyboard / 114 pointer controls use the exact merged Java source.
Earlier CPU04 results remain historical; source09 is not byte-equivalent Java.
Physical Book Cover holds, horizontal direction, smoothness and mixed-input
regression remain pending until tested on the tablet. No extra remote Windows
program is required or installed. The build-only OpenSSL guard now accepts
normal generated dependency appends while keeping recipes, flags and response
objects exact. Native C/H, archives, lookup policy and verification switches
are unchanged.

## Android-only public WinPR resource lookup

Distribution modification notice: 2026-10-29. Only the Android runtime macro
WINPR_RESOURCE_ROOT becomes /share/WinPR; host staged install destinations and
other platform definitions stay unchanged. This corrects a real raw-library
host path disclosure. No native binary is byte-patched or legitimate protocol
string removed. The lookup difference is recorded separately from unchanged
Java input tests. A newly configured and built native artifact, privacy scan
and optional-resource regression remain required before release readiness.

## Product name selected: Really Decent Portal

Distribution modification notice: 2026-10-30. App title, accessibility label,
About title and independent application ID are updated. Root Gradle project
display name is Really Decent Portal. The two distribution documents are renamed
to RDP_BUILD.md and RDP_CHANGES.md. Internal flavor, asset/resource names and all
The source11 naming layer left Java/JNI/native behavior unchanged. Existing provider authorities and
session task affinity already derive from ${applicationId}; they remain isolated
under the new ID without modifying either Android manifest. Historical DeskQuay
modification notices describe earlier work and are retained with their licenses.
This layer does not establish App, RDP/NLA, tablet or stability acceptance.

## Source12 own-asset licensing and precise native reuse scope

Distribution modification notice: 2026-10-30. Explicit MPL-2.0 declarations
apply only to this project's generated JSON metadata, original notice
arrangement and HTML markup. The 25 reproduced third-party text files and their
rights/terms are unchanged. HTML embedded text is mechanically preserved, and
legal/source manifests are regenerated for the updated assets and documents.

The historical source12 layer changed no Java/C/H/CMake/manifest or crypto behavior. Source10 actual compile
and static evidence is identified separately from this not-yet-run brand/assets
repackage. It is planned to use REUSED_APPROVED_SOURCE10_PACKAGED_BYTES with
native_recompiled=false. The x86 source10 native step passed before a later
assembly block and reused unchanged source09 partial objects; its later APK
static gate passed. ARM64 source10 had a fresh owned native build and static
PASS. Neither statement is a source12 runtime or hardware PASS. Future native
input changes require fresh native evidence; earlier Input Lab libraries are
not permitted by this reuse policy. Final source offer and public review remain
pending. Historical modification/header-layer hashes remain historical records.

## Source13: preserve existing database on failed type/key detection

Distribution modification notice: 2026-10-30. AppDatabase is a newly modified
tracked upstream path, retaining its original MPL-2.0 and author notice. Only
two detection opens receive a nondeleting handler; their failure no longer
calls deleteDatabase on the existing DB. Instead an IllegalStateException
reports that the original files are preserved. Schema/migrations, key creation,
successful plaintext export/tmp/swap and all other Java/input/native code remain
unchanged. This does not repair every possible failure in the separate successful
export/replacement path, nor establish a cause for real empty bookmark lists.

The isolated Android RED is bound by receipt SHA in source provenance; GREEN is
pending. Source13 is PLAN-only: no stage update, App build, signing, installation,
RDP connection or hardware acceptance. The Java59 reference changes only its
AppDatabase row with explicit CRLF/canonical history; previous input suites do
not become DB safety tests. Approved source10 native reuse remains eligible
because C/H/JNI/config bytes do not change. Public source URL/archive, release
certificate default and actual App/persistence regression remain pending.

## Source14: merge hostname quick-connect and certificate-policy corrections

Distribution modification notice: 2026-10-30. SessionActivity uses the existing
new-connection factory for hostname quick connect (200% default) and returns 2
for both explicit global accept-all certificate callbacks (temporary, no new pin).
LibFreeRDP removes only its unconditional /cert:ignore addition. The false global
preference branch keeps existing Yes=1/No=0 dialogs; the preference remains false
by default and global, not per-host. Existing explicit URI ?cert=ignore overrides
remain. There is no new UI, service, preference, per-host option or security-store
change in this source preparation. No blanket default certificate bypass remains.

Both source candidates are merged into one SessionActivity, preserving all three
nonoverlapping code hunks and a combined dated MPL modification notice. LibFreeRDP
retains its own dated notice. The source13 DB candidate, all other Java/native/JNI,
manifests, native reuse policy, version/name/package and raw legal text are exact
parent bytes. The historical Java59 reference updates only these two Java rows.
Compile, quick-connect GUI, certificate UI, native/RDP integration and hardware
are NOT_RUN for source14; prior tests are historical evidence, not inherited PASS.

## Source15: physical input compatibility and fine two-axis wheel

Distribution modification notice: 2026-10-30. Merge the Java-only legacy logical
tap-drag compatibility and fine two-axis wheel work into SessionView and
SessionInputManager; add own MPL-2.0 TouchpadWheelAccumulator. Preserve original
upstream notices. Classified OS touchpad-scroll variants remain accepted except
pen tools; metadata-poor terminal events retire only matching owners. Generic
View/Activity fallback shares the same wheel ledger/signed sink, preserving
historical samples, fractions, both axes and invert-safe chunks. Physical wheel
keeps source14's independence from screen-finger activity. Lifecycle resets clear
fractions/stale timestamps while true classified UP/CANCEL remains terminal.

Exact merged-source host integration:97PASS with independent signed adapters,
literal vector/1875-unit checks and independent physical-sample timestamps. Raw
original92:85PASS/7FAIL, retained. These are constructed Android/AOSP-reference and
JNI-boundary tests, not an OEM trace or whole-App/hardware PASS. Android29/33 time
API guards were tested only with an API37 external-version fixture; true old
runtime tests are NOT_RUN. Millisecond fallback cannot disambiguate all same-ms
same-vector samples. Browser-history navigation gestures remain unresolved.

No native Precision Touchpad channel, remote helper or simulated finger pinch is
introduced. Existing remote-app wheel quantization may remain; native PTP/pixel
pan is not promised. All native/JNI/CMake/config/manifests, raw legal assets,
brand/package/version, source13 DB patch and source14 connection/certificate
defaults are unchanged. Source15 full build, Android/App, RDP and tablet tests
are NOT_RUN. Earlier whole-file or whole-App results are not inherited as PASS.

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
