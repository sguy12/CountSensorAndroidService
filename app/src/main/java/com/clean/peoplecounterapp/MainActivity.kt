package com.clean.peoplecounterapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.clean.peoplecounterapp.logic.DataCollector.addListener
import com.clean.peoplecounterapp.logic.DataCollector.config
import com.clean.peoplecounterapp.logic.DataCollector.connectToDevice
import com.clean.peoplecounterapp.logic.DataCollector.disconnectDevice
import com.clean.peoplecounterapp.logic.DataCollector.removeListener
import com.clean.peoplecounterapp.logic.DataCollector.sensorState
import com.clean.peoplecounterapp.logic.DataCollector.sensorType
import com.clean.peoplecounterapp.logic.SensorCallback
import com.clean.peoplecounterapp.logic.SensorState
import com.clean.peoplecounterapp.logic.SensorState.Connected
import com.clean.peoplecounterapp.logic.SensorState.Connecting
import com.clean.peoplecounterapp.logic.SensorState.Disconnected
import com.cleen.peoplecounterapp.R
import com.cleen.peoplecounterapp.databinding.ActivityMainBinding
import com.terabee.sdk.TerabeeSdk.DeviceType
import com.terabee.sdk.TerabeeSdk.DeviceType.AUTO_DETECT
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_3M
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_60M
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_64PX
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_MINI
import com.terabee.sdk.TerabeeSdk.DeviceType.MULTI_FLEX
import com.terabee.sdk.TerabeeSdk.MultiflexConfiguration
import kotlinx.android.synthetic.main.activity_main.configuration
import kotlinx.android.synthetic.main.activity_main.connect
import kotlinx.android.synthetic.main.activity_main.data
import kotlinx.android.synthetic.main.activity_main.disconnect
import kotlinx.android.synthetic.main.activity_main.entriesCount
import kotlinx.android.synthetic.main.activity_main.lastChunkText
import kotlinx.android.synthetic.main.activity_main.sensor_type
import kotlinx.android.synthetic.main.activity_main.tvDataBandwidth
import kotlinx.android.synthetic.main.activity_main.tvLogCat
import timber.log.Timber
import java.util.Date

class MainActivity : AppCompatActivity() {

    private var entriesCounter = 0
    private var mBinding: ActivityMainBinding? = null
    private var selectedSensors: BooleanArray? = null
    private val sensorCallback: SensorCallback = object : SensorCallback {
        override fun onEntryListReceived(entryTimestamps: List<Long>) {
            updateEntries(entryTimestamps)
        }

        override fun onReceivedData(bytes: ByteArray, i: Int, i1: Int) {
            // show something if needed
        }

        override fun onMatrixReceived(list: List<List<Int>>, dataBandwidth: Int, dataSpeed: Int) {
            updateMatrix(list, dataBandwidth)
        }

        override fun onDistancesReceived(list: List<Int>, dataBandwidth: Int, dataSpeed: Int) {
            updateDistances(list, dataBandwidth)
        }

        override fun onDistanceReceived(distance: Int, dataBandwidth: Int, dataSpeed: Int) {
            updateDistance(distance, dataBandwidth)
        }

        override fun onSensorStateChanged(sensorState: SensorState) {
            updateUiState(sensorState, true)
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra("log")
            if (log?.contains("--> POST") == true) {
                tvLogCat.text = ""
            }
            tvLogCat.text = tvLogCat.text.toString().plus(log).plus("\n")
        }
    }

