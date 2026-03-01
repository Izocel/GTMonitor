package com.example.gtmonitor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.example.gtmonitor.provider.CellDataSnapshot
import com.example.gtmonitor.provider.CellInfoProvider
import com.example.gtmonitor.provider.DeviceProviderFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GTService : Service() {

    companion object {
        const val CHANNEL_ID = "gt_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.example.gtmonitor.STOP_SERVICE"
        var instance: GTService? = null
            private set
    }

    lateinit var telephonyManager: TelephonyManager
        private set
    private lateinit var listener: GTListener
    private lateinit var notificationManager: NotificationManager
    private lateinit var openPendingIntent: PendingIntent
    private lateinit var stopPendingIntent: PendingIntent

    lateinit var provider: CellInfoProvider
        private set

    private var foregroundStarted = false

    var currentInfo: ConnectionInfo = ConnectionInfo()
        private set
    var onInfoUpdated: ((ConnectionInfo) -> Unit)? = null
    var onServiceStopped: (() -> Unit)? = null

    // Cache the last cell info received from the listener
    private var lastKnownCells: List<CellInfo>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        GTLog.init(this)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "GT Monitor",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(true)
        }
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(ACTION_STOP).setPackage(packageName)
        stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // Select device-specific provider
        provider = DeviceProviderFactory.create()
        provider.start(telephonyManager)

        listener = GTListener(this)

        telephonyManager.listen(
            listener,
            PhoneStateListener.LISTEN_CELL_INFO or
            PhoneStateListener.LISTEN_SERVICE_STATE
        )

        // Log permission state for diagnostics
        val hasLocation = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasPhone = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        GTLog.d("Permissions: ACCESS_FINE_LOCATION=$hasLocation, READ_PHONE_STATE=$hasPhone")

        refreshNotification()
    }

    /** Called from GTListener with cell data the OS already provided */
    fun refreshWithCellInfo(cellInfoList: List<CellInfo>?, event: String? = null) {
        if (!cellInfoList.isNullOrEmpty()) {
            lastKnownCells = cellInfoList
        }
        val snapshot = provider.processCellInfoChanged(cellInfoList)
        if (snapshot != null) {
            publishSnapshot(snapshot, event)
        } else {
            // Provider couldn't use the list — do a full request
            refreshNotification(event)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshNotification(event: String? = null) {
        provider.requestCellData(telephonyManager) { snapshot ->
            publishSnapshot(snapshot, event)
        }
    }

    private fun publishSnapshot(snapshot: CellDataSnapshot, event: String?) {
        val networkType = getNetworkTypeName()
        val operator = telephonyManager.networkOperatorName.ifEmpty { "Unknown" }

        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val lastEvent = if (event != null) "$timestamp - $event" else "$timestamp - Initial read"

        currentInfo = ConnectionInfo(
            operator = operator,
            networkType = networkType,
            signalDbm = snapshot.signalDbm ?: "N/A",
            signalLevel = snapshot.signalLevel ?: "N/A",
            cellId = snapshot.cellId ?: "N/A",
            tac = snapshot.tac ?: "N/A",
            pci = snapshot.pci ?: "N/A",
            earfcn = snapshot.earfcn ?: "N/A",
            mcc = snapshot.mcc ?: "N/A",
            mnc = snapshot.mnc ?: "N/A",
            bandwidth = snapshot.bandwidth ?: "N/A",
            serviceState = currentInfo.serviceState,
            visibleCells = snapshot.visibleCells ?: "N/A",
            lastEvent = lastEvent
        )

        onInfoUpdated?.invoke(currentInfo)

        val title = "$operator \u2022 $networkType"
        val details = buildString {
            append("Signal: ${snapshot.signalDbm ?: "N/A"} \u2022 Cells: ${snapshot.cellCount}")
            if (event != null) append("\n$event")
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()

        if (!foregroundStarted) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            foregroundStarted = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun updateServiceState(state: String) {
        currentInfo = currentInfo.copy(serviceState = state)
        onInfoUpdated?.invoke(currentInfo)
    }

    @SuppressLint("MissingPermission")
    private fun getNetworkTypeName(): String {
        return when (telephonyManager.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO"
            else -> "Unknown"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        provider.stop(telephonyManager)
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
        instance = null
        onServiceStopped?.invoke()
        onServiceStopped = null
        onInfoUpdated = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class StopReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_STOP) {
                instance?.stopSelf()
            }
        }
    }
}