package com.hui1601.quickyandroid.data.model

import com.hui1601.quickyandroid.ble.Protocol

data class DeviceSettings(
    val ancMode: Int = 0,
    val noiseValue: Int = 0,
    val volumeLeft: Int = 50,
    val volumeRight: Int = 50,
    val gameMode: Boolean = false,
    val inEarDetection: Boolean = false,
    val ldacEnabled: Boolean = false,
    val spatialAudio: Boolean = false,
    /** Hearing protection (hidden WuQi sound cmd 0x20/0x22, prefixes A1 5A/F9). */
    val hearingProtection: Boolean = false,
    val twsEnabled: Boolean = false,
    val ledEnabled: Boolean = false,
    val soundBalance: Int = 50,
    val toneVolume: Int = 50,
    val autoOffTime: Int = 0,
    val deviceName: String = "",
    val firmwareVersion: String = "",
    val battery: BatteryStatus = BatteryStatus(),
    val eqPreset: Int = 0,
    val eqBandGains: List<Float> = emptyList(),
    /** Full parametric EQ state from 0x22/0x46/0x47 read-backs
     * (firmware custom-band unlock; see Protocol.EqBand). */
    val eqParametricBands: List<Protocol.EqBand> = emptyList(),
    val eqPreGainDb: Float = 0f,
    val selectedAncCmdId: Int = 0,
    val sleepMode: Boolean = false,
    val voiceLanguage: String = "en",
    val ledSwitch: Boolean = false,
    val focusMode: Boolean = false,
    val musicMode: Int = 0,
    val playMode: Int = 0,
    val inEarSensitivity: Int = 1,
    val monitoringValue: Int = 0,
    val testMode: Boolean = false,
    val envAdaptation: Boolean = false,
    val adaptiveEq: Boolean = false,
    val customEqTest: Boolean = false,
    val maxEqCount: Int = 0,
    val earTipFitLeft: Int? = null,
    val earTipFitRight: Int? = null,
    val earTipFitStatus: Int = 0,
    val ancWear: Int? = null,
    /** Generic product-settings toggles keyed by decimal cmdId (0x24 dual
     * device, 0x2A wind noise, 0x40 adaptive volume, ...). */
    val extraSettings: Map<Int, Int> = emptyMap(),
    /** JL ANC gain register (ADV NoiseMode): null until the device reports it. */
    val ancGainMode: Int? = null,
    val ancGainMin: Int? = null,
    val ancGainMax: Int? = null,
    val ancGainCurrent: Int? = null,
    val keyMappings: Map<Byte, Byte> = emptyMap()
)
