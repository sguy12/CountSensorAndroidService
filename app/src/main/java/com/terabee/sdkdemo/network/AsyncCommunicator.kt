/*
 * Created by Asaf Pinhassi on 19/04/2020.
 */
package com.terabee.sdkdemo.network

import android.content.Context
import android.util.Log
import com.terabee.sdkdemo.persistence.Preferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AsyncCommunicator {

    private val TAG: String = AsyncCommunicator::class.java.simpleName
    private const val BASE_URL = "https://www.google.com/search?q="  // TODO: change url

    private val networkThread: Thread = Thread { sendQueuedMessages() }
    private var shouldThreadRun: Boolean = false
    private lateinit var context: Context
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val entriesQueue : MutableList<Long> = mutableListOf()

    private var totalCount: Int = 0

    fun start(context: Context) {
        this.context = context
        if (!networkThread.isAlive) {
            entriesQueue.clear()
            //entriesQueue.addAll(Preferences.loadEntriesList(context))
            shouldThreadRun = true
            networkThread.start()
        }
    }

    fun stop() {
        shouldThreadRun = false
        lock.withLock {
            condition.signal()
        }
    }

    fun sendEntries(entries: List<Long>) {
        lock.withLock {
            entriesQueue.addAll(0, entries)
            condition.signal()
        }
        //Preferences.saveEntriesList(context, entries)
    }

    private fun sendQueuedMessages() {
        while (shouldThreadRun) {
            var entries: MutableList<Long> = mutableListOf()
            var entryTimestamp: Long = -1
            var lastEntryTimestamp: Long = -1
            lock.withLock {
                if (entriesQueue.isNotEmpty()) {
                    var k: Int = 0
                    //entryTimestamp = entriesQueue.removeAt(entriesQueue.size - 1)
                    for (entry in entriesQueue) {
                        if (++k % 2 != 0) //add only ODD entries
                            entries.add(entry)
                    }
                    lastEntryTimestamp = entriesQueue.removeAt(entriesQueue.size - 1)
                    entriesQueue.clear()
                    if (k % 2 == 0) {
                        entriesQueue.add(lastEntryTimestamp)
                    }else{}
                }
                else {
                    condition.await(5, TimeUnit.MINUTES)
                }
            }

            if (entries.isNotEmpty()) {
                try {
                    val urlString = BASE_URL + entryTimestamp
                    val request = Request.Builder()
                            .url(urlString)
                            .build()

                    val client: OkHttpClient = OkHttpClient()
                    val response: Response = client.newCall(request).execute()
                    if (!response.isSuccessful)
                        throw Exception("Error sending message to server. response code:${response.code}")
                    Log.i(TAG,"Entry sent successfully. entryTimestamp: $entryTimestamp")
                } catch (t: Throwable) {
                    Log.w(TAG, "sendQueuedMessages request error", t)
                    /* when network request fails, put the entryTimestamp back and wait*/
                    lock.withLock {
                        entriesQueue.addAll(0, entries)
                        //condition.await(5, TimeUnit.MINUTES)
                    }
                }
            }
        }

    }

}
