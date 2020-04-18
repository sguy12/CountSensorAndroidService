/*
 * Created by Asaf Pinhassi on 19/04/2020.
 */
package com.terabee.sdkdemo.logic

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.widget.Switch
import androidx.annotation.Keep
import com.terabee.sdk.TerabeeSdk
import com.terabee.sdk.TerabeeSdk.MultiflexConfiguration
import com.terabee.sdkdemo.logic.DataCollector.start
import java.lang.ref.WeakReference
import java.util.*

/**
 * Collects data from the sensor, location service and AQ API, process it and stores it in the [Repository]
 * All the data collection is done on a background handler thread
 * To start data collection run [start]
 */
object DataCollector {

    val TAG = this::class.java.simpleName

    private lateinit var context: Context

    // use linked LinkedHashSet to have a unique ordered list
    private val listeners: LinkedHashSet<WeakReference<SensorCallback>> = linkedSetOf()
    var multiflexConfiguration: MultiflexConfiguration = MultiflexConfiguration.all()

    var sensorType: TerabeeSdk.DeviceType = TerabeeSdk.DeviceType.AUTO_DETECT
    var sensorState: SensorState = SensorState.Disconnected
    private val wakeLock : PowerManager.WakeLock? = null
    private val signalDetector : SignalDetector = SignalDetector()
    /**
     * Starts initialize Sensor
     */
    fun start(context: Context) {
        this.context = context

        // init Terabee Sdk
        TerabeeSdk.getInstance().init(context)

        // set callback for receive data from sensors
        // can use single callback instead of callback for each sensor
        // this approach more convenient for connection with auto detect of sensor

        // set callback for receive data from sensors
        // can use single callback instead of callback for each sensor
        // this approach more convenient for connection with auto detect of sensor
        TerabeeSdk.getInstance().registerDataReceive(dataCollectorSensorCallback)
    }

    fun stop() {

        // release Terabee Sdk
        clearDataReceivers()
        TerabeeSdk.getInstance().dispose()

    }

    @Synchronized
    fun addListener(listener: SensorCallback) {
        listeners.add(WeakReference(listener))
    }

    @Synchronized
    fun removeListener(listener: SensorCallback) {
        val toRemoveList = listeners.filter { wrDataUpdater -> wrDataUpdater.get() == listener || wrDataUpdater.get() == null }
        listeners.removeAll(toRemoveList)
    }


    /////////////////////////////////////////////////////////
    private val dataCollectorSensorCallback: SensorCallback = object : SensorCallback {
        @Synchronized
        override fun onMatrixReceived(list: List<List<Int>>, dataBandwidth: Int, dataSpeed: Int) {
            listeners.forEach {
                it.get()?.onMatrixReceived(list, dataBandwidth, dataSpeed)
            }
//            updateMatrix(list, dataBandwidth, dataSpeed)
        }

        @Synchronized
        override fun onDistancesReceived(list: List<Int>, dataBandwidth: Int, dataSpeed: Int) {
            listeners.forEach {
                it.get()?.onDistancesReceived(list, dataBandwidth, dataSpeed)
            }
            // update entries for multiple distances (if needed)
        }

        /**
         * This is the sensor callback actually used
         **/
        @Synchronized
        override fun onDistanceReceived(distance: Int, dataBandwidth: Int, dataSpeed: Int) {

            listeners.forEach {
                it.get()?.onDistanceReceived(distance, dataBandwidth, dataSpeed)
            }
            val d : List<Long> = signalDetector.processSignal(distance.toDouble())
            if (d.isNotEmpty())
                onEntryListReceived(d)
        }

        @Synchronized
        override fun onReceivedData(data: ByteArray, dataBandwidth: Int, dataSpeed: Int) {
            listeners.forEach {
                it.get()?.onReceivedData(data, dataBandwidth, dataSpeed)
            }
        }

        @SuppressLint("WakelockTimeout")
        @Synchronized
        override fun onSensorStateChanged(sensorState: SensorState) {
            when(sensorState){
                SensorState.Connected -> wakeLock?.acquire()
                else -> if (wakeLock!= null && wakeLock.isHeld) wakeLock.release()
            }
            listeners.forEach {
                it.get()?.onSensorStateChanged(sensorState)
            }
        }

        /**
         * Called from [onDistanceReceived]
         **/
        override fun onEntryListReceived(entryTimestamps: List<Long>) {
            listeners.forEach {
                it.get()?.onEntryListReceived(entryTimestamps)
            }
        }
    }


    fun connectToDevice() {
        val connectThread = Thread(Runnable {
            try {
                setState(SensorState.Connecting)
                val configurations: MutableMap<TerabeeSdk.DeviceType, TerabeeSdk.IConfiguration> = HashMap()
                configurations[TerabeeSdk.DeviceType.MULTI_FLEX] = multiflexConfiguration
                TerabeeSdk.getInstance().connect(object : TerabeeSdk.IUsbConnect {
                    override fun connected(success: Boolean, deviceType: TerabeeSdk.DeviceType?) {
                        if (success) {
                            setState(SensorState.Connected)
                        } else {
                            setState(SensorState.Disconnected)
                        }
                    }

                    override fun disconnected() {
                        setState(SensorState.Disconnected)
                    }

                    override fun permission(granted: Boolean) {
                        // no need to implement
                        Log.d(TAG, "permission: $granted")
                    }
                }, sensorType, configurations)
            } catch (e: Exception) {
                Log.e(TAG, "Error connectToDevice", e)
            }
        })
        connectThread.start()
    }

    fun disconnectDevice() {
        try {
            TerabeeSdk.getInstance().disconnect()
            setState(SensorState.Disconnected)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnectDevice", e)
        }
    }

    private fun clearDataReceivers() {
        TerabeeSdk.getInstance().unregisterDataReceive(dataCollectorSensorCallback)

        // TerabeeSdk.getInstance().unregisterDataReceive(mDataDistanceCallback);
        // TerabeeSdk.getInstance().unregisterDataReceive(mDataMatrixCallback);
        // TerabeeSdk.getInstance().unregisterDataReceive(mDataDistancesCallback);
    }

    fun setState(sensorState: SensorState) {
        this.sensorState = sensorState
        dataCollectorSensorCallback.onSensorStateChanged(sensorState)
    }
}

/**
 * Sensor current state
 */
@Keep
enum class SensorState {
    Disconnected, Connecting, Connected
}

