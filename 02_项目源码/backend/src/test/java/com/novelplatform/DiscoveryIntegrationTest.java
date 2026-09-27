package com.novelplatform;

import com.fasterxml.jackson.databind.*;
import com.novelplatform.auth.TokenService;
import com.novelplatform.content.*;
import com.novelplatform.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements={"DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class DiscoveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired TokenService tokens;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    String author,other,reader,admin;
    @BeforeEach void identities() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-20T16:00:00Z"));
        org.mockito.Mockito.when(clock.withZone(org.mockito.ArgumentMatchers.any())).thenAnswer(a -> java.time.Clock.fixed(clock.instant(),a.getArgument(0)));
        author=identity("author_one","AUTHOR");other=identity("author_two","AUTHOR");
        reader=identity("reader_one","READER");admin=identity("admin_one","ADMIN");
    }
    String identity(String name,String role) {
        AppUser u=new AppUser();u.setUsername(name);u.setNickname("测试"+role);u.setPasswordHash("test-fixture-unused-hash");
        u.setBio("");u.setRole(role);u.setEnabled(true);u.setTokenVersion(0L);users.insert(u);return tokens.issue(u).accessToken();
    }
    JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        MockHttpServletRequestBuilder request=switch(method) {case "POST" -> post(path);case "PUT" -> put(path);case "DELETE" -> delete(path);default -> get(path);};
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var result=mvc.perform(request).andReturn().getResponse();
        assertThat(result.getStatus()).as(method+" "+path+" "+result.getContentAsString()).isEqualTo(expected);
        return result.getContentAsByteArray().length==0?json.nullNode():json.readTree(result.getContentAsByteArray());
    }
    long id(JsonNode n) { return n.get("id").asLong(); }
    long v(JsonNode n) { return n.get("version").asLong(); }
    Map<String,Object> novelInput(String title,long version) { return Map.of("title",title,"introduction","原创故事简介","categoryId",1,"serialStatus","SERIALIZING","version",version); }
    Map<String,Object> chapterInput(String text,int number,long version) { return Map.of("title","章节标题","content",text,"chapterNumber",number,"version",version); }
    JsonNode book() throws Exception { return call("POST","/api/author/novels",author,novelInput("测试小说",0),201); }
    JsonNode chapter(JsonNode n) throws Exception { return call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("初版正文",1,0),201); }
    JsonNode submit(String type,JsonNode n) throws Exception { return call("POST","/api/author/"+type+"/"+id(n)+"/submit",author,Map.of("version",v(n)),200); }
    JsonNode approve(String type,JsonNode n) throws Exception { return call("POST","/api/admin/"+type+"/"+id(n)+"/review",admin,Map.of("version",v(n),"action","APPROVE","reason",""),200); }
    JsonNode publishBook(JsonNode n) throws Exception { return approve("novels",submit("novels",n)); }
    JsonNode publishChapter(JsonNode n) throws Exception { return approve("chapters",submit("chapters",n)); }



    JsonNode detail(JsonNode n) throws Exception { return call("GET","/api/public/novels/"+id(n)+"/discovery",null,null,200).get("book"); }
    JsonNode event(JsonNode c,String token,String anon,int expected) throws Exception {
        return call("POST","/api/public/chapters/"+id(c)+"/read-event",token,anon==null?Map.of():Map.of("anonymousId",anon),expected);
    }
    @Test void dayDedupIncludesAllChaptersAndSurvivesRepeatedRequests() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        var c2=publishChapter(call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("第二章",2,0),201));
        assertThat(event(c,reader,null,200).get("counted").asBoolean()).isTrue();
        assertThat(event(c2,reader,null,200).get("counted").asBoolean()).isFalse();
        assertThat(event(c,reader,null,200).get("counted").asBoolean()).isFalse();
        assertThat(detail(n).get("totalReads").asInt()).isEqualTo(1);
        assertThat(event(c,null,"11111111-1111-4111-8111-111111111111",200).get("counted").asBoolean()).isTrue();
        event(c,null,"11111111-1111-4111-8111-111111111111",200);
        assertThat(detail(n).get("totalReads").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_read_day WHERE visitor_key LIKE '%11111111%'",Integer.class)).isZero();
    }
    @Test void invalidGuestAndFakeIdentityAreRejected() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        event(c,null,null,400);event(c,null,"bad",400);
        call("POST","/api/public/chapters/"+id(c)+"/read-event",reader,Map.of("userId",999),400);
        event(c,"broken-token",null,401);
        assertThat(detail(n).get("totalReads").asInt()).isZero();
    }
    @Test void privateAndTakenDownContentCannotGenerateEventsOrRecommendations() throws Exception {
        var n=book();var c=chapter(n);event(c,reader,null,404);
        n=publishBook(n);c=publishChapter(c);event(c,reader,null,200);
        call("POST","/api/admin/chapters/"+id(c)+"/takedown",admin,Map.of("version",v(c),"reason","测试下架"),200);
        event(c,reader,null,404);
        assertThat(call("GET","/api/public/rankings",null,null,200).get("total").asInt()).isZero();
        call("POST","/api/admin/novels/"+id(n)+"/takedown",admin,Map.of("version",v(n),"reason","测试下架"),200);
        call("GET","/api/public/novels/"+id(n)+"/discovery",null,null,404);
    }
    @Test void asiaDateBoundaryAndSevenDayTrendAreAccurate() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-20T15:59:59Z"));
        assertThat(event(c,reader,null,200).get("readDate").asText()).isEqualTo("2026-09-20");
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-20T16:00:00Z"));
        assertThat(event(c,reader,null,200).get("readDate").asText()).isEqualTo("2026-09-21");
        var stats=call("GET","/api/author/novels/"+id(n)+"/stats",author,null,200);
        assertThat(stats.get("trend").size()).isEqualTo(7);
        assertThat(stats.at("/trend/0/date").asText()).isEqualTo("2026-09-15");
        assertThat(stats.at("/trend/5/reads").asInt()).isEqualTo(1);
        assertThat(stats.at("/trend/6/reads").asInt()).isEqualTo(1);
        jdbc.update("UPDATE novel_read_day SET read_date='2026-09-14' WHERE read_date='2026-09-20'");
        var b=detail(n);assertThat(b.get("weekReads").asInt()).isEqualTo(1);assertThat(b.get("totalReads").asInt()).isEqualTo(2);
    }
    @Test void authorStatsAreScopedAndReadOnlyRequestsDoNotCount() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        call("GET","/api/public/chapters/"+id(c),null,null,200);detail(n);
        assertThat(detail(n).get("totalReads").asInt()).isZero();
        call("GET","/api/author/novels/"+id(n)+"/stats",other,null,404);
        call("GET","/api/author/stats",reader,null,403);call("GET","/api/author/stats",admin,null,403);call("GET","/api/author/stats",null,null,401);
        assertThat(call("GET","/api/author/stats",other,null,200).at("/totals/works").asInt()).isZero();
    }
    @Test void popularityCollectionAndStablePagingUseRealData() throws Exception {
        var a=publishBook(book());var ac=publishChapter(chapter(a));
        var b=publishBook(book());publishChapter(chapter(b));event(ac,reader,null,200);
        assertThat(call("GET","/api/public/rankings?kind=POPULAR&size=1",null,null,200).at("/items/0/id").asLong()).isEqualTo(id(a));
        call("PUT","/api/me/bookshelf/"+id(b),reader,null,200);
        assertThat(call("GET","/api/public/rankings?kind=COLLECTED&size=1",null,null,200).at("/items/0/id").asLong()).isEqualTo(id(b));
        call("DELETE","/api/me/bookshelf/"+id(b),reader,null,204);
        assertThat(detail(b).get("collections").asInt()).isZero();
        var page2=call("GET","/api/public/rankings?kind=POPULAR&size=1&page=2",null,null,200);
        assertThat(page2.at("/items/0/id").asLong()).isEqualTo(id(b));assertThat(page2.get("total").asInt()).isEqualTo(2);
    }
    @Test void draftEditsDoNotChangePublicWordsOrUpdateTime() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));var before=detail(n);
        call("PUT","/api/author/chapters/"+id(c),author,chapterInput("待审文本不应该计入公开字数",1,v(c)),200);
        var after=detail(n);assertThat(after.get("wordCount")).isEqualTo(before.get("wordCount"));
        assertThat(after.get("lastUpdatedAt")).isEqualTo(before.get("lastUpdatedAt"));
        assertThat(after.get("latestChapterTitle").asText()).isEqualTo("章节标题");
    }
    @Test void recommendationAndCombinedFiltersUsePublicSnapshots() throws Exception {
        var a=publishBook(book());publishChapter(chapter(a));
        var b=publishBook(call("POST","/api/author/novels",author,Map.of("title","推荐完结作品","introduction","简介","categoryId",1,"serialStatus","COMPLETED","version",0),201));publishChapter(chapter(b));
        publishBook(book()); // 无可读章节的作品不会参与推荐。
        var d=call("GET","/api/public/novels/"+id(a)+"/discovery",null,null,200);
        assertThat(d.get("similar").size()).isEqualTo(1);assertThat(d.at("/similar/0/id").asLong()).isEqualTo(id(b));
        var list=call("GET","/api/public/novels?keyword=推荐&categoryId=1&serialStatus=COMPLETED&sort=POPULAR",null,null,200);
        assertThat(list.get("total").asInt()).isEqualTo(1);
        assertThat(call("GET","/api/public/novels?keyword=%25",null,null,200).get("total").asInt()).isZero();
        call("GET","/api/public/rankings?kind=INVALID",null,null,400);call("GET","/api/public/novels?sort=INVALID",null,null,400);
        call("GET","/api/public/novels?serialStatus=PENDING",null,null,400);call("GET","/api/public/rankings?size=51",null,null,400);
    }
    @Test void deletedCommentsAreExcludedFromTotals() throws Exception {
        var n=publishBook(book());publishChapter(chapter(n));
        var c=call("POST","/api/me/comments",reader,Map.of("novelId",id(n),"body","读者反馈"),201);
        assertThat(call("GET","/api/author/stats",author,null,200).at("/totals/comments").asInt()).isEqualTo(1);
        call("DELETE","/api/me/comments/"+id(c),reader,null,204);
        assertThat(call("GET","/api/author/stats",author,null,200).at("/totals/comments").asInt()).isZero();
    }
    @Test void concurrentEventsCreateOneDailyRecord() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var go=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> task=() -> { go.await();return event(c,reader,null,200).get("counted").asBoolean(); };
            var a=pool.submit(task);var b=pool.submit(task);go.countDown();
            assertThat(java.util.List.of(a.get(),b.get())).containsExactlyInAnyOrder(true,false);
        }
        assertThat(detail(n).get("totalReads").asInt()).isEqualTo(1);
    }
}
