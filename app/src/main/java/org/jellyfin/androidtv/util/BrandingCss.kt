package org.jellyfin.androidtv.util

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * Server-supplied branding colors parsed from the `:root` block of the Jellyfin
 * server's `CustomCss` branding option (see docs/SERVER_THEMING.md).
 */
@Serializable
data class BrandingColors(
	val background: String,
	val surface: String,
	val onBackground: String,
	val primary: String,
	val secondary: String,
)

private val rootBlockRegex = Regex(":root\\s*\\{([^}]*)\\}")
private val variableRegex = Regex("(--[A-Za-z0-9_-]+)\\s*:\\s*([^;]+);")
private val varReferenceRegex = Regex("var\\((--[A-Za-z0-9_-]+)\\)")
private val rgbRegex = Regex("rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)")
private val rgbaRegex = Regex("rgba\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*([\\d.]+)\\s*\\)")

/**
 * Parse the `--jf-palette-*` CSS custom properties out of a `:root` block and
 * normalize them into [BrandingColors]. Returns null when no usable palette can
 * be derived.
 */
fun parseBrandingColors(css: String?): BrandingColors? {
	if (css.isNullOrBlank()) return null

	val rootBlock = rootBlockRegex.find(css)?.groupValues?.get(1) ?: return null

	val variables = variableRegex.findAll(rootBlock)
		.associate { match -> match.groupValues[1] to match.groupValues[2].trim() }

	fun resolve(raw: String, depth: Int = 0): String? {
		if (depth > 5) return null
		val trimmed = raw.trim()
		val match = varReferenceRegex.find(trimmed) ?: return trimmed
		val value = variables[match.groupValues[1]] ?: return null
		return resolve(trimmed.replaceRange(match.range, value), depth + 1)
	}

	val background = variables["--jf-palette-background-default"]?.let { resolve(it) }?.let(::normalize)
	val surface = variables["--jf-palette-background-paper"]?.let { resolve(it) }?.let(::normalize)
	val onBackground = variables["--jf-palette-text-primary"]?.let { resolve(it) }?.let(::normalize)
	val primary = variables["--jf-palette-primary-main"]?.let { resolve(it) }?.let(::normalize)
	val secondary = variables["--jf-palette-secondary-main"]?.let { resolve(it) }?.let(::normalize)

	if (background == null || surface == null || onBackground == null || primary == null || secondary == null) return null

	return BrandingColors(
		background = background,
		surface = surface,
		onBackground = onBackground,
		primary = primary,
		secondary = secondary,
	)
}

private fun normalize(value: String): String? {
	val v = value.trim()

	if (v.startsWith("#")) {
		val hex = v.substring(1)
		return when (hex.length) {
			6 -> "#FF${hex.uppercase()}"
			8 -> "#${hex.uppercase()}"
			else -> null
		}
	}

	rgbRegex.matchEntire(v)?.let { match ->
		val r = match.groupValues[1].toIntOrNull() ?: return null
		val g = match.groupValues[2].toIntOrNull() ?: return null
		val b = match.groupValues[3].toIntOrNull() ?: return null
		if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
		return "#FF${r.toHexByte()}${g.toHexByte()}${b.toHexByte()}"
	}

	rgbaRegex.matchEntire(v)?.let { match ->
		val r = match.groupValues[1].toIntOrNull() ?: return null
		val g = match.groupValues[2].toIntOrNull() ?: return null
		val b = match.groupValues[3].toIntOrNull() ?: return null
		val alpha = parseAlpha(match.groupValues[4]) ?: return null
		if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
		return "#${alpha.toHexByte()}${r.toHexByte()}${g.toHexByte()}${b.toHexByte()}"
	}

	return null
}

private fun parseAlpha(raw: String): Int? {
	val value = raw.trim().toDoubleOrNull() ?: return null
	val alpha = if (value <= 1.0) {
		(value * 255).roundToInt()
	} else {
		value.toInt()
	}
	return if (alpha in 0..255) alpha else null
}

private fun Int.toHexByte(): String = toString(16).padStart(2, '0').uppercase()
