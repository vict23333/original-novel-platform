package com.novelplatform.content;

import jakarta.validation.constraints.*;

public final class ContentRequests {
    private ContentRequests() {}
    public record NovelInput(
            @NotBlank @Size(max = 50) String title,
            @NotNull @Size(max = 1000) String introduction,
            @NotNull @Positive Long categoryId,
            @NotBlank @Pattern(regexp = "SERIALIZING|COMPLETED") String serialStatus,
            @NotNull @Min(0) Long version) {}
    public record ChapterInput(
            @NotNull @Min(1) @Max(100000) Integer chapterNumber,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Size(max = 20000) String content,
            @NotNull @Min(0) Long version,
            @Size(max=2000) String goal) {
        public ChapterInput(Integer number,String title,String content,Long version){this(number,title,content,version,null);}
    }
    public record Version(@NotNull @Min(0) Long version) {}
    public record Decision(
            @NotNull @Min(0) Long version,
            @NotBlank @Pattern(regexp = "APPROVE|REJECT") String action,
            @NotNull @Size(max = 500) String reason) {}
    public record Takedown(@NotNull @Min(0) Long version, @NotBlank @Size(max = 500) String reason) {}
}
