package com.clean.peoplecounterap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clean.peoplecounterap.MainActivity
import com.clean.peoplecounterap.R
import com.clean.peoplecounterap.data.local.SPManager
import com.clean.peoplecounterap.data.local.SPManager.Keys.SENSOR_MAX
import com.clean.peoplecounterap.data.local.SPManager.Keys.SENSOR_MIN
import com.clean.peoplecounterap.logic.DataCollector
import com.clean.peoplecounterap.logic.SensorCallback
import com.clean.peoplecounterap.logic.SensorState
import com.clean.peoplecounterap.repository.remote.RestManager
import com.clean.peoplecounterap.repository.remote.request.PostRequest
import com.novoda.merlin.Merlin.Builder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.Timer
import kotlin.concurrent.timerTask

class ForegroundService : Service() {

    companion object {
        private const val FOREGROUND_SERVICE_NOTIFICATION_ID: Int = 10
        private const val CHANNEL_ID = "ForegroundServiceChannel"

        private val entries = mutableListOf<PostRequest>()
        private var timer = Timer()
        private var job: Job? = null

        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, ForegroundService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun updateNotification(context: Context, text: String) {
            val notification: Notification = createNotification(context, text)
            val mNotificationManager = context.getSystemService(
                    Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.notify(FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
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

    private val sensorCallback: SensorCallback = object : SensorCallback {
        override fun onSensorStateChanged(sensorState: SensorState) {
        }

        override fun onEntryListReceived(entryTimestamps: List<Long>) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val value = if (spManager.countMode == 0) {
                entryTimestamps.size
            } else {
                processedValueInt(entryTimestamps.size)
            }
            if (value != 0) {
                val item = PostRequest(format.format(Calendar.getInstance().time), value)
                entries.add(item)
            }
        }

        override fun onDistanceReceived(distance: Int, dataBandwidth: Int, dataSpeed: Int) {
            updateNotification(applicationContext,
                    "Bandwidth: $dataBandwidth | Distance: $distance")
        }

        override fun onDistancesReceived(list: List<Int>, dataBandwidth: Int, dataSpeed: Int) {
        }

        override fun onMatrixReceived(list: List<List<Int>>, dataBandwidth: Int, dataSpeed: Int) {
        }

        override fun onReceivedData(data: ByteArray, dataBandwidth: Int, dataSpeed: Int) {
        }
    }

    private lateinit var spManager: SPManager
    private val merlin = Builder().withConnectableCallbacks().withDisconnectableCallbacks()
            .build(this)
    private var totalCount: Int = 0
    private var totalEffectiveCount: Int = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        spManager = SPManager(this)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences?, key: String? ->
            if (key?.equals(SENSOR_MAX.name) == true || key?.equals(SENSOR_MIN.name) == true) {
                DataCollector.setMinMax(spManager.sensorMax to spManager.sensorMin)
            }
        }
        spManager.sp.registerOnSharedPreferenceChangeListener(listener)

        //do heavy work on a background thread
        val text = intent?.getStringExtra("inputExtra") ?: ""
        createNotificationChannel()

        startForeground(FOREGROUND_SERVICE_NOTIFICATION_ID,
                createNotification(applicationContext, text))

        DataCollector.addListener(sensorCallback)
        try {
            DataCollector.init(this)
            DataCollector.connectToDevice()
        } catch (t: Throwable) {
            Timber.e(t)
        }
        merlin.bind()
        merlin.registerConnectable {
            timer = Timer()
            timer.schedule(timerTask { request() }, 0, 5 * 60 * 1000)
        }
        merlin.registerDisconnectable {
            timer.cancel()
            job?.cancel()
        }
        return START_STICKY
    }

    private fun request() {
        sendBroadcast(Intent("NEXT_REQUEST"))
        if (entries.isNotEmpty() && spManager.token?.isNotEmpty() == true) {
            try {
                job = GlobalScope.launch(Dispatchers.IO) {
                    RestManager(applicationContext).somePost(entries)
                    entries.clear()
                }
            } catch (e: Exception) {
                entries.clear()
                Timber.e(e)
            }
        }
    }

    override fun onDestroy() {
        DataCollector.removeListener(sensorCallback)
        DataCollector.stop()
        timer.cancel()
        job?.cancel()
        merlin.unbind()
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

    private fun processedValueInt(valueInt: Int): Int {
        var tmpValue = totalCount + valueInt
        if (tmpValue > 0 && tmpValue % 2 != 0) {
            tmpValue++
        }
        val totalVisitors = tmpValue / 2
        val visitorsSinceLastCount = totalVisitors - totalEffectiveCount
        totalEffectiveCount = totalVisitors
        totalCount += valueInt
        return visitorsSinceLastCount
    }
}