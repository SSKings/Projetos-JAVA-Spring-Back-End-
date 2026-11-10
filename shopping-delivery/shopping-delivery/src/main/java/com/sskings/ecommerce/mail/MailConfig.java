package com.sskings.ecommerce.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /** Uma thread: os e-mails saem na ordem e um SMTP lento não acumula threads (como os avisos do Telegram). */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService mailExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mail-outbox");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public MailOutbox mailOutbox(JavaMailSender sender, MailProperties props, ExecutorService mailExecutor) {
        return new MailOutbox(sender, props, mailExecutor);
    }
}
