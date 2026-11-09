package com.sskings.ecommerce.service;

/**
 * Confirmação de identidade na hora de uma ação sensível (step-up), além da sessão já aberta.
 * <ul>
 *   <li>{@link #password}: senha + código MFA (se ativo) — painel.</li>
 *   <li>{@link #mfaCode}: só o código do app autenticador — bot do Telegram, cuja sessão já foi aberta com MFA
 *       mas dura 12 h. Exige MFA ativo (sem MFA, não há o que pedir no bot).</li>
 * </ul>
 * Verificada por {@link AccountService#verifyStepUp}: falhas contam para o bloqueio de reautenticação da conta.
 */
public record StepUp(String password, String mfaCode, boolean mfaOnly) {

    public static StepUp password(String password, String mfaCode) {
        return new StepUp(password, mfaCode, false);
    }

    public static StepUp mfaCode(String code) {
        return new StepUp(null, code, true);
    }
}
