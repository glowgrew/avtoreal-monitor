package ru.glowgrew.avtorealmonitor.integration;

import com.google.common.collect.Sets;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import ru.glowgrew.avtorealmonitor.AppProperties;
import ru.glowgrew.avtorealmonitor.http.CookieHandlingClientHttpRequestInterceptor;
import ru.glowgrew.avtorealmonitor.telegram.TelegramService;

import java.net.URI;
import java.time.*;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import static org.springframework.http.HttpHeaders.COOKIE;

@Service
public class AvtorealService {

    public static final int WINDOW_DURATION_MINUTES = 20;
    public static final DayOfWeek TARGET_DAY = DayOfWeek.TUESDAY;
    public static final LocalTime TARGET_TIME = LocalTime.of(17, 0);
    public static final ZoneId TIME_ZONE = ZoneId.of("UTC+5");
    public static final String BASE_URL = "https://avtoreal-record.ru";

    private static final Logger LOGGER = LoggerFactory.getLogger(AvtorealService.class);

    private final TelegramService telegramService;
    private final AppProperties appProperties;

    private final Set<FreeScheduleEntry> previousEntries = new HashSet<>();

    private final RestClient avtorealClient = RestClient
        .builder()
        .baseUrl(BASE_URL)
        .defaultHeaders(headers -> {
            headers.add(COOKIE, "AspxAutoDetectCookieSupport=1");
            headers.add(COOKIE, "ASP.NET_SessionId=5m242fh54ipgg2bhh5jivox3");
        })
        .requestInterceptor(new CookieHandlingClientHttpRequestInterceptor())
        .build();

    private int currentScheduleId;

    public AvtorealService(TelegramService telegramService, AppProperties appProperties) {
        this.telegramService = telegramService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelay = 2 * 60 * 1000)
    public void defaultScanner() {
        scanFreeEntries();
    }

    @Scheduled(fixedDelay = 1000)
    public void rushHourScanner() {
        if (isWithinTimeRange()) {
            scanFreeEntries();
        }
    }

    public void scanFreeEntries() {
        authenticate();

        var currentEntries = getCurrentEntries();

        var freeEntries = Set.copyOf(Sets.difference(currentEntries, previousEntries));
        var occupiedEntries = Set.copyOf(Sets.difference(previousEntries, currentEntries));

        previousEntries.clear();
        previousEntries.addAll(currentEntries);

        LOGGER.info("Detected free entries: {}", freeEntries);
        LOGGER.info("Detected occupied entries: {}", occupiedEntries);

        var currentScheduleUri = getCurrentScheduleUri();
        var builder = new StringBuilder();

        freeEntries.forEach(entry -> builder.append(entry).append('\n'));
        builder.append('\n').append(currentScheduleUri);
        var freeEntriesText = "🚀 Появились свободные места для записи: \n\n%s".formatted(builder.toString().trim());

        builder.setLength(0);

        occupiedEntries.forEach(entry -> builder.append(entry).append('\n'));
        builder.append('\n').append(currentScheduleUri);
        var occupiedEntriesText = "⚠️ Занятые места: \n\n%s".formatted(builder.toString().trim());

        if (!freeEntries.isEmpty()) {
            telegramService.sendMessage(appProperties.telegramChatId(), freeEntriesText);
        }
        if (!occupiedEntries.isEmpty()) {
            telegramService.sendMessage(appProperties.telegramChatId(), occupiedEntriesText);
        }
    }

    private boolean isWithinTimeRange() {
        var now = ZonedDateTime.now(TIME_ZONE);
        if (now.getDayOfWeek() != TARGET_DAY) {
            return false;
        }
        var currentTime = now.toLocalTime();
        return !currentTime.isBefore(TARGET_TIME.minusMinutes(WINDOW_DURATION_MINUTES))
               && !currentTime.isAfter(TARGET_TIME.plusMinutes(WINDOW_DURATION_MINUTES));
    }

