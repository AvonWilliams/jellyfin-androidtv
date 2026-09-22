package org.jellyfin.androidtv.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BrandingCssParserTest : FunSpec({
	test("parses the AVJEN :root sample into the expected branding colors") {
		val css = """
			:root {
			    --avjen-bg: #071827;
			    --avjen-bg-2: #0b2638;
			    --avjen-paper: #0b2232;
			    --avjen-gold: #f4c76b;
			    --avjen-gold-bright: #ffd98a;
			    --avjen-coral: #e97852;
			    --avjen-white: #f7f0df;
			    --avjen-text: rgba(247, 240, 223, 0.90);
			    --avjen-muted: rgba(247, 240, 223, 0.62);

			    --jf-palette-background-default: #071827;
			    --jf-palette-background-paper: #0b2232;
			    --jf-palette-text-primary: #f7f0df;
			    --jf-palette-text-secondary: var(--avjen-muted);
			    --jf-palette-primary-main: #f4c76b;
			    --jf-palette-primary-dark: #d99a4c;
			    --jf-palette-primary-light: #ffd98a;
			    --jf-palette-secondary-main: #e97852;
			    --jf-palette-action-hover: rgba(244, 199, 107, 0.08);
			    --jf-palette-action-focus: rgba(244, 199, 107, 0.12);
			    --jf-palette-divider: rgba(244, 199, 107, 0.12);
			    --jf-palette-AppBar-defaultBg: #071827;
			    --jf-palette-AppBar-transparentBg: rgba(7, 24, 39, 0.72);
			}
		""".trimIndent()

		parseBrandingColors(css) shouldBe BrandingColors(
			background = "#FF071827",
			surface = "#FF0B2232",
			onBackground = "#FFF7F0DF",
			primary = "#FFF4C76B",
			secondary = "#FFE97852",
		)
	}

	test("resolves var() indirection") {
		val css = """
			:root {
			    --x: #123456;
			    --jf-palette-background-default: var(--x);
			    --jf-palette-background-paper: #ffffff;
			    --jf-palette-text-primary: #000000;
			    --jf-palette-primary-main: #ff0000;
			    --jf-palette-secondary-main: #00ff00;
			}
		""".trimIndent()

		parseBrandingColors(css)?.background shouldBe "#FF123456"
	}

	test("returns null when --jf-palette-primary-main is missing") {
		val css = """
			:root {
			    --jf-palette-background-default: #071827;
			    --jf-palette-background-paper: #0b2232;
			    --jf-palette-text-primary: #f7f0df;
			    --jf-palette-secondary-main: #e97852;
			}
		""".trimIndent()

		parseBrandingColors(css) shouldBe null
	}

	test("returns null when css is null or blank") {
		parseBrandingColors(null) shouldBe null
		parseBrandingColors("") shouldBe null
		parseBrandingColors("   ") shouldBe null
	}
})
