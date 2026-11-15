package com.sskings.ecommerce.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /** E-mails esperando o SMTP. Muito acima do normal (o Gmail aceita ~500/dia); só enche com o SMTP fora do ar. */
    static final int QUEUE_CAPACITY = 1_000;

    /** Uma thread: os e-mails saem na ordem e um SMTP lento não acumula threads (como os avisos do Telegram). */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService mailExecutor() {
        return boundedExecutor(QUEUE_CAPACITY);
    }

    /**
     * Fila com tamanho máximo (revisão 6, R6-5): com o SMTP fora do ar (10 s de timeout por e-mail) e alguém pedindo
     * links sem parar, uma fila sem limite cresceria até derrubar a API por falta de memória. Cheia, o e-mail novo é
     * descartado com aviso no log — o cliente pode pedir de novo; a requisição nunca falha por causa disso.
     */
    static ExecutorService boundedExecutor(int capacity) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(capacity),
                r -> {
                    Thread t = new Thread(r, "mail-outbox");
                    t.setDaemon(true);
                    return t;
                },
                (task, executor) -> log.warn("Fila de e-mails cheia ({} esperando o SMTP): e-mail descartado", capacity));
    }

    @Bean
    public MailOutbox mailOutbox(JavaMailSender sender, MailProperties props, ExecutorService mailExecutor) {
        return new MailOutbox(sender, props, mailExecutor);
    }
}
