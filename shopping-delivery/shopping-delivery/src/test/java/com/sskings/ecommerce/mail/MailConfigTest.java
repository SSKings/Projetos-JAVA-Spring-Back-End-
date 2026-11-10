package com.sskings.ecommerce.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MailConfigTest {

    @Test
    @DisplayName("Revisão 6 (R6-5): fila de e-mails tem tamanho máximo; cheia, descarta sem lançar exceção")
    void boundedQueue() throws Exception {
        ExecutorService executor = MailConfig.boundedExecutor(2);
        CountDownLatch smtpStuck = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        try {
            executor.execute(() -> {                     // a thread fica presa no "SMTP"
                try { smtpStuck.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                delivered.incrementAndGet();
            });
            for (int i = 0; i < 10; i++) {
                assertThatCode(() -> executor.execute(delivered::incrementAndGet)).doesNotThrowAnyException();
            }
            assertThat(((ThreadPoolExecutor) executor).getQueue()).hasSize(2);
            smtpStuck.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(delivered.get()).isEqualTo(3);        // o preso + os 2 que couberam na fila
    }

    @Test
    @DisplayName("Bean da aplicação: uma thread daemon \"mail-outbox\" e fila limitada")
    void appExecutor() throws Exception {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) new MailConfig().mailExecutor();
        try {
            assertThat(executor.getMaximumPoolSize()).isEqualTo(1);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(MailConfig.QUEUE_CAPACITY);
            String[] name = new String[1];
            boolean[] daemon = new boolean[1];
            executor.submit(() -> { name[0] = Thread.currentThread().getName(); daemon[0] = Thread.currentThread().isDaemon(); }).get();
            assertThat(name[0]).isEqualTo("mail-outbox");
            assertThat(daemon[0]).isTrue();
        } finally {
            executor.shutdown();
        }
    }
}
