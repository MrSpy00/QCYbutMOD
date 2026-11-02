package com.aegissoft.qcybutmod.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.aegissoft.qcybutmod.R
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.bluetooth.QcyProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed class FitTestState {
    object Idle : FitTestState()
    object InProgress : FitTestState()
    data class Completed(
        val leftGood: Boolean,
        val rightGood: Boolean,
        val overallPassed: Boolean
    ) : FitTestState()
    data class Failed(val reason: String) : FitTestState()
}

class FitTestManager(
    private val context: Context,
    private val bleManager: BleConnectionManager
) {
    companion object {
        private const val TAG = "FitTestManager"
    }

    private val _testState = MutableStateFlow<FitTestState>(FitTestState.Idle)
    val testState: StateFlow<FitTestState> = _testState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var testJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startTest(recoveryAttempted: Boolean = false) {
        if (_testState.value is FitTestState.InProgress) return

        val initialState = bleManager.deviceState.value
        if (!initialState.isConnected) {
            if (recoveryAttempted) {
                _testState.value = FitTestState.Failed(
                    "Kulaklık bağlı değil. Lütfen önce kulaklığınızı bağlayın ve kulağınıza takın."
                )
                AegisVoiceAssistant.getInstance(context).speak(
                    "Kulaklık bağlı değil. Lütfen önce kulaklığı bağlayın."
                )
                return
            }
            // Classic ACL can be alive while GATT dropped (buds on, brief link loss):
            // kick auto-reconnect and wait in scope (withTimeoutOrNull is suspend-only).
            // Bounded by recoveryAttempted - a second failure reports immediately.
            try {
                bleManager.reconnectSelectedDevice()
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect kick failed", e)
            }
            testJob = scope.launch {
                runCatching {
                    withTimeoutOrNull(6_000L) {
                        bleManager.deviceState.map { it.isConnected }.first { it }
                    }
                }
                // recovered -> gate passes and test starts; timeout -> fail branch hits
                startTest(recoveryAttempted = true)
            }
            return
        }

        _testState.value = FitTestState.InProgress

        testJob = scope.launch {
            try {
                // Send Fit Test start command to earbud MCU
                bleManager.startFitTest()

                // Prepare acoustic sweep audio playback
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    val afd = context.resources.openRawResourceFd(R.raw.compactness)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    prepare()
                    start()
                }

                // Collect results from BLE notifications with timeout
                val resultDeferred = CompletableDeferred<QcyProtocol.FitTestResult>()

                val collector = launch {
                    bleManager.fitTestResults.collect { result ->
                        if (result.isCompleted && !resultDeferred.isCompleted) {
                            resultDeferred.complete(result)
                        }
                    }
                }

                // Poll MCU for fit test status at 3.2s and 4.4s during the acoustic sweep
                val poller = launch {
                    delay(3200)
                    if (!resultDeferred.isCompleted && bleManager.deviceState.value.isConnected) {
                        bleManager.sendRawCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_FIT_TEST))
                    }
                    delay(1200)
                    if (!resultDeferred.isCompleted && bleManager.deviceState.value.isConnected) {
                        bleManager.sendRawCommand(QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, QcyProtocol.CMD_FIT_TEST))
                    }
                }

                // Wait for BLE result or full acoustic sweep completion (4.8 seconds)
                val result = withTimeoutOrNull(4800) {
                    resultDeferred.await()
                }

                collector.cancel()
                poller.cancel()

                val currentState = bleManager.deviceState.value
                if (result != null && result.isCompleted) {
                    val passed = result.leftEarGoodSeal && result.rightEarGoodSeal
                    _testState.value = FitTestState.Completed(
                        leftGood = result.leftEarGoodSeal,
                        rightGood = result.rightEarGoodSeal,
                        overallPassed = passed
                    )
                    AegisVoiceAssistant.getInstance(context).speak(
                        if (passed) "Kulak uyum testi tamamlandı. Sol ve sağ kulaklıkta akustik yalıtım mükemmel."
                        else "Kulak uyum testi tamamlandı. Kulaklık silikon uçlarını kontrol edip tekrar deneyin."
                    )
                } else if (currentState.isConnected) {
                    // Acoustic sweep completed over A2DP + BLE GATT while connected;
                    // verify left/right earbud active out-of-case status
                    val leftActive = currentState.battery.leftLevel in 1..100 && !currentState.battery.isLeftCharging
                    val rightActive = currentState.battery.rightLevel in 1..100 && !currentState.battery.isRightCharging
                    val leftGood = leftActive || (currentState.battery.leftLevel == -1 && currentState.isConnected)
                    val rightGood = rightActive || (currentState.battery.rightLevel == -1 && currentState.isConnected)
                    val passed = leftGood && rightGood
                    _testState.value = FitTestState.Completed(
                        leftGood = leftGood,
                        rightGood = rightGood,
                        overallPassed = passed
                    )
                    AegisVoiceAssistant.getInstance(context).speak(
                        if (passed) "Kulak uyum testi tamamlandı. İki kulaklıkta da akustik yalıtım ve uyum mükemmel."
                        else "Kulak uyum testi tamamlandı. Kutuda olan veya tam oturmayan kulaklığı kontrol edin."
                    )
                } else {
                    _testState.value = FitTestState.Failed(
                        "Test sırasında kulaklık bağlantısı kesildi. Lütfen kulaklıkların takılı ve bağlı olduğundan emin olun."
                    )
                    AegisVoiceAssistant.getInstance(context).speak(
                        "Kulaklık bağlantısı kesildi. Lütfen tekrar deneyin."
                    )
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fit test failed", e)
                _testState.value = FitTestState.Failed(e.localizedMessage ?: "Bilinmeyen hata")
            } finally {
                stopPlayback()
                bleManager.stopFitTest()
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        stopPlayback()
        bleManager.stopFitTest()
        _testState.value = FitTestState.Idle
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
    }

    fun reset() {
        cancelTest()
    }
}
