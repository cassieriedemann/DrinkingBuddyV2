package com.kyleriedemann.drinkingbuddy.ui.home

import androidx.lifecycle.*
import com.kyleriedemann.drinkingbuddy.data.models.Notification
import com.kyleriedemann.drinkingbuddy.data.models.Reading
import com.kyleriedemann.drinkingbuddy.data.source.NotificationRepository
import com.kyleriedemann.drinkingbuddy.data.source.ReadingRepository
import com.kyleriedemann.drinkingbuddy.di.ViewModelAssistedFactory
import com.kyleriedemann.drinkingbuddy.sdk.*
import com.snakydesign.livedataextensions.map
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class HomeViewModel @AssistedInject constructor (
    @Assisted private val handle: SavedStateHandle,
    private val sdk: BacTrackSdk,
    private val readingRepository: ReadingRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    var prediction: Float = 0.0f

    fun showPrediction() = _text.postValue("Prediction: $prediction")

    val connected = sdk.connectedEvents.map {
        when (it) {
            is ConnectedEvents.Connected -> saveDeviceConnected(it.deviceType)
            is ConnectedEvents.FoundDevice -> saveDeviceFound(it.device)
            is ConnectedEvents.DidConnect -> saveDidConnect(it.message)
        }
        it
    }
    val error = sdk.errorEvents
    val reading = sdk.readingEvents.map {
        if (it is ReadingEvents.Result) {
            saveReading(it)
        }
        it
    }

    private fun saveReading(readingResult: ReadingEvents.Result) = viewModelScope.launch {
        readingRepository.insertReading(Reading(result = readingResult.reading, prediction = prediction))
    }

    private fun saveDeviceConnected(deviceType: DeviceType) = viewModelScope.launch {
        notificationRepository.insertNotification(Notification("Device Connected", "$deviceType"))
    }

    private fun saveDeviceFound(device: Device) = viewModelScope.launch {
        notificationRepository.insertNotification(Notification("Device Found", "$device"))
    }

    private fun saveDidConnect(message: String) = viewModelScope.launch {
        notificationRepository.insertNotification(Notification("Device Did Connect", message))
    }

    fun permissionsGranted() {
        sdk.start()
    }

    fun errorChan() = viewModelScope.launch {
        val sub = sdk.errorChannel.openSubscription()
        val error = sub.receive()
    }

    fun connectToClosestDevice() {
        _text.postValue("Connecting to device")
        sdk.connectToClosestDevice()
    }

    fun takeReading() {
        _text.postValue("Taking reading")
        sdk.takeReading()
    }

    fun connectToClosestDeviceAsync() = viewModelScope.launch {
        try {
            _text.postValue("Connecting to device...")
            val deviceType = sdk.connectToClosestDeviceAsync()
            _text.postValue("Connected to device type $deviceType")
            notificationRepository.insertNotification(Notification("Device Connected", "$deviceType"))
        } catch (e: Exception) {
            displayException(e)
        }
    }

    fun getDeviceFirmware() = viewModelScope.launch {
        try {
            _text.postValue("Reading firmware...")
            val firmwareVersion = sdk.getFirmwareVersionAsync()
            _text.postValue("Firmware: $firmwareVersion")
            notificationRepository.insertNotification(Notification("Firmware Version", firmwareVersion))
        } catch (e: Exception) {
            displayException(e)
        }
    }

    fun getSerialNumber() = viewModelScope.launch {
        try {
            _text.postValue("Reading serial number...")
            val serialNumber = sdk.getSerialNumberAsync()
            _text.postValue("Serial: $serialNumber")
            notificationRepository.insertNotification(Notification("Serial Number", serialNumber))
        } catch (e: Exception) {
            displayException(e)
        }
    }

    fun takeReadingAsync() = viewModelScope.launch {
        sdk.readingFlow().collect {
            _text.postValue(it)
            if (it.contains("Result")) {
                return@collect
            }
        }
    }

    private fun displayException(e: Exception) {
        _text.postValue(e.message)
    }

    fun sendWelcomeNotification() = viewModelScope.launch {
        notificationRepository.insertNotification(Notification(title = "Welcome!", message = "Connect a device to start taking readings."))
    }

    @AssistedInject.Factory
    interface Factory : ViewModelAssistedFactory<HomeViewModel>
}
