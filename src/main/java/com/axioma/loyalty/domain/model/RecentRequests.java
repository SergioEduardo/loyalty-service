package com.axioma.loyalty.domain.model;

/** Solicitudes dentro de la ventana y segundos hasta que la más antigua salga de ella. */
public record RecentRequests(int requests, long retryAfterSeconds) {
}
