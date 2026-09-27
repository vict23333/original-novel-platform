package com.novelplatform.admin;

import com.novelplatform.content.ContentViews;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.novelplatform.admin.AdminModels.*;

@RestController
@RequestMapping("/api")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service=service; }
    private long actor(Authentication a) { return Long.parseLong(a.getName()); }
    @GetMapping("/admin/classification-books") public ContentViews.Page<BookRow> books(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page) { return service.books(keyword,page); }
    @GetMapping("/admin/users") public ContentViews.Page<UserRow> users(@RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="") String role,@RequestParam(required=false) Boolean enabled,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return service.users(keyword,role,enabled,page,size); }
    @PutMapping("/admin/users/{id}/profile") public UserRow profile(Authentication a,@PathVariable long id,@Valid @RequestBody Profile input) { return service.profile(actor(a),id,input); }
    @PutMapping("/admin/users/{id}/status") public UserRow status(Authentication a,@PathVariable long id,@Valid @RequestBody Status input) { return service.status(actor(a),id,input); }
    @GetMapping("/admin/taxonomy/{kind}") public List<Term> terms(@PathVariable String kind) { return service.terms(kind); }
    @PostMapping("/admin/taxonomy/{kind}") @ResponseStatus(HttpStatus.CREATED)
    public Term create(Authentication a,@PathVariable String kind,@Valid @RequestBody TermInput input) { return service.create(actor(a),kind,input); }
    @PutMapping("/admin/taxonomy/{kind}/{id}") public Term rename(Authentication a,@PathVariable String kind,@PathVariable long id,@Valid @RequestBody TermInput input) { return service.rename(actor(a),kind,id,input); }
    @DeleteMapping("/admin/taxonomy/{kind}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication a,@PathVariable String kind,@PathVariable long id,@RequestParam long version) { service.delete(actor(a),kind,id,version); }
    @GetMapping("/admin/novels/{id}/classification") public Assignment assignment(@PathVariable long id) { return service.assignment(id); }
    @PutMapping("/admin/novels/{id}/classification") public Assignment classify(Authentication a,@PathVariable long id,@Valid @RequestBody Classify input) { return service.classify(actor(a),id,input); }
    @GetMapping("/public/novels/{id}/tags") public List<Term> publicTags(@PathVariable long id) { return service.publicTags(id); }
    @GetMapping("/admin/audits") public List<Audit> audits() { return service.audits(); }
    @GetMapping("/admin/audits/search") public ContentViews.Page<Audit> searchAudits(
            @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String type,
            @RequestParam(defaultValue="") String action,@RequestParam(required=false) Long targetId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) {
        return service.searchAudits(keyword,type,action,targetId,page,size);
    }
}
