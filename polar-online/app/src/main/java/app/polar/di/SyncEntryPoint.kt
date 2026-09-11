package app.polar.di

import app.polar.data.sync.SyncManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun getSyncManager(): SyncManager
    fun getSupabaseClient(): SupabaseClient
}
