/*
 * Created by Asaf Pinhassi on 19/04/2020.
 */
package com.terabee.sdkdemo

import android.app.Application
import com.terabee.sdkdemo.network.AsyncCommunicator
import com.terabee.sdkdemo.service.ForegroundService


class App : Application() {

    override fun onCreate() {
        super.onCreate()
        print("◢◤◢◤◢◤◢◤◢◤◢◤◢◤  NEW ANDROID RUN  $packageName ◢◤◢◤◢◤◢◤◢◤◢◤◢◤")

        initComponents()
    }

    private fun initComponents(){
        AsyncCommunicator.start(this)
        ForegroundService.startService(this,"Starting...");
    }
}