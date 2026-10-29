package com.novelplatform.writing;

import com.baomidou.mybatisplus.annotation.*;

@TableName("writing_character")
public class WritingCharacter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private String name;
    private String aliases;
    private String storyRole;
    private String profile;
    private Long version;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long value) { novelId = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getAliases() { return aliases; }
    public void setAliases(String value) { aliases = value; }
    public String getStoryRole() { return storyRole; }
    public void setStoryRole(String value) { storyRole = value; }
    public String getProfile() { return profile; }
    public void setProfile(String value) { profile = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
