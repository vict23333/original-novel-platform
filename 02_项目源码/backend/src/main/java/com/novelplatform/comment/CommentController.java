package com.novelplatform.comment;

import com.novelplatform.content.ContentViews;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommentController {
    private final CommentService service;
    public CommentController(CommentService service) { this.service=service; }
    private Long user(Authentication auth) { return auth==null?null:Long.valueOf(auth.getName()); }
    @GetMapping("/public/novels/{novelId}/comments")
    public ContentViews.Page<CommentMapper.View> list(Authentication auth,@PathVariable long novelId,
            @RequestParam(required=false) Long chapterId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        return service.list(novelId,chapterId,user(auth),page,size);
    }
    @GetMapping("/public/comments/{id}/replies")
    public ContentViews.Page<CommentMapper.View> replies(Authentication auth,@PathVariable long id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        return service.replies(id,user(auth),page,size);
    }
    @PostMapping("/me/comments") @ResponseStatus(HttpStatus.CREATED)
    public CommentMapper.View create(Authentication auth,@Valid @RequestBody CommentService.Create input) { return service.create(user(auth),input); }
    @PutMapping("/me/comments/{id}/like")
    public CommentMapper.View like(Authentication auth,@PathVariable long id) { return service.like(user(auth),id,true); }
    @DeleteMapping("/me/comments/{id}/like")
    public CommentMapper.View unlike(Authentication auth,@PathVariable long id) { return service.like(user(auth),id,false); }
    @DeleteMapping("/me/comments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth,@PathVariable long id) { service.remove(user(auth),id,"本人删除",false); }
    @GetMapping("/admin/comments")
    public ContentViews.Page<CommentMapper.AdminView> adminList(@RequestParam(required=false) Long novelId,
            @RequestParam(required=false) Boolean deleted,@RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) {
        return service.adminList(novelId,deleted,keyword,page,size);
    }
    @DeleteMapping("/admin/comments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moderate(Authentication auth,@PathVariable long id,@Valid @RequestBody CommentService.Remove input) {
        service.remove(user(auth),id,input.reason().strip(),true);
    }
}
