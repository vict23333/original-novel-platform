package com.novelplatform.writing;

import com.baomidou.mybatisplus.annotation.*;

@TableName("writing_project")
public class WritingProject {
    @TableId(type = IdType.INPUT)
    private Long novelId;
    private String outline;
    private String worldSetting;
    private String writingStyle;
    private Long version;
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long value) { novelId = value; }
    public String getOutline() { return outline; }
    public void setOutline(String value) { outline = value; }
    public String getWorldSetting() { return worldSetting; }
    public void setWorldSetting(String value) { worldSetting = value; }
    public String getWritingStyle() { return writingStyle; }
    public void setWritingStyle(String value) { writingStyle = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
