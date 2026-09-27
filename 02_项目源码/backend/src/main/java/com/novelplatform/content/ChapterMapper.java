package com.novelplatform.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {
    @Select("SELECT COALESCE(MAX(chapter_number),0) FROM chapter WHERE novel_id=#{novelId} AND deleted=FALSE")
    int maximumNumber(@Param("novelId") long novelId);
    @Select("SELECT * FROM chapter WHERE id = #{id} FOR UPDATE")
    Chapter lockById(@Param("id") long id);
}
