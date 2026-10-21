package com.axioma.loyalty.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.axioma.loyalty.config.EnrollProperties;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.error.LoyaltyException;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class HmacVerificationCodeHasherTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final PhoneE164 PHONE = new PhoneE164("+525512345678");

    private static EnrollProperties properties(String salt) {
        return new EnrollProperties(null, null, null, null, null, null, salt, null, null, null);
    }

    private final HmacVerificationCodeHasher hasher =
            new HmacVerificationCodeHasher(properties("sal-de-prueba-para-hmac-de-al-menos-32-bytes"));

    @Test
    void knownVector() {
        // echo -n "11111111-1111-1111-1111-111111111111|+525512345678|123456" |
        //   openssl dgst -sha256 -hmac "sal-de-prueba-para-hmac-de-al-menos-32-bytes"
        assertThat(hasher.hash(TENANT, PHONE, "123456"))
                .isEqualTo("5149d649be82e7e345e99deabca5a455c550e09f9abadd8fb00136a8bb3f515f");
        assertThat(hasher.matches(hasher.hash(TENANT, PHONE, "123456"), TENANT, PHONE, "123456")).isTrue();
    }

    @Test
    void boundToTenantPhoneAndCode() {
        String hash = hasher.hash(TENANT, PHONE, "123456");
        assertThat(hasher.matches(hash, TENANT, PHONE, "123457")).isFalse();
        assertThat(hasher.matches(hash, UUID.randomUUID(), PHONE, "123456")).isFalse();
        assertThat(hasher.matches(hash, TENANT, new PhoneE164("+525512345679"), "123456")).isFalse();
        assertThat(hasher.matches(null, TENANT, PHONE, "123456")).isFalse();
        var other = new HmacVerificationCodeHasher(properties("otra-sal-de-prueba-de-al-menos-32-bytes!!"));
        assertThat(other.hash(TENANT, PHONE, "123456")).isNotEqualTo(hash);
    }

    @Test
    void withoutSaltIsUnavailable() {
        for (String salt : new String[]{null, "", "corta"}) {
            var unready = new HmacVerificationCodeHasher(properties(salt));
            StepVerifier.create(unready.ensureReady()).expectError(LoyaltyException.class).verify();
        }
    }

    @Test
    void generatorGivesSixUniformDigits() {
        var generator = new SecureVerificationCodeGenerator();
        var seen = new HashSet<String>();
        for (int i = 0; i < 2000; i++) {
            String code = generator.generate();
            assertThat(code).matches("[0-9]{6}");
            seen.add(code);
        }
        assertThat(seen.size()).isGreaterThan(1900);
    }
}
