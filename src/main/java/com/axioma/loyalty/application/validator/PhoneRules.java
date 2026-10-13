package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.regex.Pattern;

/**
 * Teléfono a E.164 con libphonenumber; sin código de país se toma México. Si no: INVALID_PHONE.
 * Acepta el antiguo prefijo móvil de México (+52 1 55 …), que se sigue escribiendo, y lo quita.
 */
public final class PhoneRules {

    public static final String DEFAULT_REGION = "MX";
    private static final int MAX_INPUT_LENGTH = 40;
    // Lo que acepta la columna (loyalty.phone_verifications / cards).
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneRules() {
    }

    public static PhoneE164 normalize(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_INPUT_LENGTH) {
            throw invalid();
        }
        try {
            var number = UTIL.parse(raw.strip(), DEFAULT_REGION);
            if (!UTIL.isValidNumber(number) && number.getCountryCode() == 52) {
                String national = UTIL.getNationalSignificantNumber(number);
                if (national.length() == 11 && national.charAt(0) == '1') {
                    number = UTIL.parse("+52" + national.substring(1), DEFAULT_REGION);
                }
            }
            if (!UTIL.isValidNumber(number)) {
                throw invalid();
            }
            String e164 = UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
            if (!E164.matcher(e164).matches()) {
                throw invalid();
            }
            return new PhoneE164(e164);
        } catch (NumberParseException e) {
            throw invalid();
        }
    }

    private static LoyaltyException invalid() {
        return new LoyaltyException(ErrorCode.INVALID_PHONE);
    }
}
