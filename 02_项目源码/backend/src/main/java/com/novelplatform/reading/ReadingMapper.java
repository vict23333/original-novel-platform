package com.novelplatform.reading;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReadingMapper {
    record ShelfRow(long novelId, LocalDateTime addedAt) {}
    record Progress(long novelId, long chapterId, int paragraphIndex, String contentRevision,
                    long version, LocalDateTime updatedAt) {}

    // 同一用户的书架及进度写入串行，覆盖首次插入也必须经过此锁。
    @Select("SELECT id FROM app_user WHERE id=#{userId} FOR UPDATE")
    Long lockUser(long userId);
    @Select("SELECT COUNT(*) FROM bookshelf WHERE user_id=#{userId} AND novel_id=#{novelId}")
    long contains(@Param("userId") long userId, @Param("novelId") long novelId);
    @Select("SELECT COUNT(*) FROM bookshelf WHERE user_id=#{userId}")
    long count(long userId);
    @Select("SELECT novel_id,added_at FROM bookshelf WHERE user_id=#{userId} ORDER BY added_at DESC,novel_id DESC LIMIT #{size} OFFSET #{offset}")
    List<ShelfRow> shelf(@Param("userId") long userId, @Param("size") int size, @Param("offset") long offset);
    @Insert("INSERT INTO bookshelf(user_id,novel_id) VALUES(#{userId},#{novelId})")
    void add(@Param("userId") long userId, @Param("novelId") long novelId);
    @Delete("DELETE FROM bookshelf WHERE user_id=#{userId} AND novel_id=#{novelId}")
    void remove(@Param("userId") long userId, @Param("novelId") long novelId);
    @Select("SELECT novel_id,chapter_id,paragraph_index,content_revision,version,updated_at FROM reading_progress WHERE user_id=#{userId} AND novel_id=#{novelId}")
    Progress progress(@Param("userId") long userId, @Param("novelId") long novelId);
    @Insert("INSERT INTO reading_progress(user_id,novel_id,chapter_id,paragraph_index,content_revision,version,updated_at) VALUES(#{userId},#{novelId},#{chapterId},#{paragraph},#{revision},1,#{now})")
    void insertProgress(@Param("userId") long userId, @Param("novelId") long novelId,
                        @Param("chapterId") long chapterId, @Param("paragraph") int paragraph,
                        @Param("revision") String revision, @Param("now") LocalDateTime now);
    @Update("UPDATE reading_progress SET chapter_id=#{chapterId},paragraph_index=#{paragraph},content_revision=#{revision},version=version+1,updated_at=#{now} WHERE user_id=#{userId} AND novel_id=#{novelId}")
    void updateProgress(@Param("userId") long userId, @Param("novelId") long novelId,
                        @Param("chapterId") long chapterId, @Param("paragraph") int paragraph,
                        @Param("revision") String revision, @Param("now") LocalDateTime now);
}
