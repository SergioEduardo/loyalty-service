package com.axioma.loyalty.application.command;

import java.util.UUID;

public record IssueActivationLinkCommand(UUID tenantId, UUID userId) {
}
