package com.khotwa.ielts

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

const val SITE = "https://khotwa-ielts.pages.dev"

/** Small JSON-over-HTTPS helper. Returns null on any network error, so screens can show a friendly message. */
suspend fun httpJson(url: String, body: JSONObject? = null, timeoutMs: Int = 30000): JSONObject? = withContext(Dispatchers.IO) {
    try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = timeoutMs
        c.setRequestProperty("Accept", "application/json")
        if (body != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        val o = try { JSONObject(txt) } catch (e: Exception) { JSONObject().put("error", "Bad response ($code)") }
        if (code !in 200..299 && !o.has("error")) o.put("error", "HTTP $code")
        o
    } catch (e: Exception) { null }
}

// ---------------------------------------------------------------- update check

fun installedVersion(ctx: Context): Long = try {
    val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
} catch (e: Exception) { 0L }

/** Reads version.json that the build publishes next to the APK. Returns the newer version name, or null. */
suspend fun checkUpdate(ctx: Context): String? {
    val o = httpJson("$SITE/version.json?t=${System.currentTimeMillis()}") ?: return null
    val latest = o.optLong("versionCode", 0)
    return if (latest > installedVersion(ctx)) o.optString("versionName", "new") else null
}

// ---------------------------------------------------------------- daily reminder

object Reminder {
    private const val CH = "daily"
    fun schedule(ctx: Context, hour: Int, minute: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis, AlarmManager.INTERVAL_DAY, pi(ctx))
    }
    fun cancel(ctx: Context) { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx)) }
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun show(ctx: Context) {
        val st = Store(ctx); I18n.ar = st.lang == "ar"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, tr("Daily study reminder", "تذكير المذاكرة اليومي"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 8, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val due = try { Content.load(ctx); mistakesDue(st).size } catch (e: Exception) { 0 }
        val text = tr("Your plan for today is ready (${st.minutes} min).", "خطتك لليوم جاهزة (${st.minutes} دقيقة).") + if (due > 0) tr(" $due mistakes to review.", " $due أخطاء للمراجعة.") else ""
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, CH) else android.app.Notification.Builder(ctx)
        val n = b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Khotwa IELTS").setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build()
        try { nm.notify(1, n) } catch (e: SecurityException) { }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val st = Store(ctx)
        if (st.mapInt("cfg", "rem") != 1) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminder.schedule(ctx, st.mapInt("cfg", "remH", 20), st.mapInt("cfg", "remM", 0)); return }
        if (st.mapInt("cfg", "remLast") == today()) return
        st.putMap("cfg", "remLast", today())
        Reminder.show(ctx)
    }
}
