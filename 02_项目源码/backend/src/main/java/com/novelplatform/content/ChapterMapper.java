package com.novelplatform.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {
    @Select("SELECT * FROM chapter WHERE id = #{id} FOR UPDATE")
    Chapter lockById(@Param("id") long id);
}
