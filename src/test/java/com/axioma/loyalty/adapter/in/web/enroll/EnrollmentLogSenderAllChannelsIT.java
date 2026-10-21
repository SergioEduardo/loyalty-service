package com.axioma.loyalty.adapter.in.web.enroll;

import java.util.List;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * VERIFICATION_SENDER=log con APP_ENV=dev y VERIFICATION_ENABLED_CHANNELS vacía: el modo log habilita todos
 * los canales (whatsapp, sms) y los errores de canal los listan. Se omite sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.enroll.otp-hash-salt=" + EnrollmentItSupport.SALT,
                "app.enroll.verification-sender=log",
                "app.enroll.verification-enabled-channels=",
                "app.enroll.app-env=dev"
        })
class EnrollmentLogSenderAllChannelsIT extends LogSenderChannelErrorsSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        database(registry);
    }

    @Override
    List<String> expectedChannels() {
        return List.of("whatsapp", "sms");
    }

    @Override
    String channelNotEnabled() {
        return "telegram";
    }
}
