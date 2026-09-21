package com.novelplatform.content;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("novel")
public class Novel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String title;
    private String introduction;
    private Long categoryId;
    private String serialStatus;
    private Long version;
    private String reviewStatus;
    private String reviewReason;
    private Boolean published;
    private Boolean takenDown;
    private String publicTitle;
    private String publicIntroduction;
    private Long publicCategoryId;
    private String publicSerialStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long value) { authorId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String value) { introduction = value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId = value; }
    public String getSerialStatus() { return serialStatus; }
    public void setSerialStatus(String value) { serialStatus = value; }
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
    public String getPublicTitle() { return publicTitle; }
    public void setPublicTitle(String value) { publicTitle = value; }
    public String getPublicIntroduction() { return publicIntroduction; }
    public void setPublicIntroduction(String value) { publicIntroduction = value; }
    public Long getPublicCategoryId() { return publicCategoryId; }
    public void setPublicCategoryId(Long value) { publicCategoryId = value; }
    public String getPublicSerialStatus() { return publicSerialStatus; }
    public void setPublicSerialStatus(String value) { publicSerialStatus = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
