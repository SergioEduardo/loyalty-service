package com.axioma.loyalty.application.handler;

/** Un handler es un caso de uso. Con WebFlux, R es Mono o Flux. */
public interface Handler<C, R> {
    R handle(C command);
}
