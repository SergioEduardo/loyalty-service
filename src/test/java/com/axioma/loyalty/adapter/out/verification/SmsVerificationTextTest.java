package com.axioma.loyalty.adapter.out.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Texto del SMS: ASCII, sin enlaces, un solo segmento GSM-7. */
class SmsVerificationTextTest {

    /** Alfabeto básico GSM 03.38 (sin la tabla de extensión, que cuenta doble). */
    private static final String GSM7_BASIC = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";

    @Test
    void textHasTheCodeAndValidity() {
        assertThat(SmsVerificationText.of("Mi Cafe", "042137", 10))
                .isEqualTo("Mi Cafe: tu codigo es 042137. Vence en 10 min. No lo compartas.");
    }

    @Test
    void accentedNameIsTransliteratedToAscii() {
        assertThat(SmsVerificationText.sanitizeName("Café Ñandú Pingüino Crème Brûlée")).isEqualTo("Cafe Nandu Pinguino Creme Brul");
        assertThat(SmsVerificationText.of("Panadería La Señora", "123456", 10))
                .isEqualTo("Panaderia La Senora: tu codigo es 123456. Vence en 10 min. No lo compartas.")
                .matches("[\\x20-\\x7E]+");
    }

    @Test
    void nameKeepsOnlyLettersDigitsAndSpacesSoItCannotBeALink() {
        assertThat(SmsVerificationText.sanitizeName("https://café.example.com/?x=1")).isEqualTo("httpscafeexamplecomx1");
        assertThat(SmsVerificationText.sanitizeName("  Tacos\t\"El  Güero\"\n#1 😀 ")).isEqualTo("Tacos El Guero 1");
        assertThat(SmsVerificationText.sanitizeName("www.mi-cafe.mx")).doesNotContain(".");
    }

    @Test
    void emptyOrUnusableNameFallsBackToGenericText() {
        for (String name : new String[]{null, "", "   ", "😀🎉", "..."}) {
            assertThat(SmsVerificationText.of(name, "123456", 10))
                    .isEqualTo("Tu codigo es 123456. Vence en 10 min. No lo compartas.");
        }
    }

    @Test
    void longestTextFitsInOneGsm7Segment() {
        String longName = "Ñ".repeat(10) + " Restaurante Bar y Cafetería Internacional Ñoño 2000 Plus";
        assertThat(SmsVerificationText.sanitizeName(longName)).hasSizeLessThanOrEqualTo(SmsVerificationText.MAX_NAME_LENGTH);
        // Peor caso: nombre de 30 caracteres y la vigencia más larga permitida (60 min).
        String text = SmsVerificationText.of("W".repeat(80), "999999", 60);
        assertThat(text).hasSizeLessThanOrEqualTo(SmsVerificationText.MAX_LENGTH);
        assertThat(SmsVerificationText.of(longName, "999999", 60)).hasSizeLessThanOrEqualTo(160);
        for (char c : text.toCharArray()) {
            assertThat(GSM7_BASIC.indexOf(c)).as("'%s' es GSM-7 básico", c).isNotNegative();
        }
        assertThat(SmsVerificationText.of(longName, "999999", 60).chars().allMatch(c -> GSM7_BASIC.indexOf(c) >= 0))
                .isTrue();
    }
}
