package com.novelplatform.writing;

import org.apache.ibatis.annotations.*;

@Mapper
public interface WritingNoteMapper {
    @Select("SELECT goal FROM writing_chapter_note WHERE chapter_id=#{id}") String goal(long id);
    @Insert("INSERT INTO writing_chapter_note(chapter_id,goal) VALUES(#{id},#{goal})") int insert(long id, String goal);
    @Update("UPDATE writing_chapter_note SET goal=#{goal} WHERE chapter_id=#{id}") int update(long id, String goal);
}
