package com.novelplatform.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NovelMapper extends BaseMapper<Novel> {
    @Select("SELECT * FROM novel WHERE id = #{id} FOR UPDATE")
    Novel lockById(@Param("id") long id);
}
