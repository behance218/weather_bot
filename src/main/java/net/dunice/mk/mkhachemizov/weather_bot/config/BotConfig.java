package net.dunice.mk.mkhachemizov.weather_bot.config;

import lombok.RequiredArgsConstructor;
import net.dunice.mk.mkhachemizov.weather_bot.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
public class BotConfig {
    private static final Logger log = LoggerFactory.getLogger(BotConfig.class);
    private final WeatherService weatherService;

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(weatherService);
        }
        catch (TelegramApiException exception) {
            log.error("При создании бота произошла ошибка", exception);
        }
    }
}
