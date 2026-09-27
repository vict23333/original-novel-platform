package com.novelplatform;

import com.novelplatform.ai.*;
import com.novelplatform.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiLimiterTest {
    @Test void perAuthorCooldownAndGlobalConcurrencyReleaseCorrectly() {
        Clock clock=mock(Clock.class);when(clock.millis()).thenReturn(1000L);
        var limiter=new AiLimiter(clock,new AiSettings(false,"","","",45,20,false));
        limiter.acquire(1);limiter.acquire(2);limiter.acquire(3);limiter.acquire(4);
        assertThatThrownBy(() -> limiter.acquire(1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> limiter.acquire(5)).isInstanceOf(BusinessException.class);
        limiter.release(1);limiter.acquire(5);
        assertThatThrownBy(() -> limiter.acquire(1)).isInstanceOf(BusinessException.class);
        limiter.release(2);when(clock.millis()).thenReturn(21000L);limiter.acquire(1);
        // 冷却时间过了也不能清理仍在执行的请求。
        when(clock.millis()).thenReturn(100000L);
        assertThatThrownBy(() -> limiter.acquire(3)).isInstanceOf(BusinessException.class);
    }
}
