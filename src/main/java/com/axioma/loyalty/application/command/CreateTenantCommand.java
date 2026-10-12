package com.axioma.loyalty.application.command;

import com.axioma.loyalty.application.validator.TextRules;

/** Alta de un negocio. Los textos llegan recortados; los tiempos sin valor toman su default. */
public record CreateTenantCommand(
        String slug,
        String name,
        String description,
        String brandColor,
        Integer pointsGoal,
        String rewardText,
        Integer cooldownSeconds,
        Integer undoWindowSeconds) {

    public static final int DEFAULT_COOLDOWN_SECONDS = 0;
    public static final int DEFAULT_UNDO_WINDOW_SECONDS = 300;

    public CreateTenantCommand {
        slug = TextRules.clean(slug);
        name = TextRules.clean(name);
        description = TextRules.clean(description);
        brandColor = TextRules.clean(brandColor);
        rewardText = TextRules.clean(rewardText);
        cooldownSeconds = cooldownSeconds == null ? DEFAULT_COOLDOWN_SECONDS : cooldownSeconds;
        undoWindowSeconds = undoWindowSeconds == null ? DEFAULT_UNDO_WINDOW_SECONDS : undoWindowSeconds;
    }
}
