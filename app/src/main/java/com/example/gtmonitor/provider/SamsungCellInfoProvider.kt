package com.example.gtmonitor.provider

import android.annotation.SuppressLint
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import com.example.gtmonitor.GTLog

/**
 * Samsung-specific provider.
 *
 * On many Samsung devices (especially One UI / Android 14),
 * [TelephonyManager.requestCellInfoUpdate] and [TelephonyManager.getAllCellInfo]
 * consistently return an empty list even with all permissions granted.
 *
 * This provider uses alternative APIs:
 * - [TelephonyManager.getSignalStrength] for signal dBm / level
 * - [TelephonyManager.getNetworkOperator] for MCC / MNC
 * - [PhoneStateListener.LISTEN_SIGNAL_STRENGTHS] for live signal updates
 * - Caches any cell data that *does* arrive via onCellInfoChanged
 */
class SamsungCellInfoProvider : DefaultCellInfoProvider() {

    override val providerName = "Samsung"

    private var cachedSignalStrength: SignalStrength? = null
    private var cachedCells: List<CellInfo>? = null

    private val signalListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in API 31")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            cachedSignalStrength = signalStrength
            GTLog.d("[$providerName] SignalStrength updated: ${signalStrength?.level}/4, ${getDbmFromSignalStrength(signalStrength)} dBm")
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(tm: TelephonyManager) {
        GTLog.d("[$providerName] Provider started (Samsung workaround active)")
        cachedSignalStrength = tm.signalStrength
        GTLog.d("[$providerName] Initial signal: ${cachedSignalStrength?.level}/4, ${getDbmFromSignalStrength(cachedSignalStrength)} dBm")

        tm.listen(signalListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    override fun stop(tm: TelephonyManager) {
        tm.listen(signalListener, PhoneStateListener.LISTEN_NONE)
        cachedSignalStrength = null
        cachedCells = null
        GTLog.d("[$providerName] Provider stopped")
    }

    @SuppressLint("MissingPermission")
    override fun requestCellData(tm: TelephonyManager, callback: (CellDataSnapshot) -> Unit) {
        // First try the standard path (parent class) — it might work sometimes
        super.requestCellData(tm) { snapshot ->
            if (snapshot.cellCount > 0) {
                // Standard API worked — cache and use it
                cachedCells = snapshot.rawCellInfoList
                callback(snapshot)
            } else {
                // Standard API returned nothing — build from alternate sources
                GTLog.d("[$providerName] Standard API empty, using Samsung fallback")
                callback(buildSamsungSnapshot(tm))
            }
        }
    }

    override fun processCellInfoChanged(cellInfoList: List<CellInfo>?): CellDataSnapshot? {
        if (!cellInfoList.isNullOrEmpty()) {
            cachedCells = cellInfoList
            return super.processCellInfoChanged(cellInfoList)
        }
        // Even if the list is empty, return a snapshot from signal strength
        return buildSamsungSnapshot(null)
    }

    // ── Samsung-specific snapshot builder ──────────────────────

    @SuppressLint("MissingPermission")
    private fun buildSamsungSnapshot(tm: TelephonyManager?): CellDataSnapshot {
        // If we have cached cells from a previous listener call, use them
        val cells = cachedCells
        if (!cells.isNullOrEmpty()) {
            GTLog.d("[$providerName] Using ${cells.size} cached cells from listener")
            return buildSnapshot(cells, tm)
        }

        // Otherwise build from signal strength + network operator
        val signal = cachedSignalStrength ?: tm?.signalStrength
        val dbm = getDbmFromSignalStrength(signal)
        val level = signal?.level

        val mccMnc = tm?.networkOperator ?: ""
        val mcc = if (mccMnc.length >= 3) mccMnc.substring(0, 3) else null
        val mnc = if (mccMnc.length >= 4) mccMnc.substring(3) else null

        GTLog.d("[$providerName] Built snapshot from SignalStrength: $dbm dBm, level=$level, mcc=$mcc, mnc=$mnc")

        return CellDataSnapshot(
            signalDbm = dbm?.let { "$it dBm" },
            signalLevel = level?.let { formatLevel(it) },
            cellId = null,
            tac = null,
            pci = null,
            earfcn = null,
            mcc = mcc,
            mnc = mnc,
            bandwidth = null,
            cellCount = 0,
            visibleCells = "N/A",
            rawCellInfoList = null
        )
    }

    private fun getDbmFromSignalStrength(ss: SignalStrength?): Int? {
        if (ss == null) return null
        // getCellSignalStrengths() returns all strengths; pick the strongest
        val strengths = ss.cellSignalStrengths
        if (strengths.isEmpty()) return null
        return strengths.minOf { it.dbm }  // least negative = strongest
    }

    private fun formatLevel(level: Int): String = when (level) {
        4 -> "Great (4/4)"
        3 -> "Good (3/4)"
        2 -> "Moderate (2/4)"
        1 -> "Poor (1/4)"
        0 -> "None (0/4)"
        else -> "$level/4"
    }
}
