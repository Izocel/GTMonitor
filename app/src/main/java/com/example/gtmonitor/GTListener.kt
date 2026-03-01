package com.example.gtmonitor

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.ServiceState

class GTListener(private val context: Context) : PhoneStateListener() {

    private var lastCellId: String? = null
    private var lastServiceState: Int? = null

    @SuppressLint("MissingPermission")
    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
        super.onCellInfoChanged(cellInfo)

        GTLog.d("onCellInfoChanged: ${cellInfo?.size ?: 0} cells")
        cellInfo?.forEachIndexed { i, cell ->
            GTLog.d("  listener cell[$i]: ${cell.javaClass.simpleName} isRegistered=${cell.isRegistered}")
        }

        val service = GTService.instance ?: return
        val currentCellId = service.provider.extractCellId(cellInfo)

        val towerChanged = currentCellId != null && currentCellId != lastCellId
        if (towerChanged) {
            GTLog.d("Tower changed: $lastCellId -> $currentCellId")
            service.refreshWithCellInfo(cellInfo, "Tower changed: $currentCellId")
        } else {
            GTLog.d("Cell info updated (same tower): ${cellInfo?.size ?: 0} cells")
            service.refreshWithCellInfo(cellInfo, "Cell info updated")
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
            GTLog.d("Service state changed: $stateStr")
        } else {
            GTLog.d("Service state: $stateStr")
        }

        lastServiceState = currentState
        val service = GTService.instance ?: return
        service.provider.processServiceState(state)
        service.updateServiceState(stateStr)
        service.refreshNotification("State: $stateStr")
    }

    enum class AlertLevel { SILENT, IMPORTANT, CRITICAL }
}