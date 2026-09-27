package com.novelplatform.discovery;

import com.novelplatform.content.ContentViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import static com.novelplatform.discovery.DiscoveryViews.*;

@RestController
@RequestMapping("/api")
public class DiscoveryController {
    private final DiscoveryService service;
    public DiscoveryController(DiscoveryService service) { this.service=service; }
    @Bean public static Clock readingClock() { return Clock.systemUTC(); }
    public record ReadEvent(@Size(max=36) String anonymousId) {}
    @GetMapping("/public/rankings")
    public ContentViews.Page<Book> rankings(@RequestParam(defaultValue="POPULAR") String kind,
            @RequestParam(required=false) Long categoryId,@RequestParam(required=false) String serialStatus,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) {
        return service.search("",categoryId,serialStatus,kind,page,size,true);
    }
    @GetMapping("/public/novels/{id}/discovery") public Details details(@PathVariable long id) { return service.details(id); }
    @PostMapping("/public/chapters/{id}/read-event")
    public ReadReceipt read(Authentication auth,@PathVariable long id,@Valid @RequestBody ReadEvent input) {
        return service.record(auth==null?null:Long.valueOf(auth.getName()),id,input.anonymousId());
    }
    @GetMapping("/author/stats") public Stats overview(Authentication auth) { return service.stats(Long.parseLong(auth.getName()),null); }
    @GetMapping("/author/novels/{id}/stats") public Stats stats(Authentication auth,@PathVariable long id) { return service.stats(Long.parseLong(auth.getName()),id); }
}
