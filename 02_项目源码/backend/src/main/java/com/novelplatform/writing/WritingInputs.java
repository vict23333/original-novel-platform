package com.novelplatform.writing;

import jakarta.validation.constraints.*;
import java.util.List;

public final class WritingInputs {
    private WritingInputs() {}
    public record Project(@NotNull @Size(max=4000) String outline,
            @NotNull @Size(max=3000) String worldSetting, @NotNull @Size(max=1000) String writingStyle,
            @NotNull @Min(0) Long version) {}
    public record Character(@NotBlank @Size(max=50) String name, @NotNull @Size(max=100) String aliases,
            @NotNull @Size(max=100) String storyRole, @NotNull @Size(max=1000) String profile,
            @NotNull @Min(0) Long version) {}
    public record Task(@Positive Long chapterId, @Min(0) Long version,
            @NotNull @Min(1) @Max(100000) Integer chapterNumber,
            @NotBlank @Pattern(regexp="DRAFT|CONTINUE|POLISH|EXPAND") String mode,
            @NotNull @Size(max=6000) String text, @NotNull @Size(max=2000) String goal,
            @NotNull @Size(max=500) String instructions,
            @NotNull @Size(max=6) List<@NotNull @Positive Long> characterIds,
            @Size(max=2) List<@NotNull @Positive Long> previousChapterIds,
            @Size(max=64) String contextDigest) {}
}
