package com.npk.kfv

import java.text.MessageFormat
import java.util.*

object ApplicationMessages {

    private class ResourceBundleMessageSource(baseName: String, private val locale: Locale) {

        private val resourceBundle: ResourceBundle = ResourceBundle.getBundle(baseName, locale)

        fun getMessage(key: String): String = resourceBundle.getString(key)

        fun getMessage(key: String, args: Array<out Any>): String =
            MessageFormat(getMessage(key), locale).synchronized { it.format(args) }

    }

    private val messageSource by lazy { ResourceBundleMessageSource("i18n.applicationMessages", ApplicationPrefs.locale) }

    val availableLocales: List<Locale> by lazy { listOf(Locale.ENGLISH) }

    operator fun get(key: String): String = messageSource.getMessage(key)

    operator fun get(key: String, vararg args: Any): String = messageSource.getMessage(key, args)

    val ok: String inline get() = get("button.ok")
    val cancel: String inline get() = get("button.cancel")

    val randomQuote: String inline get() = get("franz.kafka.quotes").split("\n").random()

}
