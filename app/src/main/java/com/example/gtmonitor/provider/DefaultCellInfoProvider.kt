package com.example.gtmonitor.provider

import android.annotation.SuppressLint
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import com.example.gtmonitor.GTLog
import java.util.concurrent.Executors

/**
 * Default provider that uses the standard Android APIs:
 * [TelephonyManager.requestCellInfoUpdate] and [TelephonyManager.getAllCellInfo].
 *
 * Works well on Pixel, stock-Android, and most AOSP devices.
 */
open class DefaultCellInfoProvider : CellInfoProvider {

    override val providerName = "Default"

    override fun start(tm: TelephonyManager) {
        GTLog.d("[$providerName] Provider started")
    }

    override fun stop(tm: TelephonyManager) {
        GTLog.d("[$providerName] Provider stopped")
    }

    @SuppressLint("MissingPermission")
    override fun requestCellData(tm: TelephonyManager, callback: (CellDataSnapshot) -> Unit) {
        tm.requestCellInfoUpdate(
            Executors.newSingleThreadExecutor(),
            object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfoList: MutableList<CellInfo>) {
                    GTLog.d("[$providerName] requestCellInfoUpdate: ${cellInfoList.size} cells")
                    if (cellInfoList.isNotEmpty()) {
                        callback(buildSnapshot(cellInfoList, tm))
                    } else {
                        // Fallback to cached allCellInfo
                        val fallback = try { tm.allCellInfo } catch (_: SecurityException) { null }
                        GTLog.d("[$providerName] allCellInfo fallback: ${fallback?.size ?: 0} cells")
                        callback(buildSnapshot(fallback, tm))
                    }
                }

                override fun onError(errorCode: Int, detail: Throwable?) {
                    GTLog.e("[$providerName] requestCellInfoUpdate error: $errorCode", detail)
                    val fallback = try { tm.allCellInfo } catch (_: SecurityException) { null }
                    callback(buildSnapshot(fallback, tm))
                }
            }
        )
    }

    override fun processCellInfoChanged(cellInfoList: List<CellInfo>?): CellDataSnapshot? {
        if (cellInfoList.isNullOrEmpty()) return null
        return buildSnapshot(cellInfoList, null)
    }

    override fun extractCellId(cellInfoList: List<CellInfo>?): String? {
        val registered = cellInfoList?.firstOrNull { it.isRegistered } ?: return null
        return formatCellIdString(registered)
    }

    override fun formatVisibleCells(cellInfoList: List<CellInfo>?): String {
        if (cellInfoList.isNullOrEmpty()) return "N/A"
        return cellInfoList.joinToString("\n") { cell ->
            val reg = if (cell.isRegistered) "●" else "○"
            val type = getCellType(cell)
            val id = formatCellIdString(cell)
            val dbm = getSignalDbm(cell)
            "$reg $type $id  $dbm"
        }
    }

    // ── Snapshot builder ──────────────────────────────────────────

    protected fun buildSnapshot(cellInfoList: List<CellInfo>?, tm: TelephonyManager?): CellDataSnapshot {
        val registered = cellInfoList?.firstOrNull { it.isRegistered }
            ?: cellInfoList?.firstOrNull()

        val mccMnc = tm?.networkOperator ?: ""
        val mcc = if (mccMnc.length >= 3) mccMnc.substring(0, 3) else null
        val mnc = if (mccMnc.length >= 4) mccMnc.substring(3) else null

        return CellDataSnapshot(
            signalDbm = registered?.let { getSignalDbm(it) },
            signalLevel = registered?.let { getSignalLevel(it) },
            cellId = registered?.let { formatCellIdString(it) },
            tac = registered?.let { getTac(it) },
            pci = registered?.let { getPci(it) },
            earfcn = registered?.let { getEarfcn(it) },
            mcc = registered?.let { getCellMcc(it) } ?: mcc,
            mnc = registered?.let { getCellMnc(it) } ?: mnc,
            bandwidth = registered?.let { getBandwidth(it) },
            cellCount = cellInfoList?.size ?: 0,
            visibleCells = formatVisibleCells(cellInfoList),
            rawCellInfoList = cellInfoList
        )
    }

    // ── Cell field helpers ─────────────────────────────────────

    protected fun getCellType(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> "LTE"
        is CellInfoGsm -> "GSM"
        is CellInfoWcdma -> "WCDMA"
        is CellInfoCdma -> "CDMA"
        is CellInfoNr -> "NR"
        else -> "?"
    }

    protected fun getSignalDbm(cell: CellInfo): String {
        val dbm = when (cell) {
            is CellInfoLte -> cell.cellSignalStrength.dbm
            is CellInfoNr -> cell.cellSignalStrength.dbm
            is CellInfoGsm -> cell.cellSignalStrength.dbm
            is CellInfoWcdma -> cell.cellSignalStrength.dbm
            is CellInfoCdma -> cell.cellSignalStrength.dbm
            else -> return "N/A"
        }
        return "$dbm dBm"
    }

    protected fun getSignalLevel(cell: CellInfo): String {
        val level = when (cell) {
            is CellInfoLte -> cell.cellSignalStrength.level
            is CellInfoNr -> cell.cellSignalStrength.level
            is CellInfoGsm -> cell.cellSignalStrength.level
            is CellInfoWcdma -> cell.cellSignalStrength.level
            is CellInfoCdma -> cell.cellSignalStrength.level
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

    protected fun formatCellIdString(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> "${cell.cellIdentity.ci}"
        is CellInfoGsm -> "${cell.cellIdentity.cid}"
        is CellInfoWcdma -> "${cell.cellIdentity.cid}"
        is CellInfoCdma -> "${cell.cellIdentity.basestationId}"
        else -> "N/A"
    }

    protected fun getTac(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> "${cell.cellIdentity.tac}"
        is CellInfoGsm -> "${cell.cellIdentity.lac}"
        is CellInfoWcdma -> "${cell.cellIdentity.lac}"
        else -> "N/A"
    }

    protected fun getPci(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> "${cell.cellIdentity.pci}"
        is CellInfoWcdma -> "${cell.cellIdentity.psc}"
        else -> "N/A"
    }

    protected fun getEarfcn(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> "${cell.cellIdentity.earfcn}"
        is CellInfoGsm -> "${cell.cellIdentity.arfcn}"
        is CellInfoWcdma -> "${cell.cellIdentity.uarfcn}"
        else -> "N/A"
    }

    protected fun getCellMcc(cell: CellInfo): String? = when (cell) {
        is CellInfoLte -> cell.cellIdentity.mccString
        is CellInfoGsm -> cell.cellIdentity.mccString
        is CellInfoWcdma -> cell.cellIdentity.mccString
        else -> null
    }

    protected fun getCellMnc(cell: CellInfo): String? = when (cell) {
        is CellInfoLte -> cell.cellIdentity.mncString
        is CellInfoGsm -> cell.cellIdentity.mncString
        is CellInfoWcdma -> cell.cellIdentity.mncString
        else -> null
    }

    protected fun getBandwidth(cell: CellInfo): String = when (cell) {
        is CellInfoLte -> {
            val bw = cell.cellIdentity.bandwidth
            if (bw != Int.MAX_VALUE && bw > 0) "${bw / 1000} MHz" else "N/A"
        }
        else -> "N/A"
    }
}
