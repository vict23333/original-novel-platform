package com.novelplatform.content;

import java.time.LocalDateTime;
import java.util.List;

public final class ContentViews {
    private ContentViews() {}
    public record Page<T>(List<T> items, int page, int size, long total) {}
    public record Book(long id, String title, String introduction, long categoryId,
                       String categoryName, String serialStatus, String authorName) {}
    public record ChapterSummary(long id, int chapterNumber, String title) {}
    public record Reading(long id, long novelId, int chapterNumber, String title, String content,
                          Long previousChapterId, Long nextChapterId, String contentRevision) {}
    public record ChapterDraft(long id, long novelId, int chapterNumber, String title, long version,
                               String reviewStatus, String reviewReason, boolean published, boolean takenDown) {
        public static ChapterDraft from(Chapter c) {
            return new ChapterDraft(c.getId(),c.getNovelId(),c.getChapterNumber(),c.getTitle(),c.getVersion(),
                    c.getReviewStatus(),c.getReviewReason(),c.getPublished(),c.getTakenDown());
        }
    }
    public record ReviewSummary(long id, Long chapterId, long contentVersion, String action,
                                String reason, LocalDateTime createdAt) {}
}
