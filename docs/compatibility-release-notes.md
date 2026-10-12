Testing compatibility pre-release, signed with the retained PsyZine local identity. This is not a production release and does not establish content-rights clearance.

Obtainium users should select the Direct Compatible Release APK through the README channel configuration. It keeps `app.psyzine.debug`, is non-debuggable and minified, and can update a matching retained-key installation without uninstalling or clearing its data. Historical CI certificates differ; those installations require their own retained key or an explicitly approved backup/restore path.

All five APKs use the pinned certificate. Publication requires unit/lint/build checks, the complete emulator instrumentation matrix, two independent clean builds, code1-to-candidate-to-next-code emulator upgrades, checksums and historical version-code review. Detailed evidence is included in BUILD_RESULTS.json and the linked Actions run. Full phone/Obtainium, camera, reboot, accessibility and tablet acceptance remain separate checks.

Verify downloads against SHA256SUMS. Corresponding GPL source and notices are included. Agent-run phone tests remain Direct Debug only; compatibility phone upgrades require separate approval and owner-supervised data checks. Never uninstall or clear journal data to bypass an update rejection.
