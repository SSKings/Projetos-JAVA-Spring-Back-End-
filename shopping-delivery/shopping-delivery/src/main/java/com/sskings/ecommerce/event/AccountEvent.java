package com.sskings.ecommerce.event;

/** Eventos de conta que geram e-mail (ADR-025). Publicados dentro da transação; o e-mail sai depois do commit. */
public sealed interface AccountEvent {

    /** Conta criada: manda o link de confirmação do e-mail. */
    record Registered(Long userId) implements AccountEvent {}

    /** Mudança de segurança (senha, MFA): avisa o titular, se o e-mail dele está confirmado. */
    record SecurityChange(Long userId, String what) implements AccountEvent {}
}
