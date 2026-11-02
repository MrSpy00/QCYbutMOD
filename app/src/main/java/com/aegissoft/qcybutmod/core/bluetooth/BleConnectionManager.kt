package com.aegissoft.qcybutmod.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.aegissoft.qcybutmod.core.audio.EqBand
import com.aegissoft.qcybutmod.core.audio.ParametricEq
import com.aegissoft.qcybutmod.core.audio.VoiceMessages
import com.aegissoft.qcybutmod.core.audio.VoiceSettings
import com.aegissoft.qcybutmod.core.audio.VoiceSource
import com.aegissoft.qcybutmod.core.models.*
import com.aegissoft.qcybutmod.core.utils.AegisLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class BleConnectionManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "BleConnManager"
        private const val PREFS_MAC_CACHE = "qcy_mac_cache"

        @Volatile
        private var INSTANCE: BleConnectionManager? = null

        fun getInstance(context: Context): BleConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleConnectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val macCachePrefs = appContext.getSharedPreferences(PREFS_MAC_CACHE, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // State flows
    private val _deviceState = MutableStateFlow(QcyDeviceState())
    val deviceState: StateFlow<QcyDeviceState> = _deviceState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredQcyDevice>>(emptyMap())
    val discoveredDevices: StateFlow<List<DiscoveredQcyDevice>> = _discoveredDevices.map { map ->
        map.values
            .groupBy {
                val macKey = (it.classicMac ?: it.bleAddress).uppercase()
                val nameKey = it.deviceName.replace(" ", "").replace("-", "").lowercase()
                if (it.classicMac != null) macKey else nameKey
            }
            .map { (_, list) ->
                list.maxByOrNull {
                    (if (it.isAudioConnected) 5000 else 0) +
                    (if (it.isReachable) 2000 else 0) +
                    (if (it.isBonded) 500 else 0) +
                    (if (it.battery.hasValidBattery) 250 else 0) +
                    (if (!it.classicMac.isNullOrEmpty() && !it.bleAddress.equals(it.classicMac, ignoreCase = true)) 300 else 0) +
                    it.rssi.coerceIn(-100, -20)
                } ?: list.first()
            }
            .sortedWith(
                compareByDescending<DiscoveredQcyDevice> { it.isAudioConnected }
                    .thenByDescending { it.isReachable }
                    .thenByDescending { it.isBonded }
                    .thenBy { it.deviceName }
            )
    }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Event flows
    private val _fitTestResults = MutableSharedFlow<QcyProtocol.FitTestResult>(extraBufferCapacity = 5)
    val fitTestResults: SharedFlow<QcyProtocol.FitTestResult> = _fitTestResults.asSharedFlow()

    // Connection internals
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectWatchdogJob: Job? = null
    private var pollJob: Job? = null
    private var rssiPollJob: Job? = null
    private var stopScanRunnable: Runnable? = null

    // Last time any notification arrived from the headset — used to detect a dead
    // subscription (firmware stopped notifying after a status 21 disconnect) and heal it.
    @Volatile
    private var lastRxTimestamp = 0L

    // Debounce for the automatic re-mute when firmware resurrects its prompt volume.
    @Volatile
    private var lastPromptRemuteTimestamp = 0L

    // Pending connection target waiting for BLE beacon MAC resolution
    @Volatile
    private var pendingConnectClassicMac: String? = null
    @Volatile
    private var pendingConnectModelName: String? = null

    private var commandTxChar: BluetoothGattCharacteristic? = null
    private var notifyRxChar: BluetoothGattCharacteristic? = null
    private var eqChar: BluetoothGattCharacteristic? = null
    private var keyMappingChar: BluetoothGattCharacteristic? = null
    private var batteryChar: BluetoothGattCharacteristic? = null
    private var v1SettingChar: BluetoothGattCharacteristic? = null
    private val allTxChars = mutableListOf<BluetoothGattCharacteristic>()
    val batteryTracker = BatteryTracker()

    // Write Serialization Queue
    private sealed class GattOp {
        data class WriteChar(
            val char: BluetoothGattCharacteristic,
            val data: ByteArray,
            val writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ) : GattOp()
        data class WriteDesc(val desc: BluetoothGattDescriptor, val data: ByteArray) : GattOp()
        data class ReadChar(val char: BluetoothGattCharacteristic) : GattOp()
    }

    private val opQueue = Channel<GattOp>(Channel.UNLIMITED)
    private var opCompleter: CompletableDeferred<Boolean>? = null

    private var a2dpProfile: BluetoothA2dp? = null
    private var headsetProfile: BluetoothHeadset? = null

    init {
        ProductDatabase.initFromRaw(appContext)
        startQueueProcessor()
        registerSystemBtReceiver()
        registerProfileProxyListeners()
    }

    private fun saveBleMacMapping(classicMac: String?, bleAddress: String?) {
        if (classicMac.isNullOrBlank() || bleAddress.isNullOrBlank()) return
        if (classicMac.equals(bleAddress, ignoreCase = true)) return
        // Never cache a bonded classic/phantom address (dual-bond, e.g. DD:06) as the LE GATT
        // target - it is BR/EDR-only and would cause a doomed direct-LE attempt on every connect.
        val isBondedClassic = try {
            bluetoothAdapter?.bondedDevices?.any { it.address.equals(bleAddress, ignoreCase = true) } == true
        } catch (e: Exception) { false }
        if (isBondedClassic) {
            AegisLogger.d(TAG, "Skip caching BLE mapping $classicMac -> $bleAddress (bonded classic address)")
            return
        }
        macCachePrefs.edit()
            .putString(classicMac.uppercase(), bleAddress.uppercase())
            .putString("LAST_BLE_${classicMac.uppercase()}", bleAddress.uppercase())
            .apply()
        AegisLogger.i(TAG, "Cached BLE GATT MAC mapping: Classic $classicMac -> BLE $bleAddress")
    }

    private fun getCachedBleAddress(classicMac: String?): String? {
        if (classicMac.isNullOrBlank()) return null
        return macCachePrefs.getString(classicMac.uppercase(), null)
    }

    fun isLocationEnabledForBleScan(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun registerProfileProxyListeners() {
        val adapter = bluetoothAdapter ?: return
        try {
            adapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = proxy as? BluetoothA2dp
                        Log.i(TAG, "A2DP Proxy connected. Devices: ${a2dpProfile?.connectedDevices?.map { it.name }}")
                        refreshBondedDevices(autoConnect = true)
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) a2dpProfile = null
                }
            }, BluetoothProfile.A2DP)

            adapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProfile = proxy as? BluetoothHeadset
                        refreshBondedDevices(autoConnect = false)
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HEADSET) headsetProfile = null
                }
            }, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            Log.w(TAG, "Error registering profile proxies", e)
        }
    }

    private fun registerSystemBtReceiver() {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(systemBtReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(systemBtReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error registering BT receiver", e)
        }
    }

    private val systemBtReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            intent ?: return
            when (intent.action) {
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val device = getParcelableDevice(intent) ?: return
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (level in 1..100) {
                        val name = device.name ?: ""
                        val currentBle = _deviceState.value.bleAddress
                        val currentMac = _deviceState.value.classicMac
                        if (device.address.equals(currentBle, ignoreCase = true) ||
                            device.address.equals(currentMac, ignoreCase = true) ||
                            (BleAdvertisementParser.isQcyDeviceName(name) && isDeviceConnected(device))
                        ) {
                            AegisLogger.i(TAG, "System battery update for ${device.name} (${device.address}): $level%")
                            val batt = batteryTracker.onSystemHfp(level)
                            if (_deviceState.value.isConnected || _deviceState.value.isConnecting) {
                                publishBattery(batt)
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val stateExtra = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED || stateExtra == BluetoothProfile.STATE_CONNECTED) {
                        val device = getParcelableDevice(intent)
                        if (device != null && BleAdvertisementParser.isQcyDeviceName(device.name ?: "")) {
                            AegisLogger.i(TAG, "QCY audio/ACL connected: ${device.name} (${device.address}). Triggering auto-discovery & connect...")
                            coroutineScope.launch {
                                delay(400)
                                startScanning(autoConnect = true)
                            }
                        }
                    } else if (stateExtra == BluetoothProfile.STATE_DISCONNECTED) {
                        val device = getParcelableDevice(intent)
                        val currentBle = _deviceState.value.bleAddress
                        val currentMac = _deviceState.value.classicMac
                        if (device != null && (device.address.equals(currentBle, ignoreCase = true) ||
                                    device.address.equals(currentMac, ignoreCase = true))) {
                            if (!isDeviceConnected(device)) {
                                AegisLogger.w(TAG, "Profile disconnected for ${device.name} (${device.address}). Resetting state.")
                                disconnect()
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = getParcelableDevice(intent) ?: return
                    val currentBle = _deviceState.value.bleAddress
                    val currentMac = _deviceState.value.classicMac
                    if (device.address.equals(currentBle, ignoreCase = true) ||
                        device.address.equals(currentMac, ignoreCase = true)
                    ) {
                        if (!isDeviceConnected(device)) {
                            AegisLogger.w(TAG, "ACL Disconnected for ${device.name} (${device.address}). Triggering clean disconnect...")
                            disconnect()
                        }
                    }
                    refreshBondedDevices(autoConnect = false)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (btState == BluetoothAdapter.STATE_OFF || btState == BluetoothAdapter.STATE_TURNING_OFF) {
                        disconnect()
                    } else if (btState == BluetoothAdapter.STATE_ON) {
                        startScanning(autoConnect = true)
                    }
                }
            }
        }
    }

    private fun getParcelableDevice(intent: android.content.Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun getSystemBatteryLevel(device: BluetoothDevice): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val method = device.javaClass.getMethod("getBatteryLevel")
                val level = method.invoke(device) as? Int ?: -1
                if (level in 1..100) return level
            }
        } catch (e: Exception) {}

        try {
            headsetProfile?.let { hs ->
                val method = hs.javaClass.getMethod("getBatteryLevel", BluetoothDevice::class.java)
                val level = method.invoke(hs, device) as? Int ?: -1
                if (level in 1..100) return level
            }
        } catch (e: Exception) {}

        return -1
    }

    private fun sanitizeRssi(rawRssi: Int, currentRssi: Int): Int {
        // Filter out bogus RSSI values like -2 dBm or 0 dBm returned by Android on unconnected GATT links
        if (rawRssi >= -10 || rawRssi < -105) {
            return if (currentRssi in -104..-11) currentRssi else -55
        }
        return if (currentRssi in -100..-11) {
            (currentRssi * 0.6f + rawRssi * 0.4f).roundToInt().coerceIn(-100, -20)
        } else {
            rawRssi.coerceIn(-100, -20)
        }
    }

    /**
     * Single battery publisher: updates the connected device state AND mirrors the value
     * into that device's discovered-list entry, so Home / sheet / notification / hwinfo /
     * spoken announcements can never show different values.
     */
    private fun publishBattery(batt: QcyBatteryInfo) {
        _deviceState.update { it.copy(battery = batt) }
        val st = _deviceState.value
        val ble = st.bleAddress
        val mac = st.classicMac
        if (ble.isBlank() && mac.isBlank()) return
        _discoveredDevices.update { map ->
            val key = map.keys.firstOrNull { k ->
                (ble.isNotEmpty() && k.equals(ble, ignoreCase = true)) ||
                    (mac.isNotEmpty() && k.equals(mac, ignoreCase = true))
            } ?: return@update map
            val dev = map[key] ?: return@update map
            map + (key to dev.copy(battery = batt))
        }
    }

    /**
     * When two bond records merge for the same model (real + phantom), keep the
     * record that is live now (active audio or ACL) so the entry never sticks
     * to the dead phantom MAC.
     */
    private fun resolveMergedClassicMac(
        existingMac: String?,
        incomingMac: String?,
        activeAudioAddr: String?
    ): String? {
        if (existingMac.isNullOrEmpty()) return incomingMac
        if (incomingMac.isNullOrEmpty()) return existingMac
        if (existingMac.equals(incomingMac, ignoreCase = true)) return existingMac
        val exLive = (activeAudioAddr != null && activeAudioAddr.equals(existingMac, ignoreCase = true)) ||
            isMacAclConnected(existingMac)
        val inLive = (activeAudioAddr != null && activeAudioAddr.equals(incomingMac, ignoreCase = true)) ||
            isMacAclConnected(incomingMac)
        if (inLive != exLive) return if (inLive) incomingMac else existingMac
        // Both offline: the REAL bond has a cached BLE GATT MAC from past sessions,
        // a phantom bond (Android dual-bond leftover) never connected, so no cache.
        val exCached = !getCachedBleAddress(existingMac).isNullOrEmpty()
        val inCached = !getCachedBleAddress(incomingMac).isNullOrEmpty()
        if (inCached != exCached) return if (inCached) incomingMac else existingMac
        return existingMac
    }

    private fun isMacAclConnected(mac: String): Boolean = try {
        val dev = bluetoothAdapter?.getRemoteDevice(mac)
        dev != null && isDeviceConnected(dev)
    } catch (e: Exception) { false }

    private fun addOrUpdateDiscovered(newDev: DiscoveredQcyDevice, fromBleBeacon: Boolean) {
        val activeAudioAddr = getActiveAudioDeviceAddress()
        _discoveredDevices.update { currentMap ->
            val existingEntry = currentMap.values.find { existing ->
                existing.bleAddress.equals(newDev.bleAddress, ignoreCase = true) ||
                (!existing.classicMac.isNullOrEmpty() && !newDev.classicMac.isNullOrEmpty() &&
                    existing.classicMac.equals(newDev.classicMac, ignoreCase = true)) ||
                (!existing.classicMac.isNullOrEmpty() && !newDev.otherMac.isNullOrEmpty() &&
                    existing.classicMac.equals(newDev.otherMac, ignoreCase = true)) ||
                (!existing.classicMac.isNullOrEmpty() && existing.classicMac.equals(newDev.bleAddress, ignoreCase = true)) ||
                (!newDev.classicMac.isNullOrEmpty() && newDev.classicMac.equals(existing.bleAddress, ignoreCase = true)) ||
                // Same physical device seen with classicMac (bonded/HFP), with only a beacon
                // address, or as TWO bond records for the same model (Android dual-bond
                // phantom): merge by real name. shortcut: two identical-model pairs would
                // collapse into one entry - upgrade to serial-based dedup if that ever happens.
                (existing.deviceName.isNotBlank() && newDev.deviceName.isNotBlank() &&
                    !existing.deviceName.startsWith("QCY Aygıtı") && !newDev.deviceName.startsWith("QCY Aygıtı") &&
                    existing.deviceName.replace(" ", "").equals(newDev.deviceName.replace(" ", ""), ignoreCase = true)) ||
                (fromBleBeacon && existing.isAudioConnected &&
                    existing.deviceName.replace(" ", "").equals(newDev.deviceName.replace(" ", ""), ignoreCase = true))
            }

            if (existingEntry != null) {
                val resolvedClassicMac = resolveMergedClassicMac(
                    existingEntry.classicMac, newDev.classicMac, activeAudioAddr
                )
                // CRITICAL: Never overwrite a real BLE advertisement MAC with a Classic BR/EDR MAC!
                val resolvedBleAddress = when {
                    fromBleBeacon -> newDev.bleAddress
                    !existingEntry.bleAddress.equals(resolvedClassicMac, ignoreCase = true) -> existingEntry.bleAddress
                    else -> getCachedBleAddress(resolvedClassicMac) ?: newDev.bleAddress
                }

                if (fromBleBeacon && !resolvedClassicMac.isNullOrEmpty()) {
                    saveBleMacMapping(resolvedClassicMac, resolvedBleAddress)
                }

                val isAudioConn = existingEntry.isAudioConnected || newDev.isAudioConnected || (
                    activeAudioAddr != null && (
                        activeAudioAddr.equals(resolvedClassicMac, ignoreCase = true) ||
                        activeAudioAddr.equals(resolvedBleAddress, ignoreCase = true)
                    )
                )
                val isReachableNow = fromBleBeacon || isAudioConn || existingEntry.isReachable

                val mergedBattery = if (newDev.battery.hasValidBattery) {
                    QcyBatteryInfo(
                        leftLevel = if (newDev.battery.leftLevel in 1..100) newDev.battery.leftLevel else existingEntry.battery.leftLevel,
                        isLeftCharging = newDev.battery.isLeftCharging,
                        rightLevel = if (newDev.battery.rightLevel in 1..100) newDev.battery.rightLevel else existingEntry.battery.rightLevel,
                        isRightCharging = newDev.battery.isRightCharging,
                        caseLevel = if (newDev.battery.caseLevel in 1..100) newDev.battery.caseLevel else existingEntry.battery.caseLevel,
                        isCaseCharging = newDev.battery.isCaseCharging
                    )
                } else if (isReachableNow) {
                    existingEntry.battery
                } else {
                    QcyBatteryInfo()
                }

                val mergedRssi = if (fromBleBeacon && newDev.rssi in -104..-11) {
                    sanitizeRssi(newDev.rssi, existingEntry.rssi)
                } else if (isReachableNow) {
                    existingEntry.rssi
                } else {
                    -100
                }

                val mergedDev = existingEntry.copy(
                    deviceName = if (newDev.deviceName.isNotEmpty() && !newDev.deviceName.startsWith("QCY Aygıtı")) newDev.deviceName else existingEntry.deviceName,
                    bleAddress = resolvedBleAddress,
                    classicMac = resolvedClassicMac,
                    otherMac = newDev.otherMac ?: existingEntry.otherMac,
                    vendorId = if (newDev.vendorId != 0) newDev.vendorId else existingEntry.vendorId,
                    productInfo = newDev.productInfo,
                    isBonded = existingEntry.isBonded || newDev.isBonded,
                    isReachable = isReachableNow,
                    isAudioConnected = isAudioConn,
                    battery = mergedBattery,
                    rssi = mergedRssi,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
                val key = (resolvedClassicMac ?: resolvedBleAddress).uppercase()
                val cleaned = currentMap.filterKeys { k ->
                    !k.equals(existingEntry.bleAddress, ignoreCase = true) &&
                    !k.equals(newDev.bleAddress, ignoreCase = true) &&
                    !k.equals(key, ignoreCase = true)
                }
                cleaned + (key to mergedDev)
            } else {
                if (fromBleBeacon && !newDev.classicMac.isNullOrEmpty()) {
                    saveBleMacMapping(newDev.classicMac, newDev.bleAddress)
                }
                val key = (newDev.classicMac ?: newDev.bleAddress).uppercase()
                currentMap + (key to newDev)
            }
        }
    }

    private fun startQueueProcessor() {
        coroutineScope.launch {
            for (op in opQueue) {
                executeGattOp(op)
            }
        }
    }

    private suspend fun executeGattOp(op: GattOp) {
        val gatt = bluetoothGatt ?: return
        val deferred = CompletableDeferred<Boolean>()
        opCompleter = deferred

        val success = try {
            when (op) {
                is GattOp.WriteChar -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val code = gatt.writeCharacteristic(op.char, op.data, op.writeType)
                        code == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        op.char.value = op.data
                        @Suppress("DEPRECATION")
                        op.char.writeType = op.writeType
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(op.char)
                    }
                }
                is GattOp.WriteDesc -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val code = gatt.writeDescriptor(op.desc, op.data)
                        code == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        op.desc.value = op.data
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(op.desc)
                    }
                }
                is GattOp.ReadChar -> {
                    gatt.readCharacteristic(op.char)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing GATT op: ${e.message}")
            false
        }

        if (success) {
            try {
                withTimeout(800) {
                    deferred.await()
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "GATT op timeout, proceeding...")
                AegisLogger.w(TAG, "GATT op timeout after 800ms: $op")
            }
        } else {
            Log.w(TAG, "GATT op returned false for $op")
            AegisLogger.w(TAG, "GATT op returned false: $op")
        }
        delay(20)
    }

    // --- Scanning ---

    private var allowAutoConnectOnBeacon: Boolean = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val discovered = BleAdvertisementParser.parseScanResult(result) ?: return
            AegisLogger.i(
                TAG,
                "BLE Beacon: name=${discovered.deviceName}, ble=${discovered.bleAddress}, classic=${discovered.classicMac}, other=${discovered.otherMac}, vid=${discovered.vendorId}, L=${discovered.battery.leftLevel}%, R=${discovered.battery.rightLevel}%, Case=${discovered.battery.caseLevel}%, rssi=${result.rssi}"
            )
            addOrUpdateDiscovered(discovered, fromBleBeacon = true)

            val state = _deviceState.value
            val matchesCurrentDevice =
                state.bleAddress.equals(discovered.bleAddress, ignoreCase = true) ||
                (!discovered.classicMac.isNullOrEmpty() && state.classicMac.equals(discovered.classicMac, ignoreCase = true)) ||
                (!discovered.otherMac.isNullOrEmpty() && state.classicMac.equals(discovered.otherMac, ignoreCase = true)) ||
                (state.isConnected && state.modelName.replace(" ", "").equals(discovered.deviceName.replace(" ", ""), ignoreCase = true))

            // Real-time battery & RSSI update from BLE advertisement (including charging case battery!)
            if (matchesCurrentDevice && (state.isConnected || state.isConnecting)) {
                val validRssi = sanitizeRssi(result.rssi, state.rssi)
                val updatedBatt = batteryTracker.onBeacon(discovered.battery)
                _deviceState.update {
                    it.copy(
                        rssi = validRssi,
                        vendorId = if (discovered.vendorId != 0) discovered.vendorId else it.vendorId
                    )
                }
                publishBattery(updatedBatt)
            }

            // Check if a manual or auto connection is waiting for this BLE advertisement's GATT MAC
            val pendingClassic = pendingConnectClassicMac
            val pendingModel = pendingConnectModelName
            if (pendingClassic != null) {
                val matchesPending =
                    discovered.bleAddress.equals(pendingClassic, ignoreCase = true) ||
                    (!discovered.classicMac.isNullOrEmpty() && discovered.classicMac.equals(pendingClassic, ignoreCase = true)) ||
                    (!discovered.otherMac.isNullOrEmpty() && discovered.otherMac.equals(pendingClassic, ignoreCase = true)) ||
                    (!pendingModel.isNullOrEmpty() && discovered.deviceName.replace(" ", "").equals(pendingModel.replace(" ", ""), ignoreCase = true))

                if (matchesPending) {
                    AegisLogger.i(TAG, "Resolved BLE GATT MAC ${discovered.bleAddress} for pending Classic MAC $pendingClassic! Connecting GATT now...")
                    pendingConnectClassicMac = null
                    pendingConnectModelName = null
                    saveBleMacMapping(pendingClassic, discovered.bleAddress)
                    initiateGattConnection(
                        bleAddr = discovered.bleAddress,
                        classicMac = discovered.classicMac ?: pendingClassic,
                        modelName = discovered.deviceName,
                        vendorId = discovered.vendorId,
                        beaconBattery = discovered.battery,
                        beaconRssi = result.rssi
                    )
                    return
                }
            }

            // Auto-connect if requested on startup and beacon matches the active audio QCY earbud
            val activeAudioAddr = getActiveAudioDeviceAddress()
            if (allowAutoConnectOnBeacon && !_deviceState.value.isConnected && !_deviceState.value.isConnecting && activeAudioAddr != null) {
                val activeDeviceName = try {
                    bluetoothAdapter?.getRemoteDevice(activeAudioAddr)?.name ?: ""
                } catch (e: Exception) { "" }

                val matchesActiveAudio =
                    discovered.bleAddress.equals(activeAudioAddr, ignoreCase = true) ||
                    (!discovered.classicMac.isNullOrEmpty() && discovered.classicMac.equals(activeAudioAddr, ignoreCase = true)) ||
                    (!discovered.otherMac.isNullOrEmpty() && discovered.otherMac.equals(activeAudioAddr, ignoreCase = true)) ||
                    (activeDeviceName.isNotEmpty() && discovered.deviceName.replace(" ", "").equals(activeDeviceName.replace(" ", ""), ignoreCase = true))

                if (matchesActiveAudio) {
                    AegisLogger.i(TAG, "Beacon matched active audio QCY device ($activeAudioAddr -> BLE ${discovered.bleAddress}), auto-connecting...")
                    allowAutoConnectOnBeacon = false
                    saveBleMacMapping(activeAudioAddr, discovered.bleAddress)
                    initiateGattConnection(
                        bleAddr = discovered.bleAddress,
                        classicMac = activeAudioAddr,
                        modelName = discovered.deviceName,
                        vendorId = discovered.vendorId,
                        beaconBattery = discovered.battery,
                        beaconRssi = result.rssi
                    )
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            AegisLogger.e(TAG, "BLE Scan failed with errorCode: $errorCode")
            _isScanning.value = false
        }
    }

    private fun buildQcyScanFilters(): List<ScanFilter> {
        return listOf(
            // Official QCY Manufacturer IDs (21020 / 0x521C, 21036 / 0x522C, 1494 / 0x05D6)
            ScanFilter.Builder().setManufacturerData(QcyProtocol.QCY_COMPANY_ID, ByteArray(0)).build(),
            ScanFilter.Builder().setManufacturerData(QcyProtocol.QCY_COMPANY_ID_21036, ByteArray(0)).build(),
            ScanFilter.Builder().setManufacturerData(QcyProtocol.QCY_COMPANY_ID_1494, ByteArray(0)).build(),
            ScanFilter.Builder().setManufacturerData(0x1C52, ByteArray(0)).build(),
            ScanFilter.Builder().setManufacturerData(0x2C52, ByteArray(0)).build(),
            // Official QCY GATT Service UUIDs
            ScanFilter.Builder().setServiceUuid(ParcelUuid(QcyProtocol.SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(QcyProtocol.SERVICE_WUQI)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(QcyProtocol.SERVICE_BES)).build(),
            // Empty catch-all filter so Honor MagicOS treats scan as Filtered (never suspends!) while matching all QCY advertisements
            ScanFilter.Builder().build()
        )
    }

    /**
     * Starts BLE scanning.
     * IMPORTANT: When called from UI ("Yeni Cihazları Tara"), autoConnect MUST be false so it only scans and never auto-connects.
     */
    fun startScanning(autoConnect: Boolean = false) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        allowAutoConnectOnBeacon = autoConnect
        // Refresh bonded QCY devices WITHOUT calling connectGatt yet (so we never suspend the BLE scanner!)
        refreshBondedDevices(autoConnect = false)

        if (!_isScanning.value) {
            val scanner = adapter.bluetoothLeScanner
            if (scanner != null) {
                val settingsBuilder = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setReportDelay(0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    settingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }

                _isScanning.value = true
                try {
                    val filters = buildQcyScanFilters()
                    scanner.startScan(filters, settingsBuilder.build(), scanCallback)
                    AegisLogger.i(TAG, "BLE scan started with ${filters.size} hardware ScanFilters (autoConnect=$autoConnect)")
                } catch (e: Exception) {
                    AegisLogger.e(TAG, "Error starting filtered BLE scan", e)
                    _isScanning.value = false
                }

                stopScanRunnable?.let { mainHandler.removeCallbacks(it) }
                val runnable = object : Runnable {
                    override fun run() {
                        // Keep the scanner alive while we are still waiting to auto-connect to the
                        // headset (buds may only advertise LE after they wake up): instant auto-connect
                        // without requiring a manual tap.
                        if (allowAutoConnectOnBeacon && !_deviceState.value.isConnected && !_deviceState.value.isConnecting) {
                            mainHandler.postDelayed(this, 15000)
                        } else {
                            stopScanning()
                        }
                    }
                }
                stopScanRunnable = runnable
                mainHandler.postDelayed(runnable, 15000)
            }
        }

        if (autoConnect && !_deviceState.value.isConnected && !_deviceState.value.isConnecting) {
            val activeAudioAddr = getActiveAudioDeviceAddress()
            if (activeAudioAddr != null) {
                val dev = try { adapter.getRemoteDevice(activeAudioAddr) } catch (e: Exception) { null }
                if (dev != null && BleAdvertisementParser.isQcyDeviceName(dev.name ?: "")) {
                    connect(activeAudioAddr)
                }
            }
        }
    }

    fun getActiveAudioDeviceAddress(): String? {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (info in outputs) {
                if (info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val addr = info.address
                        if (!addr.isNullOrBlank()) {
                            return addr
                        }
                    }
                }
            }
        }
        return null
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        val activeAudioAddr = getActiveAudioDeviceAddress()
        if (activeAudioAddr != null && activeAudioAddr.equals(device.address, ignoreCase = true)) {
            return true
        }

        a2dpProfile?.let { a2dp ->
            try {
                if (a2dp.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
                if (a2dp.connectedDevices.any { it.address.equals(device.address, ignoreCase = true) }) return true
            } catch (e: Exception) {}
        }

        headsetProfile?.let { hfp ->
            try {
                if (hfp.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
                if (hfp.connectedDevices.any { it.address.equals(device.address, ignoreCase = true) }) return true
            } catch (e: Exception) {}
        }

        try {
            val method = device.javaClass.getMethod("isConnected")
            val connected = method.invoke(device) as? Boolean ?: false
            if (connected) return true
        } catch (e: Exception) {}

        return false
    }

    fun scanAndAutoConnectBonded(autoConnect: Boolean = true) {
        if (autoConnect) {
            startScanning(autoConnect = true)
        } else {
            refreshBondedDevices(autoConnect = false)
        }
    }

    private fun refreshBondedDevices(autoConnect: Boolean = false) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            val activeAudioAddr = getActiveAudioDeviceAddress()
            val qcyBonded = bonded.filter { BleAdvertisementParser.isQcyDeviceName(it.name ?: "") }

            // Also inspect any already-connected GATT devices in system BluetoothManager
            val gattConnected = try {
                bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            for (gDev in gattConnected) {
                val gName = gDev.name ?: ""
                if (BleAdvertisementParser.isQcyDeviceName(gName)) {
                    val vid = BleAdvertisementParser.guessVendorIdFromName(gName)
                    val info = ProductDatabase.getProduct(vid)
                    val matchingBonded = qcyBonded.find {
                        (it.name ?: "").replace(" ", "").equals(gName.replace(" ", ""), ignoreCase = true)
                    }
                    if (matchingBonded != null && !matchingBonded.address.equals(gDev.address, ignoreCase = true)) {
                        saveBleMacMapping(matchingBonded.address, gDev.address)
                    }
                    addOrUpdateDiscovered(
                        DiscoveredQcyDevice(
                            deviceName = if (info.title.isNotEmpty() && !info.title.startsWith("QCY Aygıtı")) info.title else gName,
                            bleAddress = gDev.address,
                            classicMac = matchingBonded?.address,
                            vendorId = vid,
                            productInfo = info,
                            battery = QcyBatteryInfo(),
                            rssi = -45,
                            isBonded = matchingBonded != null,
                            isReachable = true,
                            isAudioConnected = matchingBonded != null && isDeviceConnected(matchingBonded)
                        ),
                        fromBleBeacon = true
                    )
                }
            }

            for (device in qcyBonded) {
                val name = device.name ?: ""
                val isAclConnected = isDeviceConnected(device)
                val isAudioActive = activeAudioAddr != null && device.address.equals(activeAudioAddr, ignoreCase = true)
                val vid = BleAdvertisementParser.guessVendorIdFromName(name)
                val info = ProductDatabase.getProduct(vid)
                val sysBatt = if (isAclConnected || isAudioActive) getSystemBatteryLevel(device) else -1
                val battery = if ((isAclConnected || isAudioActive) && sysBatt in 1..100) {
                    QcyBatteryInfo(systemLevel = sysBatt)
                } else {
                    QcyBatteryInfo()
                }
                val cachedBle = getCachedBleAddress(device.address)
                val discovered = DiscoveredQcyDevice(
                    deviceName = if (info.title.isNotEmpty() && !info.title.startsWith("QCY Aygıtı")) info.title else name,
                    bleAddress = cachedBle ?: device.address,
                    classicMac = device.address,
                    vendorId = vid,
                    productInfo = info,
                    battery = battery,
                    rssi = if (isAudioActive) -42 else if (isAclConnected) -48 else -100,
                    isBonded = true,
                    isReachable = isAclConnected || isAudioActive,
                    isAudioConnected = isAclConnected || isAudioActive
                )
                addOrUpdateDiscovered(discovered, fromBleBeacon = false)
            }

            if (autoConnect && !_deviceState.value.isConnected && !_deviceState.value.isConnecting) {
                val activeQcy = qcyBonded.find { dev ->
                    (activeAudioAddr != null && dev.address.equals(activeAudioAddr, ignoreCase = true)) ||
                    isDeviceConnected(dev)
                }
                if (activeQcy != null) {
                    connect(activeQcy.address)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying bonded devices", e)
        }
    }

    fun stopScanning() {
        stopScanRunnable?.let { mainHandler.removeCallbacks(it) }
        stopScanRunnable = null
        if (!_isScanning.value) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
        _isScanning.value = false
    }

    // --- Connection Lifecycle ---

    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        connectWatchdogJob?.cancel()
        closeCurrentGatt()

        // Find entry in discoveredDevices by either bleAddress, classicMac, or otherMac
        val entry = _discoveredDevices.value.values.find {
            it.bleAddress.equals(address, ignoreCase = true) ||
            (it.classicMac != null && it.classicMac.equals(address, ignoreCase = true)) ||
            (it.otherMac != null && it.otherMac.equals(address, ignoreCase = true))
        }

        val classicMac = entry?.classicMac ?: address
        val classicDevice = try { adapter.getRemoteDevice(classicMac) } catch (e: Exception) { null }

        // Defense in depth: the list only holds QCY devices, so entry==null + non-QCY
        // name means a poisoned/foreign address — reject before any state update.
        if (entry == null && classicDevice != null &&
            !BleAdvertisementParser.isQcyDeviceName(classicDevice.name)
        ) {
            AegisLogger.w(TAG, "Rejecting non-QCY connect target: ${classicDevice.name} ($address)")
            startScanning(autoConnect = true)
            return
        }

        val isAcl = classicDevice?.let { isDeviceConnected(it) } ?: false
        val sysBatt = if (isAcl && classicDevice != null) getSystemBatteryLevel(classicDevice) else -1

        val modelName = entry?.deviceName ?: classicDevice?.name ?: "QCY MeloBuds Pro"
        val vendorId = entry?.vendorId ?: BleAdvertisementParser.guessVendorIdFromName(modelName)

        // Check if we already know a distinct BLE GATT MAC for this device
        val cachedBle = getCachedBleAddress(classicMac)
        // entry.bleAddress is only a real LE address when it came from an advertisement. Bonded-loop
        // entries can carry ANOTHER bond's classic address (phantom dual-bond, e.g. DD:06), which
        // would otherwise burn a doomed direct-connect cycle - skip anything that is a bonded device.
        val entryBle = entry?.bleAddress
        val entryBleIsBondedClassic = entryBle != null && try {
            adapter.bondedDevices?.any { it.address.equals(entryBle, ignoreCase = true) } == true
        } catch (e: Exception) { false }
        // Same phantom rule for the cached mapping: a cache line can be poisoned by a
        // dual-bond classic address (e.g. DD:06) - bonded addresses are BR/EDR-only here.
        val cachedBleIsBondedClassic = !cachedBle.isNullOrEmpty() && try {
            adapter.bondedDevices?.any { it.address.equals(cachedBle, ignoreCase = true) } == true
        } catch (e: Exception) { false }
        val knownBleAddress: String? = when {
            !address.equals(classicMac, ignoreCase = true) -> address
            entryBle != null && !entryBle.equals(classicMac, ignoreCase = true) && !entryBleIsBondedClassic -> entryBle
            !cachedBle.isNullOrEmpty() && !cachedBle.equals(classicMac, ignoreCase = true) && !cachedBleIsBondedClassic -> cachedBle
            else -> null
        }

        // Reset battery cleanly for the new target device — NEVER leak previous device's battery!
        batteryTracker.reset()
        if (isAcl && sysBatt in 1..100) {
            batteryTracker.onSystemHfp(sysBatt)
        }
        // Seed order: live system HFP (just seeded) > entry younger than 60s > empty.
        // Older entry values are STALE (sheet once showed L11/R43 while GATT read L3/R18).
        val entryFresh = entry != null && entry.isReachable && entry.battery.hasValidBattery &&
            (System.currentTimeMillis() - entry.lastSeenTimestamp) in 0..60_000L
        if (entryFresh) {
            batteryTracker.onBeacon(entry.battery)
        }
        val initialBattery = batteryTracker.currentBattery

        _deviceState.update {
            it.copy(
                vendorId = vendorId,
                modelName = modelName,
                bleAddress = knownBleAddress ?: classicMac,
                classicMac = classicMac,
                isConnected = false,
                isConnecting = true,
                connectingAddress = address,
                rssi = if (isAcl) -45 else if (entry?.isReachable == true) entry.rssi else -100,
                battery = initialBattery,
                pairedPhones = buildInitialPairedPhones(isAcl)
            )
        }
        // Mirror the seed into the list entry so the sheet doesn't keep pre-connect values
        publishBattery(initialBattery)

        if (knownBleAddress != null) {
            AegisLogger.i(TAG, "Connecting to known BLE GATT MAC $knownBleAddress (Classic: $classicMac, isAcl=$isAcl)")
            pendingConnectClassicMac = null
            pendingConnectModelName = null
            initiateGattConnection(
                bleAddr = knownBleAddress,
                classicMac = classicMac,
                modelName = modelName,
                vendorId = vendorId,
                beaconBattery = initialBattery,
                beaconRssi = if (isAcl) -45 else -60
            )
            return
        }

        // We only have Classic BR/EDR MAC so far!
        // CRITICAL: Do NOT call connectGatt(classicMac) immediately because that suspends the BLE scanner!
        // Instead, scan for the 0x521C / 0x522C BLE advertisement to resolve its BLE GATT MAC!
        AegisLogger.i(TAG, "Resolving BLE GATT MAC via BLE scan for $modelName (Classic: $classicMac, isAcl=$isAcl)...")
        pendingConnectClassicMac = classicMac
        pendingConnectModelName = modelName

        if (!_isScanning.value) {
            // autoConnect=true: we are mid connect() so beacon auto-connect must stay armed,
            // and this is what keeps the scanner alive after the attempt ends (instant reconnect).
            startScanning(autoConnect = true)
        }

        connectWatchdogJob = coroutineScope.launch {
            // Wait up to 3.2 seconds for scanCallback to catch the 0x521C/0x522C BLE advertisement
            delay(3200)
            if (pendingConnectClassicMac != null && !_deviceState.value.isConnected && bluetoothGatt == null) {
                pendingConnectClassicMac = null
                pendingConnectModelName = null
                if (isAcl) {
                    AegisLogger.i(TAG, "No separate BLE beacon seen in 3.2s while ACL is active; trying direct LE GATT on $classicMac...")
                    initiateGattConnection(
                        bleAddr = classicMac,
                        classicMac = classicMac,
                        modelName = modelName,
                        vendorId = vendorId,
                        beaconBattery = initialBattery,
                        beaconRssi = -45
                    )
                } else {
                    AegisLogger.w(TAG, "Device $modelName ($classicMac) is powered off or in closed case (no ACL and no BLE beacon)")
                    markDeviceUnreachable(classicMac)
                    _deviceState.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            connectingAddress = "",
                            battery = QcyBatteryInfo(leftLevel = -1, rightLevel = -1, caseLevel = -1),
                            rssi = -100
                        )
                    }
                    com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
                        .speak("Kulaklığa ulaşılamadı. Lütfen kulaklığın açık ve kutu dışında olduğundan emin olun")
                }
            }
        }
    }

    private fun initiateGattConnection(
        bleAddr: String,
        classicMac: String,
        modelName: String,
        vendorId: Int,
        beaconBattery: QcyBatteryInfo,
        beaconRssi: Int
    ) {
        val adapter = bluetoothAdapter ?: return
        // Stop BLE scan before calling connectGatt so controller devotes 100% RF duty cycle to GATT connection
        stopScanning()
        closeCurrentGatt()

        val bleDevice = try { adapter.getRemoteDevice(bleAddr) } catch (e: Exception) { null } ?: return
        _deviceState.update {
            it.copy(
                vendorId = vendorId,
                modelName = modelName,
                bleAddress = bleAddr,
                classicMac = classicMac,
                isConnecting = true,
                connectingAddress = bleAddr,
                battery = if (beaconBattery.hasValidBattery) beaconBattery else it.battery,
                rssi = sanitizeRssi(beaconRssi, it.rssi)
            )
        }

        AegisLogger.i(TAG, "Initiating GATT connection to $modelName (BLE: $bleAddr, Classic: $classicMac) [TRANSPORT_LE]...")
        mainHandler.post {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bleDevice.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                bleDevice.connectGatt(appContext, false, gattCallback)
            }
        }

        connectWatchdogJob?.cancel()
        connectWatchdogJob = coroutineScope.launch {
            delay(6500)
            if (_deviceState.value.isConnecting && !_deviceState.value.isConnected) {
                AegisLogger.w(TAG, "GATT connection watchdog timeout for BLE $bleAddr (Classic: $classicMac)")
                closeCurrentGatt()
                // If cached BLE MAC rotated, clear it and start a fresh BLE scan!
                if (!bleAddr.equals(classicMac, ignoreCase = true)) {
                    macCachePrefs.edit().remove(classicMac.uppercase()).apply()
                    AegisLogger.i(TAG, "Cleared stale BLE MAC $bleAddr for $classicMac; restarting scan to discover rotated BLE MAC")
                    pendingConnectClassicMac = classicMac
                    pendingConnectModelName = modelName
                    // autoConnect=true: a rotated MAC means we must keep beacon auto-connect armed,
                    // otherwise the user would have to tap connect manually after every rotation.
                    startScanning(autoConnect = true)
                    delay(4000)
                } else {
                    // Direct-on-classic attempt failed (buds LE-silent): re-arm the persistent
                    // auto-connect scan BEFORE isConnecting resets below, so startScanning's
                    // immediate-connect block is skipped (isConnecting still true) and we only
                    // connect once the buds start advertising again - no connect loop, no
                    // repeated timeout voice spam.
                    AegisLogger.i(TAG, "Direct LE attempt on classic MAC failed; re-arming auto-connect scan")
                    startScanning(autoConnect = true)
                }
                if (_deviceState.value.isConnecting && !_deviceState.value.isConnected) {
                    pendingConnectClassicMac = null
                    pendingConnectModelName = null
                    _deviceState.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            connectingAddress = "",
                            battery = QcyBatteryInfo(leftLevel = -1, rightLevel = -1, caseLevel = -1),
                            rssi = -100
                        )
                    }
                    com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
                        .speak("Kulaklık bağlantısı zaman aşımına uğradı")
                }
            }
        }
    }

    private fun markDeviceUnreachable(address: String) {
        _discoveredDevices.update { map ->
            map.mapValues { (_, dev) ->
                if (dev.bleAddress.equals(address, ignoreCase = true) ||
                    (dev.classicMac != null && dev.classicMac.equals(address, ignoreCase = true))
                ) {
                    dev.copy(
                        isReachable = false,
                        isAudioConnected = false,
                        battery = QcyBatteryInfo(leftLevel = -1, rightLevel = -1, caseLevel = -1),
                        rssi = -100
                    )
                } else dev
            }
        }
    }

    private fun buildInitialPairedPhones(isAcl: Boolean): List<QcyProtocol.ConnectedPhone> {
        if (!isAcl) return emptyList()
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isPlaying = am?.isMusicActive == true
        val phoneName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        return listOf(
            QcyProtocol.ConnectedPhone(
                mac = "Bu Cihaz (Birincil)",
                name = phoneName,
                isConnected = true,
                isAudioActive = isPlaying || true
            )
        )
    }

    /**
     * (Re-)enables local notification + CCCD on every RX characteristic.
     * Called once on service discovery and again by the silence-heal in
     * [startPeriodicPolling] when the firmware stops notifying (seen after
     * GATT status-21 disconnects where CCCD ACKs succeeded but no frames arrived).
     */
    private fun resubscribeAllNotifications(gatt: BluetoothGatt) {
        val rxChars = gatt.services
            .flatMap { it.characteristics }
            .filter { c ->
                val u = c.uuid.toString().lowercase()
                u.contains("1002") || u.contains("0008") || u.contains("000f") ||
                    u.contains("2002") || u.contains("7777")
            }
            .distinct()
        for (rx in rxChars) {
            try {
                val localOk = gatt.setCharacteristicNotification(rx, true)
                val descriptor = rx.getDescriptor(QcyProtocol.CCCD_DESCRIPTOR)
                    ?: rx.descriptors.find { it.uuid.toString().contains("2902", ignoreCase = true) }
                AegisLogger.i(TAG, "CCCD resubscribe rx=${rx.uuid} localNotify=$localOk desc=${descriptor != null}")
                if (descriptor != null) {
                    opQueue.trySend(GattOp.WriteDesc(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
                }
            } catch (e: Exception) {
                AegisLogger.w(TAG, "Failed enabling notify on ${rx.uuid}: ${e.message}")
            }
        }
        lastRxTimestamp = System.currentTimeMillis()
    }

    private fun startPeriodicPolling() {
        pollJob?.cancel()
        pollJob = coroutineScope.launch {
            while (isActive) {
                delay(15000)
                if (_deviceState.value.isConnected) {
                    // Silence heal: idle firmware legitimately goes quiet (battery notifies
                    // only on change), so probe first with a query; a live subscription
                    // answers within ~100ms (refreshing lastRxTimestamp). Only after 75s
                    // of silence despite probes is the CCCD considered dead.
                    val silentFor = System.currentTimeMillis() - lastRxTimestamp
                    if (lastRxTimestamp > 0 && silentFor > 75_000L) {
                        AegisLogger.w(TAG, "No notifications for ${silentFor / 1000}s despite probes - re-subscribing CCCD")
                        lastRxTimestamp = System.currentTimeMillis()
                        bluetoothGatt?.let { resubscribeAllNotifications(it) }
                    } else if (lastRxTimestamp > 0 && silentFor > 45_000L) {
                        AegisLogger.d(TAG, "No notifications for ${silentFor / 1000}s - probing with 0xFE battery query")
                        // 0xFE wrapper gets a notify reply mid-session (unlike bare 0x2F),
                        // so an alive subscription refreshes lastRxTimestamp here.
                        sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_BATTERY_QUERY))
                    }
                    // Lightweight periodic poll: only read battery (00000008) + RSSI so we never flood GATT queue
                    batteryChar?.let {
                        opQueue.trySend(GattOp.ReadChar(it))
                    } ?: sendCommand(QcyProtocol.createBatteryQueryCommand())
                    try {
                        bluetoothGatt?.readRemoteRssi()
                    } catch (e: Exception) {
                        Log.d(TAG, "Periodic readRemoteRssi: ${e.message}")
                    }
                }
            }
        }
    }

    // Gönderilen 0x17 komutunun ham paramlarını tutar; pencere içinde gelen eski değerli
    // yankıların optimistic UI state'i ezmesini engeller.
    private val ancEchoGuard = AncEchoGuard()

    // cmdId bazlı doğrulama job'ları: aynı cmdId yeniden planlanınca SADECE kendi job'ı
    // iptal edilir, farklı cmdId'lerin bekleyen doğrulamaları çakışmaz.
    private val verifyJobs = mutableMapOf<Byte, Job>()

    private fun scheduleStateVerification(vararg cmdIds: Byte, delayMs: Long = 650L) {
        cmdIds.forEach { cid ->
            verifyJobs[cid]?.cancel()
            verifyJobs[cid] = coroutineScope.launch {
                delay(delayMs)
                if (_deviceState.value.isConnected) {
                    sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, cid))
                }
            }
        }
    }

    fun refreshKeyMappings() {
        if (!_deviceState.value.isConnected) return
        keyMappingChar?.let {
            opQueue.trySend(GattOp.ReadChar(it))
        }
        sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_KEY_FUNCTIONS))
    }

    fun startRssiPolling() {
        rssiPollJob?.cancel()
        rssiPollJob = coroutineScope.launch {
            if (!_isScanning.value) {
                startScanning(autoConnect = false)
            }
            while (isActive) {
                if (_deviceState.value.isConnected) {
                    try {
                        bluetoothGatt?.readRemoteRssi()
                    } catch (e: Exception) {
                        Log.w(TAG, "readRemoteRssi error", e)
                    }
                }
                delay(1000)
            }
        }
    }

    fun stopRssiPolling() {
        rssiPollJob?.cancel()
        rssiPollJob = null
    }

    fun queryStateAndBattery() {
        if (!_deviceState.value.isConnected) return

        sendCommand(QcyProtocol.createRequestAllDataCommand())
        // Query all official QCY setting beans (ANC 23, Game 9, InEar 6, Peidai 44, Sleep 16, Balance 22, ToneVol 29, MultiEQ 34, LDAC 35, Multipoint 36, KeyFun 43, PhoneInfo 77, PhoneStatus 79, AI ANC 84)
        val beansToQuery = listOf(23, 9, 6, 44, 16, 22, 29, 34, 35, 36, 43, 77, 79, 84)
        beansToQuery.forEach { cid ->
            sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, cid.toByte()))
        }
        sendCommand(QcyProtocol.createFirmwareQueryCommand())

        // Read direct battery characteristic (00000008)
        batteryChar?.let {
            opQueue.trySend(GattOp.ReadChar(it))
        }

        // Read key mapping characteristic (0000000D)
        keyMappingChar?.let {
            opQueue.trySend(GattOp.ReadChar(it))
        }

        // Read EQ characteristic (0000000B)
        eqChar?.let {
            opQueue.trySend(GattOp.ReadChar(it))
        }

        // Sync system HFP battery as fallback if L/R battery not yet populated from 0008 or BLE beacon
        val classicMac = _deviceState.value.classicMac
        val classicDev = if (classicMac.isNotEmpty()) {
            try { bluetoothAdapter?.getRemoteDevice(classicMac) } catch (e: Exception) { null }
        } else null

        classicDev?.let { dev ->
            if (isDeviceConnected(dev)) {
                val sysBatt = getSystemBatteryLevel(dev)
                if (sysBatt in 1..100) {
                    publishBattery(batteryTracker.onSystemHfp(sysBatt))
                }
            }
        }
    }

    private fun closeCurrentGatt() {
        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
            } catch (e: Exception) {}
            try {
                gatt.close()
            } catch (e: Exception) {}
        }
        bluetoothGatt = null
        commandTxChar = null
        notifyRxChar = null
        eqChar = null
        keyMappingChar = null
        batteryChar = null
        v1SettingChar = null
        allTxChars.clear()
    }

    fun disconnect() {
        com.aegissoft.qcybutmod.core.audio.EarbudAudioLocator.stopBeep()
        connectWatchdogJob?.cancel()
        connectWatchdogJob = null
        pendingConnectClassicMac = null
        pendingConnectModelName = null
        stopRssiPolling()
        pollJob?.cancel()
        pollJob = null
        closeCurrentGatt()

        val wasConnected = _deviceState.value.isConnected
        batteryTracker.reset()
        _deviceState.update {
            it.copy(
                isConnected = false,
                isConnecting = false,
                connectingAddress = "",
                battery = batteryTracker.currentBattery,
                rssi = -100,
                pairedPhones = emptyList()
            )
        }
        // Mirror the cleared values into the list entry so no surface keeps stale numbers
        publishBattery(batteryTracker.currentBattery)
        if (wasConnected) {
            com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
                .speak("Kulaklık bağlantısı kesildi")
        }
    }

    fun reconnectSelectedDevice() {
        val current = _deviceState.value
        var targetAddr = when {
            current.classicMac.isNotEmpty() -> current.classicMac
            current.bleAddress.isNotEmpty() -> current.bleAddress
            else -> getActiveAudioDeviceAddress() ?: ""
        }
        // Active audio may be a completely different headset (e.g. REDMI buds) —
        // never hand a non-QCY address to connect(), it would poison the state.
        if (current.classicMac.isEmpty() && current.bleAddress.isEmpty() && targetAddr.isNotEmpty()) {
            val activeName = try {
                bluetoothAdapter?.getRemoteDevice(targetAddr)?.name
            } catch (e: Exception) { null }
            if (!BleAdvertisementParser.isQcyDeviceName(activeName)) {
                AegisLogger.w(TAG, "Active audio device is not QCY ($activeName) - scanning instead")
                targetAddr = ""
            }
        }
        if (targetAddr.isNotEmpty()) {
            connect(targetAddr)
        } else {
            startScanning(autoConnect = true)
        }
    }

    fun sendRawCommand(data: ByteArray) {
        sendCommand(data)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val gattAddr = gatt?.device?.address ?: ""
                    AegisLogger.i(TAG, "GATT Connected successfully to $gattAddr (TRANSPORT_LE). Negotiating MTU & discovering services...")
                    val classic = _deviceState.value.classicMac
                    if (classic.isNotEmpty() && gattAddr.isNotEmpty() && !classic.equals(gattAddr, ignoreCase = true)) {
                        saveBleMacMapping(classic, gattAddr)
                    }
                    _deviceState.update {
                        it.copy(
                            isConnecting = true,
                            bleAddress = gattAddr.ifEmpty { it.bleAddress }
                        )
                    }
                    try {
                        gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    } catch (e: Exception) {}
                    try {
                        val mtuRequested = gatt?.requestMtu(250) ?: false
                        if (!mtuRequested) {
                            mainHandler.postDelayed({ gatt?.discoverServices() }, 200)
                        }
                    } catch (e: Exception) {
                        mainHandler.postDelayed({ gatt?.discoverServices() }, 200)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    AegisLogger.w(TAG, "GATT Disconnected (status: $status)")
                    closeCurrentGatt()
                    _deviceState.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            connectingAddress = "",
                            battery = QcyBatteryInfo(leftLevel = -1, rightLevel = -1, caseLevel = -1),
                            rssi = -100
                        )
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            AegisLogger.i(TAG, "MTU changed to: $mtu (status: $status). Discovering services...")
            mainHandler.postDelayed({
                gatt?.discoverServices()
            }, 150)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                AegisLogger.e(TAG, "Service discovery failed: $status")
                _deviceState.update { it.copy(isConnecting = false, isConnected = false) }
                return
            }

            val allServices = gatt.services
            val allChars = allServices.flatMap { it.characteristics }
            AegisLogger.i(TAG, "Services discovered: ${allServices.map { it.uuid }}; Chars: ${allChars.map { "${it.service.uuid.toString().substring(4, 8)}:${it.uuid.toString().substring(4, 8)}" }}")

            // CRITICAL: Prefer official QCY service 0000A001 over 00007033 when both expose 00001001 / 00001002!
            val qcyService = gatt.getService(QcyProtocol.SERVICE_UUID)
                ?: allServices.find { it.uuid.toString().lowercase().contains("a001") }
            val qcyChars = qcyService?.characteristics ?: allChars

            allTxChars.clear()
            // Add 0000A001 TX chars first so sendCommand always targets 0000A001:00001001
            qcyService?.characteristics?.filter {
                val u = it.uuid.toString().lowercase()
                u.contains("1001") || u.contains("7777")
            }?.let { allTxChars.addAll(it) }

            allTxChars.addAll(allChars.filter {
                val u = it.uuid.toString().lowercase()
                (u.contains("1001") || u.contains("7777") || u.contains("2001")) && !allTxChars.contains(it)
            })

            commandTxChar = qcyChars.find { it.uuid.toString().lowercase().contains("1001") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("1001") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("7777") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("2001") }

            notifyRxChar = qcyChars.find { it.uuid.toString().lowercase().contains("1002") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("1002") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("7777") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("2002") }

            eqChar = qcyChars.find { it.uuid.toString().lowercase().contains("000b") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("000b") }
            keyMappingChar = qcyChars.find { it.uuid.toString().lowercase().contains("000d") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("000d") }
            batteryChar = qcyChars.find { it.uuid.toString().lowercase().contains("0008") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("0008") }
            v1SettingChar = qcyChars.find { it.uuid.toString().lowercase().contains("000f") }
                ?: allChars.find { it.uuid.toString().lowercase().contains("000f") }

            AegisLogger.i(TAG, "Matched TX=${commandTxChar?.service?.uuid}:${commandTxChar?.uuid}, RX=${notifyRxChar?.service?.uuid}:${notifyRxChar?.uuid}, EQ=${eqChar?.uuid}, KEY=${keyMappingChar?.uuid}, BATT=${batteryChar?.uuid}")

            connectWatchdogJob?.cancel()
            _deviceState.update {
                it.copy(
                    isConnected = true,
                    isConnecting = false,
                    connectingAddress = "",
                    pairedPhones = if (it.pairedPhones.isEmpty()) buildInitialPairedPhones(true) else it.pairedPhones
                )
            }

            // Subscribe to notifications on 0000A001 RX characteristics first, then any secondary RX characteristics
            resubscribeAllNotifications(gatt)

            // Read Firmware (0007), Battery (0008), Key Mappings (000d), EQ (000b)
            allChars.find { it.uuid.toString().lowercase().contains("0007") }?.let {
                opQueue.trySend(GattOp.ReadChar(it))
            }
            batteryChar?.let {
                opQueue.trySend(GattOp.ReadChar(it))
            }
            keyMappingChar?.let {
                opQueue.trySend(GattOp.ReadChar(it))
            }
            eqChar?.let {
                opQueue.trySend(GattOp.ReadChar(it))
            }

            // Query full state after CCCD subscriptions complete
            coroutineScope.launch {
                delay(350)
                queryStateAndBattery()
                delay(300)
                applyVoiceSource(VoiceSettings.getVoiceSource(appContext))
                delay(200)
                val batt = _deviceState.value.battery
                val primaryBatt = if (batt.leftLevel in 1..100) batt.leftLevel else batt.rightLevel
                com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
                    .announceConnection(_deviceState.value.modelName, primaryBatt)
            }

            startPeriodicPolling()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            AegisLogger.d(TAG, "GATT Write ACK (${characteristic?.uuid}): status=$status")
            opCompleter?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                AegisLogger.i(TAG, "CCCD write ACK ${descriptor?.uuid}: status=$status")
            } else {
                AegisLogger.w(TAG, "CCCD write FAILED ${descriptor?.uuid}: status=$status")
            }
            opCompleter?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handleCharacteristicRead(characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicRead(characteristic, value, status)
        }

        private fun handleCharacteristicRead(characteristic: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
                val u = characteristic.uuid.toString().lowercase()
                val hex = value.joinToString(" ") { "%02X".format(it) }
                AegisLogger.i(TAG, "GATT Read ($u): $hex")
                when {
                    u.contains("0007") -> {
                        val fwStr = if (value.size >= 3 && value.all { (it.toInt() and 0xFF) < 32 }) {
                            "v${value[0].toInt() and 0xFF}.${value[1].toInt() and 0xFF}.${value[2].toInt() and 0xFF}"
                        } else {
                            String(value, Charsets.UTF_8).trim().trimEnd('\u0000')
                        }
                        if (fwStr.isNotEmpty()) {
                            _deviceState.update { it.copy(firmwareVersion = fwStr) }
                        }
                    }
                    u.contains("0008") -> {
                        publishBattery(batteryTracker.onGattRaw(value))
                    }
                    u.contains("000d") -> {
                        val map = mutableMapOf<Int, Int>()
                        var i = 0
                        while (i + 1 < value.size) {
                            val keyId = value[i].toInt() and 0xFF
                            val funcId = value[i + 1].toInt() and 0xFF
                            map[keyId] = if (funcId == 0xFF) 0 else funcId
                            i += 2
                        }
                        if (map.isNotEmpty()) {
                            _deviceState.update { current ->
                                current.copy(keyMappings = current.keyMappings + map)
                            }
                            AegisLogger.i(TAG, "Hardware key mappings read from 000D: $map")
                        }
                    }
                    u.contains("000b") -> {
                        if (value.size >= 11) {
                            val gains = (1..10).map { idx -> value[idx].toInt().toFloat().coerceIn(-10f, 10f) }
                            _deviceState.update { it.copy(eqGains = gains) }
                        }
                    }
                }
            }
            opCompleter?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic ?: return
            handleIncomingBytes(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(characteristic.uuid, value)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && _deviceState.value.isConnected) {
                if (rssi in -104..-11) {
                    val smoothed = sanitizeRssi(rssi, _deviceState.value.rssi)
                    AegisLogger.d(TAG, "GATT RSSI updated: raw=$rssi dBm, smoothed=$smoothed dBm")
                    _deviceState.update { it.copy(rssi = smoothed) }
                }
            }
        }
    }

    private fun handleIncomingBytes(uuid: UUID, bytes: ByteArray?) {
        bytes ?: return
        lastRxTimestamp = System.currentTimeMillis()
        val u = uuid.toString().lowercase()
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        AegisLogger.d(TAG, "RX ($u): $hex")

        if (u.contains("0008")) {
            val batt = batteryTracker.onGattRaw(bytes)
            publishBattery(batt)
            AegisLogger.i(TAG, "Battery notified from 0008: L=${batt.leftLevel}% R=${batt.rightLevel}% Case=${batt.caseLevel}%")
            return
        }

        val frames = QcyProtocol.unpackAllFrames(bytes)
        if (frames.isNotEmpty()) {
            frames.forEach { frame ->
                processQcyFrame(frame.cmdId, frame.payload)
            }
        }
    }

    private fun processQcyFrame(cmdId: Byte, payload: ByteArray) {
        val hexPayload = payload.joinToString(" ") { "%02X".format(it) }
        AegisLogger.d(TAG, "Frame decoded: CMD 0x%02X (%d), len=%d, payload=[%s]".format(cmdId, cmdId.toInt() and 0xFF, payload.size, hexPayload))
        when (cmdId) {
            QcyProtocol.CMD_BATTERY_QUERY -> {
                val batt = batteryTracker.onGattRaw(payload)
                publishBattery(batt)
                AegisLogger.i(TAG, "Hardware ACK Battery (0x2F): L=${batt.leftLevel}% R=${batt.rightLevel}% Case=${batt.caseLevel}%")
            }
            QcyProtocol.CMD_ANC_SETTING -> {
                if (payload.isNotEmpty()) {
                    val modeId = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0
                    val subSceneId = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
                    val rawLevel = payload.getOrNull(2)?.toInt()?.and(0xFF) ?: 2

                    // Official ANCSettingDataBean: 1 = ANC, 2 = OFF (Normal), 3 = Transparency
                    val ancMode = when (modeId) {
                        1 -> AncMode.ANC
                        2 -> AncMode.OFF
                        3 -> AncMode.TRANSPARENCY
                        else -> _deviceState.value.ancMode
                    }
                    val subScene = when {
                        ancMode == AncMode.ANC && (subSceneId == 5 || subSceneId == 0) -> AncSubScene.ADAPTIVE
                        ancMode == AncMode.ANC -> AncSubScene.values().find { it.id == subSceneId } ?: AncSubScene.ADAPTIVE
                        else -> _deviceState.value.ancSubScene
                    }
                    // For ANC subScenes 1..3 (Indoor, Commuting, Noisy), hardware level 0..2 maps to UI 1..3
                    val uiAncLevel = if (ancMode == AncMode.ANC && subSceneId in 1..3) {
                        (rawLevel + 1).coerceIn(1, 3)
                    } else {
                        _deviceState.value.ancLevel
                    }

                    // Parse sonrası, state'e yazmadan önce: gönderilen komutla eşleşmeyen
                    // (eski değerli) geç yankı optimistic UI state'i ezmesin.
                    val rawParams = intArrayOf(modeId, subSceneId, rawLevel)
                    if (!ancEchoGuard.shouldApply(rawParams, System.currentTimeMillis())) {
                        AegisLogger.d(TAG, "stale ANC echo ignored: mode=$modeId, subScene=$subSceneId, rawLevel=$rawLevel")
                        return
                    }

                    _deviceState.update {
                        it.copy(
                            ancMode = ancMode,
                            ancSubScene = subScene,
                            ancLevel = uiAncLevel,
                            transparencyLevel = if (ancMode == AncMode.TRANSPARENCY && rawLevel in 1..6) rawLevel else it.transparencyLevel,
                            isAiAdaptiveAncOn = (ancMode == AncMode.ANC && subScene == AncSubScene.ADAPTIVE)
                        )
                    }
                    AegisLogger.i(TAG, "Hardware ACK ANC (0x17): mode=$ancMode, subScene=$subScene, rawLevel=$rawLevel, uiAncLevel=$uiAncLevel")
                }
            }
            QcyProtocol.CMD_ANC_LEGACY -> {
                if (payload.isNotEmpty()) {
                    val modeId = payload[0].toInt() and 0xFF
                    val ancMode = when (modeId) {
                        1 -> AncMode.ANC
                        2 -> AncMode.TRANSPARENCY
                        else -> AncMode.OFF
                    }
                    _deviceState.update { it.copy(ancMode = ancMode) }
                }
            }
            QcyProtocol.CMD_ADAPTIVE_AI_ANC -> {
                if (payload.isNotEmpty()) {
                    val isOn = (payload[0].toInt() and 0xFF) == 0
                    _deviceState.update { it.copy(isAiAdaptiveAncOn = isOn) }
                    AegisLogger.i(TAG, "Hardware ACK AI Adaptive ANC: $isOn")
                }
            }
            QcyProtocol.CMD_GAME_MODE -> {
                val isOn = (payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0) == 1
                _deviceState.update { it.copy(isGameModeOn = isOn) }
                AegisLogger.i(TAG, "Hardware ACK Game Mode: $isOn")
            }
            QcyProtocol.CMD_LDAC_SWITCH -> {
                val isOn = (payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0) == 1
                _deviceState.update { it.copy(isLdacOn = isOn) }
                AegisLogger.i(TAG, "Hardware ACK LDAC: $isOn")
            }
            QcyProtocol.CMD_IN_EAR_DETECTION, QcyProtocol.CMD_PEIDAI -> {
                val isOn = (payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0) == 1
                _deviceState.update { it.copy(isInEarDetectionOn = isOn) }
                AegisLogger.i(TAG, "Hardware ACK In-Ear Detection (0x%02X): $isOn".format(cmdId))
            }
            QcyProtocol.CMD_SLEEP_MODE -> {
                val isOn = (payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0) == 1
                _deviceState.update { it.copy(isSleepModeOn = isOn) }
                AegisLogger.i(TAG, "Hardware ACK Sleep Mode: $isOn")
            }
            QcyProtocol.CMD_MULTIPOINT_CONTROL -> {
                val isOn = (payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0) == 1
                _deviceState.update { it.copy(isMultipointOn = isOn) }
                AegisLogger.i(TAG, "Hardware ACK Multipoint Switch: $isOn")
            }
            QcyProtocol.CMD_CHANNEL_BALANCE -> {
                val rawBal = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 50
                val uiBal = if (rawBal in 0..100) (rawBal / 5).coerceIn(0, 20) else 10
                _deviceState.update { it.copy(channelBalance = uiBal) }
                AegisLogger.i(TAG, "Hardware ACK Channel Balance: raw=$rawBal -> ui=$uiBal")
            }
            QcyProtocol.CMD_TONE_VOLUME -> {
                val vol = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 8
                if (vol in 0..15) {
                    _deviceState.update { it.copy(promptVolume = vol) }
                    val source = VoiceSettings.getVoiceSource(appContext)
                    if (source == VoiceSource.FIRMWARE_EN) {
                        if (vol in 1..15) {
                            VoiceSettings.setSavedFirmwarePromptLevel(appContext, vol)
                        }
                    } else {
                        enforcePromptMutePolicy(vol)
                    }
                }
            }
            QcyProtocol.CMD_ANC_RESULT -> {
                val res = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0
                AegisLogger.d(TAG, "Hardware ACK ANC Result (0x28): raw=$res")
                scheduleStateVerification(QcyProtocol.CMD_ANC_SETTING, delayMs = 250L)
            }
            QcyProtocol.CMD_MULTI_EQ_2 -> {
                // Parse 143-byte Little-Endian EQParamListBean (saveIndex + masterGain(2) + 20 * 7-byte bands)
                if (payload.size >= 3 + 10 * 7) {
                    val saveIdx = payload[0].toInt() and 0xFF
                    val firstFreq = (payload[3].toInt() and 0xFF) or ((payload[4].toInt() and 0xFF) shl 8)
                    // On MeloBuds Pro (19786), slots 0..5 are the 6 sys_eq baseline filters starting at 30Hz,
                    // and slots 6..15 hold the 10 user EQ bands (31..16000Hz)!
                    val startSlot = if (firstFreq == 30 && payload.size >= 3 + 16 * 7) 6 else 0
                    val parsedGains = (0 until 10).map { bandIdx ->
                        val base = 3 + (startSlot + bandIdx) * 7
                        val gLo = payload[base + 2].toInt() and 0xFF
                        val gHi = payload[base + 3].toInt() // signed high byte
                        val gainInt = (gHi shl 8) or gLo
                        (gainInt / 100f).coerceIn(-8f, 8f)
                    }
                    val presetId = when (saveIdx) {
                        0x81, 1 -> "flat"
                        0x82, 2 -> "pop"
                        0x83, 3 -> "bass_boost"
                        0x84, 4 -> "rock"
                        0x85, 5 -> "vocal"
                        0x86, 6 -> "electronic"
                        0x87, 7 -> "treble"
                        0x80, 0xFF -> "custom"
                        else -> _deviceState.value.eqPresetId
                    }
                    val displayGains = if (saveIdx in 1..6 && parsedGains.all { it == 0f }) {
                        com.aegissoft.qcybutmod.core.audio.ParametricEq.PRESETS.find { it.id == presetId }?.gains ?: parsedGains
                    } else {
                        parsedGains
                    }
                    _deviceState.update {
                        it.copy(
                            eqPresetId = presetId,
                            eqGains = displayGains
                        )
                    }
                    AegisLogger.i(TAG, "Hardware ACK Multi-EQ2 (0x22): saveIdx=0x%02X, preset=$presetId, gains=$displayGains".format(saveIdx))
                }
            }
            QcyProtocol.CMD_KEY_FUNCTIONS -> {
                if (payload.isNotEmpty()) {
                    val map = mutableMapOf<Int, Int>()
                    var i = 0
                    while (i + 1 < payload.size) {
                        val keyId = payload[i].toInt() and 0xFF
                        val funcId = payload[i + 1].toInt() and 0xFF
                        map[keyId] = if (funcId == 0xFF) 0 else funcId
                        i += 2
                    }
                    if (map.isNotEmpty()) {
                        _deviceState.update { current ->
                            current.copy(keyMappings = current.keyMappings + map)
                        }
                        AegisLogger.i(TAG, "Hardware ACK Touch Gestures (0x2B): $map")
                    }
                }
            }
            QcyProtocol.CMD_FIT_TEST, QcyProtocol.CMD_WEAR_RESULT -> {
                val result = QcyProtocol.parseFitTestResult(payload)
                _fitTestResults.tryEmit(result)
                AegisLogger.i(TAG, "Hardware ACK Fit Test (0x%02X): left=${result.leftEarGoodSeal}, right=${result.rightEarGoodSeal}, completed=${result.isCompleted}".format(cmdId))
            }
            QcyProtocol.CMD_MULTIPOINT_QUERY -> {
                // null → yanıt yok/geçersiz, mevcut liste korunur; boş liste → state temizlenir
                val phones = QcyProtocol.parsePhoneListResponse(payload)
                if (phones != null) {
                    _deviceState.update { it.copy(pairedPhones = phones) }
                    AegisLogger.i(TAG, "Hardware ACK Multipoint Paired Phones: ${phones.size}")
                }
            }
            QcyProtocol.CMD_PHONE_STATUS -> {
                val statusPair = QcyProtocol.parsePhoneStatus(payload)
                if (statusPair != null) {
                    val (mac, active) = statusPair
                    _deviceState.update { state ->
                        state.copy(
                            pairedPhones = state.pairedPhones.map { p ->
                                if (p.mac.equals(mac, ignoreCase = true)) p.copy(isAudioActive = active, isConnected = true)
                                else p.copy(isAudioActive = if (active) false else p.isAudioActive)
                            }
                        )
                    }
                    AegisLogger.i(TAG, "Hardware ACK Phone Audio Active: $mac -> $active")
                }
            }
            QcyProtocol.CMD_FIRMWARE_VERSION -> {
                val fwStr = if (payload.size >= 3 && payload.all { (it.toInt() and 0xFF) < 32 }) {
                    "v${payload[0].toInt() and 0xFF}.${payload[1].toInt() and 0xFF}.${payload[2].toInt() and 0xFF}"
                } else {
                    String(payload, Charsets.UTF_8).trim().trimEnd('\u0000')
                }
                if (fwStr.isNotEmpty()) {
                    _deviceState.update { it.copy(firmwareVersion = fwStr) }
                }
            }
            QcyProtocol.CMD_REQUEST_ALL_DATA -> {
                if (payload.size >= 8) {
                    val game = payload.getOrNull(1)?.toInt() == 1
                    val inEar = payload.getOrNull(2)?.toInt() == 1
                    val bal = payload.getOrNull(3)?.toInt() ?: 10
                    val vol = payload.getOrNull(4)?.toInt() ?: 8
                    val promptVol = vol.coerceIn(0, 15)
                    _deviceState.update {
                        it.copy(
                            isGameModeOn = game,
                            isInEarDetectionOn = inEar,
                            channelBalance = bal.coerceIn(0, 20),
                            promptVolume = promptVol
                        )
                    }
                    // Connect-time bulk replies report prompt volume > 0 (e.g. FF 04 1D 02 06 0F)
                    // which re-armed the firmware's English prompts - re-mute here too.
                    enforcePromptMutePolicy(promptVol)
                }
            }
        }
    }

    /**
     * Firmware prompt ses seviyesi sıfırdan farklı raporlandığında (0x1D tekil yanıt ve
     * 0xFE toplu yanıt) kaynak modu "firmware_en" değilse ToneVol 0 göndererek yeniden
     * susturur; 2 sn'lik debounce yankı fırtınasını engeller.
     */
    private fun enforcePromptMutePolicy(vol: Int) {
        val source = VoiceSettings.getVoiceSource(appContext)
        val now = System.currentTimeMillis()
        if (PromptMutePolicy.shouldRemute(source.id, vol, now, lastPromptRemuteTimestamp, 2000L)) {
            // Firmware reset its prompt volume mid-session (e.g. after a
            // self-restart triggered by LDAC / in-ear toggles) - re-mute so only
            // the Turkish assistant speaks.
            lastPromptRemuteTimestamp = now
            sendCommand(QcyProtocol.createToneVolumeCommand(0))
            _deviceState.update { it.copy(promptVolume = 0) }
            AegisLogger.w(TAG, "Firmware prompt volume resurrected to $vol - re-muting for $source")
        }
    }

    // --- Command Dispatch API ---

    fun sendCommand(data: ByteArray) {
        val hex = data.joinToString(" ") { "%02X".format(it) }
        AegisLogger.d(TAG, "TX CMD: $hex")

        val targetChar = commandTxChar ?: allTxChars.firstOrNull {
            val u = it.uuid.toString().lowercase()
            u.contains("1001") || u.contains("7777")
        } ?: allTxChars.firstOrNull()

        if (targetChar != null) {
            // Prefer WRITE_TYPE_DEFAULT per official QCY BLECMDWriteRequest so every write receives GATT ACK
            val writeType = if ((targetChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            opQueue.trySend(GattOp.WriteChar(targetChar, data, writeType))
        } else {
            AegisLogger.w(TAG, "No valid TX characteristic available for command: $hex")
        }
    }

    /** 0x17 frame'ini göndermeden önce ham 3 paramını (mode, subScene, byte2) yankı korumasına kaydeder. */
    private fun sendAncCommand(frame: ByteArray) {
        // packFrame düzeni: [0xFF, len, cmdId, paramLen, mode, subScene, byte2]
        ancEchoGuard.onSent(
            intArrayOf(frame[4].toInt() and 0xFF, frame[5].toInt() and 0xFF, frame[6].toInt() and 0xFF),
            System.currentTimeMillis()
        )
        sendCommand(frame)
    }

    fun setAnc(
        mode: AncMode,
        subScene: AncSubScene = _deviceState.value.ancSubScene,
        level: Int = _deviceState.value.ancLevel,
        transLevel: Int = _deviceState.value.transparencyLevel
    ) {
        val safeLevel = level.coerceIn(1, 3)
        _deviceState.update {
            it.copy(
                ancMode = mode,
                ancSubScene = subScene,
                ancLevel = safeLevel,
                transparencyLevel = transLevel,
                isAiAdaptiveAncOn = (mode == AncMode.ANC && subScene == AncSubScene.ADAPTIVE)
            )
        }
        // Send official 0x17 ANCSetting command (do NOT send legacy 0x0C which overrides V2 ANC mode!)
        sendAncCommand(QcyProtocol.createAncCommand(mode, subScene, safeLevel, transLevel))
        // Query back 0x17 state after 650ms to confirm hardware execution without reading pre-transition state
        scheduleStateVerification(QcyProtocol.CMD_ANC_SETTING)

        val voiceMsg = when (mode) {
            AncMode.ANC -> if (subScene == AncSubScene.ADAPTIVE || subScene == AncSubScene.ANTI_WIND) {
                "Gürültü engelleme, ${subScene.title}"
            } else {
                "Gürültü engelleme, ${subScene.title}, seviye $safeLevel"
            }
            AncMode.TRANSPARENCY -> "Şeffaf mod, seviye $transLevel"
            AncMode.OFF -> "Normal mod, gürültü kontrolü kapalı"
        }
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext).announceAnc(voiceMsg)
    }

    fun setAdaptiveAiAnc(enabled: Boolean) {
        val targetScene = if (enabled) AncSubScene.ADAPTIVE else AncSubScene.NOISY
        _deviceState.update {
            it.copy(
                isAiAdaptiveAncOn = enabled,
                ancMode = AncMode.ANC,
                ancSubScene = targetScene
            )
        }
        // On MeloBuds Pro (19786), Adaptive ANC is startcmdid=66816 (0x010500 -> FF 05 17 03 01 05 00)
        sendAncCommand(QcyProtocol.createAncCommand(AncMode.ANC, targetScene, _deviceState.value.ancLevel, _deviceState.value.transparencyLevel))
        scheduleStateVerification(QcyProtocol.CMD_ANC_SETTING)
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .announceFeature("Adaptif yapay zeka gürültü engelleme", enabled)
    }

    fun setGameMode(enabled: Boolean) {
        _deviceState.update { it.copy(isGameModeOn = enabled) }
        sendCommand(QcyProtocol.createGameModeCommand(enabled))
        scheduleStateVerification(QcyProtocol.CMD_GAME_MODE)
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .announceFeature("Oyun modu", enabled)
    }

    fun setLdac(enabled: Boolean) {
        _deviceState.update { it.copy(isLdacOn = enabled) }
        sendCommand(QcyProtocol.createLdacCommand(enabled))
        scheduleStateVerification(QcyProtocol.CMD_LDAC_SWITCH)
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .announceFeature("LDAC yüksek çözünürlüklü ses", enabled)
    }

    fun setInEarDetection(enabled: Boolean) {
        _deviceState.update { it.copy(isInEarDetectionOn = enabled) }
        // Send both V2 Peidai (0x2C / 44) and V1 Ruer (0x06) commands so all QCY firmware versions respond
        sendCommand(QcyProtocol.createPeidaiCommand(enabled))
        sendCommand(QcyProtocol.createInEarDetectionCommand(enabled))
        scheduleStateVerification(QcyProtocol.CMD_PEIDAI, QcyProtocol.CMD_IN_EAR_DETECTION)
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .announceFeature("Kulak algılama", enabled)
    }

    fun setSleepMode(enabled: Boolean) {
        _deviceState.update { it.copy(isSleepModeOn = enabled) }
        sendCommand(QcyProtocol.createSleepModeCommand(enabled))
        scheduleStateVerification(QcyProtocol.CMD_SLEEP_MODE)
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .announceFeature("Uyku modu", enabled)
    }

    fun setMultipoint(enabled: Boolean) {
        _deviceState.update { it.copy(isMultipointOn = enabled) }
        sendCommand(QcyProtocol.createMultipointCommand(enabled))
        scheduleStateVerification(QcyProtocol.CMD_MULTIPOINT_CONTROL)
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .announceFeature("Çift cihaz bağlantısı", enabled)
    }

    fun refreshMultipointDevices() {
        // Resmi app requestPhoneInfo: FE+0x24 (multipoint durumu) VE FE+0x4D (liste) birlikte sorgulanır
        sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_MULTIPOINT_CONTROL))
        sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_MULTIPOINT_QUERY))
        sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_PHONE_STATUS))
        // Canlı logta cihaz 0x4D'ye tek seferde cevap vermeyebiliyor → tek retry
        coroutineScope.launch {
            delay(700)
            if (_deviceState.value.isConnected) {
                sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_MULTIPOINT_QUERY))
            }
        }
    }

    fun setPhoneBtAction(connectPhone: Boolean, phoneMac: String, phoneName: String = "") {
        val cmd = QcyProtocol.createPhoneBtActionCommand(if (connectPhone) 1 else 0, phoneMac)
        if (cmd != null) {
            sendCommand(cmd)
            refreshMultipointDevices()
            val actionText = if (connectPhone) "bağlanıyor" else "bağlantısı kesildi"
            com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
                .speak("${phoneName.ifEmpty { "İkincil cihaz" }} $actionText")
        }
    }

    fun setChannelBalance(balance: Int) {
        _deviceState.update { it.copy(channelBalance = balance) }
        sendCommand(QcyProtocol.createChannelBalanceCommand(balance))
    }

    fun setPromptVolume(volume: Int) {
        // Firmware tone must stay at 0 unless the user explicitly chose the English
        // firmware voice - the slider is disabled otherwise, this is the hard guard.
        if (VoiceSettings.getVoiceSource(appContext) != VoiceSource.FIRMWARE_EN) {
            AegisLogger.w(TAG, "Ignoring prompt volume change to $volume - firmware voice is muted by policy")
            return
        }
        val safe = volume.coerceIn(0, 15)
        _deviceState.update { it.copy(promptVolume = safe) }
        sendCommand(QcyProtocol.createToneVolumeCommand(safe))
        if (safe > 0) {
            VoiceSettings.setSavedFirmwarePromptLevel(appContext, safe)
        }
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .speak("İstem sesi seviye $safe")
    }

    fun applyVoiceSource(source: VoiceSource) {
        VoiceSettings.setVoiceSource(appContext, source)
        when (source) {
            VoiceSource.AEGIS_TR, VoiceSource.OFF -> {
                val currentFw = _deviceState.value.promptVolume
                if (currentFw > 0) {
                    VoiceSettings.setSavedFirmwarePromptLevel(appContext, currentFw)
                }
                sendCommand(QcyProtocol.createToneVolumeCommand(0))
                _deviceState.update { it.copy(promptVolume = 0) }
                AegisLogger.i(TAG, "Muted firmware voice prompt to 0 for $source")
            }
            VoiceSource.FIRMWARE_EN -> {
                val savedLevel = VoiceSettings.getSavedFirmwarePromptLevel(appContext)
                sendCommand(QcyProtocol.createToneVolumeCommand(savedLevel))
                _deviceState.update { it.copy(promptVolume = savedLevel) }
                AegisLogger.i(TAG, "Restored firmware voice prompt to $savedLevel for FIRMWARE_EN")
            }
        }
        scheduleStateVerification(QcyProtocol.CMD_TONE_VOLUME)
    }

    fun triggerFindBeep(left: Boolean, right: Boolean, sendFirmware: Boolean = true) {
        val start = left || right
        if (start) {
            com.aegissoft.qcybutmod.core.audio.EarbudAudioLocator.startBeep(left, right, appContext)
        } else {
            com.aegissoft.qcybutmod.core.audio.EarbudAudioLocator.stopBeep()
        }
        // sendFirmware=false: hasFind'siz cihaza 0x05/0x3F gönderilmez, yalnız yerel zil çalışır
        if (sendFirmware) {
            sendCommand(QcyProtocol.createFindDeviceCommand(start))
            sendCommand(QcyProtocol.packFrame(QcyProtocol.CMD_FIND_DEVICE_ALT, if (start) 0x01 else 0x00))
        }
    }

    fun startFitTest() {
        sendCommand(QcyProtocol.createFitTestCommand(true))
    }

    fun stopFitTest() {
        sendCommand(QcyProtocol.createFitTestCommand(false))
    }

    fun setTouchGesture(keyId: Int, functionId: Int, earbudName: String = "", gestureName: String = "") {
        _deviceState.update { current ->
            val newMap = current.keyMappings.toMutableMap()
            newMap[keyId] = functionId
            current.copy(keyMappings = newMap)
        }

        // Official QCYHeadsetClient.setButtonFunction writes [keyId, funcId] to 0000000D on Service 0000A001
        val ch = keyMappingChar
        if (ch != null) {
            val writeType = if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            val pairBytes = QcyProtocol.createKeyMappingPayload(keyId, functionId)
            AegisLogger.i(TAG, "Writing KeyMapping to 0000000D: keyId=$keyId, funcId=$functionId")
            opQueue.trySend(GattOp.WriteChar(ch, pairBytes, writeType))
            // Read back 0000000D after 350ms to verify per QCYConnectManager.readButtonFunction()
            coroutineScope.launch {
                delay(350)
                if (_deviceState.value.isConnected) {
                    opQueue.trySend(GattOp.ReadChar(ch))
                }
            }
        } else {
            // Fallback to framed 0x2B command if 0000000D is not exposed
            sendCommand(QcyProtocol.createKeyMappingCommand(keyId, functionId))
            scheduleStateVerification(QcyProtocol.CMD_KEY_FUNCTIONS)
        }

        val funcName = GestureFunctions.functionList.find { it.first == functionId }?.second ?: "Yeni işlev"
        if (earbudName.isNotEmpty() && gestureName.isNotEmpty()) {
            com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
                .announceKeyAction(earbudName, gestureName, funcName)
        } else {
            com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
                .speak("Dokunmatik jest ayarlandı: $funcName")
        }
    }

    fun applyEqualizer(
        presetId: String,
        leftBands: List<EqBand>,
        rightBands: List<EqBand>,
        stereoLinked: Boolean
    ) {
        val eqType = when (presetId) {
            "flat" -> 1
            "pop" -> 2
            "bass_boost" -> 3
            "rock" -> 4
            "vocal" -> 5
            "electronic" -> 6
            else -> 128 // Custom 10-band EQ
        }
        // Per QCYConnectManager.setEQData: system presets use saveIndex = 1..6; custom EQ uses saveIndex = 128 (0x80)
        val saveIndex = if (eqType in 1..6) eqType else 128
        val leftBytes = ParametricEq.serializeGainsToBytes(leftBands)

        _deviceState.update {
            it.copy(
                eqPresetId = presetId,
                eqGains = leftBands.map { b -> b.gainDb }
            )
        }

        // 1. Send Little-Endian parametric Multi-EQ2 (0x22) frame with 6 sys_eq baseline filters + 10 user bands to 00001001
        sendCommand(QcyProtocol.createMultiEqCommand(QcyProtocol.CMD_MULTI_EQ_2, saveIndex, 0f, leftBands, includeEqType = true, eqType = eqType))

        // 2. If independent L/R mode is selected, also dispatch 0x46 / 0x47
        if (!stereoLinked) {
            sendCommand(QcyProtocol.createMultiEqCommand(QcyProtocol.CMD_LEFT_CHANNEL_EQ, saveIndex, 0f, leftBands, includeEqType = true, eqType = eqType))
            sendCommand(QcyProtocol.createMultiEqCommand(QcyProtocol.CMD_RIGHT_CHANNEL_EQ, saveIndex, 0f, rightBands, includeEqType = true, eqType = eqType))
        }

        // 3. Also write 11-byte [eqType, g0..g9] to 0000000B for V1 compatibility
        eqChar?.let { ch ->
            val writeType = if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            val eqPayload = QcyProtocol.createEqCharacteristicPayload(if (eqType in 1..6) eqType else 10, leftBytes)
            opQueue.trySend(GattOp.WriteChar(ch, eqPayload, writeType))
        }

        scheduleStateVerification(QcyProtocol.CMD_MULTI_EQ_2)
    }

    fun sendParametricEqDirect(eqPayload: ByteArray) {
        val ch = eqChar
        if (ch != null) {
            val payloadWithHeader = if (eqPayload.size == 10) {
                QcyProtocol.createEqCharacteristicPayload(10, eqPayload)
            } else eqPayload
            opQueue.trySend(GattOp.WriteChar(ch, payloadWithHeader, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        }
    }

    fun sendChannelEq(isLeft: Boolean, gains: ByteArray) {
        val cmdId = if (isLeft) QcyProtocol.CMD_LEFT_CHANNEL_EQ else QcyProtocol.CMD_RIGHT_CHANNEL_EQ
        val packet = QcyProtocol.packFrame(cmdId, *gains)
        sendCommand(packet)
    }

    fun factoryReset() {
        sendCommand(QcyProtocol.createFactoryResetCommand())
        sendCommand(QcyProtocol.createResetDefaultCommand())
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .speak("Kulaklık fabrika ayarlarına sıfırlandı")
    }

    fun renameDevice(newName: String) {
        if (newName.isBlank()) return
        _deviceState.update { it.copy(modelName = newName) }
        sendCommand(QcyProtocol.createDeviceRenameCommand(newName))
        // Also rename at Android system Bluetooth bond level via setAlias reflection
        val classicMac = _deviceState.value.classicMac.ifEmpty { _deviceState.value.bleAddress }
        if (classicMac.isNotEmpty()) {
            try {
                val dev = bluetoothAdapter?.getRemoteDevice(classicMac)
                val setAliasMethod = dev?.javaClass?.getMethod("setAlias", String::class.java)
                setAliasMethod?.invoke(dev, newName)
            } catch (e: Exception) {
                Log.d(TAG, "setAlias reflection not supported: ${e.message}")
            }
        }
        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(appContext)
            .speak("Kulaklık adı $newName olarak güncellendi")
    }

    // --- Official QCY App Conflict Helpers & Device Unpair ---

    fun isOfficialQcyAppInstalled(): Boolean {
        val officialPackages = listOf("com.qcymall.googleearphonesetup", "com.qcymall.earphone", "com.qcymall.qcy")
        val pm = appContext.packageManager
        for (pkg in officialPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    fun openOfficialAppSettings(context: Context) {
        val officialPackages = listOf("com.qcymall.googleearphonesetup", "com.qcymall.earphone", "com.qcymall.qcy")
        val pm = context.packageManager
        for (pkg in officialPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", pkg, null)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {}
        }
    }

    fun unpairDevice(address: String): Boolean {
        return try {
            val dev = bluetoothAdapter?.getRemoteDevice(address) ?: return false
            disconnect()
            val method = dev.javaClass.getMethod("removeBond")
            val removed = (method.invoke(dev) as? Boolean) ?: false
            refreshBondedDevices(autoConnect = false)
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Error unpairing device $address", e)
            false
        }
    }
}
