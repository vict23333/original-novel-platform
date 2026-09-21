package com.novelplatform.content;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("novel_category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
}
