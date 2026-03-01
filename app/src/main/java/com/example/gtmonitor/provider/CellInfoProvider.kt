package com.example.gtmonitor.provider

import android.telephony.CellInfo
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import com.example.gtmonitor.ConnectionInfo

/**
 * Abstraction for fetching cell/signal data.
 * Each device model can supply its own implementation
 * depending on which telephony APIs actually work.
 */
interface CellInfoProvider {

    /** Human-readable name for logging which provider is active */
    val providerName: String

    /**
     * Optional callback the provider fires whenever it has a fresh snapshot
     * (e.g. from a live signal-strength listener).  The service sets this
     * after creating the provider so the UI stays in sync.
     */
    var onSnapshotAvailable: ((CellDataSnapshot) -> Unit)?

    /**
     * Initialise the provider (register listeners, etc.).
     * Called once when the service starts.
     */
    fun start(tm: TelephonyManager)

    /**
     * Tear down the provider (unregister listeners, etc.).
     * Called once when the service stops.
     */
    fun stop(tm: TelephonyManager)

    /**
     * Actively request a fresh snapshot of cell/signal data.
     * The result is delivered asynchronously via [callback].
     */
    fun requestCellData(tm: TelephonyManager, callback: (CellDataSnapshot) -> Unit)

    /**
     * Process a cell-info-changed callback from [PhoneStateListener].
     * Returns a [CellDataSnapshot] built from the supplied list,
     * or null if the provider cannot make use of it.
     */
    fun processCellInfoChanged(cellInfoList: List<CellInfo>?): CellDataSnapshot?

    /** Extract a cell identifier string used for tower-change detection */
    fun extractCellId(cellInfoList: List<CellInfo>?): String?

    /** Format a [CellInfo] list into the visible-cells display string */
    fun formatVisibleCells(cellInfoList: List<CellInfo>?): String

    /**
     * Process a service-state change.  On devices where [CellInfo] is unavailable,
     * providers can extract cell identity from [ServiceState] →
     * [NetworkRegistrationInfo] → [CellIdentity].  Default: no-op.
     */
    fun processServiceState(state: ServiceState?) { /* no-op by default */ }
}

/**
 * Snapshot of all the cell/signal fields the UI needs.
 * Provider implementations populate what they can;
 * anything left null will show as "N/A".
 */
data class CellDataSnapshot(
    val signalDbm: String? = null,
    val signalLevel: String? = null,
    val cellId: String? = null,
    val tac: String? = null,
    val pci: String? = null,
    val earfcn: String? = null,
    val mcc: String? = null,
    val mnc: String? = null,
    val bandwidth: String? = null,
    val timingAdvance: String? = null,
    val estimatedDistance: String? = null,
    val cellCount: Int = 0,
    val visibleCells: String? = null,
    val rawCellInfoList: List<CellInfo>? = null
)
