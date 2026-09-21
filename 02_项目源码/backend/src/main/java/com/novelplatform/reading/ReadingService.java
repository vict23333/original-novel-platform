package com.novelplatform.reading;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelplatform.common.BusinessException;
import com.novelplatform.content.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
public class ReadingService {
    public record Save(@NotNull @Positive Long chapterId, @NotNull @Min(0) @Max(20000) Integer paragraphIndex,
                       @NotBlank @Pattern(regexp="[0-9a-f]{64}") String contentRevision,
                       @NotNull @Min(0) Long version) {}
    public record State(boolean shelved, long version, Long chapterId, int paragraphIndex,
                        Long resumeChapterId, int resumeParagraphIndex, String chapterTitle, String message) {}
    public record ShelfItem(long novelId, String title, boolean available, LocalDateTime addedAt, State reading) {}
    private final ReadingMapper data;
    private final NovelMapper novels;
    private final ChapterMapper chapters;

    public ReadingService(ReadingMapper data, NovelMapper novels, ChapterMapper chapters) {
        this.data = data; this.novels = novels; this.chapters = chapters;
    }
    private BusinessException missing() { return new BusinessException(NOT_FOUND,"NOT_FOUND","内容不存在或当前不可阅读"); }
    private boolean visible(Novel n) { return n != null && n.getPublished() && !n.getTakenDown(); }
    private boolean readable(Chapter c) { return c != null && !c.getDeleted() && c.getPublished() && !c.getTakenDown(); }

    @Transactional public State add(long userId, long novelId) {
        data.lockUser(userId);
        Novel n = novels.lockById(novelId);
        if (!visible(n)) throw missing();
        if (data.contains(userId, novelId) == 0) data.add(userId, novelId);
        return state(userId, novelId);
    }
    @Transactional public void remove(long userId, long novelId) {
        data.lockUser(userId);
        data.remove(userId, novelId); // 幂等移除；阅读记录独立保存。
    }
    public ContentViews.Page<ShelfItem> shelf(long userId, int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 50)
            throw new BusinessException(BAD_REQUEST,"INVALID_PAGE","页码或每页数量超出范围");
        List<ShelfItem> items = data.shelf(userId,size,(long)(page-1)*size).stream().map(row -> {
            Novel n = novels.selectById(row.novelId());
            return new ShelfItem(n.getId(),n.getPublicTitle(),visible(n),row.addedAt(),resolve(userId,n));
        }).toList();
        return new ContentViews.Page<>(items,page,size,data.count(userId));
    }
    public State state(long userId, long novelId) {
        Novel n = novels.selectById(novelId);
        // 下架信息仅允许此前收藏或阅读过此作品的用户查看，不暴露任意草稿。
        if (n == null || (!visible(n) && data.contains(userId,novelId) == 0 && data.progress(userId,novelId) == null))
            throw missing();
        return resolve(userId,n);
    }
    private State resolve(long userId, Novel n) {
        var p = data.progress(userId,n.getId());
        boolean shelved = data.contains(userId,n.getId()) > 0;
        long version = p == null ? 0 : p.version();
        Long storedId = p == null ? null : p.chapterId();
        int storedIndex = p == null ? 0 : p.paragraphIndex();
        if (!visible(n)) return new State(shelved,version,storedId,storedIndex,null,0,"","作品已下架，暂不可阅读");
        if (p != null) {
            Chapter c = chapters.selectById(p.chapterId());
            if (readable(c)) {
                boolean unchanged = p.contentRevision().equals(ReadingText.revision(c.getPublicContent()));
                return new State(shelved,version,storedId,storedIndex,c.getId(),unchanged ? storedIndex : 0,
                        c.getPublicTitle(),unchanged ? "继续上次阅读" : "章节正文已修订，将从本章开头继续");
            }
        }
        Chapter first = chapters.selectOne(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,n.getId())
                .eq(Chapter::getDeleted,false).eq(Chapter::getPublished,true).eq(Chapter::getTakenDown,false)
                .orderByAsc(Chapter::getPublicChapterNumber).last("LIMIT 1"));
        return new State(shelved,version,storedId,storedIndex,first == null ? null : first.getId(),0,
                first == null ? "" : first.getPublicTitle(), first == null ? "暂无可读章节" :
                p == null ? "开始阅读" : "原章节已不可读，将从首个可读章节开始");
    }
    @Transactional public State save(long userId, long novelId, Save input) {
        data.lockUser(userId);
        if (!visible(novels.lockById(novelId))) throw missing();
        Chapter c = chapters.lockById(input.chapterId());
        if (!readable(c) || c.getNovelId() != novelId) throw missing();
        String revision = ReadingText.revision(c.getPublicContent());
        if (!revision.equals(input.contentRevision()))
            throw new BusinessException(CONFLICT,"TEXT_CHANGED","正文已更新，请刷新章节后再保存进度");
        if (input.paragraphIndex() >= ReadingText.paragraphCount(c.getPublicContent()))
            throw new BusinessException(BAD_REQUEST,"INVALID_PARAGRAPH","阅读位置超出正文范围");
        var current = data.progress(userId,novelId);
        long version = current == null ? 0 : current.version();
        if (input.version() != version)
            throw new BusinessException(CONFLICT,"PROGRESS_CONFLICT","其他页面已更新阅读记录，自动保存已暂停；确认后可保存当前位置");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (current == null) data.insertProgress(userId,novelId,c.getId(),input.paragraphIndex(),revision,now);
        else data.updateProgress(userId,novelId,c.getId(),input.paragraphIndex(),revision,now);
        return state(userId,novelId);
    }
}
