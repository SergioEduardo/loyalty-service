package com.axioma.loyalty.adapter.out.persist.command;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Solo las columnas que administra esta API; las de bloqueo e inicio con Google se quedan con
 * su valor por defecto y no se tocan al actualizar. Sin toString a propósito (lleva el hash).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "loyalty", name = "users")
public class UserEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Column("tenant_id")
    private UUID tenantId;
    private String name;
    private String username;
    @Column("credential_hash")
    private String credentialHash;
    private String role;
    @Column("is_active")
    private boolean active;

    @Transient
    private boolean newEntity;

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
