package com.novelplatform.admin;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminModels {
    private AdminModels() {}
    public record UserRow(long id, String username, String nickname, String bio, String role,
                          boolean enabled, long version, LocalDateTime createdAt) {}
    public record Term(long id, String name, long version, long usageCount) {}
    public record BookRow(long id, String title, String reviewStatus, String categoryName) {}
    public record Assignment(long novelId, String title, String reviewStatus, long version,
                             long categoryId, Long publicCategoryId, List<Long> tagIds) {}
    public record Audit(long id, long actorId, String targetType, long targetId, String action,
                        String reason, LocalDateTime createdAt) {}
    public record Profile(@NotBlank @Size(max=30) String nickname, @NotNull @Size(max=500) String bio,
                          @NotNull @Min(0) Long version, @NotBlank @Size(max=500) String reason) {}
    public record Status(@NotNull Boolean enabled, @NotNull @Min(0) Long version,
                         @NotBlank @Size(max=500) String reason) {}
    public record TermInput(@NotBlank @Size(max=20) String name, @NotNull @Min(0) Long version) {}
    public record Classify(@NotNull @Positive Long categoryId,
                           @NotNull @Size(max=5) List<@NotNull @Positive Long> tagIds,
                           @NotNull @Min(0) Long version, @NotBlank @Size(max=500) String reason) {}
}
