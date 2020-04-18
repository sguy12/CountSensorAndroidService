/**
 * Created by Asaf Pinhassi on 19/04/2020.
 */
package com.terabee.sdkdemo.persistence

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.google.gson.Gson


/**
 * Save/Load data from SharedPreferences
 */
object Preferences {

    val gson: Gson = Gson()

    enum class SharePreferenceFile {
        Common
    }

    enum class PreferenceName {
        EntriesList
    }


    /**
     * Loads last received location from shared preferences
     * @param context
     * @return last received location
     */
    fun loadEntriesList(context: Context): List<Long> {
        val preferenceName = PreferenceName.EntriesList.toString()
        val sharedPreferences = context.getSharedPreferences(SharePreferenceFile.Common.toString(), MODE_PRIVATE)

        val res : MutableList<Long> = mutableListOf()
        val jsonStr = sharedPreferences.getString(preferenceName, null)
        if (jsonStr != null) {
            try {
                // if last location parsing failed, save an empty value instead
                res.addAll(gson.fromJson(jsonStr, List::class.java) as List<Long>)
            } catch (_: Exception) {
                sharedPreferences.edit().putString(preferenceName, null).apply()
            }
        }
        return res
    }

    fun saveEntriesList(context: Context, entries: List<Long>) {
        val preferenceName = PreferenceName.EntriesList.toString()
        val sharedPreferences = context.getSharedPreferences(SharePreferenceFile.Common.toString(), MODE_PRIVATE)
        sharedPreferences.edit().putString(preferenceName, gson.toJson(entries)).apply()
    }
}

