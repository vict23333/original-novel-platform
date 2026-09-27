package com.novelplatform.ai;

import com.novelplatform.content.ContentService;
import com.novelplatform.common.BusinessException;
import org.springframework.stereotype.Service;
import static org.springframework.http.HttpStatus.*;

@Service
public class AiService {
    private final ContentService content;
    private final AiSettings settings;
    private final AiGateway gateway;
    private final AiLimiter limiter;
    public AiService(ContentService content, AiSettings settings, AiGateway gateway, AiLimiter limiter) {
        this.content = content; this.settings = settings; this.gateway = gateway; this.limiter = limiter;
    }
    private void validate(long user, long chapterId, long version) {
        var chapter = content.authorChapter(user, chapterId);
        if (chapter.getVersion() != version || "PENDING".equals(chapter.getReviewStatus()))
            throw new BusinessException(CONFLICT, "AI_DRAFT_CHANGED", "章节已变化或正在审核，请保留输入并重新加载后使用 AI");
    }
    // 外部请求不占数据库事务，不写草稿；返回后再检查版本，避免应用已过期结果。
    public AiGateway.Result generate(long user, long chapterId, AiController.Input input) {
        validate(user, chapterId, input.version());
        if (!settings.ready()) throw new BusinessException(SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI 服务尚未配置；正文可以正常编辑和保存");
        limiter.acquire(user);
        try {
            var result = gateway.generate(input.mode(), input.text(), input.instructions());
            validate(user, chapterId, input.version());
            return result;
        } finally { limiter.release(user); }
    }
}
