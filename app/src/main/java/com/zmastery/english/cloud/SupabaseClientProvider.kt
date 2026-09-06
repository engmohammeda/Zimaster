package com.zmastery.english.cloud

import android.util.Log
import com.zmastery.english.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Central singleton provider for SupabaseClient.
 *
 * Reads SUPABASE_URL and SUPABASE_ANON_KEY injected at build time from
 * BuildConfig (see app/build.gradle.kts → `supabaseProperty`). Values are
 * resolved from, in order: environment → local.properties → gradle.properties.
 *
 * Both values are public by design: the anon/publishable key grants nothing by
 * itself — every query is still filtered by Row Level Security policies defined
 * in supabase/migrations/. The service_role key must never appear in this app.
 */
object SupabaseClientProvider {

    private const val TAG = "SupabaseClientProvider"

    private const val DUMMY_URL = "https://dummy-project.supabase.co"
    private const val DUMMY_KEY = "dummy-anon-key"

    /** Project URL baked into this build. */
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.ifBlank { DUMMY_URL }

    /** Publishable/anon key baked into this build. */
    private val supabaseKey: String = BuildConfig.SUPABASE_ANON_KEY.ifBlank { DUMMY_KEY }

    /**
     * True when this build carries real cloud credentials.
     *
     * The UI and cloud controllers check this first so the learner gets a clear
     * "this build is not connected to the cloud" message instead of a cryptic
     * network/DNS failure deep inside Ktor.
     *
     * Accepted key shapes:
     *   • `sb_publishable_…` — current Supabase publishable API key (this project)
     *   • `eyJ…`             — legacy anon JWT (still valid for older projects)
     * Secret keys (`sb_secret_…`) are rejected: they must never ship in a client.
     */
    val isConfigured: Boolean = run {
        val urlOk = supabaseUrl.startsWith("https://") &&
            supabaseUrl.contains(".supabase.co") &&
            !supabaseUrl.contains("dummy-project")
        val keyOk = !supabaseKey.startsWith("sb_secret_") &&
            (supabaseKey.startsWith("sb_publishable_") || supabaseKey.startsWith("eyJ"))
        urlOk && keyOk
    }

    init {
        if (isConfigured) {
            Log.i(TAG, "Supabase configured → $supabaseUrl")
        } else {
            Log.w(
                TAG,
                "Supabase credentials are placeholders (url=$supabaseUrl). Cloud sync, auth and " +
                    "the leaderboard will not work in this build. Set SUPABASE_URL and " +
                    "SUPABASE_ANON_KEY — see docs/SUPABASE_SETUP.md."
            )
        }
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
