package com.novelplatform.reading;

import com.novelplatform.content.ContentViews;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class ReadingController {
    private final ReadingService service;
    public ReadingController(ReadingService service) { this.service = service; }
    private long user(Authentication auth) { return Long.parseLong(auth.getName()); }

    @GetMapping("/bookshelf")
    public ContentViews.Page<ReadingService.ShelfItem> shelf(Authentication auth,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="12") int size,
            @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="ALL") String status,
            @RequestParam(defaultValue="ADDED") String sort) {
        return service.shelf(user(auth),page,size,keyword,status,sort);
    }
    @PutMapping("/bookshelf/{novelId}")
    public ReadingService.State add(Authentication auth, @PathVariable long novelId) {
        return service.add(user(auth),novelId);
    }
    @DeleteMapping("/bookshelf/{novelId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable long novelId) { service.remove(user(auth),novelId); }
    @GetMapping("/novels/{novelId}/reading")
    public ReadingService.State state(Authentication auth, @PathVariable long novelId) { return service.state(user(auth),novelId); }
    @PutMapping("/novels/{novelId}/reading")
    public ReadingService.State save(Authentication auth, @PathVariable long novelId,
            @Valid @RequestBody ReadingService.Save input) { return service.save(user(auth),novelId,input); }
}
