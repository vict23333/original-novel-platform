package com.novelplatform.discovery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DiscoveryViews {
    private DiscoveryViews() {}
    public record Book(long id, String title, String introduction, long categoryId, String categoryName,
                       String serialStatus, String authorName, long totalReads, long weekReads,
                       long collections, long comments, long chapterCount, long wordCount,
                       LocalDateTime firstPublishedAt, LocalDateTime lastUpdatedAt,
                       Long latestChapterId, String latestChapterTitle) {}
    public record Totals(long works, long totalReads, long weekReads, long collections, long comments) {}
    public record Day(LocalDate date, long reads) {}
    public record Stats(String title, Totals totals, List<Day> trend) {}
    public record Details(Book book, List<Book> similar) {}
    public record ReadReceipt(boolean counted, LocalDate readDate) {}
}
