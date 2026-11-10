package com.ashwani.monthlypayments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires once a day at the chosen time; also re-arms the alarm after reboot, app update or clock change. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Reminders.ACTION_DAILY) Reminders.notifyDue(context)
        Reminders.reschedule(context)
    }
}
