package com.novelplatform.content;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("content_review")
public class ReviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Long chapterId;
    private Long contentVersion;
    private String action;
    private Long reviewerId;
    private String reason;
    private String snapshot;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long value) { novelId = value; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long value) { chapterId = value; }
    public Long getContentVersion() { return contentVersion; }
    public void setContentVersion(Long value) { contentVersion = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long value) { reviewerId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String value) { snapshot = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
