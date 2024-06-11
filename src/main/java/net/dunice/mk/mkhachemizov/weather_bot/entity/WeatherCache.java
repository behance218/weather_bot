package net.dunice.mk.mkhachemizov.weather_bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "weather_cache")
@Getter
@Setter
public class WeatherCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String city;
    private String weatherData;
    private Timestamp timestamp;
}
