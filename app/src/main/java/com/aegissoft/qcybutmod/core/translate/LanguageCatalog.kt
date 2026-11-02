package com.aegissoft.qcybutmod.core.translate

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage

data class SupportedLanguage(
    val code: String,          // BCP-47 / ML Kit code
    val nameTr: String,        // Turkish display name
    val nativeName: String,    // Native language display name
    val flag: String           // Country flag representation or symbol
)

object LanguageCatalog {
    private const val PREFS_NAME = "qcy_translate_prefs"
    private const val KEY_FAVORITES = "favorite_languages"

    val ALL_LANGUAGES: List<SupportedLanguage> = listOf(
        SupportedLanguage(TranslateLanguage.TURKISH, "Türkçe", "Türkçe", "🇹🇷"),
        SupportedLanguage(TranslateLanguage.ENGLISH, "İngilizce", "English", "🇬🇧"),
        SupportedLanguage(TranslateLanguage.GERMAN, "Almanca", "Deutsch", "🇩🇪"),
        SupportedLanguage(TranslateLanguage.FRENCH, "Fransızca", "Français", "🇫🇷"),
        SupportedLanguage(TranslateLanguage.SPANISH, "İspanyolca", "Español", "🇪🇸"),
        SupportedLanguage(TranslateLanguage.ITALIAN, "İtalyanca", "Italiano", "🇮🇹"),
        SupportedLanguage(TranslateLanguage.RUSSIAN, "Rusça", "Русский", "🇷🇺"),
        SupportedLanguage(TranslateLanguage.ARABIC, "Arapça", "العربية", "🇸🇦"),
        SupportedLanguage(TranslateLanguage.JAPANESE, "Japonca", "日本語", "🇯🇵"),
        SupportedLanguage(TranslateLanguage.KOREAN, "Korece", "한국어", "🇰🇷"),
        SupportedLanguage(TranslateLanguage.CHINESE, "Çince", "中文", "🇨🇳"),
        SupportedLanguage(TranslateLanguage.PORTUGUESE, "Portekizce", "Português", "🇵🇹"),
        SupportedLanguage(TranslateLanguage.DUTCH, "Felemenkçe", "Nederlands", "🇳🇱"),
        SupportedLanguage(TranslateLanguage.POLISH, "Lehçe", "Polski", "🇵🇱"),
        SupportedLanguage(TranslateLanguage.UKRAINIAN, "Ukraynaca", "Українська", "🇺🇦"),
        SupportedLanguage(TranslateLanguage.GREEK, "Yunanca", "Ελληνικά", "🇬🇷"),
        SupportedLanguage(TranslateLanguage.SWEDISH, "İsveççe", "Svenska", "🇸🇪"),
        SupportedLanguage(TranslateLanguage.NORWEGIAN, "Norveççe", "Norsk", "🇳🇴"),
        SupportedLanguage(TranslateLanguage.DANISH, "Danca", "Dansk", "🇩🇰"),
        SupportedLanguage(TranslateLanguage.FINNISH, "Fince", "Suomi", "🇫🇮"),
        SupportedLanguage(TranslateLanguage.CZECH, "Çekçe", "Čeština", "🇨🇿"),
        SupportedLanguage(TranslateLanguage.HUNGARIAN, "Macarca", "Magyar", "🇭🇺"),
        SupportedLanguage(TranslateLanguage.ROMANIAN, "Romence", "Română", "🇷🇴"),
        SupportedLanguage(TranslateLanguage.BULGARIAN, "Bulgarca", "Български", "🇧🇬"),
        SupportedLanguage(TranslateLanguage.CROATIAN, "Hırvatça", "Hrvatski", "🇭🇷"),
        SupportedLanguage(TranslateLanguage.SLOVAK, "Slovakça", "Slovenčina", "🇸🇰"),
        SupportedLanguage(TranslateLanguage.SLOVENIAN, "Slovence", "Slovenščina", "🇸🇮"),
        SupportedLanguage(TranslateLanguage.HINDI, "Hintçe", "हिन्दी", "🇮🇳"),
        SupportedLanguage(TranslateLanguage.INDONESIAN, "Endonezce", "Bahasa Indonesia", "🇮🇩"),
        SupportedLanguage(TranslateLanguage.MALAY, "Malayca", "Bahasa Melayu", "🇲🇾"),
        SupportedLanguage(TranslateLanguage.THAI, "Tayca", "ไทย", "🇹🇭"),
        SupportedLanguage(TranslateLanguage.VIETNAMESE, "Vietnamca", "Tiếng Việt", "🇻🇳"),
        SupportedLanguage(TranslateLanguage.HEBREW, "İbranice", "עברית", "🇮🇱"),
        SupportedLanguage(TranslateLanguage.PERSIAN, "Farsça", "فارسی", "🇮🇷"),
        SupportedLanguage(TranslateLanguage.URDU, "Urduca", "اردو", "🇵🇰"),
        SupportedLanguage(TranslateLanguage.BENGALI, "Bengalce", "বাংলা", "🇧🇩"),
        SupportedLanguage(TranslateLanguage.CATALAN, "Katalanca", "Català", "🇪🇸"),
        SupportedLanguage(TranslateLanguage.ALBANIAN, "Arnavutça", "Shqip", "🇦🇱"),
        SupportedLanguage(TranslateLanguage.ESTONIAN, "Estonca", "Eesti", "🇪🇪"),
        SupportedLanguage(TranslateLanguage.LATVIAN, "Letonca", "Latviešu", "🇱🇻"),
        SupportedLanguage(TranslateLanguage.LITHUANIAN, "Litvanca", "Lietuvių", "🇱🇹"),
        SupportedLanguage(TranslateLanguage.MACEDONIAN, "Makedonca", "Македонски", "🇲🇰"),
        SupportedLanguage(TranslateLanguage.BELARUSIAN, "Belarusça", "Беларуская", "🇧🇾"),
        SupportedLanguage(TranslateLanguage.GEORGIAN, "Gürcüce", "ქართული", "🇬🇪"),
        SupportedLanguage(TranslateLanguage.IRISH, "İrlandaca", "Gaeilge", "🇮🇪"),
        SupportedLanguage(TranslateLanguage.ICELANDIC, "İzlandaca", "Íslenska", "🇮🇸"),
        SupportedLanguage(TranslateLanguage.AFRIKAANS, "Afrikaanca", "Afrikaans", "🇿🇦"),
        SupportedLanguage(TranslateLanguage.SWAHILI, "Svahili", "Kiswahili", "🇰🇪"),
        SupportedLanguage(TranslateLanguage.TAGALOG, "Tagalog", "Tagalog", "🇵🇭"),
        SupportedLanguage(TranslateLanguage.WELSH, "Galce", "Cymraeg", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
        SupportedLanguage(TranslateLanguage.GALICIAN, "Galiçyaca", "Galego", "🇪🇸"),
        SupportedLanguage(TranslateLanguage.GUJARATI, "Guceratça", "ગુજરાતી", "🇮🇳"),
        SupportedLanguage(TranslateLanguage.KANNADA, "Kannada", "ಕನ್ನಡ", "🇮🇳"),
        SupportedLanguage(TranslateLanguage.MARATHI, "Marathi", "मराठी", "🇮🇳"),
        SupportedLanguage(TranslateLanguage.TAMIL, "Tamilce", "தமிழ்", "🇮🇳"),
        SupportedLanguage(TranslateLanguage.TELUGU, "Telugu", "తెలుగు", "🇮🇳"),
        // Official ML Kit list (developers.google.com/ml-kit/language/translation/
        // translation-language-support) has 59 languages — these two completed it
        SupportedLanguage(TranslateLanguage.HAITIAN_CREOLE, "Haiti Kreyolu", "Kreyòl ayisyen", "🇭🇹"),
        SupportedLanguage(TranslateLanguage.MALTESE, "Maltaca", "Malti", "🇲🇹"),
        SupportedLanguage(TranslateLanguage.ESPERANTO, "Esperanto", "Esperanto", "🌐")
    )

    val DEFAULT_SOURCE: SupportedLanguage = ALL_LANGUAGES.first { it.code == TranslateLanguage.TURKISH }
    val DEFAULT_TARGET: SupportedLanguage = ALL_LANGUAGES.first { it.code == TranslateLanguage.ENGLISH }

    fun findByCode(code: String): SupportedLanguage {
        return ALL_LANGUAGES.find { it.code.equals(code, ignoreCase = true) } ?: DEFAULT_TARGET
    }

    fun getFavoriteLanguages(context: Context): List<SupportedLanguage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_FAVORITES, null)
        if (saved == null || saved.isEmpty()) {
            val defaults = listOf(
                TranslateLanguage.TURKISH,
                TranslateLanguage.ENGLISH,
                TranslateLanguage.GERMAN,
                TranslateLanguage.RUSSIAN,
                TranslateLanguage.ARABIC,
                TranslateLanguage.FRENCH,
                TranslateLanguage.SPANISH,
                TranslateLanguage.JAPANESE
            )
            return ALL_LANGUAGES.filter { defaults.contains(it.code) }
        }
        return ALL_LANGUAGES.filter { saved.contains(it.code) }
    }

    fun toggleFavorite(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_FAVORITES, null)?.toMutableSet()
            ?: mutableSetOf(
                TranslateLanguage.TURKISH,
                TranslateLanguage.ENGLISH,
                TranslateLanguage.GERMAN,
                TranslateLanguage.RUSSIAN,
                TranslateLanguage.ARABIC,
                TranslateLanguage.FRENCH,
                TranslateLanguage.SPANISH,
                TranslateLanguage.JAPANESE
            )
        if (current.contains(code)) {
            if (current.size > 1) current.remove(code)
        } else {
            current.add(code)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }
}
