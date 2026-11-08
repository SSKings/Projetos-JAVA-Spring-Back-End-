package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.SecurityProperties;
import com.sskings.ecommerce.service.admin.AdminDashboardService;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import com.sskings.ecommerce.service.admin.AdminUserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bot de administração no Telegram (ADR-024). Só existe com app.telegram-bot.enabled=true; configuração inválida
 * (token ou lista de admins) impede o startup em vez de deixar o bot meio ligado.
 */
@Configuration
@EnableConfigurationProperties(TelegramBotProperties.class)
@ConditionalOnProperty(prefix = "app.telegram-bot", name = "enabled", havingValue = "true")
public class TelegramBotConfig {

    public TelegramBotConfig(TelegramBotProperties props) {
        var problems = props.problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid Telegram admin bot configuration:\n - " + String.join("\n - ", problems));
        }
    }

    @Bean
    public TelegramClient telegramClient(RestClient.Builder builder, TelegramBotProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(props.pollTimeoutSeconds() + 15L));   // > espera do long polling
        return new TelegramClient(builder.requestFactory(factory), props);
    }

    @Bean
    public TelegramAdminBot telegramAdminBot(TelegramClient client, TelegramBotProperties props, SecurityProperties security,
                                             UserRepository users, AdminOrderService orders, AdminUserService userService,
                                             AdminDashboardService dashboard) {
        return new TelegramAdminBot(client, props, security, users, orders, userService, dashboard);
    }

    @Bean
    public TelegramBotPoller telegramBotPoller(TelegramClient client, TelegramAdminBot bot) {
        return new TelegramBotPoller(client, bot);
    }

    /** Uma thread só: os avisos saem na ordem dos eventos e um Telegram lento não acumula threads. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService telegramNotificationExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "telegram-notify");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public TelegramOrderNotifier telegramOrderNotifier(TelegramClient client, TelegramBotProperties props, TelegramAdminBot bot,
                                                       UserRepository users, AdminOrderService orders,
                                                       ExecutorService telegramNotificationExecutor) {
        return new TelegramOrderNotifier(client, props, bot, users, orders, telegramNotificationExecutor);
    }
}
