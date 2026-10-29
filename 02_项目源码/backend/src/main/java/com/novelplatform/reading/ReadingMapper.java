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
    String SHELF_FILTER = " FROM bookshelf b JOIN novel n ON n.id=b.novel_id LEFT JOIN reading_progress p ON p.user_id=b.user_id AND p.novel_id=b.novel_id WHERE b.user_id=#{userId} AND LOCATE(#{keyword},n.public_title)>0 AND (#{status}='ALL' OR (#{status}='READ' AND p.novel_id IS NOT NULL) OR (#{status}='UNREAD' AND p.novel_id IS NULL))";
    @Select("SELECT COUNT(*)" + SHELF_FILTER)
    long count(long userId,String keyword,String status);
    @Select("SELECT b.novel_id,b.added_at" + SHELF_FILTER + " ORDER BY CASE WHEN #{sort}='RECENT_READ' THEN p.updated_at END DESC,b.added_at DESC,b.novel_id DESC LIMIT #{size} OFFSET #{offset}")
    List<ShelfRow> shelf(long userId,String keyword,String status,String sort,int size,long offset);
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
