package com.axioma.loyalty.domain.model.error;

/** Error que se resuelve esperando: la respuesta lleva Retry-After con estos segundos. */
public interface RetryAfter {

    long getRetryAfterSeconds();
}
