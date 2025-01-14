package com.clean.peoplecounterap.data.local

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.clean.peoplecounterap.data.local.SPManager.Keys.COUNT_MODE
import com.clean.peoplecounterap.data.local.SPManager.Keys.SENSOR_MAX
import com.clean.peoplecounterap.data.local.SPManager.Keys.SENSOR_MIN
import com.clean.peoplecounterap.data.local.SPManager.Keys.S_NAME
import com.clean.peoplecounterap.data.local.SPManager.Keys.TOKEN
import com.clean.peoplecounterap.uitls.SharedPreferenceContext
import com.clean.peoplecounterap.uitls.get
import com.clean.peoplecounterap.uitls.put

class SPManager(context: Context) : SharedPreferenceContext {

    override val sp: SharedPreferences = context.getSharedPreferences("my_prefs", MODE_PRIVATE)

    var sName: String?
        get() = get(S_NAME) ?: ""
        set(value) = put(S_NAME, value)

    var token: String?
        get() = get(TOKEN)
        set(value) = put(TOKEN, value)

    var sensorMax: Int
    get() = get(SENSOR_MAX)?: 4000
    set(value) = put(SENSOR_MAX, value)


    var sensorMin: Int
    get() = get(SENSOR_MIN)?: 800
    set(value) = put(SENSOR_MIN, value)

    var countMode: Int
    get() = get(COUNT_MODE)?: 0
    set(value) = put(COUNT_MODE, value)

    enum class Keys {
        S_NAME,
        TOKEN,
        COUNT_MODE,
        SENSOR_MAX,
        SENSOR_MIN
    }
}