package com.d0ll1ii.dollxrstore.locale

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val welcomeText: String
)

object LanguageManager {
    val supportedLanguages = listOf(
        AppLanguage(
            code = "en",
            nativeName = "English",
            englishName = "English",
            welcomeText = "Welcome to DollXR"
        ),
        AppLanguage(
            code = "ru",
            nativeName = "Русский",
            englishName = "Russian",
            welcomeText = "Добро пожаловать в DollXR"
        ),
        AppLanguage(
            code = "es",
            nativeName = "Español",
            englishName = "Spanish",
            welcomeText = "Bienvenido a DollXR"
        ),
        AppLanguage(
            code = "fr",
            nativeName = "Français",
            englishName = "French",
            welcomeText = "Bienvenue sur DollXR"
        ),
        AppLanguage(
            code = "de",
            nativeName = "Deutsch",
            englishName = "German",
            welcomeText = "Willkommen bei DollXR"
        ),
        AppLanguage(
            code = "ja",
            nativeName = "日本語",
            englishName = "Japanese",
            welcomeText = "DollXRへようこそ"
        ),
        AppLanguage(
            code = "zh",
            nativeName = "中文",
            englishName = "Chinese",
            welcomeText = "欢迎来到 DollXR"
        )
    )

    private val _currentLanguageCode = MutableStateFlow("en")
    val currentLanguageCode: StateFlow<String> = _currentLanguageCode.asStateFlow()

    fun setLanguage(context: Context, langCode: String) {
        _currentLanguageCode.value = langCode

        // Save preference in SharedPreferences
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("language_code", langCode).apply()

        // Apply system-wide application locale using AppCompatDelegate
        val appLocales = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    fun initLanguage(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedCode = prefs.getString("language_code", "en") ?: "en"
        _currentLanguageCode.value = savedCode
    }

    fun getLocalizedWelcomeText(langCode: String): String {
        return supportedLanguages.find { it.code == langCode }?.welcomeText
            ?: "Welcome to DollXR"
    }

    fun getLocalizedContext(context: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
