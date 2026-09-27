package com.novelplatform.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import java.net.URI;

@Validated
@ConfigurationProperties("app.ai")
public record AiSettings(boolean enabled, String apiUrl, String apiKey, String model,
                         @Min(1) @Max(90) int timeoutSeconds,
                         @Min(0) @Max(300) int cooldownSeconds, boolean allowLocalHttp) {
    public boolean ready() {
        if (!enabled || apiKey == null || apiKey.isBlank() || apiKey.contains("\n") || apiKey.contains("\r")
                || model == null || !model.matches("[a-zA-Z0-9._:/-]{1,100}")) return false;
        try {
            URI uri = URI.create(apiUrl);
            boolean local = allowLocalHttp && "http".equals(uri.getScheme())
                    && java.util.Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
            return ("https".equals(uri.getScheme()) || local) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (RuntimeException e) { return false; }
    }
}
