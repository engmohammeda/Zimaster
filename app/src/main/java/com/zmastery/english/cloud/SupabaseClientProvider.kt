package com.zmastery.english.cloud

import com.zmastery.english.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Central singleton provider for SupabaseClient.
 * Reads SUPABASE_URL and SUPABASE_ANON_KEY injected from BuildConfig.
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL.ifBlank { "https://dummy-project.supabase.co" }
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "dummy-anon-key" }
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
