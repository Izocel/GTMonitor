package com.example.gtmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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


        NotificationSoundPlayer.init(this)

        // Set version dynamically
        val versionView = binding.root.findViewById<android.widget.TextView>(R.id.versionText)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        versionView?.text = "v$versionName"

        binding.toggleButton.setOnClickListener {
            if (GTService.instance != null) {
                GTService.instance?.onServiceStopped = {
                    handler.post {
                        updateUI(ConnectionInfo())
                        updateToggleState()
                    }
                }
                GTService.instance?.stopSelf()
            } else {
                startMonitoring()
            }
        }

        binding.refreshButton.setOnClickListener {
            GTService.instance?.refreshNotification("Manual refresh")
        }

        binding.logButton.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        binding.muteToggle.setOnCheckedChangeListener { _, isChecked ->
            NotificationSoundPlayer.setMuted(isChecked)
        }

        // Set initial mute toggle state
        binding.muteToggle.isChecked = NotificationSoundPlayer.isMuted()

        // Auto-start service on launch if permissions are granted
        if (GTService.instance == null && hasPermissions()) {
            startGTService()
        } else if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        updateToggleState()
        val service = GTService.instance
        if (service != null) {
            service.refreshNotification("App resumed")
            updateUI(service.currentInfo)
            bindServiceCallbacks(service)
        } else {
            updateUI(ConnectionInfo())
        }
    }

    override fun onPause() {
        super.onPause()
        GTService.instance?.onInfoUpdated = null
        GTService.instance?.onServiceStopped = null
    }

    private fun updateToggleState() {
        val running = GTService.instance != null
        if (running) {
            binding.toggleButton.text = getString(R.string.btn_stop_service)
            binding.toggleButton.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFFCF6679.toInt())
            binding.toggleButton.setTextColor(0xFFFFFFFF.toInt())
            binding.refreshButton.visibility = View.VISIBLE
            binding.statusHeader.text = getString(R.string.status_monitoring)
            binding.statusHeader.setTextColor(0xFF00E676.toInt())
        } else {
            binding.toggleButton.text = getString(R.string.btn_start_service)
            binding.toggleButton.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF00E676.toInt())
            binding.toggleButton.setTextColor(0xFF000000.toInt())
            binding.refreshButton.visibility = View.GONE
            binding.statusHeader.text = getString(R.string.status_stopped)
            binding.statusHeader.setTextColor(0xFFCF6679.toInt())
        }
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
        binding.taValue.text = info.timingAdvance
        binding.distanceValue.text = info.estimatedDistance
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

    private fun bindServiceCallbacks(service: GTService) {
        service.onInfoUpdated = { info ->
            handler.post {
                updateUI(info)
                updateToggleState()
            }
        }
        service.onServiceStopped = {
            handler.post {
                updateUI(ConnectionInfo())
                updateToggleState()
            }
        }
    }

    private fun startGTService() {
        val intent = Intent(this, GTService::class.java)
        startForegroundService(intent)
        // Poll until the service instance is available, then bind callbacks and refresh UI
        handler.postDelayed(object : Runnable {
            override fun run() {
                val service = GTService.instance
                if (service != null) {
                    updateToggleState()
                    bindServiceCallbacks(service)
                    service.refreshNotification("Service started")
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }, 100)
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