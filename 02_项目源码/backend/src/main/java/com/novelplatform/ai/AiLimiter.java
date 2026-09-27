package com.novelplatform.ai;

import com.novelplatform.common.BusinessException;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.Clock;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Component
public class AiLimiter {
    private record Entry(long started, boolean active) {}
    private final Map<Long, Entry> entries = new HashMap<>();
    private final Clock clock;
    private final AiSettings settings;
    public AiLimiter(Clock clock, AiSettings settings) { this.clock = clock; this.settings = settings; }
    public synchronized void acquire(long user) {
        long now = clock.millis(), cooldown = settings.cooldownSeconds() * 1000L;
        entries.entrySet().removeIf(e -> !e.getValue().active() && now - e.getValue().started() >= cooldown);
        Entry existing = entries.get(user);
        if (existing != null) throw new BusinessException(TOO_MANY_REQUESTS, "AI_TOO_FREQUENT", "请等待当前生成完成，并稍后再试");
        if (entries.size() >= 10000 || entries.values().stream().filter(Entry::active).count() >= 4)
            throw new BusinessException(TOO_MANY_REQUESTS, "AI_BUSY", "当前生成请求较多，请稍后重试");
        entries.put(user, new Entry(now, true));
    }
    public synchronized void release(long user) {
        Entry previous = entries.get(user);
        if (previous != null) entries.put(user, new Entry(previous.started(), false));
    }
}
