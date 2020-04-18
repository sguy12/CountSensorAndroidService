/*
 * Created by Asaf Pinhassi on 19/04/2020.
 */
package com.terabee.sdkdemo.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.terabee.sdkdemo.FOREGROUND_SERVICE_NOTIFICATION_ID
import com.terabee.sdkdemo.MainActivity
import com.terabee.sdkdemo.R
import com.terabee.sdkdemo.logic.DataCollector
import com.terabee.sdkdemo.logic.SensorCallback
import com.terabee.sdkdemo.logic.SensorState
import com.terabee.sdkdemo.network.AsyncCommunicator


/**
 * Runs continuously, even when the app is closed to keep the sensor always active
 */
class ForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private val TAG = ForegroundService::class.java.simpleName

        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, ForegroundService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ForegroundService::class.java)
            context.stopService(stopIntent)
        }

        /**
         * This is the method that can be called to update the Notification
         */
        fun updateNotification(context: Context, text: String) {
            val notification: Notification = createNotification(context, text)
            val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.notify(FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
        }

        private fun createNotification(context: Context, text: String): Notification {
            val notificationIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                    context,
                    0, notificationIntent, 0
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("Entries counter")
                    .setContentText(text)
                    .setSound(null)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentIntent(pendingIntent)
                    .build()
        }

    }

    private val sensorCallback: SensorCallback = object : SensorCallback {
        override fun onSensorStateChanged(sensorState: SensorState) {
        }

        override fun onEntryListReceived(entryTimestamps: List<Long>) {
            AsyncCommunicator.sendEntries(entryTimestamps)
        }

        override fun onDistanceReceived(distance: Int, dataBandwidth: Int, dataSpeed: Int) {
            updateNotification(applicationContext, "Bandwidth: $dataBandwidth | Distance: $distance")
        }

        override fun onDistancesReceived(list: List<Int>, dataBandwidth: Int, dataSpeed: Int) {
        }

        override fun onMatrixReceived(list: List<List<Int>>, dataBandwidth: Int, dataSpeed: Int) {
        }

        override fun onReceivedData(data: ByteArray, dataBandwidth: Int, dataSpeed: Int) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //do heavy work on a background thread
        val text = intent?.getStringExtra("inputExtra") ?: ""
        createNotificationChannel()

        startForeground(FOREGROUND_SERVICE_NOTIFICATION_ID, createNotification(applicationContext, text))

        DataCollector.addListener(sensorCallback)
        try {
            DataCollector.start(this)
            DataCollector.connectToDevice()
        } catch (t: Throwable) {
            Log.e(TAG, "Sensor connection failed", t)
        }
        return START_STICKY
    }


    override fun onDestroy() {
        DataCollector.removeListener(sensorCallback)
        DataCollector.stop()
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }


}