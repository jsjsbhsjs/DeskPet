package com.deskpet

import android.app.Application

class DeskPetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DeskPetApp
    }
}
