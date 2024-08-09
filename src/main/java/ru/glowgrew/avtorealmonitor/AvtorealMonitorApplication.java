package ru.glowgrew.avtorealmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class AvtorealMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvtorealMonitorApplication.class, args);
    }

}