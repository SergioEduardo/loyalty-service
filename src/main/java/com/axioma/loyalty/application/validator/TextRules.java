package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;

/** Reglas comunes para textos visibles: recortados, sin caracteres de control y con largo máximo. */
public final class TextRules {

    private TextRules() {
    }

    /** Recorta espacios; un texto en blanco cuenta como ausente. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static void required(String value, int maxLength) {
        if (value == null) {
            throw new LoyaltyException(ErrorCode.INVALID_TEXT);
        }
        optional(value, maxLength);
    }

    public static void optional(String value, int maxLength) {
        if (value == null) {
            return;
        }
        if (value.isBlank() || value.codePointCount(0, value.length()) > maxLength || hasControlChars(value)) {
            throw new LoyaltyException(ErrorCode.INVALID_TEXT);
        }
    }

    private static boolean hasControlChars(String value) {
        // Se permiten saltos de línea (descripción); el resto de controles no.
        return value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r');
    }
}
