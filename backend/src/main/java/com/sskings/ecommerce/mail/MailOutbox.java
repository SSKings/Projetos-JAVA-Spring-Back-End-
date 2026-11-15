package com.sskings.ecommerce.mail;

import com.sskings.ecommerce.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

/**
 * Fila de saída de e-mails (ADR-025).
 * <ul>
 *   <li>Dentro de uma transação, só envia <b>depois do commit</b>: rollback não manda e-mail de algo que não
 *       aconteceu (ex.: link de confirmação de um cadastro que falhou).</li>
 *   <li>Envia numa thread própria: o SMTP lento ou fora do ar não segura nem derruba a requisição — falha vira log.</li>
 *   <li>Texto puro (sem HTML): nada que o cliente digita (nome, motivo da devolução) vira marcação no e-mail.</li>
 *   <li>O log nunca leva o corpo (links com token) nem o endereço completo.</li>
 * </ul>
 */
@Slf4j
public class MailOutbox {

    private final JavaMailSender sender;
    private final MailProperties props;
    private final Executor executor;

    public MailOutbox(JavaMailSender sender, MailProperties props, Executor executor) {
        this.sender = sender;
        this.props = props;
        this.executor = executor;
    }

    public void send(String to, String subject, String body) {
        Runnable deliver = props.async() ? () -> executor.execute(() -> deliver(to, subject, body)) : () -> deliver(to, subject, body);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver.run();
                }
            });
        } else {
            deliver.run();
        }
    }

    void deliver(String to, String subject, String body) {
        if (!props.enabled()) {
            log.info("E-mail desligado (app.mail.enabled=false): \"{}\" para {} não enviado", subject, AuditService.maskEmail(to));
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(props.from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("E-mail \"{}\" enviado para {}", subject, AuditService.maskEmail(to));
        } catch (RuntimeException e) {
            log.warn("E-mail \"{}\" para {} não enviado: {}", subject, AuditService.maskEmail(to), e.getMessage());
        }
    }
}
