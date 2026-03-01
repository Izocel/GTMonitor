package com.example.gtmonitor

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.util.Log

class GTListener(private val context: Context) : PhoneStateListener() {

    private var lastCellId: String? = null
    private var lastServiceState: Int? = null

    @SuppressLint("MissingPermission")
    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
        super.onCellInfoChanged(cellInfo)

        val registered = cellInfo?.firstOrNull { it.isRegistered }
        val currentCellId = registered?.let { getCellId(it) }

        val towerChanged = currentCellId != null && currentCellId != lastCellId
        if (towerChanged) {
            Log.d("GT", "Tower changed: $lastCellId -> $currentCellId")
            playAlert(AlertLevel.IMPORTANT)
            GTService.instance?.refreshNotification("Tower changed: $currentCellId")
        } else {
            Log.d("GT", "Cell info updated (same tower): ${cellInfo?.size ?: 0} cells")
            GTService.instance?.refreshNotification("Cell info updated")
        }

        lastCellId = currentCellId
    }

    override fun onServiceStateChanged(state: ServiceState?) {
        super.onServiceStateChanged(state)

        val currentState = state?.state
        val stateStr = when (currentState) {
            0 -> "In service"; 1 -> "Out of service"; 2 -> "Emergency only"; 3 -> "Power off"; else -> "Unknown"
        }

        val changed = lastServiceState != null && currentState != lastServiceState
        if (changed) {
            val level = when (currentState) {
                1, 2, 3 -> AlertLevel.CRITICAL   // lost service / emergency / power off
                0 -> AlertLevel.IMPORTANT         // back in service
                else -> AlertLevel.SILENT
            }
            Log.d("GT", "Service state changed: $stateStr")
            playAlert(level)
        } else {
            Log.d("GT", "Service state: $stateStr")
        }

        lastServiceState = currentState
        GTService.instance?.updateServiceState(stateStr)
        GTService.instance?.refreshNotification("State: $stateStr")
    }

    private fun getCellId(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> "LTE-${cell.cellIdentity.ci}"
            is CellInfoGsm -> "GSM-${cell.cellIdentity.cid}"
            is CellInfoWcdma -> "WCDMA-${cell.cellIdentity.cid}"
            is CellInfoCdma -> "CDMA-${cell.cellIdentity.basestationId}"
            else -> "Unknown-${cell.hashCode()}"
        }
    }

    enum class AlertLevel { SILENT, IMPORTANT, CRITICAL }

    private fun playAlert(level: AlertLevel) {
        when (level) {
            AlertLevel.CRITICAL -> {
                // Long urgent alert tone
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                tone.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
            }
            AlertLevel.IMPORTANT -> {
                // Short double beep
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            }
            AlertLevel.SILENT -> { /* no sound */ }
        }
    }
}