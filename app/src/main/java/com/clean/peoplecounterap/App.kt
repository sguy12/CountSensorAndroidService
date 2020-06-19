package com.clean.peoplecounterap

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import com.clean.peoplecounterap.service.ForegroundService
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        print("◢◤◢◤◢◤◢◤◢◤◢◤◢◤  NEW ANDROID RUN  $packageName ◢◤◢◤◢◤◢◤◢◤◢◤◢◤")

        initComponents()
    }

    private fun initComponents(){
        ForegroundService.startService(this,"Starting...");
        AndroidThreeTen.init(this)
        Timber.plant(Timber.DebugTree())
    }
}