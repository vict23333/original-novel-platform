package com.novelplatform.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelplatform.common.BusinessException;
import com.novelplatform.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import static org.springframework.http.HttpStatus.*;
import static com.novelplatform.content.ContentViews.*;

@Service
public class ContentService {
    private final NovelMapper novels;
    private final ChapterMapper chapters;
    private final CategoryMapper categories;
    private final ReviewRecordMapper reviews;
    private final UserMapper users;
    private final ObjectMapper json;

    public ContentService(NovelMapper novels, ChapterMapper chapters, CategoryMapper categories,
                          ReviewRecordMapper reviews, UserMapper users, ObjectMapper json) {
        this.novels=novels; this.chapters=chapters; this.categories=categories;
        this.reviews=reviews; this.users=users; this.json=json;
    }
    private BusinessException missing() { return new BusinessException(NOT_FOUND,"NOT_FOUND","内容不存在或当前不可访问"); }
    private BusinessException conflict(String message) { return new BusinessException(CONFLICT,"CONTENT_CONFLICT",message); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private void version(long actual, long expected) {
        if (actual != expected) throw conflict("内容已发生变化，请保留输入并重新加载最新版本");
    }
    private void editable(String status) {
        if ("PENDING".equals(status)) throw conflict("内容正在审核，审核完成后才能修改或再次提交");
    }
    private void pending(String status) {
        if (!"PENDING".equals(status)) throw conflict("内容不再处于待审核状态，请刷新列表");
    }
    private void category(long id) {
        if (categories.selectById(id)==null) throw new BusinessException(BAD_REQUEST,"INVALID_CATEGORY","请选择有效题材");
    }
    private Novel novel(long id) { Novel n=novels.selectById(id); if(n==null) throw missing(); return n; }
    private Novel lockNovel(long id) { Novel n=novels.lockById(id); if(n==null) throw missing(); return n; }
    private void owns(Novel n,long userId) { if(n.getAuthorId()!=userId) throw missing(); }
    private Chapter chapter(long id) {
        Chapter c=chapters.selectById(id); if(c==null || c.getDeleted()) throw missing(); return c;
    }
    // 所有章节写操作均先锁父作品，再锁章节；同时防止序号竞争和与作品下架交错。
    private Chapter lockChapter(long id, Long owner) {
        Chapter found=chapter(id); Novel n=lockNovel(found.getNovelId());
        if(owner!=null) owns(n,owner);
        Chapter c=chapters.lockById(id); if(c==null || c.getDeleted()) throw missing(); return c;
    }
    private Novel visibleNovel(long id) {
        Novel n=novel(id); if(!n.getPublished() || n.getTakenDown()) throw missing(); return n;
    }
    private String limit(int page,int size) {
        if(page<1 || page>100000 || size<1 || size>50) throw new BusinessException(BAD_REQUEST,"INVALID_PAGE","页码或每页数量超出范围");
        return "LIMIT "+size+" OFFSET "+((long)(page-1)*size);
    }
    public List<Category> categories() { return categories.selectList(Wrappers.<Category>lambdaQuery().orderByAsc(Category::getId)); }

    public Page<Novel> authorNovels(long owner,int page,int size) {
        String pagination=limit(page,size);
        long total=novels.selectCount(Wrappers.<Novel>lambdaQuery().eq(Novel::getAuthorId,owner));
        return new Page<>(novels.selectList(Wrappers.<Novel>lambdaQuery().eq(Novel::getAuthorId,owner)
                .orderByDesc(Novel::getId).last(pagination)),page,size,total);
    }
    public Novel authorNovel(long owner,long id) { Novel n=novel(id); owns(n,owner); return n; }
    @Transactional public Novel createNovel(long owner,ContentRequests.NovelInput input) {
        if(input.version()!=0) throw conflict("新作品版本应为 0");
        category(input.categoryId());
        Novel n=new Novel(); n.setAuthorId(owner); apply(n,input); n.setVersion(0L);
        n.setReviewStatus("DRAFT");n.setReviewReason("");n.setPublished(false);n.setTakenDown(false);
        n.setCreatedAt(now());n.setUpdatedAt(now());novels.insert(n);return n;
    }
    private void apply(Novel n,ContentRequests.NovelInput i) {
        n.setTitle(i.title().strip());n.setIntroduction(i.introduction().strip());
        n.setCategoryId(i.categoryId());n.setSerialStatus(i.serialStatus());
    }
    @Transactional public Novel updateNovel(long owner,long id,ContentRequests.NovelInput input) {
        Novel n=lockNovel(id);owns(n,owner);version(n.getVersion(),input.version());editable(n.getReviewStatus());category(input.categoryId());
        apply(n,input);n.setVersion(n.getVersion()+1);n.setReviewStatus("DRAFT");n.setUpdatedAt(now());novels.updateById(n);return n;
    }
    @Transactional public Novel submitNovel(long owner,long id,long expected) {
        Novel n=lockNovel(id);owns(n,owner);version(n.getVersion(),expected);editable(n.getReviewStatus());
        if("APPROVED".equals(n.getReviewStatus())) throw conflict("当前版本已审核通过，无需重复提交");
        if(n.getIntroduction().isBlank()) throw new BusinessException(BAD_REQUEST,"INCOMPLETE_CONTENT","提交审核前请填写作品简介");
        n.setReviewStatus("PENDING");n.setReviewReason("");n.setVersion(n.getVersion()+1);n.setUpdatedAt(now());novels.updateById(n);return n;
    }

    public Page<ChapterDraft> authorChapters(long owner,long novelId,int page,int size) {
        authorNovel(owner,novelId);String pagination=limit(page,size);
        long total=chapters.selectCount(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,novelId).eq(Chapter::getDeleted,false));
        List<ChapterDraft> items=chapters.selectList(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,novelId).eq(Chapter::getDeleted,false)
                .select(Chapter.class,field -> !field.getColumn().equals("content") && !field.getColumn().equals("public_content"))
                .orderByAsc(Chapter::getChapterNumber).last(pagination)).stream().map(ChapterDraft::from).toList();
        return new Page<>(items,page,size,total);
    }
    public Chapter authorChapter(long owner,long id) { Chapter c=chapter(id);authorNovel(owner,c.getNovelId());return c; }
    private void availableNumber(long novelId,int number,Long except) {
        if(chapters.selectCount(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,novelId).eq(Chapter::getChapterNumber,number)
                .eq(Chapter::getDeleted,false).ne(except!=null,Chapter::getId,except))>0) throw conflict("章节序号已被使用，请选择其他序号");
    }
    @Transactional public Chapter createChapter(long owner,long novelId,ContentRequests.ChapterInput i) {
        owns(lockNovel(novelId),owner);if(i.version()!=0) throw conflict("新章节版本应为 0");availableNumber(novelId,i.chapterNumber(),null);
        Chapter c=new Chapter();c.setNovelId(novelId);c.setTitle(i.title().strip());c.setContent(i.content());c.setChapterNumber(i.chapterNumber());
        c.setVersion(0L);c.setReviewStatus("DRAFT");c.setReviewReason("");c.setPublished(false);c.setTakenDown(false);c.setDeleted(false);
        c.setCreatedAt(now());c.setUpdatedAt(now());chapters.insert(c);return c;
    }
    @Transactional public Chapter updateChapter(long owner,long id,ContentRequests.ChapterInput i) {
        Chapter c=lockChapter(id,owner);version(c.getVersion(),i.version());editable(c.getReviewStatus());availableNumber(c.getNovelId(),i.chapterNumber(),id);
        c.setTitle(i.title().strip());c.setContent(i.content());c.setChapterNumber(i.chapterNumber());
        c.setVersion(c.getVersion()+1);c.setReviewStatus("DRAFT");c.setUpdatedAt(now());chapters.updateById(c);return c;
    }
    @Transactional public Chapter submitChapter(long owner,long id,long expected) {
        Chapter c=lockChapter(id,owner);version(c.getVersion(),expected);editable(c.getReviewStatus());
        if("APPROVED".equals(c.getReviewStatus())) throw conflict("当前版本已审核通过，无需重复提交");
        if(c.getContent().isBlank()) throw new BusinessException(BAD_REQUEST,"EMPTY_CHAPTER","空白正文可以保存草稿，但不能提交审核");
        c.setReviewStatus("PENDING");c.setReviewReason("");c.setVersion(c.getVersion()+1);c.setUpdatedAt(now());chapters.updateById(c);return c;
    }
    @Transactional public void deleteChapter(long owner,long id,long expected) {
        Chapter c=lockChapter(id,owner);version(c.getVersion(),expected);editable(c.getReviewStatus());
        // 保留稳定编号及审核关联，释放序号；公开接口始终过滤 deleted。
        chapters.update(null,Wrappers.<Chapter>lambdaUpdate().eq(Chapter::getId,id).set(Chapter::getDeleted,true)
                .set(Chapter::getChapterNumber,null).set(Chapter::getPublicChapterNumber,null)
                .set(Chapter::getVersion,c.getVersion()+1).set(Chapter::getUpdatedAt,now()));
    }

    private String state(String state) {
        if(!List.of("PENDING","PUBLISHED","ALL").contains(state)) throw new BusinessException(BAD_REQUEST,"INVALID_STATE","筛选状态不正确");return state;
    }
    private LambdaQueryWrapper<Novel> adminNovelQuery(String state) {
        state(state);return Wrappers.<Novel>lambdaQuery().eq("PENDING".equals(state),Novel::getReviewStatus,"PENDING")
                .eq("PUBLISHED".equals(state),Novel::getPublished,true);
    }
    public Page<Novel> adminNovels(String state,int page,int size) {
        String pagination=limit(page,size);long total=novels.selectCount(adminNovelQuery(state));
        return new Page<>(novels.selectList(adminNovelQuery(state).orderByDesc(Novel::getId).last(pagination)),page,size,total);
    }
    public Novel adminNovel(long id) { return novel(id); }
    public Chapter adminChapter(long id) { return chapter(id); }
    private LambdaQueryWrapper<Chapter> adminChapterQuery(String state) {
        state(state);return Wrappers.<Chapter>lambdaQuery().eq(Chapter::getDeleted,false)
                .eq("PENDING".equals(state),Chapter::getReviewStatus,"PENDING").eq("PUBLISHED".equals(state),Chapter::getPublished,true);
    }
    public Page<ChapterDraft> adminChapters(String state,int page,int size) {
        String pagination=limit(page,size);long total=chapters.selectCount(adminChapterQuery(state));
        return new Page<>(chapters.selectList(adminChapterQuery(state)
                .select(Chapter.class,field -> !field.getColumn().equals("content") && !field.getColumn().equals("public_content"))
                .orderByDesc(Chapter::getId).last(pagination)).stream().map(ChapterDraft::from).toList(),page,size,total);
    }
    private void decision(ContentRequests.Decision d) {
        if("REJECT".equals(d.action()) && d.reason().isBlank()) throw new BusinessException(BAD_REQUEST,"REASON_REQUIRED","驳回时必须说明原因");
    }
    private void record(long reviewer,Novel n,Chapter c,String action,String reason) {
        ReviewRecord r=new ReviewRecord();r.setNovelId(n.getId());r.setChapterId(c==null?null:c.getId());
        r.setContentVersion(c==null?n.getVersion():c.getVersion());r.setAction(action);r.setReason(reason.strip());r.setReviewerId(reviewer);r.setCreatedAt(now());
        try { r.setSnapshot(json.writeValueAsString(c==null?n:c)); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Unable to record content snapshot",e); }
        reviews.insert(r);
    }
    @Transactional public Novel reviewNovel(long reviewer,long id,ContentRequests.Decision d) {
        Novel n=lockNovel(id);version(n.getVersion(),d.version());pending(n.getReviewStatus());decision(d);
        record(reviewer,n,null,d.action(),d.reason());
        if("APPROVE".equals(d.action())) {
            n.setPublicTitle(n.getTitle());n.setPublicIntroduction(n.getIntroduction());n.setPublicCategoryId(n.getCategoryId());
            n.setPublicSerialStatus(n.getSerialStatus());n.setPublished(true);n.setTakenDown(false);n.setReviewStatus("APPROVED");
        } else n.setReviewStatus("REJECTED");
        n.setReviewReason(d.reason().strip());n.setVersion(n.getVersion()+1);n.setUpdatedAt(now());novels.updateById(n);return n;
    }
    @Transactional public Chapter reviewChapter(long reviewer,long id,ContentRequests.Decision d) {
        Chapter c=lockChapter(id,null);version(c.getVersion(),d.version());pending(c.getReviewStatus());decision(d);
        if("APPROVE".equals(d.action()) && chapters.selectCount(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,c.getNovelId())
                .eq(Chapter::getPublicChapterNumber,c.getChapterNumber()).ne(Chapter::getId,id))>0) throw conflict("该序号仍被另一公开章节使用，请先处理章节顺序");
        record(reviewer,novel(c.getNovelId()),c,d.action(),d.reason());
        if("APPROVE".equals(d.action())) {
            c.setPublicTitle(c.getTitle());c.setPublicContent(c.getContent());c.setPublicChapterNumber(c.getChapterNumber());
            c.setPublished(true);c.setTakenDown(false);c.setReviewStatus("APPROVED");
        } else c.setReviewStatus("REJECTED");
        c.setReviewReason(d.reason().strip());c.setVersion(c.getVersion()+1);c.setUpdatedAt(now());chapters.updateById(c);return c;
    }
    @Transactional public Novel takedownNovel(long reviewer,long id,ContentRequests.Takedown d) {
        Novel n=lockNovel(id);version(n.getVersion(),d.version());
        if(!n.getPublished() || n.getTakenDown()) throw conflict("作品未公开或已经下架");
        record(reviewer,n,null,"TAKEDOWN",d.reason());n.setTakenDown(true);n.setReviewStatus("REJECTED");n.setReviewReason(d.reason().strip());
        n.setVersion(n.getVersion()+1);n.setUpdatedAt(now());novels.updateById(n);return n;
    }
    @Transactional public Chapter takedownChapter(long reviewer,long id,ContentRequests.Takedown d) {
        Chapter c=lockChapter(id,null);version(c.getVersion(),d.version());
        if(!c.getPublished() || c.getTakenDown()) throw conflict("章节未公开或已经下架");
        record(reviewer,novel(c.getNovelId()),c,"TAKEDOWN",d.reason());c.setTakenDown(true);c.setReviewStatus("REJECTED");c.setReviewReason(d.reason().strip());
        c.setVersion(c.getVersion()+1);c.setUpdatedAt(now());chapters.updateById(c);return c;
    }
    public List<ReviewSummary> history(long novelId,Long owner) {
        Novel n=novel(novelId);if(owner!=null) owns(n,owner);
        return reviews.selectList(Wrappers.<ReviewRecord>lambdaQuery().eq(ReviewRecord::getNovelId,novelId)
                .select(ReviewRecord.class,field -> !field.getColumn().equals("snapshot")).orderByDesc(ReviewRecord::getId).last("LIMIT 50"))
                .stream().map(r -> new ReviewSummary(r.getId(),r.getChapterId(),r.getContentVersion(),r.getAction(),r.getReason(),r.getCreatedAt())).toList();
    }

    private LambdaQueryWrapper<Novel> publicQuery(String keyword,Long categoryId) {
        if(keyword.length()>100) throw new BusinessException(BAD_REQUEST,"INVALID_SEARCH","搜索词不能超过 100 字");
        String literal=keyword.strip().replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        return Wrappers.<Novel>lambdaQuery().eq(Novel::getPublished,true).eq(Novel::getTakenDown,false)
                .eq(categoryId!=null,Novel::getPublicCategoryId,categoryId)
                .and(!literal.isEmpty(),query -> query.like(Novel::getPublicTitle,literal)
                        .or().apply("author_id IN (SELECT id FROM app_user WHERE nickname LIKE {0})","%"+literal+"%"));
    }
    private Book book(Novel n) {
        return new Book(n.getId(),n.getPublicTitle(),n.getPublicIntroduction(),n.getPublicCategoryId(),
                categories.selectById(n.getPublicCategoryId()).getName(),n.getPublicSerialStatus(),users.selectById(n.getAuthorId()).getNickname());
    }
    public Page<Book> publicBooks(String keyword,Long categoryId,int page,int size) {
        String pagination=limit(page,size);long total=novels.selectCount(publicQuery(keyword,categoryId));
        return new Page<>(novels.selectList(publicQuery(keyword,categoryId).orderByDesc(Novel::getId).last(pagination))
                .stream().map(this::book).toList(),page,size,total);
    }
    public Book publicBook(long id) { return book(visibleNovel(id)); }
    public Page<ChapterSummary> publicChapters(long novelId,int page,int size) {
        visibleNovel(novelId);String pagination=limit(page,size);
        long total=chapters.selectCount(visibleChapters(novelId));
        List<ChapterSummary> items=chapters.selectList(visibleChapters(novelId)
                .select(Chapter::getId,Chapter::getPublicChapterNumber,Chapter::getPublicTitle)
                .orderByAsc(Chapter::getPublicChapterNumber).last(pagination))
                .stream().map(c -> new ChapterSummary(c.getId(),c.getPublicChapterNumber(),c.getPublicTitle())).toList();
        return new Page<>(items,page,size,total);
    }
    private LambdaQueryWrapper<Chapter> visibleChapters(long novelId) {
        return Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,novelId).eq(Chapter::getDeleted,false)
                .eq(Chapter::getPublished,true).eq(Chapter::getTakenDown,false);
    }
    public Reading read(long id) {
        Chapter c=chapter(id);visibleNovel(c.getNovelId());if(!c.getPublished() || c.getTakenDown()) throw missing();
        Chapter previous=chapters.selectOne(visibleChapters(c.getNovelId()).select(Chapter::getId)
                .lt(Chapter::getPublicChapterNumber,c.getPublicChapterNumber()).orderByDesc(Chapter::getPublicChapterNumber).last("LIMIT 1"));
        Chapter next=chapters.selectOne(visibleChapters(c.getNovelId()).select(Chapter::getId)
                .gt(Chapter::getPublicChapterNumber,c.getPublicChapterNumber()).orderByAsc(Chapter::getPublicChapterNumber).last("LIMIT 1"));
        return new Reading(c.getId(),c.getNovelId(),c.getPublicChapterNumber(),c.getPublicTitle(),c.getPublicContent(),
                previous==null?null:previous.getId(),next==null?null:next.getId(),ReadingText.revision(c.getPublicContent()));
    }
}
