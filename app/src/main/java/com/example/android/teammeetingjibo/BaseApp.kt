package com.example.android.teammeetingjibo

import android.app.Application
import com.jibo.apptoolkit.android.JiboRemoteControl

class BaseApp : Application() {
    override fun onCreate(){
        super.onCreate()

        JiboRemoteControl.init(this, getString(R.string.appId),
                                        getString(R.string.appSecret))
    }
}