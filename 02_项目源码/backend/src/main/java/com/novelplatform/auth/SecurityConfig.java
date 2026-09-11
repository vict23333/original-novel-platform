package com.novelplatform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.novelplatform.common.ApiError;
import com.novelplatform.user.AppUser;
import com.novelplatform.user.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }

    @Bean SecretKey signingKey(@Value("${app.jwt-secret}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length < 32) throw new IllegalArgumentException("JWT_SECRET requires at least 32 random bytes encoded as Base64");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("novel-platform"));
        return decoder;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, UserMapper users, ObjectMapper json) throws Exception {
        return http.csrf(csrf -> csrf.disable()) // 仅接收 Authorization Bearer，不使用 Cookie 鉴权。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/author/**").hasRole("AUTHOR")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, e) -> error(res, json, 401, "UNAUTHORIZED", "请登录后继续"))
                        .accessDeniedHandler((req, res, e) -> error(res, json, 403, "FORBIDDEN", "当前账号没有此操作权限")))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((req, res, e) -> error(res, json, 401, "UNAUTHORIZED", "登录已失效，请重新登录"))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            try {
                                AppUser user = users.selectById(Long.parseLong(token.getSubject()));
                                Number version = token.getClaim("ver");
                                // 每次请求读取账号状态和真实角色，停用与退出立即使旧令牌失效。
                                if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || version == null
                                        || version.longValue() != user.getTokenVersion())
                                    throw new OAuth2AuthenticationException("invalid_token");
                                return new JwtAuthenticationToken(token,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())), token.getSubject());
                            } catch (NumberFormatException | ClassCastException e) {
                                throw new OAuth2AuthenticationException("invalid_token");
                            }
                        })))
                .build();
    }

    private static void error(HttpServletResponse response, ObjectMapper json, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), new ApiError(code, message));
    }
}
