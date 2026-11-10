package com.ashwani.monthlypayments

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Reminders {
    const val PREFS = "payments"
    const val ACTION_DAILY = "com.ashwani.monthlypayments.DAILY_CHECK"
    private const val KEY_STATE = "state"
    private const val CHANNEL = "payment_reminders"
    private const val NOTIF_ID = 1001

    private data class Due(val name: String, val date: LocalDate, val amount: Double)

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Payment reminders", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Reminders for payments that are due or overdue"
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun saveState(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATE, json).apply()
        reschedule(ctx)
    }

    private fun state(ctx: Context): JSONObject? = try {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null)?.let { JSONObject(it) }
    } catch (e: Exception) { null }

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DAILY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        am.cancel(pi)
        val settings = state(ctx)?.optJSONObject("settings")
        if (settings?.optBoolean("remindersOn", true) == false) return
        val time = try { LocalTime.parse(settings?.optString("time", "09:00") ?: "09:00") } catch (e: Exception) { LocalTime.of(9, 0) }
        var next = LocalDate.now().atTime(time)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    /** Unpaid items whose reminder window has started: from N days before the due date until the month ends. */
    private fun findDue(s: JSONObject, today: LocalDate): List<Due> {
        val items = s.optJSONArray("items") ?: return emptyList()
        val months = s.optJSONObject("months") ?: JSONObject()
        val out = mutableListOf<Due>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val lead = item.optInt("remind", -1)
            val dueDay = item.optInt("dueDay", 0)
            if (lead < 0 || dueDay <= 0) continue
            val yearly = item.optString("freq", "monthly") == "yearly"
            val dueMonth = item.optInt("dueMonth", 0)
            val created = item.optString("created", "")
            val id = item.optString("id")
            for (offset in 0L..1L) {   // this month, plus next month for early reminders
                val ym = YearMonth.from(today).plusMonths(offset)
                if (yearly && dueMonth != ym.monthValue) continue
                val key = String.format(Locale.US, "%04d-%02d", ym.year, ym.monthValue)
                if (created.isNotEmpty() && created > key) continue
                if (months.optJSONObject(key)?.has(id) == true) continue
                val due = ym.atDay(minOf(dueDay, ym.lengthOfMonth()))
                if (!today.isBefore(due.minusDays(lead.toLong())) && !today.isAfter(ym.atEndOfMonth())) {
                    val amt = item.optDouble("amount", 0.0)
                    out += Due(item.optString("name", "Payment"), due, if (amt.isNaN()) 0.0 else amt)
                }
            }
        }
        return out.sortedBy { it.date }
    }

    private val money: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    private fun whenText(d: Due, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, d.date)
        return when {
            days < 0 -> "overdue since ${d.date.format(dayFmt)}"
            days == 0L -> "due today"
            days == 1L -> "due tomorrow"
            else -> "due ${d.date.format(dayFmt)}"
        }
    }

    private fun line(d: Due, today: LocalDate): String =
        (if (d.amount > 0) "${d.name} · ₹${money.format(d.amount)}" else d.name) + " — " + whenText(d, today)

    fun notifyDue(ctx: Context) {
        val s = state(ctx) ?: return
        if (s.optJSONObject("settings")?.optBoolean("remindersOn", true) == false) return
        val today = LocalDate.now()
        val due = findDue(s, today)
        if (due.isEmpty()) return
        val title = if (due.size == 1) {
            val d = due[0]
            if (d.date.isBefore(today)) "${d.name} is overdue" else "${d.name} is ${whenText(d, today)}"
        } else {
            val overdue = due.count { it.date.isBefore(today) }
            if (overdue > 0) "${due.size} payments need attention ($overdue overdue)" else "${due.size} payments coming up"
        }
        val body = if (due.size == 1) line(due[0], today) else due.joinToString(", ") { it.name }
        val style = Notification.InboxStyle().also { st -> due.take(7).forEach { st.addLine(line(it, today)) } }
        post(ctx, title, body, if (due.size > 1) style else Notification.BigTextStyle().bigText(body))
    }

    fun notifyTest(ctx: Context) {
        createChannel(ctx)
        val s = state(ctx)
        val due = if (s != null) findDue(s, LocalDate.now()) else emptyList()
        val body = if (due.isEmpty()) "Nothing is due right now. You’ll be reminded at your chosen time."
                   else "Right now: " + due.joinToString(", ") { it.name }
        post(ctx, "Reminders are working", body, Notification.BigTextStyle().bigText(body))
    }

    private fun post(ctx: Context, title: String, text: String, style: Notification.Style) {
        if (!canNotify(ctx)) return
        createChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFF1E7F5C.toInt())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }
}