    private Set<FreeScheduleEntry> getCurrentEntries() {
        var response = avtorealClient.get()
            .uri(this::getCurrentScheduleUri)
            .accept(MediaType.TEXT_HTML)
            .retrieve()
            .toEntity(String.class);

        return parseFreeScheduleEntries(response.getBody());
    }

    private URI getCurrentScheduleUri() {
        return getCurrentScheduleUri(UriComponentsBuilder.fromHttpUrl(BASE_URL));
    }

    private URI getCurrentScheduleUri(UriBuilder uri) {
        var scheduleId = getScheduleId();
        return uri.path("/Users/SelectShedule.aspx").queryParam("id", scheduleId).build();
    }

    private int getScheduleId() {
        var allSchedules = avtorealClient.get().uri("/Users/Default.aspx").retrieve().toEntity(String.class);
        var scheduleLinks = Jsoup.parse(allSchedules.getBody()).select("table a");
        int scheduleId = -1;

        for (var scheduleLink : scheduleLinks) {
            if (!scheduleLink.text().equalsIgnoreCase(appProperties.instructorName())) {
                continue;
            }
            var href = scheduleLink.attr("href");
            var parts = href.split("=");
            if (parts.length <= 1) {
                continue;
            }
            var currentId = parseInt(parts[1]);
            if (currentId.isEmpty() || currentId.getAsInt() <= scheduleId) {
                continue;
            }
            scheduleId = currentId.getAsInt();
        }

        if (currentScheduleId != scheduleId) {
            currentScheduleId = scheduleId;
            var currentScheduleUri = getCurrentScheduleUri();
            var newScheduleText = "🚀 Выложено новое расписание!\n\n%s".formatted(currentScheduleUri);
            telegramService.sendMessage(appProperties.telegramChatId(), newScheduleText);
        }

        return scheduleId;
    }

    private OptionalInt parseInt(String s) {
        try {
            return OptionalInt.of(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private Set<FreeScheduleEntry> parseFreeScheduleEntries(String body) {
        var doc = Jsoup.parse(body);
        var elements = doc.select("tr");

        Set<FreeScheduleEntry> entries = new HashSet<>();
        for (var row : elements.subList(1, elements.size())) {
            var columns = row.select("td");

            if ("выходной".equalsIgnoreCase(columns.get(4).text().strip())) {
                continue;
            }

            var dayOfWeek = columns.get(0).text().strip();
            var date = columns.get(1).text().strip();
            var startTime = columns.get(2).text().strip();
            var endTime = columns.get(3).text().strip();

            if (columns.get(5).select("input").isEmpty()) {
                continue;
            }

            entries.add(new FreeScheduleEntry(dayOfWeek, date, startTime, endTime));
        }
        return entries;
    }

    private void authenticate() {
        avtorealClient.post()
            .uri("/Auth.aspx")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(getAuthData(appProperties.avtorealLogin(), appProperties.avtorealPassword()))
            .retrieve()
            .toBodilessEntity();
    }

    private MultiValueMap<String, String> getAuthData(String login, String password) {
        var response = avtorealClient.get().uri("/Auth.aspx").retrieve().toEntity(String.class);

        var data = new LinkedMultiValueMap<String, String>() {{
            add("__EVENTTARGET", "");
            add("__EVENTARGUMENT", "");
            add("__LASTFOCUS", "");
        }};

        var doc = Jsoup.parse(response.getBody());
        var inputs = doc.select("input[type=hidden]");
        for (var input : inputs) {
            data.add(input.attr("name"), input.attr("value"));
        }

        data.add("LoginTextBox", login);
        data.add("PasswdTextBox", password);
        data.add("OkButton", "foo");

        return data;
    }

    private record FreeScheduleEntry(String dayOfWeek, String date, String startTime, String endTime) {
        @Override
        public String toString() {
            return "%s (%s) %s-%s".formatted(date, dayOfWeek, startTime, endTime);
        }
    }

}
