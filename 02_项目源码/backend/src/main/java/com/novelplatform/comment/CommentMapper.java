package com.novelplatform.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<ReaderComment> {
    record View(long id, long novelId, Long chapterId, Long rootId, Long replyToId, String nickname,
                String replyToName, String body, boolean deleted, boolean mine, long likeCount,
                boolean liked, long replyCount, LocalDateTime createdAt) {}
    record AdminView(long id, long novelId, Long chapterId, Long rootId, String novelTitle,
                     String nickname, String body, boolean deleted, String deleteReason, LocalDateTime createdAt,
                     LocalDateTime deletedAt) {}

    String VIEW = "SELECT c.id,c.novel_id,c.chapter_id,c.root_id,c.reply_to_id,u.nickname," +
        "CASE WHEN target.deleted=TRUE THEN '评论已删除' ELSE target_user.nickname END AS reply_to_name," +
        "CASE WHEN c.deleted=TRUE THEN '' ELSE c.body END AS body,c.deleted," +
        "CASE WHEN c.user_id=#{viewer} THEN TRUE ELSE FALSE END AS mine," +
        "(SELECT COUNT(*) FROM comment_like l WHERE l.comment_id=c.id) AS like_count," +
        "CASE WHEN EXISTS(SELECT 1 FROM comment_like l WHERE l.comment_id=c.id AND l.user_id=#{viewer}) THEN TRUE ELSE FALSE END AS liked," +
        "(SELECT COUNT(*) FROM reader_comment r WHERE r.root_id=c.id) AS reply_count,c.created_at " +
        "FROM reader_comment c JOIN app_user u ON u.id=c.user_id " +
        "LEFT JOIN reader_comment target ON target.id=c.reply_to_id LEFT JOIN app_user target_user ON target_user.id=target.user_id ";
    String SCOPE = "c.novel_id=#{novelId} AND ((#{chapterId} IS NULL AND c.chapter_id IS NULL) OR c.chapter_id=#{chapterId}) AND c.root_id IS NULL";
    @Select("<script>"+VIEW+"WHERE "+SCOPE+" ORDER BY c.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<View> roots(@Param("novelId") long novelId, @Param("chapterId") Long chapterId, @Param("viewer") Long viewer, @Param("size") int size, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM reader_comment c WHERE "+SCOPE)
    long rootCount(@Param("novelId") long novelId, @Param("chapterId") Long chapterId);
    @Select(VIEW+"WHERE c.root_id=#{rootId} ORDER BY c.id ASC LIMIT #{size} OFFSET #{offset}")
    List<View> replies(@Param("rootId") long rootId, @Param("viewer") Long viewer, @Param("size") int size, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM reader_comment WHERE root_id=#{rootId}")
    long replyCount(long rootId);
    @Select(VIEW+"WHERE c.id=#{id}")
    View view(@Param("id") long id, @Param("viewer") Long viewer);
    @Select("SELECT * FROM reader_comment WHERE id=#{id} FOR UPDATE")
    ReaderComment lock(long id);
    @Select("SELECT COUNT(*) FROM comment_like WHERE comment_id=#{id} AND user_id=#{userId}")
    long hasLike(@Param("id") long id, @Param("userId") long userId);
    @Insert("INSERT INTO comment_like(comment_id,user_id) VALUES(#{id},#{userId})")
    void like(@Param("id") long id, @Param("userId") long userId);
    @Delete("DELETE FROM comment_like WHERE comment_id=#{id} AND user_id=#{userId}")
    void unlike(@Param("id") long id, @Param("userId") long userId);
    @Delete("DELETE FROM comment_like WHERE comment_id=#{id}")
    void clearLikes(long id);
    @Update("UPDATE reader_comment SET body='',deleted=TRUE,deleted_by=#{userId},delete_reason=#{reason},deleted_at=#{now} WHERE id=#{id}")
    void remove(@Param("id") long id, @Param("userId") long userId, @Param("reason") String reason, @Param("now") LocalDateTime now);
    String ADMIN_FROM = "FROM reader_comment c JOIN novel n ON n.id=c.novel_id JOIN app_user u ON u.id=c.user_id ";
    String ADMIN_WHERE = "<where><if test='novelId != null'>c.novel_id=#{novelId} AND </if><if test='deleted != null'>c.deleted=#{deleted} AND </if> (#{keyword}='' OR c.body LIKE CONCAT('%',#{keyword},'%') OR n.title LIKE CONCAT('%',#{keyword},'%') OR u.nickname LIKE CONCAT('%',#{keyword},'%'))</where>";
    @Select("<script>SELECT c.id,c.novel_id,c.chapter_id,c.root_id,n.title AS novel_title,u.nickname,CASE WHEN c.deleted=TRUE THEN '' ELSE c.body END AS body,c.deleted,c.delete_reason,c.created_at,c.deleted_at "+ADMIN_FROM+ADMIN_WHERE+" ORDER BY c.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<AdminView> adminList(@Param("novelId") Long novelId, @Param("deleted") Boolean deleted, @Param("keyword") String keyword, @Param("size") int size, @Param("offset") long offset);
    @Select("<script>SELECT COUNT(*) "+ADMIN_FROM+ADMIN_WHERE+"</script>")
    long adminCount(@Param("novelId") Long novelId, @Param("deleted") Boolean deleted, @Param("keyword") String keyword);
}
