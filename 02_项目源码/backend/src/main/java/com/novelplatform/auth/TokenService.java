package com.novelplatform.auth;

import com.novelplatform.user.AppUser;
import com.novelplatform.user.UserView;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    public TokenService(JwtEncoder encoder) { this.encoder = encoder; }
    public record LoginResult(String accessToken, Instant expiresAt, UserView user) {}

    public LoginResult issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(7200);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("novel-platform")
                .subject(user.getId().toString()).issuedAt(now).expiresAt(expiry)
                .claim("ver", user.getTokenVersion()).build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResult(value, expiry, UserView.from(user));
    }
}
