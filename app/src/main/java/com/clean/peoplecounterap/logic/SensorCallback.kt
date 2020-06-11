package com.clean.peoplecounterap.logic

import com.terabee.sdk.TerabeeSdk

interface SensorCallback : TerabeeSdk.DataSensorCallback{
    fun onSensorStateChanged(sensorState: SensorState)
    fun onEntryListReceived(entryTimestamps: List<Long>)
}