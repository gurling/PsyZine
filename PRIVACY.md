# PsyZine privacy policy

PsyZine processes your journal on your Android device. It has no analytics, advertisements, developer crash reporting, account, cloud sync, or network fetcher. Bundled reference information works offline. Developers receive no journal data through the app.

Journal entries, ingestions, ratings, timed notes, custom substances, and optimized photos are stored in the private application sandbox. The device camera app receives temporary access to a private staging file when you choose Take photo. Photos and files selected through Android’s picker are copied locally. Image pixels are normalized and re-encoded to remove EXIF/XMP and location metadata. Source photos are not modified. Thumbnails can be regenerated. Pending edit manifests and camera files remain private.

The journal database, photos, and staging files are explicitly excluded from Android automatic cloud backup and device transfer. The preferences DataStore may be backed up, including appearance, display settings, notification options, past onboarding/permission-request state, and the last/clone ingestion timestamps used by the entry workflow. These preferences contain no journal text or photos. Android controls its own permission grants; importing a backup never grants permission or accepts onboarding.

An optional ongoing notification shows the active experience (the latest entry containing an ingestion within the previous 12 hours). Details are redacted on the lock screen by default. You can explicitly allow lock-screen details in Settings. A home-screen widget shows details when placed; you can hide those details in Settings. Both surfaces may be visible to anyone who can see your device. Denying notification permission does not affect journal use and does not start a hidden foreground service. Estimated phases describe reference durations, not your measured condition.

Explicit .psyzine backups contain journal data, processed photos, and appearance/presentation settings. Entries-only JSON exports omit photos and those settings. Backups are not encrypted by PsyZine. You choose their destination with Android’s document UI; a destination provider may be a cloud service. Import reads your selected file and validates it before replacing the local journal. Sharing the app shares only its source link, never journal content.

Reference article, support, donation, and source links open another application only when tapped. Their operators and Android/device services have their own privacy policies. The optional donation page clearly identifies the original author as the recipient.

Debug import logs contain only an error code or exception class, not journal contents, paths, or selected URIs. No production journal logging is added. Private storage protects against other ordinary apps; device security, backups you export, and operating-system behavior remain under your control.

This policy applies to PsyZine 1.0.0, dated 11 October 2026. Source and licensing notices are available offline in Settings → About and licenses.
