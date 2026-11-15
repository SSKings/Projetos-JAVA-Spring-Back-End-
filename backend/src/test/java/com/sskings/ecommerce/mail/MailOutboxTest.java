package com.sskings.ecommerce.mail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** ADR-025: e-mail só depois do commit, falha vira log, log sem token nem endereço completo. */
@ExtendWith(OutputCaptureExtension.class)
class MailOutboxTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final List<Runnable> queued = new ArrayList<>();

    private MailOutbox outbox(boolean enabled, boolean async) {
        return new MailOutbox(sender, new MailProperties(enabled, "loja@gmail.com", "https://loja.com.br", async, null), queued::add);
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("Fora de transação: envia na hora (texto puro, remetente configurado)")
    void sendsImmediately() {
        outbox(true, false).send("ana@teste.com", "Assunto", "Corpo");
        var msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(msg.capture());
        assertThat(msg.getValue().getFrom()).isEqualTo("loja@gmail.com");
        assertThat(msg.getValue().getTo()).containsExactly("ana@teste.com");
        assertThat(msg.getValue().getSubject()).isEqualTo("Assunto");
        assertThat(msg.getValue().getText()).isEqualTo("Corpo");
    }

    @Test
    @DisplayName("Dentro de transação: só envia depois do commit; rollback não envia")
    void onlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        outbox(true, false).send("ana@teste.com", "Assunto", "Corpo");
        verifyNoInteractions(sender);
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));   // rollback: nada
        verifyNoInteractions(sender);
        syncs.forEach(TransactionSynchronization::afterCommit);
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Assíncrono: o envio vai para a thread de e-mail, não roda na requisição")
    void async() {
        outbox(true, true).send("ana@teste.com", "Assunto", "Corpo");
        verifyNoInteractions(sender);
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SMTP fora do ar: só log (mascarado, sem o corpo com o link); desligado: não envia")
    void failuresAndDisabled(CapturedOutput output) {
        doThrow(new MailSendException("connection refused")).when(sender).send(any(SimpleMailMessage.class));
        outbox(true, false).send("ana.souza@teste.com", "Redefinição de senha", "link com token-secreto-123");
        assertThat(output).contains("não enviado", "a***@teste.com").doesNotContain("ana.souza@teste.com", "token-secreto-123");

        reset(sender);
        outbox(false, false).send("ana@teste.com", "Assunto", "Corpo");
        verifyNoInteractions(sender);
        assertThat(output).contains("app.mail.enabled=false");
    }
}
