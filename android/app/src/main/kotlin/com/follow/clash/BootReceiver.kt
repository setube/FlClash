package com.follow.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.follow.clash.common.BroadcastLease
import com.follow.clash.common.GlobalState
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val pendingResult = goAsync()
        val lease = BroadcastLease { pendingResult.finish() }
        val timeout = Runnable {
            lease.release { GlobalState.log("Boot broadcast handling timed out") }
        }
        mainHandler.postDelayed(timeout, BROADCAST_TIMEOUT_MILLIS)
        GlobalState.launch {
            try {
                GlobalState.log("Device booted, auto-starting service")
                ServiceState.handleStartAction()
            } catch (error: Exception) {
                GlobalState.log("Unable to handle boot broadcast: $error")
            } finally {
                mainHandler.removeCallbacks(timeout)
                lease.release()
            }
        }
    }

    companion object {
        private const val BROADCAST_TIMEOUT_MILLIS = 9_000L
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
