package com.novelplatform.content;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.novelplatform.content.ContentViews.*;

@RestController
@RequestMapping("/api")
public class ContentController {
    private final ContentService service;
    private final com.novelplatform.discovery.DiscoveryService discovery;
    public ContentController(ContentService service,com.novelplatform.discovery.DiscoveryService discovery) { this.service=service;this.discovery=discovery; }
    private long user(Authentication a) { return Long.parseLong(a.getName()); }

    @GetMapping("/public/categories") public List<Category> categories() { return service.categories(); }
    @GetMapping("/public/novels") public Page<com.novelplatform.discovery.DiscoveryViews.Book> books(@RequestParam(defaultValue="") String keyword,
            @RequestParam(required=false) Long categoryId,@RequestParam(required=false) String serialStatus,
            @RequestParam(defaultValue="NEWEST") String sort,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) {
        return discovery.search(keyword,categoryId,serialStatus,sort,page,size,false);
    }
    @GetMapping("/public/novels/{id}") public Book book(@PathVariable long id) { return service.publicBook(id); }
    @GetMapping("/public/novels/{id}/chapters") public Page<ChapterSummary> directory(@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return service.publicChapters(id,page,size); }
    @GetMapping("/public/chapters/{id}") public Reading read(@PathVariable long id) { return service.read(id); }

    @GetMapping("/author/novels") public Page<Novel> authorBooks(Authentication a,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="12") int size) { return service.authorNovels(user(a),page,size); }
    @PostMapping("/author/novels") @ResponseStatus(HttpStatus.CREATED)
    public Novel create(Authentication a,@Valid @RequestBody ContentRequests.NovelInput input) { return service.createNovel(user(a),input); }
    @GetMapping("/author/novels/{id}") public Novel authorBook(Authentication a,@PathVariable long id) { return service.authorNovel(user(a),id); }
    @PutMapping("/author/novels/{id}") public Novel update(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.NovelInput input) { return service.updateNovel(user(a),id,input); }
    @PostMapping("/author/novels/{id}/submit") public Novel submit(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.Version v) { return service.submitNovel(user(a),id,v.version()); }
    @GetMapping("/author/novels/{id}/chapters") public Page<ChapterDraft> chapters(Authentication a,@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return service.authorChapters(user(a),id,page,size); }
    @PostMapping("/author/novels/{id}/chapters") @ResponseStatus(HttpStatus.CREATED)
    public Chapter createChapter(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.ChapterInput input) { return service.createChapter(user(a),id,input); }
    @GetMapping("/author/novels/{id}/next-chapter-number")
    public java.util.Map<String,Integer> nextChapterNumber(Authentication a,@PathVariable long id) { return service.nextChapterNumber(user(a),id); }
    @GetMapping("/author/chapters/{id}") public Chapter chapter(Authentication a,@PathVariable long id) { return service.authorChapter(user(a),id); }
    @PutMapping("/author/chapters/{id}") public Chapter updateChapter(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.ChapterInput input) { return service.updateChapter(user(a),id,input); }
    @PostMapping("/author/chapters/{id}/submit") public Chapter submitChapter(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.Version v) { return service.submitChapter(user(a),id,v.version()); }
    @DeleteMapping("/author/chapters/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChapter(Authentication a,@PathVariable long id,@RequestParam long version) { service.deleteChapter(user(a),id,version); }
    @GetMapping("/author/novels/{id}/reviews") public List<ReviewSummary> history(Authentication a,@PathVariable long id) { return service.history(id,user(a)); }

    @GetMapping("/admin/novels") public Page<Novel> adminBooks(@RequestParam(defaultValue="PENDING") String state,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return service.adminNovels(state,page,size); }
    @GetMapping("/admin/novels/{id}") public Novel reviewBook(@PathVariable long id) { return service.adminNovel(id); }
    @GetMapping("/admin/chapters") public Page<ChapterDraft> adminChapters(@RequestParam(defaultValue="PENDING") String state,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return service.adminChapters(state,page,size); }
    @GetMapping("/admin/chapters/{id}") public Chapter reviewChapter(@PathVariable long id) { return service.adminChapter(id); }
    @PostMapping("/admin/novels/{id}/review") public Novel decideBook(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.Decision d) { return service.reviewNovel(user(a),id,d); }
    @PostMapping("/admin/chapters/{id}/review") public Chapter decideChapter(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.Decision d) { return service.reviewChapter(user(a),id,d); }
    @PostMapping("/admin/novels/{id}/takedown") public Novel takedownBook(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.Takedown d) { return service.takedownNovel(user(a),id,d); }
    @PostMapping("/admin/chapters/{id}/takedown") public Chapter takedownChapter(Authentication a,@PathVariable long id,@Valid @RequestBody ContentRequests.Takedown d) { return service.takedownChapter(user(a),id,d); }
    @GetMapping("/admin/novels/{id}/reviews") public List<ReviewSummary> adminHistory(@PathVariable long id) { return service.history(id,null); }
}
