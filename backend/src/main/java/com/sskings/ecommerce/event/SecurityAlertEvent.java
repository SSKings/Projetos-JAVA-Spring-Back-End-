package com.sskings.ecommerce.event;

/**
 * Evento de segurança que os admins precisam saber na hora (revisão 4): ataque de senha em andamento, alguém
 * promovido a admin, admin bloqueado. O bot do Telegram (se ligado) avisa todos os admins depois do commit.
 * O texto não pode levar dados pessoais além do email mascarado.
 */
public record SecurityAlertEvent(String text) {}
