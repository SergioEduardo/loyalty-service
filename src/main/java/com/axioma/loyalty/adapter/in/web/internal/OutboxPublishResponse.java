package com.axioma.loyalty.adapter.in.web.internal;

import com.axioma.loyalty.domain.model.PublishReport;
import com.fasterxml.jackson.annotation.JsonInclude;

/** disabled solo aparece (true) cuando no hay tópico configurado. */
public record OutboxPublishResponse(
        int claimed,
        int published,
        int failed,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean disabled) {

    public static OutboxPublishResponse from(PublishReport report) {
        return new OutboxPublishResponse(report.claimed(), report.published(), report.failed(),
                report.disabled() ? Boolean.TRUE : null);
    }
}
