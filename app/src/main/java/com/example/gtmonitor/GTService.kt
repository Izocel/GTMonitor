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
import android.os.IBinder
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoGsm
import android.telephony.CellInfoWcdma
import android.telephony.CellInfoCdma
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityWcdma
import android.telephony.CellIdentityCdma
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
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

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var listener: GTListener
    private lateinit var notificationManager: NotificationManager
    private lateinit var openPendingIntent: PendingIntent
    private lateinit var stopPendingIntent: PendingIntent

    private var foregroundStarted = false

    var currentInfo: ConnectionInfo = ConnectionInfo()
        private set
    var onInfoUpdated: ((ConnectionInfo) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

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
        listener = GTListener(this)

        telephonyManager.listen(
            listener,
            PhoneStateListener.LISTEN_CELL_INFO or
            PhoneStateListener.LISTEN_SERVICE_STATE
        )

        refreshNotification()
    }

    @SuppressLint("MissingPermission")
    fun refreshNotification(event: String? = null) {
        val networkType = getNetworkTypeName()
        val operator = telephonyManager.networkOperatorName.ifEmpty { "Unknown" }
        val cellInfoList = try { telephonyManager.allCellInfo } catch (_: SecurityException) { null }
        val registered = cellInfoList?.firstOrNull { it.isRegistered }

        val signalDbm = registered?.let { formatSignalDbm(it) } ?: "N/A"
        val signalLevel = registered?.let { formatSignalLevel(it) } ?: "N/A"
        val cellId = registered?.let { formatCellId(it) } ?: "N/A"
        val tac = registered?.let { formatTac(it) } ?: "N/A"
        val pci = registered?.let { formatPci(it) } ?: "N/A"
        val earfcn = registered?.let { formatEarfcn(it) } ?: "N/A"
        val mcc = registered?.let { formatMcc(it) } ?: "N/A"
        val mnc = registered?.let { formatMnc(it) } ?: "N/A"
        val bandwidth = registered?.let { formatBandwidth(it) } ?: "N/A"
        val cellCount = cellInfoList?.size ?: 0

        val visibleCells = cellInfoList?.joinToString("\n") { cell ->
            val reg = if (cell.isRegistered) "●" else "○"
            val type = getCellType(cell)
            val id = formatCellId(cell)
            val dbm = formatSignalDbm(cell)
            "$reg $type $id  $dbm"
        } ?: "N/A"

        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val lastEvent = if (event != null) "$timestamp - $event" else "$timestamp - Initial read"

        currentInfo = ConnectionInfo(
            operator = operator,
            networkType = networkType,
            signalDbm = signalDbm,
            signalLevel = signalLevel,
            cellId = cellId,
            tac = tac,
            pci = pci,
            earfcn = earfcn,
            mcc = mcc,
            mnc = mnc,
            bandwidth = bandwidth,
            serviceState = currentInfo.serviceState,
            visibleCells = visibleCells,
            lastEvent = lastEvent
        )

        onInfoUpdated?.invoke(currentInfo)

        val title = "$operator \u2022 $networkType"
        val details = buildString {
            append("Signal: $signalDbm \u2022 Cells: $cellCount")
            if (event != null) append("\n$event")
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()

        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
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

    private fun getCellType(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> "LTE"
            is CellInfoGsm -> "GSM"
            is CellInfoWcdma -> "WCDMA"
            is CellInfoCdma -> "CDMA"
            else -> if (android.os.Build.VERSION.SDK_INT >= 29 && cell is CellInfoNr) "NR" else "?"
        }
    }

    private fun formatSignalDbm(cell: CellInfo): String {
        val dbm = when {
            cell is CellInfoLte -> cell.cellSignalStrength.dbm
            android.os.Build.VERSION.SDK_INT >= 29 && cell is CellInfoNr -> cell.cellSignalStrength.dbm
            cell is CellInfoGsm -> cell.cellSignalStrength.dbm
            cell is CellInfoWcdma -> cell.cellSignalStrength.dbm
            cell is CellInfoCdma -> cell.cellSignalStrength.dbm
            else -> return "N/A"
        }
        return "$dbm dBm"
    }

    private fun formatSignalLevel(cell: CellInfo): String {
        val level = when {
            cell is CellInfoLte -> cell.cellSignalStrength.level
            android.os.Build.VERSION.SDK_INT >= 29 && cell is CellInfoNr -> cell.cellSignalStrength.level
            cell is CellInfoGsm -> cell.cellSignalStrength.level
            cell is CellInfoWcdma -> cell.cellSignalStrength.level
            cell is CellInfoCdma -> cell.cellSignalStrength.level
            else -> return "N/A"
        }
        return when (level) {
            4 -> "Great (4/4)"
            3 -> "Good (3/4)"
            2 -> "Moderate (2/4)"
            1 -> "Poor (1/4)"
            0 -> "None (0/4)"
            else -> "$level/4"
        }
    }

    private fun formatCellId(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> "${cell.cellIdentity.ci}"
            is CellInfoGsm -> "${cell.cellIdentity.cid}"
            is CellInfoWcdma -> "${cell.cellIdentity.cid}"
            is CellInfoCdma -> "${cell.cellIdentity.basestationId}"
            else -> "N/A"
        }
    }

    private fun formatTac(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> "${cell.cellIdentity.tac}"
            is CellInfoGsm -> "${cell.cellIdentity.lac}"
            is CellInfoWcdma -> "${cell.cellIdentity.lac}"
            else -> "N/A"
        }
    }

    private fun formatPci(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> "${cell.cellIdentity.pci}"
            is CellInfoWcdma -> "${cell.cellIdentity.psc}"
            else -> "N/A"
        }
    }

    private fun formatEarfcn(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> "${cell.cellIdentity.earfcn}"
            is CellInfoGsm -> "${cell.cellIdentity.arfcn}"
            is CellInfoWcdma -> "${cell.cellIdentity.uarfcn}"
            else -> "N/A"
        }
    }

    private fun formatMcc(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> cell.cellIdentity.mccString ?: "N/A"
            is CellInfoGsm -> cell.cellIdentity.mccString ?: "N/A"
            is CellInfoWcdma -> cell.cellIdentity.mccString ?: "N/A"
            else -> "N/A"
        }
    }

    private fun formatMnc(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> cell.cellIdentity.mncString ?: "N/A"
            is CellInfoGsm -> cell.cellIdentity.mncString ?: "N/A"
            is CellInfoWcdma -> cell.cellIdentity.mncString ?: "N/A"
            else -> "N/A"
        }
    }

    private fun formatBandwidth(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> {
                val bw = cell.cellIdentity.bandwidth
                if (bw != Int.MAX_VALUE && bw > 0) "${bw / 1000} MHz" else "N/A"
            }
            else -> "N/A"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
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