package com.novelplatform;

import com.fasterxml.jackson.databind.*;
import com.novelplatform.ai.AiGateway;
import com.novelplatform.auth.TokenService;
import com.novelplatform.common.BusinessException;
import com.novelplatform.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest(properties={"app.ai.enabled=true", "app.ai.api-url=https://example.invalid/chat/completions", "app.ai.api-key=test-only-provider-key", "app.ai.model=test-model", "app.ai.cooldown-seconds=0"})
@AutoConfigureMockMvc @ActiveProfiles("test")
@Sql(statements={"DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WritingIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserMapper users; @Autowired TokenService tokens; @Autowired JdbcTemplate jdbc;
    @MockitoBean AiGateway gateway;
    String author,other,reader,admin;long novel,first,second,future,alien;
    @BeforeEach void setup() throws Exception {
        author=identity("writing_author","AUTHOR");other=identity("writing_other","AUTHOR");reader=identity("writing_reader","READER");admin=identity("writing_admin","ADMIN");
        novel=book(author);first=chapter(novel,1,"前文".repeat(1200)+"前章结尾",author);second=chapter(novel,2,"林砚在城门前等待，断剑仍无声。",author);future=chapter(novel,4,"未来事件不应引用",author);
        alien=chapter(book(author),1,"另一本作品的私密正文",author);
        when(gateway.generateContext(anyString(),anyString())).thenReturn(new AiGateway.Result("隔离模拟候选，不代表真实 AI 质量。",false,"test-model"));
    }
    String identity(String name,String role){var u=new AppUser();u.setUsername(name);u.setNickname("隔离测试");u.setPasswordHash("unused");u.setBio("");u.setTokenVersion(0L);u.setRole(role);u.setEnabled(true);users.insert(u);return tokens.issue(u).accessToken();}
    JsonNode call(String method,String path,String token,Object body,int expected)throws Exception{
        var r=request(HttpMethod.valueOf(method),path);if(token!=null)r.header("Authorization","Bearer "+token);if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var result=mvc.perform(r).andReturn().getResponse();assertThat(result.getStatus()).as(path+" "+result.getContentAsString()).isEqualTo(expected);
        return result.getContentAsByteArray().length==0?json.nullNode():json.readTree(result.getContentAsByteArray());
    }
    long book(String token)throws Exception{return call("POST","/api/author/novels",token,Map.of("title","示例创作","introduction","山城故事","categoryId",1,"serialStatus","SERIALIZING","version",0),201).path("id").asLong();}
    long chapter(long book,int number,String text,String token)throws Exception{return call("POST","/api/author/novels/"+book+"/chapters",token,Map.of("title","测试章节 "+number,"chapterNumber",number,"content",text,"version",0,"goal","保存的章目标"),201).path("id").asLong();}
    String path(){return "/api/author/novels/"+novel+"/writing";}
    Map<String,Object> task(){return new LinkedHashMap<>(Map.of("chapterNumber",3,"mode","DRAFT","text","","goal","找到断剑的来历，不提前揭开结局","instructions","保持第三人称","characterIds",List.of()));}
    Map<String,Object> project(long version){return Map.of("outline","私有大纲内容","worldSetting","风帆只能借自然风","writingStyle","简洁对话","version",version);}
    Map<String,Object> character(String name,long version){return Map.of("name",name,"aliases","小铸剑师","storyRole","主角","profile","谨慎，寻找师父留下的线索","version",version);}
    @Test void privateWorkspaceCreatesOnlyOnSaveAndUsesVersions()throws Exception{
        assertThat(call("GET",path(),author,null,200).path("project").path("version").asLong()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM writing_project",Long.class)).isZero();
        call("PUT",path(),author,project(0),200);call("PUT",path(),author,project(0),409);
        assertThat(call("GET",path(),author,null,200).path("project").path("outline").asText()).isEqualTo("私有大纲内容");
        call("GET",path(),other,null,404);call("GET",path(),reader,null,403);call("GET",path(),admin,null,403);call("GET",path(),null,null,401);
        assertThat(call("GET","/api/public/novels",null,null,200).toString()).doesNotContain("私有大纲内容");
    }
    @Test void characterCrudAndCrossNovelBoundary()throws Exception{
        var id=call("POST",path()+"/characters",author,character("林砚",0),201).path("id").asLong();
        call("POST",path()+"/characters",author,character("林砚",0),409);call("PUT",path()+"/characters/"+id,other,character("林砚",0),404);
        call("PUT",path()+"/characters/"+id,author,character("林砚",0),200);call("DELETE",path()+"/characters/"+id+"?version=0",author,null,409);
        call("DELETE",path()+"/characters/"+id+"?version=1",author,null,204);
        assertThat(call("GET",path(),author,null,200).path("characters").size()).isZero();
    }
    @Test void chapterGoalSurvivesSaveAndRenumberWithoutPublicLeak()throws Exception{
        var c=call("GET","/api/author/chapters/"+first,author,null,200);assertThat(c.path("goal").asText()).isEqualTo("保存的章目标");
        call("PUT","/api/author/chapters/"+first,author,Map.of("title","改序号","content","保留正文","chapterNumber",6,"version",0,"goal","更新章目标"),200);
        assertThat(call("GET","/api/author/chapters/"+first,author,null,200).path("goal").asText()).isEqualTo("更新章目标");
        call("PUT","/api/author/chapters/"+first,author,Map.of("title","旧版本","content","不得覆盖","chapterNumber",6,"version",0,"goal","冲突"),409);
        assertThat(jdbc.queryForObject("SELECT goal FROM writing_chapter_note WHERE chapter_id=?",String.class,first)).isEqualTo("更新章目标");
        assertThat(call("GET","/api/admin/chapters/"+first,admin,null,200).has("goal")).isFalse();
    }
    @Test void emptyDraftPreviewIncludesPreviousInOrderAndExplicitClipping()throws Exception{
        call("PUT",path(),author,project(0),200);var charId=call("POST",path()+"/characters",author,character("林砚",0),201).path("id").asLong();
        var input=task();input.put("characterIds",List.of(charId));var preview=call("POST",path()+"/context",author,input,200);
        var sources=preview.path("payload").path("sources");assertThat(sources.toString()).contains("私有大纲内容","谨慎").doesNotContain("未来事件不应引用","另一本作品");
        var selected=new ArrayList<JsonNode>();sources.forEach(s->{if(s.path("kind").asText().equals("CHAPTER"))selected.add(s);});
        assertThat(selected).hasSize(2);assertThat(selected.get(0).path("id").asLong()).isEqualTo(first);assertThat(selected.get(1).path("id").asLong()).isEqualTo(second);
        assertThat(selected.get(0).path("text").asText()).hasSize(2000);assertThat(selected.get(0).path("omittedCharacters").asInt()).isPositive();verifyNoInteractions(gateway);
    }
    @Test void refusesFutureForeignDuplicateAndMissingGoalBeforeCallingProvider()throws Exception{
        var input=task();input.put("previousChapterIds",List.of(future));call("POST",path()+"/context",author,input,400);
        input.put("previousChapterIds",List.of(alien));call("POST",path()+"/context",author,input,404);
        input.put("previousChapterIds",List.of(first,first));call("POST",path()+"/context",author,input,400);
        input.put("previousChapterIds",List.of());input.put("goal","");call("POST",path()+"/context",author,input,400);verifyNoInteractions(gateway);
    }
    @Test void generateRequiresPreviewAndDoesNotCreateOrSaveChapter()throws Exception{
        var input=task();call("POST",path()+"/generate",author,input,409);verifyNoInteractions(gateway);
        var preview=call("POST",path()+"/context",author,input,200);input.put("contextDigest",preview.path("digest").asText());
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM chapter",Long.class);call("POST",path()+"/generate",author,input,200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chapter",Long.class)).isEqualTo(count);
        verify(gateway).generateContext(eq("DRAFT"),contains("前章结尾"));verify(gateway,never()).generate(anyString(),anyString(),anyString());
    }
    @Test void changedSourceBeforeGenerationIsRejectedWithoutProviderCost()throws Exception{
        var input=task();var preview=call("POST",path()+"/context",author,input,200);input.put("contextDigest",preview.path("digest").asText());
        jdbc.update("UPDATE chapter SET version=version+1 WHERE id=?",first);call("POST",path()+"/generate",author,input,409);verifyNoInteractions(gateway);
    }
    @Test void changedCharacterDuringProviderCallDiscardsCandidate()throws Exception{
        var id=call("POST",path()+"/characters",author,character("林砚",0),201).path("id").asLong();var input=task();input.put("characterIds",List.of(id));
        var preview=call("POST",path()+"/context",author,input,200);input.put("contextDigest",preview.path("digest").asText());
        when(gateway.generateContext(anyString(),anyString())).thenAnswer(i->{jdbc.update("UPDATE writing_character SET version=version+1 WHERE id=?",id);return new AiGateway.Result("过期候选",false,"test-model");});
        call("POST",path()+"/generate",author,input,409);
    }
    @Test void providerFailureLeavesCurrentDraftUnchanged()throws Exception{
        var input=task();input.put("chapterId",second);input.put("version",0);input.put("chapterNumber",2);input.put("mode","CONTINUE");input.put("text","尚未保存的段落");
        var preview=call("POST",path()+"/context",author,input,200);input.put("contextDigest",preview.path("digest").asText());
        when(gateway.generateContext(anyString(),anyString())).thenThrow(new BusinessException(HttpStatus.GATEWAY_TIMEOUT,"AI_TIMEOUT","隔离超时"));
        call("POST",path()+"/generate",author,input,504);
        assertThat(jdbc.queryForObject("SELECT content FROM chapter WHERE id=?",String.class,second)).isEqualTo("林砚在城门前等待，断剑仍无声。");
    }
    @Test void pendingChapterAndOtherAuthorsCannotUseWorkspaceGeneration()throws Exception{
        var input=task();input.put("chapterId",second);input.put("version",0);
        jdbc.update("UPDATE chapter SET review_status='PENDING' WHERE id=?",second);call("POST",path()+"/context",author,input,409);
        call("POST",path()+"/context",other,task(),404);call("POST",path()+"/generate",reader,task(),403);verifyNoInteractions(gateway);
    }
    @Test void excessiveContextIsRefusedInsteadOfSilentlyClippingSettings()throws Exception{
        call("PUT",path(),author,Map.of("outline","\"".repeat(4000),"worldSetting","\"".repeat(3000),"writingStyle","\"".repeat(1000),"version",0),200);
        var input=task();input.put("mode","CONTINUE");input.put("text","\"".repeat(6000));input.put("goal","\"".repeat(2000));input.put("instructions","\"".repeat(500));
        call("POST",path()+"/context",author,input,400);verifyNoInteractions(gateway);
    }
}
