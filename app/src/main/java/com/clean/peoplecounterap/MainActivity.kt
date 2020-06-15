package com.clean.peoplecounterap

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.clean.peoplecounterap.data.local.SPManager
import com.clean.peoplecounterap.logic.DataCollector.addListener
import com.clean.peoplecounterap.logic.DataCollector.config
import com.clean.peoplecounterap.logic.DataCollector.connectToDevice
import com.clean.peoplecounterap.logic.DataCollector.disconnectDevice
import com.clean.peoplecounterap.logic.DataCollector.removeListener
import com.clean.peoplecounterap.logic.DataCollector.sensorState
import com.clean.peoplecounterap.logic.DataCollector.sensorType
import com.clean.peoplecounterap.logic.SensorCallback
import com.clean.peoplecounterap.logic.SensorState
import com.clean.peoplecounterap.logic.SensorState.Connected
import com.clean.peoplecounterap.logic.SensorState.Connecting
import com.clean.peoplecounterap.logic.SensorState.Disconnected
import com.clean.peoplecounterap.repository.remote.RestManager
import com.fondesa.kpermissions.PermissionStatus
import com.fondesa.kpermissions.allGranted
import com.fondesa.kpermissions.anyPermanentlyDenied
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.fondesa.kpermissions.request.PermissionRequest.Listener
import com.google.android.material.snackbar.Snackbar
import com.terabee.sdk.TerabeeSdk.DeviceType
import com.terabee.sdk.TerabeeSdk.DeviceType.AUTO_DETECT
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_3M
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_60M
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_64PX
import com.terabee.sdk.TerabeeSdk.DeviceType.EVO_MINI
import com.terabee.sdk.TerabeeSdk.DeviceType.MULTI_FLEX
import com.terabee.sdk.TerabeeSdk.MultiflexConfiguration
import kotlinx.android.synthetic.main.activity_main.btnSubmit
import kotlinx.android.synthetic.main.activity_main.configuration
import kotlinx.android.synthetic.main.activity_main.connect
import kotlinx.android.synthetic.main.activity_main.data
import kotlinx.android.synthetic.main.activity_main.disconnect
import kotlinx.android.synthetic.main.activity_main.entriesCount
import kotlinx.android.synthetic.main.activity_main.etSensorHeight
import kotlinx.android.synthetic.main.activity_main.etSymbolsToSend
import kotlinx.android.synthetic.main.activity_main.lastChunkText
import kotlinx.android.synthetic.main.activity_main.llActivityRoot
import kotlinx.android.synthetic.main.activity_main.sensor_type
import kotlinx.android.synthetic.main.activity_main.tvDataBandwidth
import kotlinx.android.synthetic.main.activity_main.tvLogCat
import kotlinx.android.synthetic.main.activity_main.tvRemain
import kotlinx.android.synthetic.main.activity_main.tvTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date

class MainActivity : AppCompatActivity() {

    private var entriesCounter = 0
    private var selectedSensors: BooleanArray? = null
    private lateinit var spManager: SPManager
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
            tvLogCat.visibility = View.VISIBLE
            val log = intent?.getStringExtra("log")
            if (log?.contains("--> POST") == true) {
                tvLogCat.text = ""
            }
            tvLogCat.text = tvLogCat.text.toString().plus(log).plus("\n")
        }
    }

    private val nextRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            countDownTimer.start()
        }
    }

    private val countDownTimer = object : CountDownTimer(5 * 60 * 1000L, 1000L) {
        override fun onFinish() {
        }

        override fun onTick(millisUntilFinished: Long) {
            val remainedSecs = millisUntilFinished / 1000
            tvRemain.text = "Time remain: " + (remainedSecs / 60) + ":" + (remainedSecs % 60)
        }
    }

    @SuppressLint("ShowToast", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
        registerReceiver(nextRequestReceiver, IntentFilter("NEXT_REQUEST"))
        btnSubmit.setOnClickListener { checkIsLetterValid() }
        spManager = SPManager(this)
        if (spManager.sName?.isNotEmpty() == true) setSName()
        etSensorHeight.setText(spManager.sensorHeight.toString())
        etSensorHeight.doOnTextChanged { text, _, _, _ ->
            text?.toString()?.toIntOrNull()?.let { spManager.sensorHeight = it }
        }
    }

    private fun checkIsLetterValid() {
        if (etSymbolsToSend.text.toString().contains(Regex("^[a-zA-Z]{6}\$"))) {
            checkPermissions()
        } else {
            snackbar("Text is not equal to 6 letters")
        }
    }

    private fun snackbar(text: String) {
        Snackbar.make(llActivityRoot, text, Snackbar.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        val build = permissionsBuilder(permission.READ_PHONE_STATE).build()
        build.addListener(object : Listener {
            override fun onPermissionsResult(result: List<PermissionStatus>) {
                when {
                    result.allGranted() -> sendLettersRequest()
                    result.anyPermanentlyDenied() -> {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.data = Uri.fromParts("package", packageName, null)
                        startActivity(intent)
                    }
                    else -> snackbar("To send request you should grant permissions")
                }
            }
        })
        build.send()
    }

    @SuppressLint("MissingPermission")
    private fun sendLettersRequest() { // todo hide keyboard
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var uuid = telephonyManager.deviceId
        if (uuid.isNullOrEmpty()) {
            uuid = (if (VERSION.SDK_INT >= VERSION_CODES.O) telephonyManager.imei else "EMPTY_UUID")
        }

        GlobalScope.launch(Dispatchers.IO) {
            val response = RestManager(applicationContext).generateToken(
                    mapOf("pCode" to etSymbolsToSend.text.toString(), "uuid" to uuid))
            if (response.isSuccessful) {
                spManager.sName = response.body()?.sname
                spManager.token = response.body()?.t
                setSName()
            } else {
                snackbar(response.errorBody()?.string() ?: return@launch)
            }
        }
    }

    private fun setSName() {
        runOnUiThread { tvTokenResponse.text = "SName: ${spManager.sName}" }
    }

    override fun onResume() {
        super.onResume()
        entriesCount.text = entriesCounter.toString()
        lastChunkText.text = ""
    }

    override fun onDestroy() {
        removeListener(sensorCallback)
        unregisterReceiver(logReceiver)
        unregisterReceiver(nextRequestReceiver)
        countDownTimer.cancel()
        super.onDestroy()
    }

    private fun updateUiState(sensorState: SensorState, showToast: Boolean) {
        runOnUiThread {
            val deviceType = sensorType
            when (sensorState) {
                Disconnected -> { // todo refactor to generic method
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
            lastChunkText.text = dateStr + ": " + list.size + " items"
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