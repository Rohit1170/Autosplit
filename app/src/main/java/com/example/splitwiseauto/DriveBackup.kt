package com.example.splitwiseauto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * Google Drive backup.
 *
 * - Optional and off by default. Turned on from the intro or Settings > Backup.
 * - Only asks Google for the "drive.appdata" scope: a hidden folder in the
 *   user's own Drive that only Splitlee can see. No access to their files,
 *   mail, contacts or profile.
 * - Splitlee has no server. The phone talks to Google Drive directly.
 * - A backup is one zip: the Room database plus the app's own settings
 *   (see [BackupArchive.PREFS]). SMS text, location history and
 *   phone-specific state are not included.
 * - The last [DriveBackup.KEEP] backups are kept; older ones are deleted.
 */

/** Local state for backup. This file itself is never backed up. */
object BackupStore {
    private const val PREFS = "splitlee_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WIFI = "wifi_only"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_LAST_SIZE = "last_size"
    private const val KEY_EMAIL = "email"
    private const val KEY_NEEDS_SIGN_IN = "needs_sign_in"
    private const val KEY_LAST_ERROR = "last_error"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, false)
    fun wifiOnly(c: Context) = prefs(c).getBoolean(KEY_WIFI, true)
    fun lastAt(c: Context) = prefs(c).getLong(KEY_LAST_AT, 0L)
    fun lastSize(c: Context) = prefs(c).getLong(KEY_LAST_SIZE, 0L)
    fun email(c: Context): String? = prefs(c).getString(KEY_EMAIL, null)
    fun needsSignIn(c: Context) = prefs(c).getBoolean(KEY_NEEDS_SIGN_IN, false)
    fun lastError(c: Context): String? = prefs(c).getString(KEY_LAST_ERROR, null)

    fun enable(c: Context, email: String?) {
        prefs(c).edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_NEEDS_SIGN_IN, false)
            .putString(KEY_EMAIL, email ?: email(c))
            .remove(KEY_LAST_ERROR)
            .commit() // the app may restart right after (restore)
        DriveBackupWorker.schedule(c)
    }

    fun setWifiOnly(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_WIFI, on).apply()
        if (isEnabled(c)) DriveBackupWorker.schedule(c)
    }

    fun recordSuccess(c: Context, size: Long) {
        prefs(c).edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_SIZE, size)
            .putBoolean(KEY_NEEDS_SIGN_IN, false)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordNeedsSignIn(c: Context) {
        prefs(c).edit().putBoolean(KEY_NEEDS_SIGN_IN, true).apply()
    }

    fun recordError(c: Context, message: String) {
        prefs(c).edit().putString(KEY_LAST_ERROR, message).apply()
    }

    fun setEmail(c: Context, email: String?) {
        if (email != null) prefs(c).edit().putString(KEY_EMAIL, email).apply()
    }

    /** Turn off: stop the daily job and forget the account. */
    fun disable(c: Context) {
        prefs(c).edit().clear().apply()
        DriveBackupWorker.cancel(c)
    }

    /** One line for the Settings row. */
    fun summary(c: Context): String = when {
        !isEnabled(c) -> "Off · keep a copy in your Google Drive"
        needsSignIn(c) -> "Paused · sign in again"
        lastAt(c) == 0L -> "On · first backup soon"
        else -> "On · last backup ${whenLabel(lastAt(c))}"
    }

    fun whenLabel(millis: Long): String {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        val startOfToday = now - (now + TimeZone.getDefault().getOffset(now)) % day
        return when {
            millis >= startOfToday -> "today, $time"
            millis >= startOfToday - day -> "yesterday, $time"
            else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))
        }
    }
}

class BackupException(message: String) : Exception(message)

/** One backup file in the user's Drive. */
data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedMillis: Long,
    val size: Long,
    val device: String?,
    /** Expenses inside; null for backups made before this was recorded. */
    val expenses: Int? = null
) {
    /** An empty backup (fresh install). Older ones without a count: tiny zip. */
    val looksEmpty: Boolean get() = expenses?.let { it == 0 } ?: (size < 4096)
}

/** Talks to the Google Drive REST API directly (no extra Drive library). */
object DriveBackup {
    /** Hidden per-app folder. The only Google permission Splitlee asks for. */
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val KEEP = 3
    private const val NAME_PREFIX = "splitlee-backup-"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Token without any UI. Null when the user has to sign in again. */
    suspend fun silentToken(context: Context): String? = try {
        val r = Identity.getAuthorizationClient(context).authorize(request()).await()
        if (r.hasResolution()) null else r.accessToken
    } catch (e: Exception) {
        null
    }

