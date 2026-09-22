package org.jellyfin.androidtv.util

import android.content.Context
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Suppress("DEPRECATION")
val Context.locale: Locale
	get() = when {
		AndroidVersion.isAtLeastN -> resources.configuration.getLocales().get(0)
		else -> resources.configuration.locale
	}

// DateTimeFormatter is immutable and thread-safe, so cached instances can be shared safely.
private val dateFormatters = ConcurrentHashMap<Pair<FormatStyle, Locale>, DateTimeFormatter>()
private val timeFormatters = ConcurrentHashMap<Pair<FormatStyle, Locale>, DateTimeFormatter>()
private val weekdayFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

@JvmOverloads
fun Context.getDateFormatter(
	style: FormatStyle = FormatStyle.SHORT
): DateTimeFormatter = dateFormatters.computeIfAbsent(style to locale) {
	DateTimeFormatter.ofLocalizedDateTime(style).withLocale(locale)
}

@JvmOverloads
fun Context.getTimeFormatter(
	style: FormatStyle = FormatStyle.SHORT
): DateTimeFormatter = timeFormatters.computeIfAbsent(style to locale) {
	DateTimeFormatter.ofLocalizedTime(style).withLocale(locale)
}

val Context.weekdayFormatter: DateTimeFormatter
	get() = weekdayFormatters.computeIfAbsent(locale) {
		DateTimeFormatter.ofPattern("EE", it)
	}
