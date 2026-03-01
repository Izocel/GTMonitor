package com.example.gtmonitor.provider

import android.os.Build
import com.example.gtmonitor.GTLog

/**
 * Selects the correct [CellInfoProvider] based on the device manufacturer/model.
 *
 * To add support for a new device family:
 * 1. Create a class that extends [DefaultCellInfoProvider] (or implements [CellInfoProvider])
 * 2. Add a matching rule below
 */
object DeviceProviderFactory {

    fun create(): CellInfoProvider {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        GTLog.d("DeviceProviderFactory: manufacturer=$manufacturer, model=$model, sdk=${Build.VERSION.SDK_INT}")

        val provider = when {
            manufacturer.contains("samsung") -> SamsungCellInfoProvider()
            // Add more device-specific providers here:
            // manufacturer.contains("huawei") -> HuaweiCellInfoProvider()
            // manufacturer.contains("xiaomi") -> XiaomiCellInfoProvider()
            else -> DefaultCellInfoProvider()
        }

        GTLog.d("DeviceProviderFactory: selected provider=${provider.providerName}")
        return provider
    }
}
