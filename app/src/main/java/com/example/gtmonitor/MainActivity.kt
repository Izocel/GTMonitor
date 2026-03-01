package com.example.gtmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.gtmonitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.stopButton.setOnClickListener {
            GTService.instance?.stopSelf()
            finish()
        }

        // Auto-start on launch
        startMonitoring()
    }

    override fun onResume() {
        super.onResume()
        val service = GTService.instance
        if (service != null) {
            updateUI(service.currentInfo)
            service.onInfoUpdated = { info ->
                handler.post { updateUI(info) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        GTService.instance?.onInfoUpdated = null
    }

    private fun updateUI(info: ConnectionInfo) {
        binding.operatorValue.text = info.operator
        binding.networkTypeValue.text = info.networkType
        binding.signalValue.text = info.signalDbm
        binding.signalLevelValue.text = info.signalLevel
        binding.cellIdValue.text = info.cellId
        binding.tacValue.text = info.tac
        binding.pciValue.text = info.pci
        binding.earfcnValue.text = info.earfcn
        binding.mccValue.text = info.mcc
        binding.mncValue.text = info.mnc
        binding.bandwidthValue.text = info.bandwidth
        binding.serviceStateValue.text = info.serviceState
        binding.visibleCellsValue.text = info.visibleCells
        binding.lastEventValue.text = info.lastEvent
    }

    private fun startMonitoring() {
        if (hasPermissions()) {
            startGTService()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startGTService() {
        val alreadyRunning = GTService.instance != null
        val intent = Intent(this, GTService::class.java)
        startForegroundService(intent)
        // Only auto-close on first start, not when reopened via notification
        if (!alreadyRunning) {
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && hasPermissions()) {
            startGTService()
        }
    }
}