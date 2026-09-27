package com.novelplatform.admin;

import org.apache.ibatis.annotations.*;
import java.util.List;
import static com.novelplatform.admin.AdminModels.*;

@Mapper
public interface AdminMapper {
    @Select("SELECT n.id,n.title,n.review_status,c.name AS category_name FROM novel n JOIN novel_category c ON c.id=n.category_id WHERE LOCATE(#{keyword},n.title)>0 ORDER BY n.id DESC LIMIT #{size} OFFSET #{offset}")
    List<BookRow> books(String keyword,int size,long offset);
    @Select("SELECT COUNT(*) FROM novel WHERE LOCATE(#{keyword},title)>0")
    long bookCount(String keyword);
    String USER_FILTER = " FROM app_user WHERE (#{role}='' OR role=#{role}) AND (#{enabled} IS NULL OR enabled=#{enabled}) AND (LOCATE(#{keyword},username)>0 OR LOCATE(#{keyword},nickname)>0)";
    @Select("SELECT id,username,nickname,bio,role,enabled,admin_version AS version,created_at" + USER_FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<UserRow> users(String keyword, String role, Boolean enabled, int size, long offset);
    @Select("SELECT COUNT(*)" + USER_FILTER)
    long userCount(String keyword, String role, Boolean enabled);
    @Select("SELECT id,username,nickname,bio,role,enabled,admin_version AS version,created_at FROM app_user WHERE id=#{id} FOR UPDATE")
    UserRow lockUser(long id);
    @Update("UPDATE app_user SET nickname=#{nickname},bio=#{bio},admin_version=admin_version+1,updated_at=#{now} WHERE id=#{id}")
    void profile(long id, String nickname, String bio, java.time.LocalDateTime now);
    @Update("UPDATE app_user SET enabled=#{enabled},token_version=token_version+1,admin_version=admin_version+1,updated_at=#{now} WHERE id=#{id}")
    void status(long id, boolean enabled, java.time.LocalDateTime now);

    // table 仅由服务内固定枚举映射生成，不接受客户端 SQL 标识符。
    @Select("SELECT id,name,version,0 AS usage_count FROM ${table} WHERE id=#{id} FOR UPDATE")
    Term lockTerm(String table, long id);
    @Select("SELECT c.id,c.name,c.version,(SELECT COUNT(*) FROM novel n WHERE n.category_id=c.id OR n.public_category_id=c.id) AS usage_count FROM novel_category c ORDER BY c.id")
    List<Term> categories();
    @Select("SELECT t.id,t.name,t.version,(SELECT COUNT(*) FROM novel_tag_link l WHERE l.tag_id=t.id) AS usage_count FROM novel_tag t ORDER BY t.id")
    List<Term> tags();
    @Insert("INSERT INTO ${table}(name) VALUES(#{name})")
    void createTerm(String table, String name);
    @Select("SELECT id,name,version,0 AS usage_count FROM ${table} WHERE name=#{name}")
    Term byName(String table, String name);
    @Update("UPDATE ${table} SET name=#{name},version=version+1 WHERE id=#{id}")
    void renameTerm(String table, long id, String name);
    @Delete("DELETE FROM ${table} WHERE id=#{id}")
    void deleteTerm(String table, long id);
    @Select("SELECT COUNT(*) FROM novel WHERE category_id=#{id} OR public_category_id=#{id}")
    long categoryUse(long id);
    @Select("SELECT COUNT(*) FROM novel_tag_link WHERE tag_id=#{id}")
    long tagUse(long id);
    @Select("SELECT tag_id FROM novel_tag_link WHERE novel_id=#{id} ORDER BY tag_id")
    List<Long> tagIds(long id);
    @Select("SELECT t.id,t.name,t.version,0 AS usage_count FROM novel_tag t JOIN novel_tag_link l ON l.tag_id=t.id WHERE l.novel_id=#{id} ORDER BY t.id")
    List<Term> bookTags(long id);
    @Delete("DELETE FROM novel_tag_link WHERE novel_id=#{id}")
    void clearTags(long id);
    @Insert("INSERT INTO novel_tag_link(novel_id,tag_id) VALUES(#{id},#{tagId})")
    void link(long id, long tagId);
    @Insert("INSERT INTO admin_audit(actor_id,target_type,target_id,action,reason,created_at) VALUES(#{actor},#{type},#{id},#{action},#{reason},#{now})")
    void audit(long actor, String type, long id, String action, String reason, java.time.LocalDateTime now);
    @Select("SELECT * FROM admin_audit ORDER BY id DESC LIMIT 50")
    List<Audit> audits();
    String AUDIT_FILTER = " FROM admin_audit WHERE (#{type}='' OR target_type=#{type}) AND (#{action}='' OR action=#{action}) AND (#{targetId} IS NULL OR target_id=#{targetId}) AND LOCATE(#{keyword},reason)>0";
    @Select("SELECT *" + AUDIT_FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Audit> searchAudits(String keyword,String type,String action,Long targetId,int size,long offset);
    @Select("SELECT COUNT(*)" + AUDIT_FILTER)
    long auditCount(String keyword,String type,String action,Long targetId);
}
