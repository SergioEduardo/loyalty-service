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

/** El id lo genera la aplicación; isNew distingue el INSERT del UPDATE. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "loyalty", name = "tenants")
public class TenantEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private String slug;
    @Column("business_name")
    private String businessName;
    private String description;
    @Column("logo_url")
    private String logoUrl;
    @Column("brand_color")
    private String brandColor;
    @Column("points_goal")
    private int pointsGoal;
    @Column("reward_text")
    private String rewardText;
    @Column("cooldown_seconds")
    private int cooldownSeconds;
    @Column("undo_window_seconds")
    private int undoWindowSeconds;
    @Column("is_active")
    private boolean active;

    @Transient
    private boolean newEntity;

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
