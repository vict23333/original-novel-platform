package com.novelplatform.comment;

import com.novelplatform.common.BusinessException;
import com.novelplatform.content.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static org.springframework.http.HttpStatus.*;

@Service
public class CommentService {
    public record Create(@NotNull @Positive Long novelId, @Positive Long chapterId, @Positive Long replyToId,
                         @NotBlank @Size(max=500) String body) {}
    public record Remove(@NotBlank @Size(max=500) String reason) {}
    private final CommentMapper comments;
    private final NovelMapper novels;
    private final ChapterMapper chapters;
    public CommentService(CommentMapper comments, NovelMapper novels, ChapterMapper chapters) {
        this.comments=comments; this.novels=novels; this.chapters=chapters;
    }
    private BusinessException missing() { return new BusinessException(NOT_FOUND,"COMMENT_UNAVAILABLE","评论或所属内容不存在、已删除或不可公开访问"); }
    private void paging(int page,int size) {
        if(page<1 || page>100000 || size<1 || size>50) throw new BusinessException(BAD_REQUEST,"INVALID_PAGE","页码或每页数量超出范围");
    }
    private ReaderComment find(long id) {
        ReaderComment c=comments.selectById(id); if(c==null) throw missing(); return c;
    }
    private void scope(long novelId,Long chapterId,boolean lock,boolean requireVisible) {
        Novel n=lock?novels.lockById(novelId):novels.selectById(novelId);
        if(n==null || (requireVisible && (!n.getPublished() || n.getTakenDown()))) throw missing();
        if(chapterId!=null) {
            Chapter c=lock?chapters.lockById(chapterId):chapters.selectById(chapterId);
            if(c==null || c.getNovelId()!=novelId || (requireVisible && (c.getDeleted() || !c.getPublished() || c.getTakenDown()))) throw missing();
        }
    }
    public ContentViews.Page<CommentMapper.View> list(long novelId,Long chapterId,Long viewer,int page,int size) {
        paging(page,size); scope(novelId,chapterId,false,true);
        return new ContentViews.Page<>(comments.roots(novelId,chapterId,viewer,size,(long)(page-1)*size),page,size,comments.rootCount(novelId,chapterId));
    }
    public ContentViews.Page<CommentMapper.View> replies(long rootId,Long viewer,int page,int size) {
        paging(page,size); var root=find(rootId);
        if(root.getRootId()!=null) throw missing();
        scope(root.getNovelId(),root.getChapterId(),false,true);
        return new ContentViews.Page<>(comments.replies(rootId,viewer,size,(long)(page-1)*size),page,size,comments.replyCount(rootId));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public CommentMapper.View create(long userId,Create input) {
        scope(input.novelId(),input.chapterId(),true,true);
        ReaderComment reply=null;
        if(input.replyToId()!=null) {
            reply=find(input.replyToId());
            if(!reply.getNovelId().equals(input.novelId()) || !Objects.equals(reply.getChapterId(),input.chapterId()))
                throw new BusinessException(BAD_REQUEST,"WRONG_COMMENT_TARGET","回复必须属于同一作品及章节评论区");
            var root=comments.lock(reply.getRootId()==null?reply.getId():reply.getRootId());
            reply=comments.lock(reply.getId());
            if(root.getDeleted() || reply.getDeleted()) throw missing();
        }
        ReaderComment c=new ReaderComment(); c.setNovelId(input.novelId()); c.setChapterId(input.chapterId());
        c.setUserId(userId); c.setBody(input.body().strip()); c.setDeleted(false);
        c.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        if(c.getBody().isBlank()) throw new BusinessException(BAD_REQUEST,"INVALID_INPUT","请输入评论正文");
        if(reply!=null) { c.setRootId(reply.getRootId()==null?reply.getId():reply.getRootId()); c.setReplyToId(reply.getId()); }
        comments.insert(c); return comments.view(c.getId(),userId);
    }
    // MySQL 默认可重复读会保留等待锁之前的快照；取得作品锁后必须读取最新点赞状态。
    @Transactional(isolation=Isolation.READ_COMMITTED) public CommentMapper.View like(long userId,long id,boolean add) {
        var c=find(id); scope(c.getNovelId(),c.getChapterId(),true,true); c=comments.lock(id);
        if(c.getDeleted()) throw missing();
        if(add) { if(comments.hasLike(id,userId)==0) comments.like(id,userId); }
        else comments.unlike(id,userId);
        return comments.view(id,userId);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public void remove(long userId,long id,String reason,boolean admin) {
        var c=find(id); scope(c.getNovelId(),c.getChapterId(),true,false); c=comments.lock(id);
        if(!admin && c.getUserId()!=userId) throw new BusinessException(FORBIDDEN,"FORBIDDEN","只能删除自己发表的评论");
        if(c.getDeleted()) return;
        comments.remove(id,userId,reason,LocalDateTime.now(ZoneOffset.UTC)); comments.clearLikes(id);
    }
    public ContentViews.Page<CommentMapper.AdminView> adminList(Long novelId,Boolean deleted,String keyword,int page,int size) {
        paging(page,size); if(keyword.length()>100) throw new BusinessException(BAD_REQUEST,"INVALID_INPUT","搜索词最多 100 字");
        return new ContentViews.Page<>(comments.adminList(novelId,deleted,keyword.strip(),size,(long)(page-1)*size),page,size,comments.adminCount(novelId,deleted,keyword.strip()));
    }
}
