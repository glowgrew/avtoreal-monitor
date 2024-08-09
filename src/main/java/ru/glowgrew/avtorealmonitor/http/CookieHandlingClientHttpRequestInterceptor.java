package ru.glowgrew.avtorealmonitor.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CookieHandlingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final Map<String, HttpCookie> cookies = new HashMap<>();

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution
    ) throws IOException {
        List<String> cookiesForRequest = cookies.values()
            .stream()
            .filter(cookie -> cookie.getPath() != null && request.getURI().getPath().startsWith(cookie.getPath()))
            .map(HttpCookie::toString)
            .collect(Collectors.toList());
        request.getHeaders().addAll(HttpHeaders.COOKIE, cookiesForRequest);

        ClientHttpResponse response = execution.execute(request, body);

        List<String> newCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (newCookies != null) {
            List<HttpCookie> parsedCookies = newCookies.stream()
                .flatMap(rawCookie -> HttpCookie.parse(HttpHeaders.SET_COOKIE + ": " + rawCookie).stream())
                .toList();
            parsedCookies.forEach(newCookie -> cookies.put(newCookie.getName(), newCookie));
        }

        return response;
    }

}