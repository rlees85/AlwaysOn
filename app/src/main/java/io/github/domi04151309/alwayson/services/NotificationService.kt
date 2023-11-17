package io.github.domi04151309.alwayson.services

import android.app.Notification
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.preference.PreferenceManager
import io.github.domi04151309.alwayson.actions.alwayson.AlwaysOn
import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.helpers.Global
import io.github.domi04151309.alwayson.helpers.JSON
import io.github.domi04151309.alwayson.receivers.CombinedServiceReceiver
import org.json.JSONArray
import java.lang.Math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class NotificationService : NotificationListenerService() {

    private lateinit var prefs: SharedPreferences
    private var sentRecently: Boolean = false
    private var cache: Int = -1

    interface OnNotificationsChangedListener {
        fun onNotificationsChanged()
    }

    companion object {
        internal var count: Int = 0; private set
        internal var icons: ArrayList<Pair<Icon, Int>> = arrayListOf(); private set
        internal var detailed: Array<StatusBarNotification> = arrayOf(); private set

        @JvmField
        internal val listeners: ArrayList<OnNotificationsChangedListener> = arrayListOf()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        updateVars()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        updateVars()

        val rules = Rules(this, prefs)
        if (
            isValidNotification(sbn)
            && !CombinedServiceReceiver.isScreenOn
            && !CombinedServiceReceiver.isAlwaysOnRunning
            && rules.isAlwaysOnDisplayEnabled()
            && rules.isAmbientMode()
            && rules.matchesChargingState()
            && rules.matchesBatteryPercentage()
            && rules.isInTimePeriod()
        ) {
            startActivity(
                Intent(
                    this,
                    AlwaysOn::class.java
                ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        updateVars()
    }

    private fun updateVars() {
        if (!sentRecently) {
            sentRecently = true
            val apps: ArrayList<String>
            icons = arrayListOf()
            count = 0
            try {
                detailed = activeNotifications
                apps = ArrayList(detailed.size)
                icons = ArrayList(detailed.size)
                for (notification in detailed) {
                    if (isValidNotification(notification)) {
                        if (notification.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) count++
                        if (!apps.contains(notification.packageName)) {
                            apps += notification.packageName

                            var color: Int = notification.notification.color

                            color = if (color == Color.BLACK) {
                                Color.WHITE
                            } else {
                                var oldRed: Int = Color.red(color)
                                var oldGreen: Int = Color.green(color)
                                var oldBlue: Int = Color.blue(color)
                                var rgbMax: Int = max(oldRed, oldGreen, oldBlue)
                                var rgbFactor: Float = 255 / rgbMax.toFloat()
                                var newRed: Int = min(255, (oldRed * rgbFactor).roundToInt())
                                var newGreen: Int = min(255, (oldGreen * rgbFactor).roundToLong())
                                var newBlue: Int = min(255, (oldBlue * rgbFactor).roundToInt())
                                Color.rgb(newRed, newGreen, newBlue)
                            }

                            icons.add(
                                Pair(
                                    notification.notification.smallIcon,
                                    color
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(Global.LOG_TAG, e.toString())
                count = 0
                icons = arrayListOf()
            }
            if (cache != count) {
                cache = count
                listeners.forEach { it.onNotificationsChanged() }
            }
            Handler(Looper.getMainLooper()).postDelayed({ sentRecently = false }, 500)
        }
    }

    private fun isValidNotification(notification: StatusBarNotification): Boolean {
        return !notification.isOngoing && !JSON.contains(
            JSONArray(prefs.getString("blocked_notifications", "[]")),
            notification.packageName
        )
    }
}