    fun friendlyError(e: Throwable): String = when {
        e is ApiException && e.statusCode == 10 ->
            "Google sign-in isn't set up for this version of the app yet."
        e is ApiException && e.statusCode == 7 -> "No internet connection."
        e is ApiException && e.statusCode == 16 -> "Sign-in cancelled."
        e is BackupException -> e.message ?: "Something went wrong."
        e is IOException -> "Couldn't reach Google Drive. Check your connection."
        else -> "Something went wrong. Try again."
    }

    // ---------------- REST helpers ----------------

    private class HttpError(val code: Int, body: String) : IOException("HTTP $code: ${body.take(200)}")

    private fun open(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.readOk(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        disconnect()
        if (code !in 200..299) throw HttpError(code, body)
        return body
    }

    private fun parseTime(s: String?): Long {
        if (s == null) return 0L
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            runCatching {
                val f = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return f.parse(s)!!.time
            }
        }
        return 0L
    }

    /** Newest first. */
    suspend fun list(token: String): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("files(id,name,modifiedTime,size,appProperties)", "UTF-8")
        val body = open(
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=100&fields=$q",
            token
        ).readOk()
        val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
        (0 until files.length()).map { files.getJSONObject(it) }
            .filter { it.optString("name").startsWith(NAME_PREFIX) }
            .map {
                DriveBackupFile(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    modifiedMillis = parseTime(it.optString("modifiedTime", null)),
                    size = it.optString("size", "0").toLongOrNull() ?: 0L,
                    device = it.optJSONObject("appProperties")?.optString("device", null),
                    expenses = it.optJSONObject("appProperties")?.optString("expenses", null)?.toIntOrNull()
                )
            }
            .sortedByDescending { it.modifiedMillis }
    }

    private suspend fun upload(token: String, file: File, name: String, device: String, expenses: Int) = withContext(Dispatchers.IO) {
        val boundary = "splitlee${System.currentTimeMillis()}"
        val meta = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject().put("device", device).put("expenses", expenses.toString()))
        val c = open(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            token, "POST"
        )
        c.doOutput = true
        c.setChunkedStreamingMode(0)
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        c.outputStream.buffered().use { out ->
            out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            out.write(meta.toString().toByteArray())
            out.write("\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        c.readOk()
    }

    suspend fun download(token: String, id: String, into: File) = withContext(Dispatchers.IO) {
        val c = open("https://www.googleapis.com/drive/v3/files/$id?alt=media", token)
        if (c.responseCode !in 200..299) c.readOk() // throws
        c.inputStream.use { input -> into.outputStream().use { input.copyTo(it) } }
        c.disconnect()
    }

    suspend fun delete(token: String, id: String) {
        withContext(Dispatchers.IO) {
            try {
                open("https://www.googleapis.com/drive/v3/files/$id", token, "DELETE").readOk()
            } catch (e: HttpError) {
                // Already gone is fine.
                if (e.code != 404) throw e
            }
            Unit
        }
    }

    /** The signed-in account's email, so the user can see which Drive is used. */
    suspend fun accountEmail(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode("user(emailAddress)", "UTF-8")
            val body = open("https://www.googleapis.com/drive/v3/about?fields=$q", token).readOk()
            JSONObject(body).optJSONObject("user")?.optString("emailAddress", null)
        }.getOrNull()
    }

    /** Removes Splitlee's access to the Google account. */
    suspend fun revoke(token: String) = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write("token=${URLEncoder.encode(token, "UTF-8")}".toByteArray()) }
            c.responseCode
            c.disconnect()
        }
    }

    // ---------------- High-level actions ----------------

    sealed class Outcome {
        object Done : Outcome()
        object NeedsSignIn : Outcome()
        /** Nothing to back up yet (no expenses). Nothing was uploaded. */
        object Empty : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    /** Builds a backup, uploads it, keeps the newest [KEEP]. */
    suspend fun backupNow(context: Context, token: String? = null): Outcome {
        val app = context.applicationContext
        val t = token ?: silentToken(app) ?: run {
            BackupStore.recordNeedsSignIn(app)
            return Outcome.NeedsSignIn
        }
        return try {
            // Never upload an empty backup: a fresh install would otherwise
            // push real backups out of the "keep last 3" window.
            val expenses = BackupArchive.expenseCount(app)
            if (expenses == 0) return Outcome.Empty
            val zip = BackupArchive.build(app)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            upload(t, zip, "$NAME_PREFIX$stamp.zip", Build.MODEL ?: "Android", expenses)
            val size = zip.length()
            zip.delete()
            // Keep the newest KEEP, but never delete the biggest real backup.
            val all = list(t)
            val biggest = all.filterNot { it.looksEmpty }.maxByOrNull { it.size }
            all.drop(KEEP).filter { it.id != biggest?.id }.forEach { runCatching { delete(t, it.id) } }
            if (BackupStore.email(app) == null) BackupStore.setEmail(app, accountEmail(t))
            BackupStore.recordSuccess(app, size)
            Outcome.Done
        } catch (e: HttpError) {
            if (e.code == 401 || e.code == 403) {
                BackupStore.recordNeedsSignIn(app)
                Outcome.NeedsSignIn
            } else {
                Outcome.Failed(friendlyError(IOException(e))).also { BackupStore.recordError(app, it.message) }
            }
        } catch (e: Exception) {
            Outcome.Failed(friendlyError(e)).also { BackupStore.recordError(app, it.message) }
        }
    }

    /** Downloads [file] and swaps it in. Restart the app afterwards ([AppRestart]). */
    suspend fun restore(context: Context, token: String, file: DriveBackupFile): BackupArchive.Summary {
        val app = context.applicationContext
        val dir = File(app.cacheDir, "restore_dl").apply { deleteRecursively(); mkdirs() }
        val zip = File(dir, "backup.zip")
        download(token, file.id, zip)
        return try {
            BackupArchive.restore(app, zip)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Deletes every Splitlee backup from the user's Drive. */
    suspend fun deleteAll(token: String): Int {
        val all = list(token)
        all.forEach { delete(token, it.id) }
        return all.size
    }
}

/** Packs and unpacks the backup zip. */
object BackupArchive {
    private const val DB_NAME = "splitwise_auto_database"
    private const val ENTRY_PREFS = "prefs.json"
    private const val ENTRY_META = "meta.json"
    private const val FORMAT = 1

    /**
     * App settings that travel with the backup. Left out on purpose:
     * sms_catch_up (inbox position, tied to the old phone), location_prefs,
     * ussd_pay (old SIM), splitlee_intro, pending_payment_timeout and
     * splitlee_backup itself.
     */
    val PREFS = listOf(
        "processed_sms", "expense_counter", "active_trip_prefs", "custom_categories",
        "splitlee_profile", "splitlee_account_kinds", "fx_memory", "auto_settle",
        "splitlee_theme", "widget_theme", "splitlee_privacy", "home_period",
        "trip_reminders", "category_recheck", "notification_alerts"
    )

    data class Summary(val expenses: Int, val friends: Int, val groups: Int, val trips: Int) {
        fun label(): String {
            val parts = listOf(
                expenses to "expense", friends to "friend", groups to "group", trips to "trip"
            ).filter { it.first > 0 }.map { (n, w) -> "$n $w${if (n == 1) "" else "s"}" }
            return if (parts.isEmpty()) "Nothing to restore." else "Restored " + joinNice(parts) + "."
        }

        private fun joinNice(p: List<String>) =
            if (p.size == 1) p[0] else p.dropLast(1).joinToString(", ") + " and " + p.last()
    }

    /** Number of expenses in the database right now. */
    suspend fun expenseCount(context: Context): Int = withContext(Dispatchers.IO) {
        runCatching {
            AppDatabase.getDatabase(context).openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM `expenses`").use { it.moveToFirst(); it.getInt(0) }
        }.getOrDefault(0)
    }

    private fun count(table: String, q: (String) -> Int): Int = runCatching { q(table) }.getOrDefault(0)

    private fun countsOf(q: (String) -> Int) =
        Summary(count("expenses", q), count("friends", q), count("split_groups", q), count("trips", q))

    suspend fun build(context: Context): File = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context).openHelper.writableDatabase
        runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
        val summary = countsOf { t -> db.query("SELECT COUNT(*) FROM `$t`").use { it.moveToFirst(); it.getInt(0) } }
        val dbVersion = db.version

        val dir = File(context.cacheDir, "backup_out").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "backup.zip")
        val main = context.getDatabasePath(DB_NAME)
        val wal = File(main.path + "-wal")

        // A write transaction stops other writers while the files are copied,
        // so the database and its WAL are a consistent pair.
        db.beginTransaction()
        try {
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DB_NAME))
                main.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                if (wal.exists() && wal.length() > 0) {
                    zip.putNextEntry(ZipEntry("$DB_NAME-wal"))
                    wal.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(ENTRY_PREFS))
                zip.write(prefsJson(context).toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(ENTRY_META))
                val meta = JSONObject()
                    .put("format", FORMAT)
                    .put("dbVersion", dbVersion)
                    .put("created", System.currentTimeMillis())
                    .put("device", Build.MODEL ?: "Android")
                    .put("expenses", summary.expenses)
                zip.write(meta.toString().toByteArray())
                zip.closeEntry()
            }
        } finally {
            db.endTransaction()
        }
        out
    }

    private fun prefsJson(context: Context): JSONObject {
        val root = JSONObject()
        PREFS.forEach { name ->
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                val e = when (v) {
                    is String -> JSONObject().put("t", "s").put("v", v)
                    is Boolean -> JSONObject().put("t", "b").put("v", v)
                    is Int -> JSONObject().put("t", "i").put("v", v)
                    is Long -> JSONObject().put("t", "l").put("v", v)
                    is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                    is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                    else -> null
                }
                if (e != null) values.put(k, e)
            }
            root.put(name, values)
        }
        return root
    }

    private fun restorePrefs(context: Context, root: JSONObject) {
        PREFS.forEach { name ->
            val values = root.optJSONObject(name) ?: return@forEach
            val ed = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.keys().forEach { k ->
                val e = values.getJSONObject(k)
                when (e.optString("t")) {
                    "s" -> ed.putString(k, e.getString("v"))
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "ss" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            ed.commit()
        }
    }

    /** Checks the zip, swaps the database in, restores settings. */
    suspend fun restore(context: Context, zipFile: File): Summary = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "restore_db").apply { deleteRecursively(); mkdirs() }
        val allowed = setOf(DB_NAME, "$DB_NAME-wal", ENTRY_PREFS, ENTRY_META)
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name in allowed && !entry.isDirectory) {
                    File(dir, entry.name).outputStream().use { zip.copyTo(it) }
                }
            }
        }
        val candidate = File(dir, DB_NAME)
        if (!candidate.exists()) throw BackupException("This backup is empty or damaged.")
        val header = candidate.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
            throw BackupException("This backup is damaged.")
        }

        val currentVersion = AppDatabase.getDatabase(context).openHelper.readableDatabase.version
        val summary: Summary
        val sq = SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            if (sq.version > currentVersion) {
                throw BackupException("This backup is from a newer Splitlee. Update the app, then restore.")
            }
            val ok = sq.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            if (!ok) throw BackupException("This backup is damaged.")
            // Fold the WAL in and make the file self-contained.
            sq.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            sq.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            summary = countsOf { t -> sq.rawQuery("SELECT COUNT(*) FROM `$t`", null).use { it.moveToFirst(); it.getInt(0) } }
        } finally {
            sq.close()
        }

        AppDatabase.closeInstance()
        val target = context.getDatabasePath(DB_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
        candidate.copyTo(target, overwrite = true)

        File(dir, ENTRY_PREFS).takeIf { it.exists() }?.let {
            runCatching { restorePrefs(context, JSONObject(it.readText())) }
        }
        dir.deleteRecursively()
        summary
    }
}

