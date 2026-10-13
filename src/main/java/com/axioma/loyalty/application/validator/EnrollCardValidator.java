package com.axioma.loyalty.application.validator;

import com.axioma.loyalty.application.command.EnrollCardCommand;
import com.axioma.loyalty.domain.model.EnrollmentRequest;
import com.axioma.loyalty.domain.model.error.ErrorCode;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Nombre: texto plano de 1 a 80 caracteres (recortado; sin caracteres de control ni etiquetas) o
 * INVALID_NAME. Teléfono normalizable a E.164 o INVALID_PHONE. Código de 6 dígitos y verificationId con
 * forma de UUID, o INVALID_CODE (sin decir cuál de los dos).
 */
@Component
public class EnrollCardValidator {

    public static final int NAME_MAX_LENGTH = 80;
    private static final Pattern CODE = Pattern.compile("^[0-9]{6}$");

    public Mono<EnrollmentRequest> validate(EnrollCardCommand command) {
        return Mono.fromCallable(() -> {
            String name = cleanName(command.name());
            var phone = PhoneRules.normalize(command.phone());
            String code = command.code() == null ? "" : command.code().strip();
            if (!CODE.matcher(code).matches()) {
                throw new LoyaltyException(ErrorCode.INVALID_CODE);
            }
            return new EnrollmentRequest(command.slug(), phone, name, parseId(command.verificationId()), code);
        });
    }

    static String cleanName(String raw) {
        String name = raw == null ? "" : raw.strip();
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > NAME_MAX_LENGTH
                || name.codePoints().anyMatch(EnrollCardValidator::forbidden)) {
            throw new LoyaltyException(ErrorCode.INVALID_NAME);
        }
        return name;
    }

    /** Controles, etiquetas y controles de dirección bidi (U+202A–U+202E, U+2066–U+2069), que disfrazan texto. */
    private static boolean forbidden(int c) {
        return Character.isISOControl(c) || c == '<' || c == '>'
                || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069);
    }

    private static UUID parseId(String raw) {
        try {
            if (raw != null && raw.strip().length() == 36) {
                return UUID.fromString(raw.strip());
            }
        } catch (IllegalArgumentException ignored) {
            // cae en INVALID_CODE
        }
        throw new LoyaltyException(ErrorCode.INVALID_CODE);
    }
}
