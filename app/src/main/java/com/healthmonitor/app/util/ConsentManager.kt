package com.healthmonitor.app.util

import android.content.Context
import androidx.core.content.edit

object ConsentManager {

    private const val PREFS          = "health_monitor_prefs"
    private const val KEY_CONSENT    = "analytics_consent_granted"
    private const val KEY_CONSENT_ASKED = "analytics_consent_asked"

    fun isConsentGranted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONSENT, false)

    fun isConsentAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONSENT_ASKED, false)

    fun setConsent(context: Context, granted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CONSENT, granted)
            putBoolean(KEY_CONSENT_ASKED, true)
        }
    }
}