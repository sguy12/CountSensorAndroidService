/*
 * Created by Asaf Pinhassi on 19/04/2020.
 */
package com.terabee.sdkdemo.logic

import com.terabee.sdk.TerabeeSdk

interface SensorCallback : TerabeeSdk.DataSensorCallback{
    fun onSensorStateChanged(sensorState: SensorState)
    fun onEntryListReceived(entryTimestamps: List<Long>)
}