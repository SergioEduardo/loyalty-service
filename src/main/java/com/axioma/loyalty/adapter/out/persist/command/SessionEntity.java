package com.axioma.loyalty.adapter.out.persist.command;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Solo para crear la sesión; rotar y revocar van con SQL. Sin toString a propósito. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "loyalty", name = "sessions")
public class SessionEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Column("tenant_id")
    private UUID tenantId;
    @Column("user_id")
    private UUID userId;
    @Column("refresh_token_hash")
    private String refreshTokenHash;
    @Column("expires_at")
    private Instant expiresAt;

    @Transient
    private boolean newEntity;

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
