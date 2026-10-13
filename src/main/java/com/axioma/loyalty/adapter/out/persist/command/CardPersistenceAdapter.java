package com.axioma.loyalty.adapter.out.persist.command;

import com.axioma.loyalty.adapter.out.persist.SqlResource;
import com.axioma.loyalty.domain.model.Card;
import com.axioma.loyalty.domain.model.PhoneE164;
import com.axioma.loyalty.domain.port.out.CardRepository;
import io.r2dbc.spi.Readable;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CardPersistenceAdapter implements CardRepository {

    private static final String INSERT_IF_ABSENT = SqlResource.load("sql/card/insert-if-absent.sql");
    private static final String FIND_BY_PHONE = SqlResource.load("sql/card/find-by-phone.sql");

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Card> insertIfAbsent(UUID tenantId, PhoneE164 phone, String holderName) {
        return databaseClient.sql(INSERT_IF_ABSENT)
                .bind("tenantId", tenantId)
                .bind("phone", phone.value())
                .bind("holderName", holderName)
                .map(CardPersistenceAdapter::toCard)
                .one();
    }

    @Override
    public Mono<Card> findByPhone(UUID tenantId, PhoneE164 phone) {
        return databaseClient.sql(FIND_BY_PHONE)
                .bind("tenantId", tenantId)
                .bind("phone", phone.value())
                .map(CardPersistenceAdapter::toCard)
                .one();
    }

    private static Card toCard(Readable row) {
        return new Card(row.get("id", UUID.class), row.get("tenant_id", UUID.class),
                row.get("holder_name", String.class), row.get("points", Integer.class));
    }
}
