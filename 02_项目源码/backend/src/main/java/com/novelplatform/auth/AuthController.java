package com.novelplatform.auth;

import com.novelplatform.user.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody AuthRequests.Register input) { return service.register(input); }
    @PostMapping("/auth/login")
    public TokenService.LoginResult login(@Valid @RequestBody AuthRequests.Login input) { return service.login(input); }
    @GetMapping("/users/me") public UserView me(Authentication auth) { return service.current(id(auth)); }
    @PutMapping("/users/me")
    public UserView update(Authentication auth, @Valid @RequestBody AuthRequests.Profile input) { return service.update(id(auth), input); }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication auth) { service.logout(id(auth)); }
    @GetMapping("/author/workspace")
    public Map<String, String> author() { return Map.of("role", "AUTHOR", "message", "作者身份验证通过，作品与章节功能将在后续迭代实现。"); }
    @GetMapping("/admin/workspace")
    public Map<String, String> admin() { return Map.of("role", "ADMIN", "message", "管理员身份验证通过，审核与管理功能将在后续迭代实现。"); }
    private long id(Authentication auth) { return Long.parseLong(auth.getName()); }
}
