package ru.glowgrew.avtorealmonitor.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import ru.glowgrew.avtorealmonitor.AppProperties;

@Service
public class TelegramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramService.class);

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    private final RestClient restClient = RestClient.create();

    public TelegramService(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void setupWebhook() {
        restClient.get()
            .uri(invokeMethod("setWebhook")
                .queryParam("url", appProperties.webhookPublicEndpoint())
                .queryParam("secret_token", appProperties.webhookSecretToken())
                .queryParam("allowed_updates", objectMapper.createArrayNode().add("message"))
                .queryParam("drop_pending_updates", true)
                .toUriString())
            .retrieve()
            .onStatus(HttpStatusCode::is2xxSuccessful,
                (request, response) -> LOGGER.info("Completed Telegram bot setup: {}, {}", request, response))
            .onStatus(HttpStatusCode::isError,
                (request, response) -> LOGGER.error("Cannot setup Telegram bot: {}, {}", request, response))
            .toBodilessEntity();
    }

    public void sendMessage(String chatId, String text) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("chat_id", chatId);
        requestBody.put("text", text);

        restClient.post()
            .uri(invokeMethod("sendMessage").toUriString())
            .body(requestBody)
            .retrieve()
            .toBodilessEntity();
    }

    private UriBuilder invokeMethod(String methodName) {
        return UriComponentsBuilder.fromHttpUrl(appProperties.apiEndpoint())
            .pathSegment("bot".concat(appProperties.botToken()))
            .pathSegment(methodName);
    }

}
