package net.dunice.mk.mkhachemizov.weather_bot.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import net.dunice.mk.mkhachemizov.weather_bot.config.properties.BotProperties;
import net.dunice.mk.mkhachemizov.weather_bot.entity.WeatherCache;
import net.dunice.mk.mkhachemizov.weather_bot.repository.WeatherRepository;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("ALL")
public class WeatherService extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private final BotProperties bot;
    private final WeatherRepository weatherRepository;
    private final OkHttpClient client = new OkHttpClient();
    @Value("${bot.openweathermap-api-key}")
    private String openWeatherMapApiKey;
    //    TODO: THINK ABOUT THIS FLAG SWITCH (HOW TO IMPLEMENT ITS FUNC FULLY IN onUpdateRecevied METHOD
    private final Map<Long, Boolean> userRequestMap = new HashMap<>();

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

            switch (messageText) {
                case "/start" -> sendWelcomeMessage(chatId);
                case "Погода" -> {
                    userRequestMap.put(chatId, false);
                    requestLocation(chatId);
                }
                case "Погода на неделю" -> {
                    userRequestMap.put(chatId, true);
                    requestLocation(chatId);
                }
            }
        }
        else if (update.hasMessage() && update.getMessage().hasLocation()) {
            long chatId = update.getMessage().getChatId();
            Location location = update.getMessage().getLocation();
            String city = getCityFromLocation(location.getLatitude(), location.getLongitude());
            if (city != null) {
                Boolean isWeeklyRequest = userRequestMap.get(chatId);
                if (Boolean.TRUE.equals(isWeeklyRequest)) {
                    handleWeeklyWeatherRequest(chatId, city);
                }
                else {
                    handleWeatherRequest(chatId, city);
                }
                userRequestMap.remove(chatId); // Очистить состояние после обработки запроса
            }
            else {
                sendMessage(chatId, "Не удалось определить город по вашему местоположению.");
            }
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

    @NotNull
    private ReplyKeyboardMarkup getMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        KeyboardButton weatherButton = new KeyboardButton("Погода");
        weatherButton.setRequestLocation(true);
        row.add(weatherButton);

        KeyboardButton weekWeatherButton = new KeyboardButton("Погода на неделю");
        weekWeatherButton.setRequestLocation(true);
        row.add(weekWeatherButton);

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void requestLocation(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Пожалуйста отправьте ваше местоположение");
        message.setReplyMarkup(getMenuKeyboard());
        try {
            execute(message);
        }
        catch (TelegramApiException exception) {
            log.error("Не удалось запросить местоположение юзера", exception);
        }
    }

    private void handleWeatherRequest(long chatId, String city) {
        try {
            String weatherData = fetchWeather(city);
            if (weatherData == null) {
                weatherData = getWeatherFromCache(city);
            }
            sendMessage(chatId, weatherData);
        }
        catch (Exception e) {
            log.error("Ошибка получения прогноза погоды", e);
            sendMessage(chatId, "Ошибка при получении прогноза погоды.");
        }
    }

    private void handleWeeklyWeatherRequest(long chatId, String city) {
        try {
            String weatherData = fetchWeeklyWeather(city);
            if (weatherData == null) {
                weatherData = getWeatherFromCache(city);
            }
            sendMessage(chatId, weatherData);
        }
        catch (Exception e) {
            log.error("Ошибка получения прогноза погоды на неделю", e);
            sendMessage(chatId, "Ошибка при получении прогноза погоды на неделю.");
        }
    }

    private String fetchWeather(String city) throws Exception {
        String url = String.format("http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric&lang=ru",
                city,
                openWeatherMapApiKey);
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        if (response.body() != null) {
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);
            String cityName = json.getString("name");
            String weatherDescription = json.getJSONArray("weather")
                    .getJSONObject(0)
                    .getString("description");
            double temperature = json.getJSONObject("main").getDouble("temp");
            double feelsLike = json.getJSONObject("main").getDouble("feels_like");
            int humidity = json.getJSONObject("main").getInt("humidity");
            double windSpeed = json.getJSONObject("wind").getDouble("speed");

            String weatherData = String.format("""
                            Город: %s
                            Погода: %s
                            Температура: %.2f°C
                            Ощущается как: %.2f°C
                            Влажность: %d%%
                            Скорость ветра: %.2f м/с""",
                    cityName, weatherDescription, temperature, feelsLike, humidity, windSpeed);
            WeatherCache weatherCache = new WeatherCache();
            weatherCache.setCity(city);
            weatherCache.setWeatherData(weatherData);
            weatherCache.setTimestamp(Timestamp.from(Instant.now()));
            weatherRepository.save(weatherCache);
            return weatherData;
        }

        return null;
    }

    private String fetchWeeklyWeather(String city) throws Exception {
        String url = String.format("http://api.openweathermap.org/data/2.5/forecast/daily?q=%s&cnt=7&appid=%s&units=metric&lang=ru", city, openWeatherMapApiKey);
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        if (response.body() != null) {
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);
            JSONArray dailyForecasts = json.getJSONArray("list");

            StringBuilder weeklyWeatherData = new StringBuilder();
            for (int i = 0; i < dailyForecasts.length(); i++) {
                JSONObject dayForecast = dailyForecasts.getJSONObject(i);
                long date = dayForecast.getLong("dt");
                String weatherDescription = dayForecast.getJSONArray("weather")
                        .getJSONObject(0)
                        .getString("description");
                double temperature = dayForecast.getJSONObject("temp").getDouble("day");
                double feelsLike = dayForecast.getJSONObject("temp").getDouble("feels_like");
                int humidity = dayForecast.getInt("humidity");
                double windSpeed = dayForecast.getDouble("speed");

                weeklyWeatherData.append(String.format("""
                                Дата: %s
                                Погода: %s
                                Температура: %.2f°C
                                Ощущается как: %.2f°C
                                Влажность: %d%%
                                Скорость ветра: %.2f м/с

                                """,
                        new java.util.Date(date * 1000), weatherDescription, temperature, feelsLike, humidity, windSpeed));
            }

            String weatherData = weeklyWeatherData.toString();
            WeatherCache weatherCache = new WeatherCache();
            weatherCache.setCity(city);
            weatherCache.setWeatherData(weatherData);
            weatherCache.setTimestamp(Timestamp.from(Instant.now()));
            weatherRepository.save(weatherCache);
            return weatherData;
        }

        return null;
    }

    private String getWeatherFromCache(String city) {
        Timestamp timestamp = Timestamp.from(Instant.now().minusSeconds(86400)); // 24 часа
        Optional<WeatherCache> weatherCache = weatherRepository.findByCityAndTimestampAfter(city, timestamp);
        return weatherCache.map(WeatherCache::getWeatherData).orElse(null);
    }

    private String getCityFromLocation(double latitude, double longitude) {
        try {
            String url = String.format("http://api.openweathermap.org/geo/1.0/reverse?lat=%f&lon=%f&limit=1&appid=%s",
                    latitude,
                    longitude,
                    openWeatherMapApiKey);
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            assert response.body() != null;
            String responseBody = response.body().string();
            JSONArray jsonArray = new JSONArray(responseBody);
            if (!jsonArray.isEmpty()) {
                JSONObject jsonObject = jsonArray.getJSONObject(0);
                return jsonObject.getString("name");
            }
        }
        catch (Exception exception) {
            log.error("Не удалось вытянуть город из локации юзера", exception);
        }
        return null;
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