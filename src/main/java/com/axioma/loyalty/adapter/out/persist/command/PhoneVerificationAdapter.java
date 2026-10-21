package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.IssuedVerification;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.model.PhoneVerificationState;
import com.axioma.loyalty.domain.model.RecentRequests;
import com.axioma.loyalty.domain.model.VerificationChannel;
import com.axioma.loyalty.domain.model.VerificationAttempt;
import com.axioma.loyalty.domain.port.out.PhoneVerificationRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Vigencia, conteo e intentos con el reloj de la base. */
@Component
@RequiredArgsConstructor
public class PhoneVerificationAdapter implements PhoneVerificationRepository {

    private static final String LOCK_PHONE = SqlResource.load("sql/verification/lock-phone.sql");
    private static final String COUNT_RECENT = SqlResource.load("sql/verification/count-recent.sql");
    private static final String INSERT = SqlResource.load("sql/verification/insert.sql");
    private static final String FIND = SqlResource.load("sql/verification/find.sql");
    private static final String RECORD_ATTEMPT = SqlResource.load("sql/verification/record-attempt.sql");
    private static final String MARK_VERIFIED = SqlResource.load("sql/verification/mark-verified.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> lockPhone(UUID tenantId, PhoneE164 phone) {
        return databaseClient.sql(LOCK_PHONE)
                .bind("lockKey", "phone-verification|" + tenantId + "|" + phone.value())
                .map(row -> 1)
                .one()
                .then();
    }

    @Override
    public Mono<RecentRequests> countRecent(UUID tenantId, PhoneE164 phone, int windowMinutes) {
        return databaseClient.sql(COUNT_RECENT)
                .bind("tenantId", tenantId)
                .bind("phone", phone.value())
                .bind("windowMinutes", windowMinutes)
                .map(row -> new RecentRequests(row.get("requests", Integer.class),
                        row.get("retry_after_seconds", Integer.class)))
                .one();
    }

    @Override
    public Mono<IssuedVerification> create(UUID tenantId, PhoneE164 phone, String codeHash,
                                           VerificationChannel channel, int ttlMinutes) {
        return databaseClient.sql(INSERT)
                .bind("tenantId", tenantId)
                .bind("phone", phone.value())
                .bind("codeHash", codeHash)
                .bind("channel", channel.value())
                .bind("ttlMinutes", ttlMinutes)
                .map(row -> new IssuedVerification(row.get("id", UUID.class),
                        row.get("expires_at", OffsetDateTime.class).toInstant()))
                .one();
    }

    @Override
    public Mono<PhoneVerificationState> find(UUID verificationId, UUID tenantId, PhoneE164 phone) {
        return databaseClient.sql(FIND)
                .bind("id", verificationId)
                .bind("tenantId", tenantId)
                .bind("phone", phone.value())
                .map(row -> new PhoneVerificationState(
                        row.get("id", UUID.class),
                        row.get("attempts", Integer.class),
                        Boolean.TRUE.equals(row.get("verified", Boolean.class)),
                        Boolean.TRUE.equals(row.get("expired", Boolean.class))))
                .one();
    }

    @Override
    public Mono<VerificationAttempt> recordAttempt(UUID verificationId, UUID tenantId, PhoneE164 phone,
                                                   int maxAttempts) {
        return databaseClient.sql(RECORD_ATTEMPT)
                .bind("id", verificationId)
                .bind("tenantId", tenantId)
                .bind("phone", phone.value())
                .bind("maxAttempts", maxAttempts)
                .map(row -> new VerificationAttempt(row.get("attempts", Integer.class),
                        row.get("code_hash", String.class)))
                .one();
    }

    @Override
    public Mono<Void> markVerified(UUID verificationId) {
        return databaseClient.sql(MARK_VERIFIED).bind("id", verificationId).fetch().rowsUpdated().then();
    }
}
