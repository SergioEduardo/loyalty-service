package com.axioma.loyalty.domain.model;

import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.regex.Pattern;

/** Dirección pública del negocio: minúsculas, números y guiones; sin guion al inicio ni al final. */
public record Slug(String value) {

    public static final int MAX_LENGTH = 50;
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public Slug {
        if (!isValid(value)) {
            throw new LoyaltyException(ErrorCode.INVALID_SLUG);
        }
    }

    public static boolean isValid(String value) {
        return value != null && value.length() <= MAX_LENGTH && FORMAT.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
