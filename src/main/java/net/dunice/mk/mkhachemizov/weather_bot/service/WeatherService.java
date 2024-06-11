package net.dunice.mk.mkhachemizov.weather_bot.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import net.dunice.mk.mkhachemizov.weather_bot.config.properties.BotProperties;
import net.dunice.mk.mkhachemizov.weather_bot.entity.WeatherCache;
import net.dunice.mk.mkhachemizov.weather_bot.repository.WeatherRepository;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WeatherService extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private final BotProperties bot;
    private final WeatherRepository weatherRepository;
    private final OkHttpClient client = new OkHttpClient();
    @Value("${bot.openweathermap-api-key}")
    private String openWeatherMapApiKey;

    @Override
    public String getBotUsername() {
        return bot.name();
    }

    @Override
    public String getBotToken() {
        return bot.token();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendStartMessage(chatId);
            }
            else if (messageText.equals("Старт")) {
                sendWelcomeMessage(chatId);
            }
            else if (messageText.equals("Меню")) {
                sendMenuMessage(chatId);
            }
            else if (messageText.startsWith("Погода")) {
                String city = messageText.split(" ")[1];
                handleWeatherRequest(chatId, city);
            }
        }
    }

    private void sendStartMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Добро пожаловать! Нажмите кнопку 'Старт' для начала.");
        message.setReplyMarkup(getStartKeyboard());
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Не удалось создать кнопку Старт", e);
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Привет! Добро пожаловать в бота! Выберите действие:");
        message.setReplyMarkup(getMenuKeyboard());
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Не удалось отправить приветственное сообщение юзеру", e);
        }
    }

    private void sendMenuMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите действие:");
        message.setReplyMarkup(getMenuKeyboard());
        try {
            execute(message);
        }
        catch (TelegramApiException exception) {
            log.error("Не удалось отправить сообщение юзеру о его потенциальных действиях", exception);
        }
    }

    @NotNull
    private ReplyKeyboardMarkup getStartKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Старт"));
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    @NotNull
    private ReplyKeyboardMarkup getMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Погода"));
        row.add(new KeyboardButton("Инфо"));
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void handleWeatherRequest(long chatId, String city) {
        try {
            String weatherData = getWeatherFromCache(city);
            if (weatherData == null) {
                weatherData = fetchWeather(city);
                saveWeatherToCache(city, weatherData);
            }
            sendMessage(chatId, weatherData);
        }
        catch (Exception e) {
            log.error("Ошибка получения прогноза погоды");
            sendMessage(chatId, "Ошибка при получении прогноза погоды.");
        }
    }

    private String fetchWeather(String city) throws Exception {
        String url = String.format("http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric&lang=ru", city, openWeatherMapApiKey);
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        if (response.body() != null) {
            return response.body().string();
        }
        return null;
    }

    private void saveWeatherToCache(String city, String weatherData) {
        WeatherCache weatherCache = new WeatherCache();
        weatherCache.setCity(city);
        weatherCache.setWeatherData(weatherData);
        weatherCache.setTimestamp(Timestamp.from(Instant.now()));
        weatherRepository.save(weatherCache);
    }

    private String getWeatherFromCache(String city) {
        Timestamp timestamp = Timestamp.from(Instant.now().minusSeconds(86400)); // 24 часа
        Optional<WeatherCache> weatherCache = weatherRepository.findByCityAndTimestampAfter(city, timestamp);
        return weatherCache.map(WeatherCache::getWeatherData).orElse(null);
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение юзеру");
        }
    }
}
