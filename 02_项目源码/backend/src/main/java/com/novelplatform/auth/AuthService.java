package com.novelplatform.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelplatform.common.BusinessException;
import com.novelplatform.user.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyHash;
    public AuthService(UserMapper users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users; this.passwords = passwords; this.tokens = tokens;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public UserView register(AuthRequests.Register input) {
        return UserView.from(create(input.username(), input.password(), input.nickname(), input.role()));
    }

    AppUser create(String username, String password, String nickname, String role) {
        AppUser user = new AppUser();
        user.setUsername(username.toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwords.encode(password));
        user.setNickname(nickname.strip()); user.setBio(""); user.setRole(role);
        user.setEnabled(true); user.setTokenVersion(0L);
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)); user.setUpdatedAt(user.getCreatedAt());
        try { users.insert(user); }
        catch (DuplicateKeyException e) { throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "账号已存在，请更换账号"); }
        return user;
    }

    public TokenService.LoginResult login(AuthRequests.Login input) {
        AppUser user = users.selectOne(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getUsername, input.username().toLowerCase(Locale.ROOT)));
        boolean matches = passwords.matches(input.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !Boolean.TRUE.equals(user.getEnabled()))
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "账号或密码错误，或账号已停用");
        return tokens.issue(user);
    }

    public UserView current(long id) { return UserView.from(users.selectById(id)); }

    public UserView update(long id, AuthRequests.Profile input) {
        // 更新目标仅来自已验签令牌，客户端无法提交 id、role 或 enabled。
        users.update(null, Wrappers.<AppUser>lambdaUpdate().eq(AppUser::getId, id)
                .set(AppUser::getNickname, input.nickname().strip()).set(AppUser::getBio, input.bio().strip())
                .set(AppUser::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
        return current(id);
    }

    public void logout(long id) {
        users.update(null, Wrappers.<AppUser>lambdaUpdate().eq(AppUser::getId, id)
                .setSql("token_version = token_version + 1"));
    }
}
