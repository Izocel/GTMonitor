package com.example.gtmonitor

data class ConnectionInfo(
    val operator: String = "--",
    val networkType: String = "--",
    val signalDbm: String = "--",
    val signalLevel: String = "--",
    val cellId: String = "--",
    val tac: String = "--",
    val pci: String = "--",
    val earfcn: String = "--",
    val mcc: String = "--",
    val mnc: String = "--",
    val bandwidth: String = "--",
    val timingAdvance: String = "--",
    val estimatedDistance: String = "--",
    val serviceState: String = "--",
    val visibleCells: String = "--",
    val lastEvent: String = "--"
)
