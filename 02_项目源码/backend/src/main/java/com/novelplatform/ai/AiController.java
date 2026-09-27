package com.novelplatform.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@EnableConfigurationProperties(AiSettings.class)
@RequestMapping("/api/author")
public class AiController {
    public record Input(@NotNull @Min(0) Long version,
                        @NotBlank @Pattern(regexp="CONTINUE|POLISH") String mode,
                        @NotBlank @Size(max=6000) String text,
                        @NotNull @Size(max=500) String instructions) {}
    public record Status(boolean available, String model, int cooldownSeconds) {}
    private final AiService service;
    private final AiSettings settings;
    public AiController(AiService service, AiSettings settings) { this.service = service; this.settings = settings; }
    @GetMapping("/ai/status") public Status status() {
        return new Status(settings.ready(), settings.ready() ? settings.model() : "", settings.cooldownSeconds());
    }
    @PostMapping("/chapters/{id}/ai") public AiGateway.Result generate(Authentication auth, @PathVariable long id, @Valid @RequestBody Input input) {
        return service.generate(Long.parseLong(auth.getName()), id, input);
    }
}
