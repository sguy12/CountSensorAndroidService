package com.clean.peoplecounterapp

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import com.clean.peoplecounterapp.service.ForegroundService


class App : Application() {

    override fun onCreate() {
        super.onCreate()
        print("◢◤◢◤◢◤◢◤◢◤◢◤◢◤  NEW ANDROID RUN  $packageName ◢◤◢◤◢◤◢◤◢◤◢◤◢◤")

        initComponents()
    }

    private fun initComponents(){
        ForegroundService.startService(this,"Starting...");
        AndroidThreeTen.init(this)
    }
}