/** Daily backup in the background (Wi-Fi only unless the user changes it). */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BackupStore.isEnabled(applicationContext)) return Result.success()
        return when (DriveBackup.backupNow(applicationContext)) {
            DriveBackup.Outcome.Done -> Result.success()
            DriveBackup.Outcome.NeedsSignIn -> Result.success() // shown in Settings; no retry loop
            DriveBackup.Outcome.Empty -> Result.success() // nothing yet; tries again tomorrow
            is DriveBackup.Outcome.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val NAME = "splitlee_drive_backup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (BackupStore.wifiOnly(context)) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

/** Restarts the app so every screen and cache reads the restored data. */
object AppRestart {
    fun now(activity: Activity) {
        val component = activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.component
            ?: android.content.ComponentName(activity, MainActivity::class.java)
        val restart = Intent.makeRestartActivityTask(component)
        activity.startActivity(restart)
        Runtime.getRuntime().exit(0)
    }
}

/**
 * Returns a function that asks Google for Drive access (account picker and
 * consent the first time, nothing after that) and hands back a token.
 */
@Composable
fun rememberDriveSignIn(onToken: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val tokenCb by rememberUpdatedState(onToken)
    val errorCb by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) {
            errorCb("Sign-in cancelled.")
            return@rememberLauncherForActivityResult
        }
        try {
            val r = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data)
            val t = r.accessToken
            if (t != null) tokenCb(t) else errorCb("Google didn't give access. Try again.")
        } catch (e: Exception) {
            errorCb(DriveBackup.friendlyError(e))
        }
    }
    return {
        Identity.getAuthorizationClient(context).authorize(DriveBackup.request())
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) {
                    launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    val t = r.accessToken
                    if (t != null) tokenCb(t) else errorCb("Google didn't give access. Try again.")
                }
            }
            .addOnFailureListener { errorCb(DriveBackup.friendlyError(it)) }
    }
}
