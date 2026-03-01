package com.example.gtmonitor.provider

import android.annotation.SuppressLint
import android.os.Build
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.NetworkRegistrationInfo
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
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
    private var telephonyManagerRef: TelephonyManager? = null

    // Cell identity extracted from ServiceState → NetworkRegistrationInfo
    private var ssCellId: String? = null
    private var ssTac: String? = null
    private var ssPci: String? = null
    private var ssEarfcn: String? = null
    private var ssBandwidth: String? = null
    private var ssMcc: String? = null
    private var ssMnc: String? = null
    private var ssCellType: String? = null

    /** All cells seen via ServiceState NetworkRegistrationInfo (for visible-cells list) */
    private var ssRegisteredCells: List<RegistrationEntry> = emptyList()

    /** Parsed cell entry from a single NetworkRegistrationInfo */
    private data class RegistrationEntry(
        val type: String,
        val cellId: String?,
        val tac: String?,
        val pci: String?,
        val earfcn: String?,
        val bandwidth: String?,
        val mcc: String?,
        val mnc: String?,
        val isRegistered: Boolean
    )

    private val signalListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in API 31")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            cachedSignalStrength = signalStrength
            GTLog.d("[$providerName] SignalStrength updated: ${signalStrength?.level}/4, ${getDbmFromSignalStrength(signalStrength)} dBm")
            // Push live update to the service so the UI refreshes
            onSnapshotAvailable?.let { callback ->
                callback(buildSamsungSnapshot(telephonyManagerRef))
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(tm: TelephonyManager) {
        GTLog.d("[$providerName] Provider started (Samsung workaround active)")
        telephonyManagerRef = tm
        cachedSignalStrength = tm.signalStrength
        GTLog.d("[$providerName] Initial signal: ${cachedSignalStrength?.level}/4, ${getDbmFromSignalStrength(cachedSignalStrength)} dBm")

        tm.listen(signalListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    override fun stop(tm: TelephonyManager) {
        tm.listen(signalListener, PhoneStateListener.LISTEN_NONE)
        telephonyManagerRef = null
        cachedSignalStrength = null
        cachedCells = null
        ssCellId = null; ssTac = null; ssPci = null; ssEarfcn = null
        ssBandwidth = null; ssMcc = null; ssMnc = null; ssCellType = null
        ssRegisteredCells = emptyList()
        onSnapshotAvailable = null
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

    override fun extractCellId(cellInfoList: List<CellInfo>?): String? {
        // Try standard CellInfo first, then fall back to ServiceState identity
        return super.extractCellId(cellInfoList)
            ?: ssCellId?.let { "${ssCellType ?: "?"}-$it" }
    }

    // ── ServiceState-based cell identity extraction (API 30+) ──

    override fun processServiceState(state: ServiceState?) {
        if (state == null || Build.VERSION.SDK_INT < 30) return

        // Collect ALL registration entries (different domains / RATs)
        val allRegInfos = try {
            state.networkRegistrationInfoList
        } catch (e: Exception) {
            GTLog.w("[$providerName] Failed to get NetworkRegistrationInfoList: ${e.message}")
            return
        }

        if (allRegInfos.isNullOrEmpty()) {
            GTLog.d("[$providerName] ServiceState: empty NetworkRegistrationInfoList")
            return
        }

        GTLog.d("[$providerName] ServiceState: ${allRegInfos.size} registration entries")

        val entries = mutableListOf<RegistrationEntry>()
        val seenIds = mutableSetOf<String>()

        for (regInfo in allRegInfos) {
            val identity = regInfo.cellIdentity ?: continue
            val entry = parseIdentity(identity, regInfo.isRegistered)
            if (entry != null) {
                // Dedup by cellId+type so CS and PS domain don't double-count the same cell
                val key = "${entry.type}-${entry.cellId}"
                if (seenIds.add(key)) {
                    entries.add(entry)
                }
            }
        }

        if (entries.isEmpty()) {
            GTLog.d("[$providerName] ServiceState: no usable CellIdentity found")
            return
        }

        ssRegisteredCells = entries

        // Pick the primary registered cell for the main fields
        val primary = entries.firstOrNull { it.isRegistered } ?: entries.first()
        ssCellType = primary.type
        ssCellId = primary.cellId
        ssTac = primary.tac
        ssPci = primary.pci
        ssEarfcn = primary.earfcn
        ssBandwidth = primary.bandwidth
        ssMcc = primary.mcc
        ssMnc = primary.mnc

        GTLog.i("[$providerName] ServiceState: ${entries.size} unique cells. Primary: type=$ssCellType cid=$ssCellId tac=$ssTac pci=$ssPci earfcn=$ssEarfcn bw=$ssBandwidth")

        // Push updated snapshot to UI
        onSnapshotAvailable?.invoke(buildSamsungSnapshot(telephonyManagerRef))
    }

    private fun parseIdentity(identity: android.telephony.CellIdentity, isRegistered: Boolean): RegistrationEntry? {
        return when (identity) {
            is CellIdentityLte -> RegistrationEntry(
                type = "LTE",
                cellId = identity.ci.takeIf { it != Int.MAX_VALUE }?.toString(),
                tac = identity.tac.takeIf { it != Int.MAX_VALUE }?.toString(),
                pci = identity.pci.takeIf { it != Int.MAX_VALUE }?.toString(),
                earfcn = identity.earfcn.takeIf { it != Int.MAX_VALUE }?.toString(),
                bandwidth = identity.bandwidth.takeIf { it != Int.MAX_VALUE && it > 0 }?.let { "${it / 1000} MHz" },
                mcc = identity.mccString, mnc = identity.mncString,
                isRegistered = isRegistered
            )
            is CellIdentityNr -> RegistrationEntry(
                type = "NR",
                cellId = identity.nci.takeIf { it != Long.MAX_VALUE }?.toString(),
                tac = identity.tac.takeIf { it != Int.MAX_VALUE }?.toString(),
                pci = identity.pci.takeIf { it != Int.MAX_VALUE }?.toString(),
                earfcn = identity.nrarfcn.takeIf { it != Int.MAX_VALUE }?.toString(),
                bandwidth = null,
                mcc = identity.mccString, mnc = identity.mncString,
                isRegistered = isRegistered
            )
            is CellIdentityGsm -> RegistrationEntry(
                type = "GSM",
                cellId = identity.cid.takeIf { it != Int.MAX_VALUE }?.toString(),
                tac = identity.lac.takeIf { it != Int.MAX_VALUE }?.toString(),
                pci = null,
                earfcn = identity.arfcn.takeIf { it != Int.MAX_VALUE }?.toString(),
                bandwidth = null,
                mcc = identity.mccString, mnc = identity.mncString,
                isRegistered = isRegistered
            )
            is CellIdentityWcdma -> RegistrationEntry(
                type = "WCDMA",
                cellId = identity.cid.takeIf { it != Int.MAX_VALUE }?.toString(),
                tac = identity.lac.takeIf { it != Int.MAX_VALUE }?.toString(),
                pci = identity.psc.takeIf { it != Int.MAX_VALUE }?.toString(),
                earfcn = identity.uarfcn.takeIf { it != Int.MAX_VALUE }?.toString(),
                bandwidth = null,
                mcc = identity.mccString, mnc = identity.mncString,
                isRegistered = isRegistered
            )
            else -> {
                GTLog.d("[$providerName] Unhandled CellIdentity type: ${identity.javaClass.simpleName}")
                null
            }
        }
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

        // Build from signal strength + network operator + ServiceState identity
        val signal = cachedSignalStrength ?: tm?.signalStrength
        val dbm = getDbmFromSignalStrength(signal)
        val level = signal?.level

        // Timing Advance from SignalStrength (LTE)
        val ta = getTimingAdvanceFromSignalStrength(signal)
        val taStr = ta?.toString()
        val distStr = ta?.let { formatDistance(it) }

        val mccMnc = tm?.networkOperator ?: ""
        val mcc = ssMcc ?: if (mccMnc.length >= 3) mccMnc.substring(0, 3) else null
        val mnc = ssMnc ?: if (mccMnc.length >= 4) mccMnc.substring(3) else null

        // Build visible cells from ALL registration entries
        val entries = ssRegisteredCells
        val cellCount = entries.size.coerceAtLeast(if (ssCellId != null) 1 else 0)

        val visibleCells = if (entries.isNotEmpty()) {
            entries.joinToString("\n") { entry ->
                val reg = if (entry.isRegistered) "●" else "○"
                val pciPart = entry.pci?.let { " PCI:$it" } ?: ""
                val idPart = entry.cellId ?: "??"
                val sigPart = if (entry == entries.firstOrNull { it.isRegistered } || entries.size == 1) {
                    dbm?.let { " $it dBm" } ?: ""
                } else ""
                val distPart = if (entry.isRegistered && distStr != null) " ~$distStr" else ""
                "$reg ${entry.type}  ID:$idPart$pciPart$sigPart$distPart"
            }
        } else if (ssCellId != null && ssCellType != null) {
            val distPart = distStr?.let { " ~$it" } ?: ""
            "● $ssCellType  ID:$ssCellId${ssPci?.let { " PCI:$it" } ?: ""}  ${dbm?.let { "$it dBm" } ?: ""}$distPart"
        } else {
            "N/A"
        }

        GTLog.d("[$providerName] Built snapshot: $dbm dBm, level=$level, cid=$ssCellId, tac=$ssTac, pci=$ssPci, ta=$taStr, cells=$cellCount")

        return CellDataSnapshot(
            signalDbm = dbm?.let { "$it dBm" },
            signalLevel = level?.let { formatLevel(it) },
            cellId = ssCellId,
            tac = ssTac,
            pci = ssPci,
            earfcn = ssEarfcn,
            mcc = mcc,
            mnc = mnc,
            bandwidth = ssBandwidth,
            timingAdvance = taStr,
            estimatedDistance = distStr,
            cellCount = cellCount,
            visibleCells = visibleCells,
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
