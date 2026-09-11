package com.novelplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelplatform.user.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @BeforeEach void reset() { users.delete(Wrappers.emptyWrapper()); }

    ResultActions register(String name, String role) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",name,"password","ExamplePass_2026","nickname","示例用户","role",role))));
    }
    String login(String name) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",name,"password","ExamplePass_2026"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    AppUser find(String name) { return users.selectOne(Wrappers.<AppUser>lambdaQuery().eq(AppUser::getUsername,name)); }

    @Test void registrationPersistsHashAndDoesNotExposeSecrets() throws Exception {
        register("reader_one", "READER").andExpect(status().isCreated())
                .andExpect(jsonPath("role").value("READER")).andExpect(jsonPath("passwordHash").doesNotExist());
        AppUser user = find("reader_one");
        assertThat(user.getPasswordHash()).isNotEqualTo("ExamplePass_2026");
        assertThat(passwords.matches("ExamplePass_2026",user.getPasswordHash())).isTrue();
    }
    @Test void duplicateNamesAreCaseInsensitive() throws Exception {
        register("reader_one","READER").andExpect(status().isCreated());
        register("READER_ONE","AUTHOR").andExpect(status().isConflict());
        assertThat(users.selectCount(Wrappers.emptyWrapper())).isEqualTo(1);
    }
    @Test void publicRegistrationCannotCreateAdmin() throws Exception {
        register("admin_bad","ADMIN").andExpect(status().isBadRequest());
        assertThat(users.selectCount(Wrappers.emptyWrapper())).isZero();
    }
    @Test void invalidFieldsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"password\":\"short\",\"nickname\":\"\",\"role\":\"READER\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void incorrectPasswordAndUnknownUserReturnSameError() throws Exception {
        register("reader_one","READER");
        for (String name : new String[]{"reader_one","unknown_user"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username",name,"password","WrongPassword"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("LOGIN_FAILED"));
        }
    }
    @Test void unauthenticatedAccessIsDenied() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
    @Test void readerCannotAccessAuthorOrAdmin() throws Exception {
        register("reader_one","READER"); String token=login("reader_one");
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/author/workspace").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/workspace").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void authorHasReaderCapabilitiesButNotAdmin() throws Exception {
        register("author_one","AUTHOR"); String token=login("author_one");
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/author/workspace").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/workspace").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void adminOnlyGetsAdminWorkspace() throws Exception {
        register("admin_local","READER");
        users.update(null,Wrappers.<AppUser>lambdaUpdate().eq(AppUser::getUsername,"admin_local").set(AppUser::getRole,"ADMIN"));
        String token=login("admin_local");
        mvc.perform(get("/api/admin/workspace").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/author/workspace").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void updateOnlyChangesCurrentUserAndRejectsMassAssignment() throws Exception {
        register("reader_one","READER"); register("reader_two","READER"); String token=login("reader_one");
        mvc.perform(put("/api/users/me").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"新的昵称\",\"bio\":\"阅读笔记\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("nickname").value("新的昵称"));
        assertThat(find("reader_two").getNickname()).isEqualTo("示例用户");
        for (String extra : new String[]{"\"role\":\"ADMIN\"", "\"id\":"+find("reader_two").getId(),"\"enabled\":false"}) {
            mvc.perform(put("/api/users/me").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"恶意修改\",\"bio\":\"\","+extra+"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(find("reader_one").getRole()).isEqualTo("READER");
    }
    @Test void logoutRevokesAlreadyIssuedTokens() throws Exception {
        register("reader_one","READER"); String token=login("reader_one");
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+login("reader_one"))).andExpect(status().isOk());
    }
    @Test void disabledUserCannotUseOldTokenOrLogIn() throws Exception {
        register("reader_one","READER"); String token=login("reader_one");
        users.update(null,Wrappers.<AppUser>lambdaUpdate().eq(AppUser::getUsername,"reader_one").set(AppUser::getEnabled,false));
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reader_one\",\"password\":\"ExamplePass_2026\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void forgedAndExpiredTokensAreRejected() throws Exception {
        register("reader_one","READER");
        String token=login("reader_one");
        String[] segments=token.split("\\.");
        String forged=segments[0]+"."+segments[1]+"."+(segments[2].charAt(0)=='A'?'B':'A')+segments[2].substring(1);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+forged)).andExpect(status().isUnauthorized());
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("novel-platform").subject(find("reader_one").getId().toString())
                .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(120)).claim("ver",0).build();
        String expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
    }
}
