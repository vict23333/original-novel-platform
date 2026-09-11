package com.novelplatform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}
    public record Register(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{4,20}", message = "账号需为 4—20 位字母、数字或下划线") String username,
            @NotBlank @Size(min = 8, max = 64, message = "密码长度需为 8—64 位") String password,
            @NotBlank @Size(max = 30, message = "昵称不能超过 30 字") String nickname,
            @NotBlank @Pattern(regexp = "READER|AUTHOR", message = "只能注册读者或作者账号") String role) {}

    public record Login(
            @NotBlank @Size(max = 20) String username,
            @NotBlank @Size(max = 64) String password) {}

    public record Profile(
            @NotBlank @Size(max = 30, message = "昵称不能超过 30 字") String nickname,
            @jakarta.validation.constraints.NotNull @Size(max = 500, message = "简介不能超过 500 字") String bio) {}
}
