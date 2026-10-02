package com.hui1601.quickyandroid.ui.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hui1601.quickyandroid.ble.BleService
import com.hui1601.quickyandroid.util.Hex
import com.hui1601.quickyandroid.ble.JlGattClient
import com.hui1601.quickyandroid.ble.Protocol
import com.hui1601.quickyandroid.ble.QcyGattClient
import com.hui1601.quickyandroid.ble.VendorRouter
import com.hui1601.quickyandroid.ble.WuqiSoundProtocol
import com.hui1601.quickyandroid.ble.WuqiGattClient
import com.hui1601.quickyandroid.ble.ZrGattClient
import com.hui1601.quickyandroid.data.model.BatteryStatus
import com.hui1601.quickyandroid.data.model.ConnectionState
import com.hui1601.quickyandroid.data.model.DeviceSettings
import com.hui1601.quickyandroid.data.model.ProductMetadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import com.hui1601.quickyandroid.widget.DeviceWidgetState
import com.hui1601.quickyandroid.widget.DeviceWidgetStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var standardClient: QcyGattClient? = null
    private var jlClient: JlGattClient? = null
    private var wuqiClient: WuqiGattClient? = null
    private var zrClient: ZrGattClient? = null
    private var currentVendor: VendorRouter.VendorType = VendorRouter.VendorType.STANDARD
    private var retriedWithWuqi = false

    private var eventCollectorJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _settings = MutableStateFlow(DeviceSettings())
    val settings: StateFlow<DeviceSettings> = _settings.asStateFlow()

    private val _product = MutableStateFlow<ProductMetadata?>(null)
    val product: StateFlow<ProductMetadata?> = _product.asStateFlow()

    init {
        // Mirror connection + battery state into the home-screen widget. The
        // foreground service keeps this process alive while connected, so
        // live refreshes ride normal state changes (battery is polled every
        // 30 s); after process death the provider renders the last persisted
        // snapshot. Unrelated settings churn (EQ, volume, …) is filtered by
        // distinctUntilChanged so only widget-relevant changes push updates.
        viewModelScope.launch {
            combine(_connectionState, _settings) { state, settings ->
                when (state) {
                    is ConnectionState.Connected -> DeviceWidgetState(
                        connected = true,
                        deviceName = state.deviceName,
                        battery = settings.battery
                    )
                    is ConnectionState.Connecting -> DeviceWidgetState(
                        connecting = true,
                        deviceName = _product.value?.title.orEmpty()
                    )
                    else -> DeviceWidgetState.Disconnected
                }
            }
                .distinctUntilChanged()
                .collect { DeviceWidgetStore.persistAndPush(getApplication(), it) }
        }
    }

    // Set in connect() before navigation; read-only during composition — intentionally not Compose state.
    var connectedAddress: String? = null
        private set

    // Auto-reconnect bookkeeping
    private var userRequestedDisconnect = false
    private var reconnectAttempts = 0

    private fun getActiveClient(): Any? {
        return when (currentVendor) {
            VendorRouter.VendorType.JL, VendorRouter.VendorType.JL_NEW -> jlClient
            VendorRouter.VendorType.WUQI -> wuqiClient
            VendorRouter.VendorType.ZR -> zrClient
            else -> standardClient
        }
    }

    private var batteryPollJob: Job? = null

    private fun collectEventsFrom(client: Any?) {
        // One collector for the active client only: stale clients' events
        // (e.g. the old standard client during the Wuqi retry) must not
        // clobber the state of the replacement connection.
        eventCollectorJob?.cancel()
        eventCollectorJob = viewModelScope.launch {
            val flow = when (client) {
                is QcyGattClient -> client.events
                is JlGattClient -> client.events
                is WuqiGattClient -> client.events
                is ZrGattClient -> client.events
                else -> return@launch
            }
            flow.collect { event -> handleEvent(event) }
        }
    }

    private fun handleEvent(event: QcyGattClient.GattEvent) {
        when (event) {
            is QcyGattClient.GattEvent.Connected -> {
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.Connected(
                    _product.value?.title ?: "QCY Device",
                    connectedAddress ?: ""
                )
                if (currentVendor == VendorRouter.VendorType.JL || currentVendor == VendorRouter.VendorType.JL_NEW) {
                    jlClient?.runConnectHandshake()
                }
                requestInitialState()
                refreshHiddenSoundChannel()
                _parametricEqSupported.value = getActiveClient() is QcyGattClient
                startBatteryPolling()
                if (_settings.value.battery.isEmpty()) {
                    requestBattery()
                }
                // Foreground service is best-effort: a failure here must
                // never kill the event collector or the initial-state sync.
                try {
                    BleService.start(getApplication(), _product.value?.title ?: "QCY Device")
                } catch (e: Exception) {
                    android.util.Log.w("QuickyBle", "Failed to start foreground service", e)
                }
            }
            is QcyGattClient.GattEvent.Disconnected -> {
                batteryPollJob?.cancel()
                batteryPollJob = null
                _hiddenSoundChannel.value = false
                _parametricEqSupported.value = false
                if (!userRequestedDisconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    // Bounded automatic retry, mirroring the official app's
                    // reconnection behavior on unexpected drops.
                    reconnectAttempts++
                    _connectionState.value = ConnectionState.Connecting
                    viewModelScope.launch {
                        delay(3_000)
                        if (!userRequestedDisconnect && _connectionState.value !is ConnectionState.Connected) {
                            reconnect()
                        }
                    }
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                    try {
                        BleService.stop(getApplication())
                    } catch (e: Exception) {
                        android.util.Log.w("QuickyBle", "Failed to stop foreground service", e)
                    }
                }
            }
            is QcyGattClient.GattEvent.UnsupportedDevice -> {
                // Standard QCY service missing but Wuqi service present —
                // retry once with WuqiGattClient
                val address = connectedAddress
                if (currentVendor == VendorRouter.VendorType.STANDARD && !retriedWithWuqi && address != null) {
                    retriedWithWuqi = true
                    standardClient?.disconnect()
                    connect(address, _product.value, forceVendor = VendorRouter.VendorType.WUQI)
                }
            }
            is QcyGattClient.GattEvent.Notification -> {
                handleNotification(event.cmdId, event.params)
            }
            is QcyGattClient.GattEvent.BatteryRead -> {
                logDev("← battery ${event.battery}")
                _settings.update { it.copy(battery = event.battery) }
            }
            is QcyGattClient.GattEvent.VersionRead -> {
                _settings.update { it.copy(firmwareVersion = event.left) }
            }
            is QcyGattClient.GattEvent.RawNotification -> {
                handleRawNotification(event.uuid, event.value)
            }
        }
    }

    private fun startBatteryPolling() {
        batteryPollJob?.cancel()
        batteryPollJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (_connectionState.value is ConnectionState.Connected) {
                    requestBattery()
                }
            }
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun connect(
        macAddress: String,
        product: ProductMetadata?,
        initialBattery: BatteryStatus? = null,
        forceVendor: VendorRouter.VendorType? = null
    ) {
        if (_connectionState.value is ConnectionState.Connecting) return
        _connectionState.value = ConnectionState.Connecting
        _product.value = product
        connectedAddress = macAddress
        if (forceVendor == null) {
            retriedWithWuqi = false
            userRequestedDisconnect = false
            reconnectAttempts = 0
        }
        if (initialBattery != null) {
            _settings.update { it.copy(battery = initialBattery) }
        }

        if (!hasConnectPermission()) {
            _connectionState.value = ConnectionState.Error("Bluetooth connect permission required")
            return
        }

        val vendorType = forceVendor ?: product?.vendorType?.let {
            try {
                VendorRouter.VendorType.valueOf(it.uppercase())
            } catch (_: Exception) {
                VendorRouter.detectVendorType(product.vendorId)
            }
        } ?: VendorRouter.detectVendorType(product?.vendorId ?: 0)
        currentVendor = vendorType

        viewModelScope.launch {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(macAddress)
                if (device == null) {
                    _connectionState.value = ConnectionState.Error("Invalid MAC address")
                    return@launch
                }

                val success = withTimeoutOrNull(15_000) {
                    when (vendorType) {
                        VendorRouter.VendorType.JL, VendorRouter.VendorType.JL_NEW -> {
                            // Old-JL (0x70xx/0x200) speaks the ADV-opcode
                            // dialect; new-JL (0x807x) the custom tunnel.
                            val oldJl = vendorType == VendorRouter.VendorType.JL
                            jlClient = JlGattClient(getApplication(), oldJl).apply {
                                collectEventsFrom(this)
                            }
                            jlClient?.connect(device) == true
                        }
                        VendorRouter.VendorType.WUQI -> {
                            wuqiClient = WuqiGattClient(getApplication()).apply {
                                collectEventsFrom(this)
                            }
                            wuqiClient?.connect(device) == true
                        }
                        VendorRouter.VendorType.ZR -> {
                            zrClient = ZrGattClient(getApplication()).apply {
                                collectEventsFrom(this)
                            }
                            zrClient?.connect(device) == true
                        }
                        else -> {
                            standardClient = QcyGattClient(getApplication()).apply {
                                collectEventsFrom(this)
                            }
                            standardClient?.connect(device) == true
                        }
                    }
                } ?: false

                if (!success) {
                    _connectionState.value = ConnectionState.Error("Connection failed or timed out")
                }
            } catch (e: SecurityException) {
                _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
            } catch (e: IllegalArgumentException) {
                _connectionState.value = ConnectionState.Error("Invalid device: ${e.message}")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error("Connection error: ${e.message}")
            }
        }
    }

    fun disconnect() {
        userRequestedDisconnect = true
        eventCollectorJob?.cancel()
        eventCollectorJob = null
        standardClient?.disconnect()
        jlClient?.disconnect()
        wuqiClient?.disconnect()
        zrClient?.disconnect()
        standardClient = null
        jlClient = null
        wuqiClient = null
        zrClient = null
        batteryPollJob?.cancel()
        batteryPollJob = null
        try {
            BleService.stop(getApplication())
        } catch (_: Exception) {
        }
        _connectionState.value = ConnectionState.Disconnected
    }

    fun reconnect() {
        val address = connectedAddress ?: return
        val prod = _product.value
        connect(address, prod, _settings.value.battery.takeIf { !it.isEmpty() })
    }

    private fun send(cmdId: Byte, params: ByteArray = byteArrayOf()): Boolean {
        return when (val client = getActiveClient()) {
            is QcyGattClient -> client.sendCommand(cmdId, params)
            is JlGattClient -> client.sendCommand(cmdId, params)
            is WuqiGattClient -> client.sendCommand(cmdId, params)
            is ZrGattClient -> client.sendCommand(cmdId, params)
            else -> false
        }
    }

    private var lastAncSendTime: Long = 0

    private fun checkCanAncSet(): Boolean {
        val vendorId = _product.value?.vendorId ?: 0
        // 0x4176 (H3 Lite) throttles ANC writes to one per 3 s in the official
        // app (QCYConnectManager.smali:1413-1476); everything else uses 1 s.
        val debounceMs = if (vendorId == 0x4176) 3000L else 1000L
        val now = System.currentTimeMillis()
        return if (now - lastAncSendTime >= debounceMs) {
            lastAncSendTime = now
            true
        } else {
            false
        }
    }

    fun setAncMode(mode: Byte) {
        if (!checkCanAncSet()) return
        send(0x0C, byteArrayOf(mode))
    }

    fun setAncSetting(mode: Byte, subScene: Byte, noiseValue: Byte) {
        if (!checkCanAncSet()) return
        send(0x17, byteArrayOf(mode, subScene, noiseValue))
    }

    fun setVocalBoost(enabled: Boolean) {
        if (!checkCanAncSet()) return
        // 0x17 has no pure "off" for voice enhancement (that would be [0x00, 0x00, ...]),
        // so disabling intentionally falls back to Transparency Level 1 (0x0A/0x01).
        val mode: Byte = if (enabled) 0x0E else 0x0A
        val subScene: Byte = 0x01
        val noiseValue: Byte = 0x00
        send(0x17, byteArrayOf(mode, subScene, noiseValue))
        _settings.update { it.copy(ancMode = mode.toInt() and 0xFF, selectedAncCmdId = (mode.toInt() shl 16) or (subScene.toInt() shl 8)) }
    }

    fun setAncPacked(cmdId: Int) {
        if (!checkCanAncSet()) return
        if (cmdId <= 0xFF) {
            send(0x0C, byteArrayOf(cmdId.toByte()))
            _settings.update { it.copy(ancMode = cmdId, selectedAncCmdId = cmdId) }
        } else {
            val mode = ((cmdId and 0xFF0000) shr 16).toByte()
            val subScene = ((cmdId and 0xFF00) shr 8).toByte()
            val noiseValue = (cmdId and 0xFF).toByte()
            // Official special case (setNoiseMode, QCYHeadsetClient.smali:466-474):
            // packed 0x030100 is sent as [0x03, 0x02, 0x00].
            if (cmdId == 0x030100) {
                send(0x17, byteArrayOf(0x03, 0x02, 0x00))
            } else {
                send(0x17, byteArrayOf(mode, subScene, noiseValue))
            }
            _settings.update { it.copy(ancMode = mode.toInt() and 0xFF, selectedAncCmdId = cmdId) }
        }
    }

    fun setVolume(left: Int, right: Int) {
        send(0x08, byteArrayOf(left.toByte(), right.toByte(), 0x00))
        _settings.update { it.copy(volumeLeft = left, volumeRight = right) }
    }

    fun setGameMode(enabled: Boolean) {
        send(0x09, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(gameMode = enabled) }
    }

    fun setInEarDetection(enabled: Boolean) {
        send(0x06, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(inEarDetection = enabled) }
    }

    fun setLDAC(enabled: Boolean) {
        send(0x23, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(ldacEnabled = enabled) }
    }

    fun setSpatialAudio(enabled: Boolean) {
        send(0x2D, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(spatialAudio = enabled) }
    }

    // ── Hidden WuQi sound-command channel ──────────────────────────────

    private val _hiddenSoundChannel = MutableStateFlow(false)
    /** True when the connected device exposes the WuQi 0x7033/0x2001
     * diagnostics channel — the surface carrying the hidden spatial-audio /
     * hearing-protection commands. */
    val hiddenSoundChannel: StateFlow<Boolean> = _hiddenSoundChannel.asStateFlow()

    private fun refreshHiddenSoundChannel() {
        _hiddenSoundChannel.value = when (val client = getActiveClient()) {
            is QcyGattClient -> client.hasWuqiChannel()
            is WuqiGattClient -> true
            else -> false
        }
    }

    /** Send a WuQi sound command by firmware command ID over the 0x2001
     * channel (works for the standard client when the WuQi service is also
     * present, and for WuQi-only connections). */
    fun sendSound(wuqiCmdId: Int, payload: ByteArray): Boolean {
        return when (val client = getActiveClient()) {
            is WuqiGattClient -> client.sendSoundCommand(wuqiCmdId, payload)
            is QcyGattClient -> WuqiSoundProtocol.prefixFor(wuqiCmdId)?.let { client.sendWuqiSoundFrame(it, payload) } ?: false
            else -> false
        }
    }

    /**
     * Hearing protection toggle — hidden WuQi command pair 0x22/0x20
     * (prefixes A1 F9 / A1 5A, catalog/hidden_features.md §2): parsed by the
     * firmware but absent from the retail HT18 control panel.
     */
    fun setHearingProtection(enabled: Boolean) {
        if (sendSound(WuqiSoundProtocol.CMD_SET_HEARING_PROTECTION_STATUS, byteArrayOf(if (enabled) 1 else 0))) {
            _settings.update { it.copy(hearingProtection = enabled) }
        }
    }

    /** Read back the hidden spatial-audio / hearing-protection state. */
    fun requestHiddenSoundState() {
        viewModelScope.launch {
            sendSound(WuqiSoundProtocol.CMD_SPATIAL_AUDIO_STATUS, byteArrayOf())
            delay(60)
            sendSound(WuqiSoundProtocol.CMD_HEARING_PROTECTION_STATUS, byteArrayOf())
        }
    }

    /** Developer console: send any catalog sound command with a raw payload. */
    fun sendHiddenSoundCommand(wuqiCmdId: Int, payload: ByteArray) {
        if (sendSound(wuqiCmdId, payload)) {
            logDev("\u2192 snd 0x${Hex.format(wuqiCmdId)} [${Hex.format(payload)}]")
        }
    }

    // ── Custom parametric EQ (firmware custom-band unlock) ────────────

    private val _parametricEqSupported = MutableStateFlow(false)
    /** True when the active client speaks the QCY DataBean protocol that
     * carries the parametric EQ commands (0x22 write, 0xFE 0x22 read). */
    val parametricEqSupported: StateFlow<Boolean> = _parametricEqSupported.asStateFlow()

    /**
     * Push a fully custom parametric EQ: arbitrary frequency, gain, Q and
     * filter type per band (up to 20) plus the pre-gain, via DataBean cmd
     * 0x22 with the custom preset type (catalog/EQ_PROTOCOL.md — the retail
     * app only sends fixed 10-band presets).
     */
    fun setParametricEq(preGainDb: Float, bands: List<Protocol.EqBand>) {
        val client = getActiveClient()
        if (client !is QcyGattClient) return
        val sanitized = Protocol.sanitizeEqBands(bands)
        if (sanitized.isEmpty()) return
        if (client.sendCommand(0x22.toByte(), Protocol.buildCustomEqBody(preGainDb, sanitized))) {
            _settings.update {
                it.copy(
                    eqPreset = Protocol.EQ_CUSTOM_PRESET_TYPE,
                    eqParametricBands = sanitized,
                    eqPreGainDb = preGainDb,
                    eqBandGains = sanitized.map { band -> band.gainDb }
                )
        }
        }
    }

    /** Read back the full parametric EQ (0xFE sub-read 0x22 — accepted by
     * the firmware’s sub-dispatch). */
    fun requestParametricEq() {
        send(0xFE.toByte(), byteArrayOf(0x22.toByte()))
    }

    fun setSoundBalance(value: Int) {
        send(0x16, byteArrayOf(value.toByte()))
        _settings.update { it.copy(soundBalance = value) }
    }

    fun setToneVolume(value: Int) {
        send(0x1D, byteArrayOf(value.toByte()))
        _settings.update { it.copy(toneVolume = value) }
    }

    fun renameDevice(name: String) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        send(0x18, bytes)
        _settings.update { it.copy(deviceName = name) }
    }

    fun resetDefault() {
        send(0x01, byteArrayOf())
    }

    fun clearPairing() {
        send(0x02, byteArrayOf())
    }

    fun factoryReset() {
        send(0x03, byteArrayOf())
    }

    fun findEarphone(start: Boolean) {
        send(0x05, byteArrayOf(if (start) 0x01 else 0x00))
    }

    fun setSleepMode(enabled: Boolean) {
        send(0x10, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(sleepMode = enabled) }
    }

    fun setAutoOffTime(minutes: Int) {
        val lo = (minutes and 0xFF).toByte()
        val hi = ((minutes shr 8) and 0xFF).toByte()
        send(0x14, byteArrayOf(lo, hi, 0x00, 0x00))
        _settings.update { it.copy(autoOffTime = minutes) }
    }

    fun setLedSwitch(enabled: Boolean) {
        send(0x35, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(ledSwitch = enabled) }
    }

    fun setLedMode(enabled: Boolean) {
        send(0x12, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(ledEnabled = enabled) }
    }

    fun setVoiceLanguage(lang: String) {
        val bytes = lang.toByteArray(Charsets.UTF_8)
        send(0x19, bytes)
        _settings.update { it.copy(voiceLanguage = lang) }
    }

    fun setFocusMode(enabled: Boolean) {
        send(0x39, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(focusMode = enabled) }
    }

    fun setMusicMode(mode: Byte) {
        send(0x2E, byteArrayOf(mode))
        _settings.update { it.copy(musicMode = mode.toInt() and 0xFF) }
    }

    fun setPlayMode(mode: Byte) {
        send(0x37, byteArrayOf(mode))
        _settings.update { it.copy(playMode = mode.toInt() and 0xFF) }
    }

    fun setInEarSensitivity(level: Int) {
        send(0x48, byteArrayOf(level.toByte()))
        _settings.update { it.copy(inEarSensitivity = level) }
    }

    fun setMonitoring(value: Int) {
        send(0x0A, byteArrayOf(value.toByte()))
        _settings.update { it.copy(monitoringValue = value) }
    }

    fun setTestMode(enabled: Boolean) {
        send(0x0D, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(testMode = enabled) }
    }

    /**
     * Generic product-settings toggle — the official app drives every
     * control-panel `settings[]` entry through setSingleValue
     * (QCYConnectManager.smali:6462): `FF 04 <cmd> 01 <1|2>`. Covers dual
     * device connection (0x24), wind noise detection (0x2A), adaptive
     * volume (0x40) and any future cmdid-keyed toggle.
     */
    fun setSettingValue(cmdId: Int, enabled: Boolean) {
        val v = if (enabled) 0x01 else 0x02
        send(cmdId.toByte(), byteArrayOf(v.toByte()))
        _settings.update { it.copy(extraSettings = it.extraSettings + (cmdId to v)) }
    }

    /**
     * Manual ANC gain (JL ADV op 0xB): clamps into the device-reported
     * [min, max] bracket and writes the gain register, mirroring the
     * official getCurNoiseMode → setCurrentNoiseMode flow.
     */
    fun setAncGain(value: Int) {
        val s = _settings.value
        val mode = s.ancGainMode ?: return
        val min = s.ancGainMin ?: return
        val max = s.ancGainMax ?: return
        if (max <= min) return
        val clamped = value.coerceIn(min, max)
        val client = jlClient ?: return
        if (client.setAncGain(mode, min, max, clamped)) {
            _settings.update { it.copy(ancGainCurrent = clamped) }
        }
    }

    // ── Developer tools ───────────────────────────────────────────────

    private val _devLog = MutableStateFlow<List<DevLogEntry>>(emptyList())
    val devLog: StateFlow<List<DevLogEntry>> = _devLog.asStateFlow()

    private fun logDev(text: String) {
        _devLog.update { current ->
            (current + DevLogEntry(System.currentTimeMillis(), text)).takeLast(DEV_LOG_LIMIT)
        }
    }

    /**
     * Test write for the standalone noise-value register (cmd 0x07). The
     * official app never writes this byte — only reads it with the 0xFF
     * sentinel — so this is a developer probe for undocumented firmware
     * behavior. Responses update [DeviceSettings.noiseValue] and the log.
     */
    fun sendNoiseValue(value: Int) {
        val v = value.coerceIn(0, 255)
        if (send(0x07, byteArrayOf(v.toByte()))) {
            logDev("→ 0x07 value=0x${Hex.format(v)}")
        }
    }

    /** Official read path for the noise value: cmd 0x07 with the 0xFF sentinel. */
    fun requestNoiseValue() {
        if (send(0x07, byteArrayOf(0xFF.toByte()))) {
            logDev("→ 0x07 read=0xff")
        }
    }

    /** Raw TLV command for arbitrary cmdId/params probing. */
    fun sendRawCommand(cmdId: Int, params: ByteArray) {
        val cmd = cmdId.coerceIn(0, 255)
        if (send(cmd.toByte(), params)) {
            logDev("→ 0x${Hex.format(cmd)} [${Hex.format(params)}]")
        }
    }

    /** 0xFE request-data probe for any cmdId. */
    fun requestDataFor(cmdId: Int) {
        val cmd = cmdId.coerceIn(0, 255)
        if (send(0xFE.toByte(), byteArrayOf(cmd.toByte()))) {
            logDev("→ 0xfe request=0x${Hex.format(cmd)}")
        }
    }

    fun clearDevLog() {
        _devLog.value = emptyList()
    }

    data class DevLogEntry(val timestamp: Long, val text: String)

    // ── Per-screen state refresh ──────────────────────────────────────

    private val lastScreenRefresh = HashMap<String, Long>()

    /**
     * Re-read the device settings a submenu renders, so it opens with live
     * values instead of the connect-time snapshot. Debounced to 2 s per
     * screen to tolerate rapid navigation.
     */
    fun refreshScreenState(screen: String) {
        val now = System.currentTimeMillis()
        val last = lastScreenRefresh[screen] ?: 0L
        if (now - last < 2000) return
        lastScreenRefresh[screen] = now

        viewModelScope.launch {
            when (screen) {
                "eq" -> {
                    when (val client = getActiveClient()) {
                        is QcyGattClient -> client.readEQ()
                        is ZrGattClient -> client.readEQ()
                        is JlGattClient -> client.readEqNative()
                        else -> Unit // Wuqi EQ info response is not decoded
                    }
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(0x20.toByte()))
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(0x22.toByte()))
                    delay(60)
                    // Per-side EQ presets (firmware-verified 0xFE reads)
                    send(0xFE.toByte(), byteArrayOf(0x46.toByte()))
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(0x47.toByte()))
                }
                "anc" -> {
                    send(0xFE.toByte(), byteArrayOf(0x0C.toByte()))
                    delay(60)
                    send(0x07, byteArrayOf(0xFF.toByte())) // noise strength read
                }
                "volume" -> {
                    send(0xFE.toByte(), byteArrayOf(0x08.toByte()))
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(0x16.toByte()))
                }
                "key" -> {
                    when (val client = getActiveClient()) {
                        is QcyGattClient -> client.readKeyFunction()
                        is ZrGattClient -> client.readKeyFunction()
                        is JlGattClient -> client.readCommand(0x2B)
                        else -> Unit
                    }
                }
                "settings" -> {
                    listOf(0x14, 0x16, 0x1D, 0x18, 0x19, 0x35, 0x12, 0x10, 0x09, 0x06, 0x17, 0x24, 0x2A).forEach { cmd ->
                        delay(60)
                        send(0xFE.toByte(), byteArrayOf(cmd.toByte()))
                    }
                    // Product-declared generic toggles (dual device, wind
                    // noise, adaptive volume, ...)
                    _product.value?.features?.settings
                        ?.mapNotNull { it.cmdId }
                        ?.filter { it !in SPECIALIZED_SETTING_IDS }
                        ?.forEach { cmdId ->
                            delay(60)
                            send(0xFE.toByte(), byteArrayOf(cmdId.toByte()))
                        }
                }
                "wearing" -> {
                    (getActiveClient() as? ZrGattClient)?.readSettingsChar()
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(0x2C.toByte()))
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(0x06.toByte()))
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(0x48.toByte()))
                }
            }
        }
    }

    fun setEnvAdaptation(enabled: Boolean) {
        send(0x32, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(envAdaptation = enabled) }
    }

    fun setAdaptiveEq(enabled: Boolean) {
        send(0x27, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(adaptiveEq = enabled) }
    }

    fun setTwsEnabled(enabled: Boolean) {
        send(0x34, byteArrayOf(if (enabled) 0x01 else 0x02))
        _settings.update { it.copy(twsEnabled = enabled) }
    }

    fun setWearingDetection(enable: Boolean, musicIndex: Byte, ancIndex: Byte, toneEnable: Byte? = null) {
        val enableByte = if (enable) 0x01.toByte() else 0x02.toByte()
        val params = if (toneEnable != null) {
            byteArrayOf(enableByte, musicIndex, ancIndex, toneEnable)
        } else {
            byteArrayOf(enableByte, musicIndex, ancIndex)
        }
        send(0x2C, params)
        _settings.update { it.copy(inEarDetection = enable) }
    }

    fun setEarTipFitTest(start: Boolean) {
        // QCYConnectManager.setCompactnessEnable (smali:4540-4650):
        // three identical bytes, 1 = start, 0 = stop.
        send(0x11, ByteArray(3) { if (start) 0x01 else 0x00 })
    }

    fun setLedEffect(speed: Byte, brightness: Byte, effectIndex: Byte, colors: List<Triple<Byte, Byte, Byte>>) {
        val colorBytes = colors.flatMap { listOf(it.first, it.second, it.third) }.toByteArray()
        val params = byteArrayOf(speed, brightness, effectIndex, *colorBytes)
        send(0x36, params)
    }

    fun takePhoto(action: Byte) {
        send(0x1E, byteArrayOf(action))
    }

    fun setStandby(state: Byte) {
        send(0x1F, byteArrayOf(state))
    }

    fun syncTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, dayOfWeek: Int) {
        when (val client = getActiveClient()) {
            is ZrGattClient -> {
                // ZR: raw 6-byte epoch write to 0x000E — the date arguments are
                // only used for the standard/JL paths.
                client.syncTime()
            }
            is JlGattClient -> {
                // JL: native RCSP ADV time sync (epoch from the device clock)
                client.syncTime()
            }
            else -> {
                // Standard: framed 0x3E [yy%100, month, day, hour, min, sec,
                // weekday bitmask 1<<(DAY_OF_WEEK-1)].
                val y = (year % 100).toByte()
                send(0x3E, byteArrayOf(y, month.toByte(), day.toByte(), hour.toByte(), minute.toByte(), second.toByte(), dayOfWeek.toByte()))
            }
        }
    }

    /** Convenience for a "sync now" action: uses the current wall clock. */
    fun syncTimeNow() {
        when (val client = getActiveClient()) {
            is ZrGattClient -> client.syncTime()
            is JlGattClient -> client.syncTime()
            else -> {
                val cal = Calendar.getInstance()
                val y = (cal.get(Calendar.YEAR) % 100).toByte()
                val m = (cal.get(Calendar.MONTH) + 1).toByte()
                val d = cal.get(Calendar.DAY_OF_MONTH).toByte()
                val h = cal.get(Calendar.HOUR_OF_DAY).toByte()
                val min = cal.get(Calendar.MINUTE).toByte()
                val s = cal.get(Calendar.SECOND).toByte()
                // Calendar.DAY_OF_WEEK: Sun=1..Sat=7 -> bitmask 1<<(n-1)
                val dow = (1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1)).toByte()
                send(0x3E, byteArrayOf(y, m, d, h, min, s, dow))
            }
        }
    }

    fun triggerAI(action: Byte) {
        send(0x43, byteArrayOf(action))
    }

    fun setCustomEqTest(on: Boolean) {
        send(0x45, byteArrayOf(if (on) 0x01 else 0x02))
        _settings.update { it.copy(customEqTest = on) }
    }

    fun setGameConfig(config: Byte) {
        send(0x4A, byteArrayOf(config))
    }

    fun musicControl(action: Byte) {
        send(0x04, byteArrayOf(action))
    }

    fun setKeyFunctions(pairs: List<Pair<Byte, Byte>>) {
        when (val client = getActiveClient()) {
            is ZrGattClient -> {
                // ZR: raw pair list with ZR key-id remap to 0x000D
                client.writeKeyFunction(pairs)
            }
            is QcyGattClient -> {
                // Standard: raw pair list to 0x000D (no TLV framing)
                client.writeKeyFunction(Protocol.buildKeyFunctionMap(pairs))
            }
            else -> {
                // JL / Wuqi: framed command 0x2B — payload is the raw
                // [keyId, funId, ...] pairs; the paramLen lives in the TLV
                // header. (WuqiGattClient deliberately drops this command —
                // its key-function layout is incompatible.)
                send(0x2B, Protocol.buildKeyFunctionMap(pairs))
            }
        }
    }

    fun updateKeyMappings(mappings: Map<Byte, Byte>) {
        _settings.update { it.copy(keyMappings = mappings) }
    }

    fun setEQ(presetIndex: Int, bandGains: List<Float>, isCustom: Boolean = false) {
        val eqData = bandGains.map { gain ->
            (gain.coerceIn(-12f, 12f) * 10).toInt().toByte()
        }.toByteArray()
        when (val client = getActiveClient()) {
            is QcyGattClient -> {
                val payload = ByteArray(1 + eqData.size)
                payload[0] = presetIndex.toByte()
                System.arraycopy(eqData, 0, payload, 1, eqData.size)
                client.writeEQ(payload)
            }
            is JlGattClient -> {
                // Official JL semantics: preset modes send 0x7F gain slots
                // (firmware applies its own curve); custom sends mode 0xFF
                // with the real gains (JLDeviceImpl.setEQ, smali:1957-2052).
                val mode = if (isCustom) 0xFF else presetIndex
                client.writeEQ(mode, eqData)
            }
            is WuqiGattClient -> client.writeEQ(presetIndex, eqData)
            is ZrGattClient -> client.writeEQ(presetIndex, eqData)
        }
        _settings.update { it.copy(eqPreset = presetIndex, eqBandGains = bandGains) }
    }

    fun requestBattery() {
        when (val client = getActiveClient()) {
            is QcyGattClient -> {
                if (!client.readBattery()) {
                    client.sendCommand(0x2F, byteArrayOf())
                }
            }
            is ZrGattClient -> {
                if (!client.readBattery()) {
                    client.sendCommand(0x2F, byteArrayOf())
                }
            }
            is JlGattClient -> {
                // JL reads go through the 0xFE tunnel: [0xFE, 0x01, 0x2F]
                client.readCommand(0x2F)
            }
            else -> send(0xFE.toByte(), byteArrayOf(0x2F.toByte()))
        }
    }

    fun requestVersion() {
        when (val client = getActiveClient()) {
            is QcyGattClient -> {
                if (!client.readVersion()) {
                    client.sendCommand(0x30, byteArrayOf())
                }
            }
            is ZrGattClient -> {
                if (!client.readVersion()) {
                    client.sendCommand(0x30, byteArrayOf())
                }
            }
            is JlGattClient -> {
                client.readCommand(0x30)
            }
            else -> send(0xFE.toByte(), byteArrayOf(0x30.toByte()))
        }
    }

    private fun requestInitialState() {
        viewModelScope.launch {
            // Match the official QCY app init sequence: direct characteristic
            // reads with pacing, then the 0xFE request burst.
            when (val client = getActiveClient()) {
                is QcyGattClient -> {
                    client.readEQ()
                    delay(60)
                    client.readKeyFunction()
                    delay(60)
                    client.readStateChar()
                    client.readNotifyChar()
                    delay(60)
                }
                is ZrGattClient -> {
                    client.readEQ()
                    delay(60)
                    client.readKeyFunction()
                    delay(60)
                    client.readSettingsChar()
                    delay(60)
                }
                else -> Unit // JL handshake runs its own sweep; Wuqi uses 0xFE reads below
            }
            requestBattery()
            requestVersion()
            delay(1000)
            // MAXEQ_COUNT — the only 0xFE command the official app sends on connect
            send(0xFE.toByte(), byteArrayOf(0x44.toByte()))
            // Old-JL already received its full ADV-state read from the connect
            // handshake (getAdvInfo mask -1); the 0xFE burst below is new-JL
            // and standard-protocol territory. Also request the native JL EQ
            // state (GetSysInfo attr 0x10) which answers as opcode 0x07.
            val jl = getActiveClient()
            if (jl is JlGattClient) {
                if (!currentVendorOldJl()) {
                    jl.readEqNative()
                }
                return@launch
            }
            // Paced 0xFE commands for settings that don't have direct-read
            // equivalents. 0x07 with the 0xFF sentinel reads the ANC strength
            // (NoiseDataBean.getReadNoiseCMD).
            send(0x07, byteArrayOf(0xFF.toByte()))
            delay(60)
            val extraCmds = listOf(
                0x0C, 0x08, 0x09, 0x06, 0x23, 0x2D, 0x10, 0x16, 0x1D, 0x35, 0x39,
                0x32, 0x27, 0x34, 0x0A, 0x20, 0x22,
                // read-backs for state the UI renders on first open
                0x14, 0x18, 0x19, 0x2C,
                // firmware-verified 0xFE reads newly queried
                // (catalog/databean_fe_subdispatch.md)
                0x17, 0x24, 0x2A
            )
            extraCmds.forEach { cmdId ->
                delay(60)
                send(0xFE.toByte(), byteArrayOf(cmdId.toByte()))
            }
            // Product-specific generic toggles (dual device 0x24, wind noise
            // 0x2A, adaptive volume 0x40) — read current values.
            _product.value?.features?.settings
                ?.mapNotNull { it.cmdId }
                ?.filter { it !in SPECIALIZED_SETTING_IDS }
                ?.forEach { cmdId ->
                    delay(60)
                    send(0xFE.toByte(), byteArrayOf(cmdId.toByte()))
                }
            // Hidden spatial-audio / hearing-protection state over the WuQi
            // sound channel (0x2001), when the device exposes it.
            if (_hiddenSoundChannel.value) {
                delay(60)
                sendSound(WuqiSoundProtocol.CMD_SPATIAL_AUDIO_STATUS, byteArrayOf())
                delay(60)
                sendSound(WuqiSoundProtocol.CMD_HEARING_PROTECTION_STATUS, byteArrayOf())
            }
        }
    }



    /** The JL client is constructed with oldJl = (vendorType == JL). */
    private fun currentVendorOldJl(): Boolean = currentVendor == VendorRouter.VendorType.JL

    private fun handleRawNotification(uuid: java.util.UUID, value: ByteArray) {
        logDev("← char ${uuid.toString().substring(3, 8)} [${Hex.format(value)}]")
        when (uuid) {
            Protocol.WUQI_NOTIFY_UUID -> {
                // Hidden sound-command response: decode and surface under the
                // QCY toggle opcodes (0x2D spatial, 0x26 hearing protection).
                val response = WuqiSoundProtocol.parseResponse(value) ?: return
                val toggle = WuqiSoundProtocol.responseToQcyToggle(response) ?: return
                handleNotification(toggle.first, byteArrayOf(toggle.second))
            }
            Protocol.EQ_UUID -> {
                if (currentVendor == VendorRouter.VendorType.ZR) {
                    // ZR EQ read: [eqType, 5 gains] at bytes 0x78..0x7D
                    val parsed = ZrGattClient.parseEqRead(value)
                    if (parsed != null) {
                        val (eqType, gains) = parsed
                        _settings.update { it.copy(eqPreset = eqType, eqBandGains = gains) }
                    }
                    return
                }
                // Standard EQDataBean parsing: currentMode (byte 12) is only
                // read when the packet is exactly 13 bytes:
                // [eqType, eqData(10), gameEQType, currentMode]. For any other length
                // eqData = bytes 1..end and currentMode keeps its previous value.
                when {
                    value.size == 13 -> {
                        val currentMode = value[12].toInt() and 0xFF
                        val gains = value.copyOfRange(1, 11).map { it.toInt() / 10f }
                        _settings.update { it.copy(eqPreset = currentMode, eqBandGains = gains) }
                    }
                    value.size > 1 -> {
                        val gains = value.copyOfRange(1, value.size).map { it.toInt() / 10f }
                        _settings.update { it.copy(eqBandGains = gains) }
                    }
                }
            }
            Protocol.KEY_FUNC_UUID -> {
                // Key function read response: [keyId1, funcId1, keyId2, funcId2, ...]
                if (value.size >= 2 && value.size % 2 == 0) {
                    val zr = currentVendor == VendorRouter.VendorType.ZR
                    val mappings = mutableMapOf<Byte, Byte>()
                    for (i in value.indices step 2) {
                        val keyId = if (zr) ZrGattClient.keyIdFromZr(value[i].toInt() and 0xFF).toByte() else value[i]
                        mappings[keyId] = value[i + 1]
                    }
                    _settings.update { it.copy(keyMappings = mappings) }
                }
            }
        }
    }

    private fun handleNotification(cmdId: Byte, params: ByteArray) {
        logDev("← 0x${Hex.format(cmdId.toInt() and 0xFF)} [${Hex.format(params)}]")
        when (cmdId.toInt() and 0xFF) {
            0x0C -> {
                if (params.isNotEmpty()) _settings.update { it.copy(ancMode = params[0].toInt() and 0xFF) }
            }
            0x17 -> {
                if (params.size >= 3) {
                    val mode = params[0].toInt() and 0xFF
                    val subScene = params[1].toInt() and 0xFF
                    val noiseValue = params[2].toInt() and 0xFF
                    _settings.update { it.copy(ancMode = mode, selectedAncCmdId = (mode shl 16) or (subScene shl 8) or noiseValue) }
                }
            }
            0x07 -> {
                // ANC strength (noise value); SingleDataBean dedups 1-byte values
                if (params.isNotEmpty()) _settings.update { it.copy(noiseValue = params[0].toInt() and 0xFF) }
            }
            0x08 -> {
                if (params.size >= 3) {
                    _settings.update { it.copy(volumeLeft = params[0].toInt() and 0xFF, volumeRight = params[1].toInt() and 0xFF) }
                }
            }
            0x09 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(gameMode = params[0] == 0x01.toByte()) }
            }
            0x06 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(inEarDetection = params[0] == 0x01.toByte()) }
            }
            0x23 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(ldacEnabled = params[0] == 0x01.toByte()) }
            }
            0x2D -> {
                if (params.isNotEmpty()) _settings.update { it.copy(spatialAudio = params[0] == 0x01.toByte()) }
            }
            0x26 -> {
                // Hearing protection (synthetic opcode; see WuqiSoundProtocol)
                if (params.isNotEmpty()) _settings.update { it.copy(hearingProtection = params[0] == 0x01.toByte()) }
            }
            0x16 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(soundBalance = params[0].toInt() and 0xFF) }
            }
            0x1D -> {
                if (params.isNotEmpty()) _settings.update { it.copy(toneVolume = params[0].toInt() and 0xFF) }
            }
            0x2F -> {
                if (params.size >= 3) {
                    val battery = BatteryStatus(
                        leftLevel = params[0].toInt() and 0x7F,
                        leftCharging = params[0].toInt() and 0x80 != 0,
                        rightLevel = params[1].toInt() and 0x7F,
                        rightCharging = params[1].toInt() and 0x80 != 0,
                        caseLevel = params[2].toInt() and 0x7F,
                        caseCharging = params[2].toInt() and 0x80 != 0
                    )
                    _settings.update { it.copy(battery = battery) }
                }
            }
            0x30 -> {
                if (params.size >= 3) {
                    val version = "${params[0].toInt() and 0xFF}.${params[1].toInt() and 0xFF}.${params[2].toInt() and 0xFF}"
                    _settings.update { it.copy(firmwareVersion = version) }
                }
            }
            0x18 -> {
                val name = params.toString(Charsets.UTF_8).trimEnd('\u0000')
                _settings.update { it.copy(deviceName = name) }
            }
            0x10 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(sleepMode = params[0] == 0x01.toByte()) }
            }
            0x14 -> {
                if (params.size >= 4) {
                    val minutes = (params[0].toInt() and 0xFF) or ((params[1].toInt() and 0xFF) shl 8)
                    _settings.update { it.copy(autoOffTime = minutes) }
                }
            }
            0x35 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(ledSwitch = params[0] == 0x01.toByte()) }
            }
            0x12 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(ledEnabled = params[0] == 0x01.toByte()) }
            }
            0x19 -> {
                val lang = params.toString(Charsets.UTF_8).trimEnd('\u0000')
                _settings.update { it.copy(voiceLanguage = lang) }
            }
            0x39 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(focusMode = params[0] == 0x01.toByte()) }
            }
            0x2E -> {
                if (params.isNotEmpty()) _settings.update { it.copy(musicMode = params[0].toInt() and 0xFF) }
            }
            0x37 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(playMode = params[0].toInt() and 0xFF) }
            }
            0x48 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(inEarSensitivity = params[0].toInt() and 0xFF) }
            }
            0x0A -> {
                if (params.isNotEmpty()) _settings.update { it.copy(monitoringValue = params[0].toInt() and 0xFF) }
            }
            0x0D -> {
                if (params.isNotEmpty()) _settings.update { it.copy(testMode = params[0] == 0x01.toByte()) }
            }
            0x32 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(envAdaptation = params[0] == 0x01.toByte()) }
            }
            0x27 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(adaptiveEq = params[0] == 0x01.toByte()) }
            }
            0x34 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(twsEnabled = params[0] == 0x01.toByte()) }
            }
            0x44 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(maxEqCount = params[0].toInt() and 0xFF) }
            }
            0x20, 0x22, 0x46, 0x47 -> {
                // Parametric EQ response: [eqType, masterGainLo, masterGainHi, bands...]
                val parsed = Protocol.parseParametricEq(cmdId, params)
                if (parsed != null) {
                    _settings.update {
                        it.copy(
                            eqPreset = parsed.eqIndex,
                            eqBandGains = parsed.bands.map { band -> band.gainDb },
                            eqParametricBands = parsed.bands,
                            eqPreGainDb = parsed.masterGainDb
                        )
                    }
                }
            }
            0x29 -> {
                // ANC wear state; if 2 params the value is duplicated, first byte suffices
                if (params.isNotEmpty()) _settings.update { it.copy(ancWear = params[0].toInt() and 0xFF) }
            }
            0x36 -> {
                // Device echoes the last LED effect; no persistent state is kept
            }
            0x45 -> {
                if (params.isNotEmpty()) _settings.update { it.copy(customEqTest = params[0] == 0x01.toByte()) }
            }
            0x4A -> {
                // Game config response - no state to store currently
            }
            0x2B -> {
                // Key function response: [keyId1, funId1, keyId2, funId2, ...]
                if (params.size >= 2 && params.size % 2 == 0) {
                    val zr = currentVendor == VendorRouter.VendorType.ZR
                    val mappings = mutableMapOf<Byte, Byte>()
                    for (i in params.indices step 2) {
                        val keyId = if (zr) ZrGattClient.keyIdFromZr(params[i].toInt() and 0xFF).toByte() else params[i]
                        mappings[keyId] = params[i + 1]
                    }
                    _settings.update { it.copy(keyMappings = mappings) }
                }
            }
            0x2C -> {
                if (params.isNotEmpty()) _settings.update { it.copy(inEarDetection = params[0] == 0x01.toByte()) }
            }
            0x11 -> {
                // Ear tip fit test result: [status, leftResult, rightResult]
                if (params.size >= 3) {
                    val status = params[0].toInt() and 0xFF
                    val left = params[1].toInt() and 0xFF
                    val right = params[2].toInt() and 0xFF
                    _settings.update { it.copy(earTipFitStatus = status, earTipFitLeft = left, earTipFitRight = right) }
                }
            }
            // ── JL RCSP opcodes ──

            // JL ANC gain register echo: [mode, 6, min BE16, max BE16, cur BE16]
            0xA2 -> {
                if (params.size >= 8) {
                    fun be16(o: Int) = ((params[o].toInt() and 0xFF) shl 8) or (params[o + 1].toInt() and 0xFF)
                    _settings.update {
                        it.copy(
                            ancGainMode = params[0].toInt() and 0xFF,
                            ancGainMin = be16(2),
                            ancGainMax = be16(4),
                            ancGainCurrent = be16(6)
                        )
                    }
                }
            }
            0xA1 -> {
                if (currentVendor == VendorRouter.VendorType.JL || currentVendor == VendorRouter.VendorType.JL_NEW) {
                    // EQ state (custom tunnel 0x20/0x22 or native sys-info attr 0x04):
                    // [eqType, gains...] — gains are dB*10 bytes, 0x7F = no change (0 dB)
                    if (params.isNotEmpty()) {
                        val eqType = params[0].toInt() and 0xFF
                        val gains = params.drop(1).map { it.toInt() / 10f }
                        _settings.update { it.copy(eqPreset = eqType, eqBandGains = gains) }
                    }
                }
            }
            // Generic single-value settings (dual device 0x24, wind noise
            // 0x2A, adaptive volume 0x40, ...) — stored by decimal cmdId.
            else -> {
                if (params.size == 1) {
                    val key = cmdId.toInt() and 0xFF
                    _settings.update { it.copy(extraSettings = it.extraSettings + (key to (params[0].toInt() and 0xFF))) }
                }
            }
            // Other JL opcodes: no state to store currently
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val DEV_LOG_LIMIT = 200

        /** Settings cmdIds already exposed through dedicated UI rows; the
         * dashboard renders every other `settings[]` entry generically. */
        val SPECIALIZED_SETTING_IDS = setOf(6, 9, 16, 18, 35, 39, 45, 0x14, 0x18, 0x19)
    }
}
