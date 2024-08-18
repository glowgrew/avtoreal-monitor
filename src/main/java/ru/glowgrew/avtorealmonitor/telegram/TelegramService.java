package ru.glowgrew.avtorealmonitor.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.glowgrew.avtorealmonitor.AppProperties;

@Service
public class TelegramService {

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    private final RestClient restClient;

    public TelegramService(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.restClient = RestClient.create(appProperties.apiEndpoint());
    }

    public void sendMessage(String chatId, String text) {
        var body = objectMapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", text);
        restClient.post()
            .uri(uri -> uri.pathSegment("bot".concat(appProperties.botToken())).pathSegment("sendMessage").build())
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

}
