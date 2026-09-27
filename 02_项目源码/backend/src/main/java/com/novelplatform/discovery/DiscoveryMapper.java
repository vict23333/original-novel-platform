package com.novelplatform.discovery;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static com.novelplatform.discovery.DiscoveryViews.*;

@Mapper
public interface DiscoveryMapper {
    String READABLE = "c.published=TRUE AND c.taken_down=FALSE AND c.deleted=FALSE";
    String FROM = "FROM novel n JOIN novel_category cat ON cat.id=n.public_category_id JOIN app_user u ON u.id=n.author_id ";
    String FILTER = "<where>n.published=TRUE AND n.taken_down=FALSE " +
        "<if test='categoryId != null'>AND n.public_category_id=#{categoryId} </if>" +
        "<if test='id != null'>AND n.id=#{id} </if><if test='exclude != null'>AND n.id != #{exclude} </if>" +
        "<if test='serial != null'>AND n.public_serial_status=#{serial} </if>" +
        "<if test='keyword != null'>AND (n.public_title LIKE #{keyword} ESCAPE '!' OR u.nickname LIKE #{keyword} ESCAPE '!') </if>" +
        "<if test='readable'>AND EXISTS(SELECT 1 FROM chapter c WHERE c.novel_id=n.id AND "+READABLE+") </if></where>";
    String BOOK = "SELECT n.id,n.public_title AS title,n.public_introduction AS introduction,n.public_category_id AS category_id," +
        "cat.name AS category_name,n.public_serial_status AS serial_status,u.nickname AS author_name," +
        "(SELECT COUNT(*) FROM novel_read_day d WHERE d.novel_id=n.id) AS total_reads," +
        "(SELECT COUNT(*) FROM novel_read_day d WHERE d.novel_id=n.id AND d.read_date BETWEEN #{from} AND #{today}) AS week_reads," +
        "(SELECT COUNT(*) FROM bookshelf s WHERE s.novel_id=n.id) AS collections," +
        "(SELECT COUNT(*) FROM reader_comment m WHERE m.novel_id=n.id AND m.deleted=FALSE " +
        "AND (m.chapter_id IS NULL OR EXISTS(SELECT 1 FROM chapter c WHERE c.id=m.chapter_id AND "+READABLE+"))) AS comments," +
        "(SELECT COUNT(*) FROM chapter c WHERE c.novel_id=n.id AND "+READABLE+") AS chapter_count," +
        "(SELECT COALESCE(SUM(CHAR_LENGTH(c.public_content)),0) FROM chapter c WHERE c.novel_id=n.id AND "+READABLE+") AS word_count," +
        "(SELECT MIN(r.created_at) FROM content_review r WHERE r.novel_id=n.id AND r.chapter_id IS NULL AND r.action='APPROVE') AS first_published_at," +
        "(SELECT MAX(r.created_at) FROM content_review r JOIN chapter c ON c.id=r.chapter_id WHERE r.novel_id=n.id AND r.action='APPROVE' AND "+READABLE+") AS last_updated_at," +
        "(SELECT c.id FROM chapter c WHERE c.novel_id=n.id AND "+READABLE+" ORDER BY c.public_chapter_number DESC LIMIT 1) AS latest_chapter_id," +
        "(SELECT c.public_title FROM chapter c WHERE c.novel_id=n.id AND "+READABLE+" ORDER BY c.public_chapter_number DESC LIMIT 1) AS latest_chapter_title "+FROM;

    // order 仅由服务内枚举映射提供，禁止直接传入请求参数。
    @Select("<script>"+BOOK+FILTER+" ORDER BY ${order} LIMIT #{size} OFFSET #{offset}</script>")
    List<Book> books(@Param("keyword") String keyword,@Param("categoryId") Long categoryId,@Param("serial") String serial,
                     @Param("id") Long id,@Param("exclude") Long exclude,@Param("readable") boolean readable,
                     @Param("order") String order,@Param("from") LocalDate from,@Param("today") LocalDate today,
                     @Param("size") int size,@Param("offset") long offset);
    @Select("<script>SELECT COUNT(*) "+FROM+FILTER+"</script>")
    long count(@Param("keyword") String keyword,@Param("categoryId") Long categoryId,@Param("serial") String serial,
               @Param("id") Long id,@Param("exclude") Long exclude,@Param("readable") boolean readable);

    String OWNED = "n.author_id=#{owner} AND (#{novelId} IS NULL OR n.id=#{novelId})";
    @Select("SELECT (SELECT COUNT(*) FROM novel n WHERE "+OWNED+") AS works," +
        "(SELECT COUNT(*) FROM novel_read_day d JOIN novel n ON n.id=d.novel_id WHERE "+OWNED+") AS total_reads," +
        "(SELECT COUNT(*) FROM novel_read_day d JOIN novel n ON n.id=d.novel_id WHERE "+OWNED+" AND d.read_date BETWEEN #{from} AND #{today}) AS week_reads," +
        "(SELECT COUNT(*) FROM bookshelf s JOIN novel n ON n.id=s.novel_id WHERE "+OWNED+") AS collections," +
        "(SELECT COUNT(*) FROM reader_comment m JOIN novel n ON n.id=m.novel_id WHERE "+OWNED+" AND m.deleted=FALSE) AS comments")
    Totals totals(@Param("owner") long owner,@Param("novelId") Long novelId,@Param("from") LocalDate from,@Param("today") LocalDate today);
    @Select("SELECT d.read_date AS date,COUNT(*) AS `reads` FROM novel_read_day d JOIN novel n ON n.id=d.novel_id WHERE "+OWNED+
        " AND d.read_date BETWEEN #{from} AND #{today} GROUP BY d.read_date ORDER BY d.read_date")
    List<Day> trend(@Param("owner") long owner,@Param("novelId") Long novelId,@Param("from") LocalDate from,@Param("today") LocalDate today);
    @Select("SELECT COUNT(*) FROM novel_read_day WHERE novel_id=#{novelId} AND visitor_key=#{key} AND read_date=#{date}")
    long recorded(@Param("novelId") long novelId,@Param("key") String key,@Param("date") LocalDate date);
    @Insert("INSERT INTO novel_read_day(novel_id,user_id,visitor_key,read_date,first_chapter_id,first_read_at) VALUES(#{novelId},#{userId},#{key},#{date},#{chapterId},#{now})")
    void record(@Param("novelId") long novelId,@Param("userId") Long userId,@Param("key") String key,
                @Param("date") LocalDate date,@Param("chapterId") long chapterId,@Param("now") LocalDateTime now);
}
