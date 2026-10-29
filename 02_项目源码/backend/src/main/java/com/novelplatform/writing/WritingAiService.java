package com.novelplatform.writing;

import com.novelplatform.ai.*;
import com.novelplatform.common.BusinessException;
import org.springframework.stereotype.Service;
import static org.springframework.http.HttpStatus.*;

@Service
public class WritingAiService {
    private final WritingContext context;
    private final AiSettings settings;
    private final AiGateway gateway;
    private final AiLimiter limiter;
    public WritingAiService(WritingContext context,AiSettings settings,AiGateway gateway,AiLimiter limiter) {
        this.context=context;this.settings=settings;this.gateway=gateway;this.limiter=limiter;
    }
    public AiGateway.Result generate(long owner,long novel,WritingInputs.Task task) {
        var snapshot=context.build(owner,novel,task);
        compare(snapshot.digest(),task.contextDigest());
        if(!settings.ready())throw new BusinessException(SERVICE_UNAVAILABLE,"AI_NOT_CONFIGURED","AI 服务尚未配置，仍可维护资料和手动写作");
        limiter.acquire(owner);
        try {
            var result=gateway.generateContext(task.mode(),context.encode(snapshot));
            compare(context.build(owner,novel,task).digest(),snapshot.digest());
            return result;
        } finally {limiter.release(owner);}
    }
    private void compare(String actual,String expected) {
        if(!actual.equals(expected))throw new BusinessException(CONFLICT,"WRITING_CHANGED","引用资料或正文已变化，请重新预览；原稿未改变");
    }
}
