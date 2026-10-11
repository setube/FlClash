package com.follow.clash

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import com.follow.clash.common.BroadcastLease
import com.follow.clash.common.GlobalState
import kotlinx.coroutines.launch

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_WATCHDOG) return
        val pendingResult = goAsync()
        val lease = BroadcastLease { pendingResult.finish() }
        val timeout = Runnable {
            lease.release { GlobalState.log("Watchdog broadcast handling timed out") }
        }
        mainHandler.postDelayed(timeout, BROADCAST_TIMEOUT_MILLIS)
        GlobalState.launch {
            try {
                val state = ServiceState.runState.value
                if (state == RunState.STOPPED) {
                    GlobalState.log("Watchdog: service stopped, attempting restart")
                    ServiceState.handleStartAction()
                }
            } catch (error: Exception) {
                GlobalState.log("Watchdog: unable to restart service: $error")
            } finally {
                mainHandler.removeCallbacks(timeout)
                lease.release()
            }
        }
    }

    companion object {
        private const val BROADCAST_TIMEOUT_MILLIS = 9_000L
        private const val ACTION_WATCHDOG = "com.follow.clash.action.WATCHDOG"
        private const val REQUEST_CODE = 2001
        private val mainHandler = Handler(Looper.getMainLooper())

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_WATCHDOG).apply {
                setClass(context, WatchdogReceiver::class.java)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }

        fun schedule(context: Context) {
            val am = context.getSystemService<AlarmManager>() ?: return
            val pi = pendingIntent(context)
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pi,
            )
            GlobalState.log("Watchdog scheduled every 15 minutes")
        }

        fun cancel(context: Context) {
            val am = context.getSystemService<AlarmManager>() ?: return
            am.cancel(pendingIntent(context))
            GlobalState.log("Watchdog cancelled")
        }
    }
}
