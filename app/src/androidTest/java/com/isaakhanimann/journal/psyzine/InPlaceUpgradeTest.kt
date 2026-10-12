// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** No references to app classes/resources: the same signed test APK runs against minified updates. */
@RunWith(AndroidJUnit4::class)
class InPlaceUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val marker get() = File(context.filesDir, "upgrade-fixture.json")
    private val tables = listOf("Experience", "Ingestion", "TimedNote", "ShulginRating", "CustomSubstance",
        "CustomUnit", "SubstanceCompanion", "photo_attachment", "journal_state")
    private val keyAlias = "psyzine-emulator-upgrade-fixture"

    private fun requireEmulator() {
        check(arguments.getString("psyzineUpgradeFixture") == "true") { "Explicit emulator fixture mode required" }
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1") { "Physical-device fixture seeding is forbidden" }
        check(Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk", ignoreCase = true)) { "Unrecognized emulator" }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Unrecognized emulator hardware" }
        check(context.packageName == "app.psyzine.debug") { "Wrong target sandbox" }
    }

    private fun database(): SQLiteDatabase {
        // Application reconciliation initializes Room. Wait for its real database, never replace it.
        val expectedSchema = requireNotNull(arguments.getString("expectedSchemaVersion")) { "Explicit schema version required" }.toInt()
        val file = context.getDatabasePath("experiences_db")
        val deadline = System.currentTimeMillis() + 30000
        while (System.currentTimeMillis() < deadline) {
            if (file.isFile) {
                val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
                if (db.version == expectedSchema) return db
                db.close()
            }
            Thread.sleep(100)
        }
        error("Application did not initialize schema $expectedSchema")
    }

    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private fun snapshot(): String {
        val result = JSONObject()
        database().use { db ->
            tables.forEach { table ->
                val rows = JSONArray()
                db.rawQuery("SELECT * FROM `$table` ORDER BY " + if (table == "SubstanceCompanion") "substanceName" else "id", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        cursor.columnNames.indices.forEach { index -> row.put(if (cursor.isNull(index)) JSONObject.NULL else cursor.getString(index)) }
                        rows.put(row)
                    }
                }
                result.put(table, rows)
            }
            val photos = JSONArray()
            db.rawQuery("SELECT relativePath FROM photo_attachment ORDER BY experienceId, position, id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val file = File(context.filesDir, cursor.getString(0))
                    assertNotNull("Stored photo decodes", BitmapFactory.decodeFile(file.path)?.also { it.recycle() })
                    photos.put(JSONObject().put("path", cursor.getString(0)).put("sha256", hash(file)))
                }
            }
            result.put("orderedPhotoFiles", photos)
        }
        result.put("preferences", hash(File(context.filesDir, "datastore/user_preferences.preferences_pb")))
        return result.toString()
    }

    @Test fun seedVersionOneFixture() = runBlocking {
        assumeTrue(arguments.getString("psyzineUpgradeFixture") == "true")
        requireEmulator()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        assertEquals(arguments.getString("expectedVersionCode")!!.toInt(), info.versionCode)
        check(!marker.exists()) { "Never reseed an existing fixture" }
        database().use { db ->
            tables.filter { it != "journal_state" }.forEach { table ->
                db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { cursor ->
                    cursor.moveToFirst(); check(cursor.getInt(0) == 0) { "Refusing to seed a nonempty journal" }
                }
            }
            db.beginTransaction()
            try {
                db.execSQL("INSERT OR IGNORE INTO journal_state (id,generation) VALUES (1,'upgrade-fixture-generation')")
                db.execSQL("INSERT INTO Experience (id,title,text,creationDate,sortDate,isFavorite,name,longitude,latitude) VALUES (42,'Upgrade fixture','Synthetic private notes',1700000000,1700000100,1,'Synthetic place',14.0,50.0)")
                db.execSQL("INSERT INTO CustomSubstance (id,name,units,description) VALUES (72,'Synthetic substance','mg','Synthetic description')")
                db.execSQL("INSERT INTO CustomUnit (id,substanceName,name,creationDate,administrationRoute,dose,estimatedDoseStandardDeviation,isEstimate,isArchived,unit,unitPlural,originalUnit,note) VALUES (73,'Caffeine','Synthetic unit',1700000000,'ORAL',10.5,0.5,1,0,'capsule','capsules','mg','Unit note')")
                db.execSQL("INSERT INTO SubstanceCompanion (substanceName,color) VALUES ('Caffeine','RED')")
                db.execSQL("INSERT INTO Ingestion (id,substanceName,time,endTime,creationDate,administrationRoute,dose,isDoseAnEstimate,estimatedDoseStandardDeviation,units,experienceId,notes,stomachFullness,consumerName,customUnitId) VALUES (51,'Caffeine',1700000100,1700000200,1700000000,'ORAL',2,1,0.5,'capsule',42,'Ingestion note',NULL,'Synthetic companion',73)")
                db.execSQL("INSERT INTO TimedNote (id,creationDate,time,note,color,experienceId,isPartOfTimeline) VALUES (61,1700000000,1700000150,'Timed note','RED',42,1)")
                db.execSQL("INSERT INTO ShulginRating (id,time,creationDate,option,experienceId) VALUES (62,1700000150,1700000000,'PLUS',42)")
                for (position in 0..1) {
                    val id = UUID.randomUUID().toString()
                    val file = File(context.filesDir, "attachments/$id.png").apply { parentFile!!.mkdirs() }
                    val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(if (position == 0) android.graphics.Color.RED else android.graphics.Color.BLUE)
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    bitmap.recycle()
                    db.execSQL("INSERT INTO photo_attachment (id,experienceId,relativePath,mimeType,width,height,byteSize,createdAt,position,sha256) VALUES (?,42,?,'image/png',32,24,?,1700000000,?,?)",
                        arrayOf(id, "attachments/$id.png", file.length(), position, hash(file)))
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        // Write through DataStore to a temporary file, avoiding a second live store for the app's path.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val temporary = File(context.cacheDir, "upgrade.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope) { temporary }
        try {
            store.edit {
                it[stringPreferencesKey("appearance_theme")] = "nord"
                it[stringPreferencesKey("key_time_display_option")] = "AUTO"
                for (key in listOf("are_conditions_accepted", "is_migrated_0", "is_migrated_1", "key_hide_oral_disclaimer", "key_hide_dosage_dots", "active_notification_lock_screen_details", "notification_permission_requested")) it[booleanPreferencesKey(key)] = true
                for (key in listOf("active_notification_enabled", "active_widget_details", "KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT")) it[booleanPreferencesKey(key)] = false
                it[longPreferencesKey("KEY_LAST_INGESTION_OF_EXPERIENCE")] = 0L
                it[longPreferencesKey("KEY_CLONED_INGESTION_TIME")] = 0L
                it[stringSetPreferencesKey("substanceInteractions")] = setOf("Synthetic interaction")
            }
        } finally { scope.cancel() }
        temporary.copyTo(File(context.filesDir, "datastore/user_preferences.preferences_pb").apply { parentFile!!.mkdirs() }, overwrite = false)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, generator.generateKey()) }
        val payload = cipher.doFinal("Synthetic retained secret".toByteArray())
        File(context.filesDir, "upgrade-ciphertext").writeBytes(payload)
        marker.writeText(JSONObject().put("uid", context.applicationInfo.uid).put("snapshot", snapshot())
            .put("iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP)).toString())
    }

    @Test fun verifyRetainedFixtureAndKeystore() {
        assumeTrue(arguments.getString("psyzineUpgradeFixture") == "true")
        requireEmulator()
        val expected = JSONObject(marker.readText())
        assertEquals(expected.getInt("uid"), context.applicationInfo.uid)
        @Suppress("DEPRECATION")
        assertEquals(arguments.getString("expectedVersionCode")!!.toInt(), context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        assertEquals(expected.getString("snapshot"), snapshot())
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(keyAlias, null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, android.util.Base64.decode(expected.getString("iv"), android.util.Base64.NO_WRAP)))
        }
        assertEquals("Synthetic retained secret", String(cipher.doFinal(File(context.filesDir, "upgrade-ciphertext").readBytes())))
    }

    @Test fun upgradedJournalPhotosAndBackupWork() {
        assumeTrue(arguments.getString("psyzineUpgradeFixture") == "true")
        requireEmulator()
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        check(device.wait(Until.hasObject(By.text("Journal")), 30000)) { "Journal did not open" }
        check(device.wait(Until.hasObject(By.text("Upgrade fixture")), 15000)) { "Preserved entry is missing" }
        device.findObject(By.text("Upgrade fixture")).click()
        val scroll = device.wait(Until.findObject(By.scrollable(true)), 10000)
        repeat(8) { if (!device.hasObject(By.desc("Open photo 1 of 2"))) scroll?.scroll(Direction.DOWN, 0.6f) }
        check(device.wait(Until.hasObject(By.desc("Open photo 1 of 2")), 10000)) { "Photos did not render" }
        device.findObject(By.desc("Open photo 1 of 2")).click()
        check(device.wait(Until.hasObject(By.desc("Close photo viewer")), 10000)) { "Photo viewer did not open" }
        device.pressBack()
        device.pressBack()
        device.findObject(By.text("Settings")).click()
        val settingsScroll = device.wait(Until.findObject(By.scrollable(true)), 10000)
        repeat(12) { if (!device.hasObject(By.text("Back up journal and photos"))) settingsScroll?.scroll(Direction.DOWN, 0.6f) }
        check(device.wait(Until.hasObject(By.text("Back up journal and photos")), 10000))
        device.findObject(By.text("Back up journal and photos")).click()
        check(device.wait(Until.hasObject(By.text("Export")), 10000))
        device.findObject(By.text("Export")).click()
        val filename = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 15000)
            ?: error("Document filename input missing")
        // Select Downloads explicitly; a provider's last-used location is not reliable across APIs.
        device.findObject(By.desc("Show roots"))?.click()
        device.wait(Until.findObject(By.text("Downloads")), 5000)?.click()
        val name = "psyzine-upgrade-${arguments.getString("expectedVersionCode")}.psyzine"
        val currentFilename = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000) ?: filename
        currentFilename.text = name
        val save = device.wait(Until.findObject(By.res("android", "button1")), 5000)
            ?: device.findObject(By.res("com.android.documentsui", "button1"))
            ?: device.findObject(By.text(java.util.regex.Pattern.compile("(?i)save")))
            ?: error("Document save action missing")
        save.click()
        check(device.wait(Until.hasObject(By.text("Backup saved")), 30000)) { "Backup export did not report completion" }
        // Shell copies only the synthetic export back into the target app's external test directory.
        val destination = File(context.getExternalFilesDir(null), name)
        val deadline = System.currentTimeMillis() + 15000
        while (!destination.isFile && System.currentTimeMillis() < deadline) {
            device.executeShellCommand("cp /sdcard/Download/$name ${destination.path}")
            if (!destination.isFile) device.executeShellCommand("cp /sdcard/Download/$name.zip ${destination.path}")
            Thread.sleep(250)
        }
        check(destination.isFile) { "Backup export did not complete in Downloads" }
        ZipFile(destination).use { archive ->
            val manifest = JSONObject(archive.getInputStream(archive.getEntry("manifest.json")).bufferedReader().use { it.readText() })
            assertEquals("nord", manifest.getString("theme"))
            assertEquals(1, manifest.getJSONObject("journal").getJSONArray("experiences").length())
            val photos = manifest.getJSONArray("photos")
            assertEquals(2, photos.length())
            for (i in 0 until photos.length()) {
                val photo = photos.getJSONObject(i)
                val bytes = archive.getInputStream(archive.getEntry(photo.getString("path"))).use { it.readBytes() }
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                assertEquals(photo.getString("sha256"), digest)
            }
        }
    }
}
