package com.novelplatform.content;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("chapter")
public class Chapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Integer chapterNumber;
    private String title;
    private String content;
    @TableField(exist=false)
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String goal;
    private Long version;
    private String reviewStatus;
    private String reviewReason;
    private Boolean published;
    private Boolean takenDown;
    private Boolean deleted;
    private String publicTitle;
    private String publicContent;
    private Integer publicChapterNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long value) { novelId = value; }
    public Integer getChapterNumber() { return chapterNumber; }
    public void setChapterNumber(Integer value) { chapterNumber = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getContent() { return content; }
    public String getGoal() { return goal; }
    public void setGoal(String value) { goal=value; }
    public void setContent(String value) { content = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String value) { reviewStatus = value; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String value) { reviewReason = value; }
    public Boolean getPublished() { return published; }
    public void setPublished(Boolean value) { published = value; }
    public Boolean getTakenDown() { return takenDown; }
    public void setTakenDown(Boolean value) { takenDown = value; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean value) { deleted = value; }
    public String getPublicTitle() { return publicTitle; }
    public void setPublicTitle(String value) { publicTitle = value; }
    public String getPublicContent() { return publicContent; }
    public Integer getPublicChapterNumber() { return publicChapterNumber; }
    public void setPublicChapterNumber(Integer value) { publicChapterNumber = value; }
    public void setPublicContent(String value) { publicContent = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
