package ru.glowgrew.avtorealmonitor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(
    String botToken,
    String apiEndpoint,
    String telegramChatId,
    String avtorealLogin,
    String avtorealPassword,
    String drivingInstructor
) {

}