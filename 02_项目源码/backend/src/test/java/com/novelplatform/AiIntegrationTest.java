package com.novelplatform;

import com.fasterxml.jackson.databind.*;
import com.novelplatform.ai.*;
import com.novelplatform.auth.TokenService;
import com.novelplatform.common.BusinessException;
import com.novelplatform.content.ContentService;
import com.novelplatform.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"app.ai.enabled=true", "app.ai.api-url=https://example.invalid/chat/completions", "app.ai.api-key=test-only-provider-key", "app.ai.model=test-model", "app.ai.cooldown-seconds=0"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements={"DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired TokenService tokens;
    @Autowired ContentService content;
    @Autowired AiLimiter limiter;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean AiGateway gateway;
    String author, other, reader, admin;
    long owner, chapterId;
    @BeforeEach void setup() throws Exception {
        author=identity("ai_author","AUTHOR");other=identity("ai_other","AUTHOR");reader=identity("ai_reader","READER");admin=identity("ai_admin","ADMIN");
        owner=jdbc.queryForObject("SELECT id FROM app_user WHERE username='ai_author'",Long.class);
        var book=call("POST","/api/author/novels",author,Map.of("title","原创验收作品","introduction","简介","categoryId",1,"serialStatus","SERIALIZING","version",0),201);
        var chapter=call("POST","/api/author/novels/"+book.get("id").asLong()+"/chapters",author,Map.of("title","章节一","content","原始正文","chapterNumber",1,"version",0),201);
        chapterId=chapter.get("id").asLong();
        when(gateway.generate(anyString(),anyString(),anyString())).thenReturn(new AiGateway.Result("测试模拟返回：新的句子。",false,"test-model"));
    }
    String identity(String name,String role) {
        AppUser user=new AppUser();user.setUsername(name);user.setNickname("测试角色");user.setPasswordHash("test-fixture-unused-hash");user.setBio("");user.setRole(role);user.setEnabled(true);user.setTokenVersion(0L);users.insert(user);return tokens.issue(user).accessToken();
    }
    JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        var request="POST".equals(method)?post(path):get(path);
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response=mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(path+" "+response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsByteArray());
    }
    Map<String,Object> input(String mode,long version) { return Map.of("version",version,"mode",mode,"text","尚未保存的正文片段","instructions","保持第一人称"); }
    String path() { return "/api/author/chapters/"+chapterId+"/ai"; }
    @Test void previewDoesNotWriteDraftAndSupportsBothModes() throws Exception {
        var before=content.authorChapter(owner,chapterId);
        assertThat(call("POST",path(),author,input("CONTINUE",0),200).get("content").asText()).contains("测试模拟");
        call("POST",path(),author,input("POLISH",0),200);
        assertThat(content.authorChapter(owner,chapterId)).usingRecursiveComparison().isEqualTo(before);
        verify(gateway).generate("POLISH","尚未保存的正文片段","保持第一人称");
    }
    @Test void rolesAndOwnershipAreEnforcedBeforeProviderCall() throws Exception {
        call("POST",path(),null,input("CONTINUE",0),401);
        call("POST",path(),reader,input("CONTINUE",0),403);
        call("POST",path(),admin,input("CONTINUE",0),403);
        call("POST",path(),other,input("CONTINUE",0),404);
        verifyNoInteractions(gateway);
    }
    @Test void invalidTextModeAndUnexpectedFieldsNeverReachProvider() throws Exception {
        call("POST",path(),author,input("INVALID",0),400);
        call("POST",path(),author,Map.of("version",0,"mode","CONTINUE","text"," ","instructions",""),400);
        call("POST",path(),author,Map.of("version",0,"mode","POLISH","text","字".repeat(6001),"instructions",""),400);
        call("POST",path(),author,Map.of("version",0,"mode","CONTINUE","text","正文","instructions","", "apiUrl","https://example.invalid"),400);
        verifyNoInteractions(gateway);
    }
    @Test void pendingDeletedAndStaleDraftsAreBlocked() throws Exception {
        call("POST",path(),author,input("CONTINUE",1),409);
        jdbc.update("UPDATE chapter SET review_status='PENDING' WHERE id=?",chapterId);
        call("POST",path(),author,input("CONTINUE",0),409);
        jdbc.update("UPDATE chapter SET deleted=true WHERE id=?",chapterId);
        call("POST",path(),author,input("CONTINUE",0),404);
        verifyNoInteractions(gateway);
    }
    @Test void draftChangesWhileWaitingDiscardOutdatedResult() throws Exception {
        when(gateway.generate(anyString(),anyString(),anyString())).thenAnswer(invocation -> {
            jdbc.update("UPDATE chapter SET version=version+1,content='另一页保存的正文' WHERE id=?",chapterId);
            return new AiGateway.Result("过期的模拟内容",false,"test-model");
        });
        call("POST",path(),author,input("CONTINUE",0),409);
        assertThat(content.authorChapter(owner,chapterId).getContent()).isEqualTo("另一页保存的正文");
    }
    @Test void providerFailureKeepsDraftAndReleasesSlot() throws Exception {
        when(gateway.generate(anyString(),anyString(),anyString()))
                .thenThrow(new BusinessException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,"AI_TIMEOUT","生成超时"))
                .thenReturn(new AiGateway.Result("重试模拟成功",false,"test-model"));
        call("POST",path(),author,input("CONTINUE",0),504);
        call("POST",path(),author,input("CONTINUE",0),200);
        assertThat(content.authorChapter(owner,chapterId).getContent()).isEqualTo("原始正文");
    }
    @Test void statusDoesNotExposeConfigurationAndRequiresAuthor() throws Exception {
        var status=call("GET","/api/author/ai/status",author,null,200);
        assertThat(status.get("available").asBoolean()).isTrue();
        assertThat(status.toString()).doesNotContain("test-only-provider-key","example.invalid","apiKey","apiUrl");
        call("GET","/api/author/ai/status",reader,null,403);
    }
    @Test void unconfiguredServiceMakesNoExternalRequest() {
        var settings=new AiSettings(false,"","","",45,20,false);
        var service=new AiService(content,settings,gateway,limiter);
        assertThatThrownBy(() -> service.generate(owner,chapterId,new AiController.Input(0L,"CONTINUE","正文","")))
                .isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.code).isEqualTo("AI_NOT_CONFIGURED"));
        verifyNoInteractions(gateway);
    }
}
