package com.novelplatform.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelplatform.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserMapper users;
    private final AuthService auth;
    private final String username;
    private final String password;
    public AdminBootstrap(UserMapper users, AuthService auth,
                          @Value("${app.admin-username}") String username,
                          @Value("${app.admin-password}") String password) {
        this.users = users; this.auth = auth; this.username = username; this.password = password;
    }
    @Override public void run(ApplicationArguments args) {
        if (username.isEmpty() && password.isEmpty()) return;
        if (!username.matches("[a-z0-9_]{4,20}") || password.length() < 8 || password.length() > 64 || password.isBlank())
            throw new IllegalArgumentException("Administrator bootstrap configuration is invalid");
        AppUser existing = users.selectOne(Wrappers.<AppUser>lambdaQuery().eq(AppUser::getUsername, username));
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole())) throw new IllegalStateException("Administrator account name is already occupied");
            return; // 重启不重置管理员密码，也不提升已存在的普通账号。
        }
        auth.create(username, password, "平台管理员", "ADMIN");
    }
}
