package com.clean.peoplecounterap.data.local

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.clean.peoplecounterap.data.local.SPManager.Keys.S_NAME
import com.clean.peoplecounterap.data.local.SPManager.Keys.TOKEN
import com.clean.peoplecounterap.uitls.SharedPreferenceContext
import com.clean.peoplecounterap.uitls.get
import com.clean.peoplecounterap.uitls.put

class SPManager(context: Context) : SharedPreferenceContext {

    override val sp: SharedPreferences = context.getSharedPreferences("my_prefs", MODE_PRIVATE)

    var sName: String?
        get() = get(S_NAME) ?: ""
        set(value) {
            put(S_NAME, value)
        }

    var token: String?
        get() = get(TOKEN)
        set(value) = put(TOKEN, value)

    private enum class Keys {
        S_NAME,
        TOKEN
    }
}