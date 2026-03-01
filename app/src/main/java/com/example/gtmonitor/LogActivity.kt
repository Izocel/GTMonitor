package com.example.gtmonitor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.gtmonitor.databinding.ActivityLogBinding

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private val handler = Handler(Looper.getMainLooper())

    private val liveListener: (String) -> Unit = { line ->
        handler.post {
            binding.logTextView.append("$line\n")
            binding.logScrollView.post {
                binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load existing logs
        binding.logTextView.text = GTLog.readAll()

        // Scroll to bottom
        binding.logScrollView.post {
            binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }

        binding.clearButton.setOnClickListener {
            GTLog.clear()
            binding.logTextView.text = ""
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        GTLog.addListener(liveListener)
    }

    override fun onPause() {
        super.onPause()
        GTLog.removeListener(liveListener)
    }
}
