package net.dunice.mk.mkhachemizov.weather_bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

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
//    @Type(type = "jsonb")
    @Column(columnDefinition = "TEXT")
    private String weatherData;
    private Timestamp timestamp;
}
