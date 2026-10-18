package com.example.smsforwarder

import android.app.Application
import com.example.smsforwarder.di.AppContainer

class SmsForwarderApp : Application() {
    val container by lazy { AppContainer(this) }
}
