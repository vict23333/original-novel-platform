package com.novelplatform.comment;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("reader_comment")
public class ReaderComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId, chapterId, userId, rootId, replyToId;
    private String body;
    private Boolean deleted;
    private LocalDateTime createdAt;
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt=v; }
    public Long getId() { return id; }
    public void setId(Long v) { id=v; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long v) { novelId=v; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long v) { chapterId=v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId=v; }
    public Long getRootId() { return rootId; }
    public void setRootId(Long v) { rootId=v; }
    public Long getReplyToId() { return replyToId; }
    public void setReplyToId(Long v) { replyToId=v; }
    public String getBody() { return body; }
    public void setBody(String v) { body=v; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean v) { deleted=v; }
}
