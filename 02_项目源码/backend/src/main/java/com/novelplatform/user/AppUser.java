package com.novelplatform.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("app_user")
public class AppUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String bio;
    private String role;
    private Boolean enabled;
    private Long tokenVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public String getNickname() { return nickname; }
    public void setNickname(String value) { nickname = value; }
    public String getBio() { return bio; }
    public void setBio(String value) { bio = value; }
    public String getRole() { return role; }
    public void setRole(String value) { role = value; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean value) { enabled = value; }
    public Long getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(Long value) { tokenVersion = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
