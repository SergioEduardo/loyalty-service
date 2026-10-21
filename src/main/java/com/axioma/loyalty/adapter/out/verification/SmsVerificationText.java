package com.axioma.loyalty.adapter.out.verification;

import java.text.Normalizer;

/**
 * Texto del SMS: corto, sin acentos ni enlaces, para que quepa en un solo segmento GSM-7 (160
 * caracteres). El nombre del negocio se translitera a ASCII (sin diacríticos: "Café" -> "Cafe"), se
 * filtra a letras, números y espacios (así tampoco puede formar un enlace) y se recorta a 30 caracteres.
 */
final class SmsVerificationText {

    static final int MAX_NAME_LENGTH = 30;
    static final int MAX_LENGTH = 160;

    private SmsVerificationText() {
    }

    static String of(String businessName, String code, int validMinutes) {
        String name = sanitizeName(businessName);
        String prefix = name.isEmpty() ? "Tu codigo" : name + ": tu codigo";
        return prefix + " es " + code + ". Vence en " + validMinutes + " min. No lo compartas.";
    }

    static String sanitizeName(String businessName) {
        if (businessName == null) {
            return "";
        }
        String ascii = Normalizer.normalize(businessName, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String filtered = ascii.replaceAll("\\s+", " ").replaceAll("[^A-Za-z0-9 ]", "")
                .replaceAll(" {2,}", " ").strip();
        return filtered.length() <= MAX_NAME_LENGTH ? filtered : filtered.substring(0, MAX_NAME_LENGTH).strip();
    }
}
