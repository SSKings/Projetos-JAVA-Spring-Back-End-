package com.sskings.ecommerce.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * Long polling da Bot API numa thread própria, ligada ao ciclo de vida da aplicação. Não precisa de URL pública
 * (sem webhook): funciona em dev, na rede local e atrás do nginx de produção sem abrir nada na borda.
 * Falha de rede não derruba a API: tenta de novo com espera crescente (até 60 s).
 */
@Slf4j
public class TelegramBotPoller implements SmartLifecycle {

    static final long MAX_BACKOFF_SECONDS = 60;

    private final TelegramClient client;
    private final TelegramAdminBot bot;
    private volatile boolean running;
    private Thread thread;
    private long offset;
    private boolean backlogSkipped;

    public TelegramBotPoller(TelegramClient client, TelegramAdminBot bot) {
        this.client = client;
        this.bot = bot;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "telegram-admin-bot");
        thread.setDaemon(true);
        thread.start();
        log.info("Telegram: bot de administração iniciado (long polling)");
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        int failures = 0;
        while (running) {
            try {
                pollOnce();
                failures = 0;
            } catch (RuntimeException e) {
                if (!running) break;
                failures++;
                long wait = backoffSeconds(failures);
                log.warn("Telegram: falha ao buscar mensagens ({}). Nova tentativa em {} s", e.getMessage(), wait);
                try {
                    Thread.sleep(wait * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Uma rodada: na primeira descarta o acumulado; depois busca, confirma (offset) e trata cada update. */
    void pollOnce() {
        if (!backlogSkipped) {
            offset = client.skipBacklog();
            backlogSkipped = true;
        }
        for (JsonNode update : client.getUpdates(offset)) {
            offset = update.path("update_id").asLong() + 1;
            try {
                bot.handle(update);
            } catch (RuntimeException e) {
                // um comando com erro não pode travar a fila: o offset já andou, o próximo update segue
                log.error("Telegram: erro ao tratar o update {}", update.path("update_id").asLong(), e);
            }
        }
    }

    static long backoffSeconds(int failures) {
        return Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(failures, 6));
    }

    long offset() {
        return offset;
    }
}
