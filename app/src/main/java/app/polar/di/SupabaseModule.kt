package app.polar.di

import app.polar.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import javax.inject.Singleton

// SUPABASE_URL/SUPABASE_ANON_KEY come from local.properties -> BuildConfig (never hardcoded,
// see agent-docs/supabase-sync/08-configuracion-y-credenciales.md). The anon/publishable key is
// safe to ship client-side by Supabase's own design; real data isolation comes from RLS.
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        // Postgrest rows carry columns our DTOs don't model (server_updated_at, and image_path
        // until Storage sync lands) — ignoreUnknownKeys keeps pulls from crashing on those instead
        // of requiring every DTO to mirror the full server schema.
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
        install(Postgrest)
        install(Auth)
        install(Realtime)
        install(Storage)
    }
}
