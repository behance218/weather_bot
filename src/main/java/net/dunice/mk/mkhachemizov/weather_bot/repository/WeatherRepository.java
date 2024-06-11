package net.dunice.mk.mkhachemizov.weather_bot.repository;

import net.dunice.mk.mkhachemizov.weather_bot.entity.WeatherCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public interface WeatherRepository extends JpaRepository<WeatherCache, Long> {
    @Query("SELECT w from WeatherCache w WHERE w.city = :city AND w.timestamp > :timestamp")
    Optional<WeatherCache> findByCityAndTimestampAfter(
            @Param("city") String city,
            @Param("timestamp") Timestamp timestamp);
}