    @SuppressLint("ShowToast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        configuration.isEnabled = true
        // initialize UI
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                SENSOR_TYPES)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sensor_type.adapter = adapter
        sensor_type.setSelection(0)
        sensor_type.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int,
                    id: Long) {
                setCurrentType((view as TextView).text.toString())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no any action
            }
        }
        connect.setOnClickListener { connectToDevice() }
        disconnect.setOnClickListener { disconnectDevice() }
        configuration.setOnClickListener { showMultiflexConfigurationDialog() }
        updateUiState(sensorState, false)
        addListener(sensorCallback)
        registerReceiver(logReceiver, IntentFilter("LOG"))
    }

    override fun onResume() {
        super.onResume()
        entriesCounter = 0
        entriesCount.text = entriesCounter.toString()
        lastChunkText.text = ""
    }

    override fun onDestroy() {
        removeListener(sensorCallback)
        unregisterReceiver(logReceiver)
        super.onDestroy()
    }

    private fun updateUiState(sensorState: SensorState, showToast: Boolean) {
        runOnUiThread {
            val deviceType = sensorType
            when (sensorState) {
                Disconnected -> {
                    sensor_type.isEnabled = true
                    connect.isEnabled = true
                    disconnect.isEnabled = false
                    data.text = ""
                    tvDataBandwidth.text = "0"
                    configuration.isEnabled = (deviceType == MULTI_FLEX || deviceType == AUTO_DETECT)
                    if (showToast) {
                        showShortToast("Unable connect to sensor: " + deviceType.name)
                    }
                }
                Connecting -> {
                    sensor_type.isEnabled = false
                    connect.isEnabled = false
                    disconnect.isEnabled = false
                    data.text = ""
                    tvDataBandwidth.text = "0"
                    configuration.isEnabled = false
                }
                Connected -> {
                    sensor_type.isEnabled = false
                    connect.isEnabled = false
                    disconnect.isEnabled = true
                    data.text = ""
                    tvDataBandwidth.text = "0"
                    configuration.isEnabled = false
                    if (showToast) {
                        showShortToast("Connected sensor: " + deviceType.name)
                    }
                }
            }
        }
    }

    private fun updateEntries(list: List<Long>) {
        runOnUiThread {
            entriesCounter += list.size
            entriesCount.text = entriesCounter.toString()
            val dateStr = Date(list[list.size - 1]).toString()
            val lastChunk = dateStr + ": " + list.size + " items"
            lastChunkText.text = lastChunk
        }
    }

    private fun setCurrentType(value: String) {
        sensorType = DeviceType.valueOf(value)
        configuration.isEnabled = sensorType == MULTI_FLEX || sensorType == AUTO_DETECT
    }

    private fun showMultiflexConfigurationDialog() {
        val multiflexConfig = config
        selectedSensors = booleanArrayOf(
                multiflexConfig.isSensor1Enable,
                multiflexConfig.isSensor2Enable,
                multiflexConfig.isSensor3Enable,
                multiflexConfig.isSensor4Enable,
                multiflexConfig.isSensor5Enable,
                multiflexConfig.isSensor6Enable,
                multiflexConfig.isSensor7Enable,
                multiflexConfig.isSensor8Enable
        )
        Builder(this)
                .setTitle("Multiflex configuration")
                .setMultiChoiceItems(
                        MULTIFLEX_SENSORS_LIST,
                        selectedSensors) { _: DialogInterface?, selectedItemId: Int, isSelected: Boolean ->
                    selectedSensors?.set(selectedItemId, isSelected)
                }.setPositiveButton("Done") { _: DialogInterface?, _: Int ->
                    // apply changes
                    val newMultiflexConfiguration = MultiflexConfiguration.custom(
                            selectedSensors?.get(0) == true,
                            selectedSensors?.get(1) == true,
                            selectedSensors?.get(2) == true,
                            selectedSensors?.get(3) == true,
                            selectedSensors?.get(4) == true,
                            selectedSensors?.get(5) == true,
                            selectedSensors?.get(6) == true,
                            selectedSensors?.get(7) == true
                    )
                    config = newMultiflexConfiguration
                }.setNegativeButton("Cancel") { _: DialogInterface?, _: Int -> }
                .create()
                .show()
    }

    @SuppressLint("SetTextI18n")
    private fun updateMatrix(list: List<List<Int>>?, dataBandwidth: Int) {
        runOnUiThread {
            if (sensorState === Connected) {
                tvDataBandwidth.text = "Bandwidth: $dataBandwidth"
                if (list != null) {
                    var matrix = "Matrix: \n"
                    for (i in list.indices) {
                        for (j in list[i].indices) {
                            matrix += list[i][j].toString() + ", "
                        }
                        matrix += "\n"
                    }
                    data.text = matrix
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDistance(distance: Int, dataBandwidth: Int) {
        runOnUiThread {
            if (sensorState === Connected) {
                tvDataBandwidth.text = "Bandwidth: $dataBandwidth"
                data.text = "Distance: $distance"
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDistances(list: List<Int>?, dataBandwidth: Int) {
        runOnUiThread {
            if (sensorState === Connected) {
                tvDataBandwidth.text = "Bandwidth: $dataBandwidth"
                if (list != null) {
                    Timber.d("onDistancesReceived, size: %s", list.size)
                    Timber.d("onDistancesReceived, list: $list")
                    var distances = "Distances: \n"
                    for (i in list.indices) {
                        distances += "Sensor " + (i + 1).toString()
                        distances += ": " + list[i].toString()
                        distances += "\n"
                    }
                    data.text = distances
                }
            }
        }
    }

    private fun showShortToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val SENSOR_TYPES = arrayOf(
                AUTO_DETECT.name,
                EVO_3M.name,
                EVO_60M.name,
                EVO_64PX.name,
                MULTI_FLEX.name,
                EVO_MINI.name)
        private val MULTIFLEX_SENSORS_LIST = arrayOf(
                "Enable Sensor 1",
                "Enable Sensor 2",
                "Enable Sensor 3",
                "Enable Sensor 4",
                "Enable Sensor 5",
                "Enable Sensor 6",
                "Enable Sensor 7",
                "Enable Sensor 8"
        )
    }
}