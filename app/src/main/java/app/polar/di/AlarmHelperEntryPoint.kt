package app.polar.di

import app.polar.util.AlarmManagerHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlarmHelperEntryPoint {
    fun getAlarmManagerHelper(): AlarmManagerHelper
}
