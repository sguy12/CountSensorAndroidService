package com.clean.peoplecounterapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clean.peoplecounterapp.MainActivity
import com.cleen.peoplecounterapp.R
import com.clean.peoplecounterapp.logic.DataCollector
import com.clean.peoplecounterapp.logic.SensorCallback
import com.clean.peoplecounterapp.logic.SensorState
import com.clean.peoplecounterapp.repository.remote.RestManager
import com.clean.peoplecounterapp.repository.remote.request.PostRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * Runs continuously, even when the app is closed to keep the sensor always active
 */
class ForegroundService : Service() {

    companion object {
        private const val FOREGROUND_SERVICE_NOTIFICATION_ID : Int = 10
        private const val CHANNEL_ID = "ForegroundServiceChannel"

        private val entries = mutableListOf<PostRequest>()
        private var timer = Timer()
        private var job: Job? = null

        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, ForegroundService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        /**
         * This is the method that can be called to update the Notification
         */
        fun updateNotification(context: Context, text: String) {
            val notification: Notification = createNotification(
                    context, text)
            val mNotificationManager = context.getSystemService(
                    Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.notify(
                    FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
        }

        private fun createNotification(context: Context, text: String): Notification {
            val notificationIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                    context,
                    0, notificationIntent, 0
            )
            return NotificationCompat.Builder(context,
                    CHANNEL_ID)
                    .setContentTitle("Entries counter")
                    .setContentText(text)
                    .setSound(null)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentIntent(pendingIntent)
                    .build()
        }
    }

    private val sensorCallback: SensorCallback = object :
            SensorCallback {
        override fun onSensorStateChanged(sensorState: SensorState) {
        }

        override fun onEntryListReceived(entryTimestamps: List<Long>) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val item = PostRequest(
                    format.format(Calendar.getInstance().time), entryTimestamps.size)
            entries.add(item)
        }

        override fun onDistanceReceived(distance: Int, dataBandwidth: Int, dataSpeed: Int) {
            updateNotification(
                    applicationContext,
                    "Bandwidth: $dataBandwidth | Distance: $distance")
        }

        override fun onDistancesReceived(list: List<Int>, dataBandwidth: Int, dataSpeed: Int) {
        }

        override fun onMatrixReceived(list: List<List<Int>>, dataBandwidth: Int, dataSpeed: Int) {
        }

        override fun onReceivedData(data: ByteArray, dataBandwidth: Int, dataSpeed: Int) {
        }
    }

    private val task = timerTask {
        if (entries.isNotEmpty()) {
            try {
                job = GlobalScope.launch(Dispatchers.IO) {
                    RestManager(
                            applicationContext).somePost(
                            entries)
                    entries.clear()
                }
            } catch (e: Exception) {
                entries.clear()
                Timber.e(e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //do heavy work on a background thread
        val text = intent?.getStringExtra("inputExtra") ?: ""
        createNotificationChannel()

        startForeground(
                FOREGROUND_SERVICE_NOTIFICATION_ID,
                createNotification(
                        applicationContext, text))

        DataCollector.addListener(sensorCallback)
        try {
            DataCollector.init(this)
            DataCollector.connectToDevice()
        } catch (t: Throwable) {
            Timber.e(t)
        }
        timer = Timer()
        timer.schedule(task, 0, 5 * 60 * 1000)
        return START_STICKY
    }

    override fun onDestroy() {
        DataCollector.removeListener(sensorCallback)
        DataCollector.stop()
        timer.cancel()
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                    CHANNEL_ID, "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}