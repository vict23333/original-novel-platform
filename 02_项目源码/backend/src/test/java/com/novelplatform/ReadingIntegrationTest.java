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
@Sql(statements={"DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ReadingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired TokenService tokens;
    @Autowired ReviewRecordMapper reviews;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    String author,other,reader,admin;
    @BeforeEach void identities() {
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

    String shelf(JsonNode n) { return "/api/me/bookshelf/"+id(n); }
    @Test void shelfSearchUsesPublicTitleAndKeepsUsersIsolated() throws Exception {
        var n=publishBook(book());call("PUT",shelf(n),reader,null,200);
        call("PUT","/api/author/novels/"+id(n),author,novelInput("未审核的私有书名",v(n)),200);
        assertThat(call("GET","/api/me/bookshelf?keyword=测试",reader,null,200).get("total").asInt()).isEqualTo(1);
        assertThat(call("GET","/api/me/bookshelf?keyword=私有",reader,null,200).get("total").asInt()).isZero();
        assertThat(call("GET","/api/me/bookshelf?keyword=_",reader,null,200).get("total").asInt()).isZero();
        assertThat(call("GET","/api/me/bookshelf?keyword=测试",other,null,200).get("total").asInt()).isZero();
        call("POST","/api/admin/novels/"+id(n)+"/takedown",admin,Map.of("version",v(n)+1,"reason","测试下架"),200);
        var hidden=call("GET","/api/me/bookshelf?keyword=测试",reader,null,200).get("items").get(0);
        assertThat(hidden.get("available").asBoolean()).isFalse();
        assertThat(hidden.get("reading").get("resumeChapterId").isNull()).isTrue();
    }
    @Test void shelfReadFiltersAndSortingRunBeforePagination() throws Exception {
        var a=publishBook(book());var ca=threeParagraphs(a);
        var b=publishBook(book());var cb=threeParagraphs(b);
        var c=publishBook(book());
        for(var n:java.util.List.of(a,b,c)) call("PUT",shelf(n),reader,null,200);
        call("PUT",progress(a),reader,position(ca,0,0),200);
        call("PUT",progress(b),reader,position(cb,1,0),200);
        jdbc.update("UPDATE reading_progress SET updated_at=? WHERE novel_id=?",java.sql.Timestamp.valueOf("2026-01-01 00:00:00"),id(a));
        jdbc.update("UPDATE reading_progress SET updated_at=? WHERE novel_id=?",java.sql.Timestamp.valueOf("2026-01-02 00:00:00"),id(b));
        var first=call("GET","/api/me/bookshelf?sort=RECENT_READ&size=1",reader,null,200);
        assertThat(first.get("total").asInt()).isEqualTo(3);assertThat(first.get("items").get(0).get("novelId").asLong()).isEqualTo(id(b));
        assertThat(call("GET","/api/me/bookshelf?sort=RECENT_READ&size=1&page=3",reader,null,200).get("items").get(0).get("novelId").asLong()).isEqualTo(id(c));
        assertThat(call("GET","/api/me/bookshelf?status=READ",reader,null,200).get("total").asInt()).isEqualTo(2);
        assertThat(call("GET","/api/me/bookshelf?status=UNREAD",reader,null,200).get("items").get(0).get("novelId").asLong()).isEqualTo(id(c));
        call("DELETE",shelf(a),reader,null,204);call("PUT",shelf(a),reader,null,200);
        assertThat(call("GET","/api/me/bookshelf?status=READ",reader,null,200).get("total").asInt()).isEqualTo(2);
    }
    @Test void shelfFiltersRejectInvalidInputAndGuests() throws Exception {
        for(String q:java.util.List.of("status=FINISHED","sort=SQL","keyword="+"x".repeat(101),"size=51","page=0"))
            call("GET","/api/me/bookshelf?"+q,reader,null,400);
        call("GET","/api/me/bookshelf?status=READ&sort=RECENT_READ",null,null,401);
    }
    String progress(JsonNode n) { return "/api/me/novels/"+id(n)+"/reading"; }
    Map<String,Object> position(JsonNode c,int index,long version) throws Exception {
        var publicChapter=call("GET","/api/public/chapters/"+id(c),null,null,200);
        return Map.of("chapterId",id(c),"paragraphIndex",index,"contentRevision",publicChapter.get("contentRevision").asText(),"version",version);
    }
    JsonNode threeParagraphs(JsonNode n) throws Exception {
        return publishChapter(call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("第一段\n\n第二段\r\n\r\n第三段",1,0),201));
    }
    @Test void guestsCannotAccessAnyPrivateReadingEndpoint() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);
        call("GET","/api/me/bookshelf",null,null,401);
        call("PUT",shelf(n),null,null,401);call("DELETE",shelf(n),null,null,401);
        call("GET",progress(n),null,null,401);call("PUT",progress(n),null,position(c,0,0),401);
    }
    @Test void shelfAddAndRemoveAreIdempotentAndIsolated() throws Exception {
        var n=publishBook(book());
        call("PUT",shelf(n),reader,null,200);call("PUT",shelf(n),reader,null,200);
        assertThat(call("GET","/api/me/bookshelf",reader,null,200).get("total").asInt()).isEqualTo(1);
        assertThat(call("GET","/api/me/bookshelf",other,null,200).get("total").asInt()).isZero();
        call("DELETE",shelf(n),other,null,204);
        assertThat(call("GET",progress(n),reader,null,200).get("shelved").asBoolean()).isTrue();
        call("DELETE",shelf(n),reader,null,204);call("DELETE",shelf(n),reader,null,204);
        assertThat(call("GET","/api/me/bookshelf",reader,null,200).get("total").asInt()).isZero();
    }
    @Test void authorAndAdminCanKeepTheirOwnShelf() throws Exception {
        var n=publishBook(book());call("PUT",shelf(n),author,null,200);call("PUT",shelf(n),admin,null,200);
        assertThat(call("GET","/api/me/bookshelf",author,null,200).get("total").asInt()).isEqualTo(1);
        assertThat(call("GET","/api/me/bookshelf",admin,null,200).get("total").asInt()).isEqualTo(1);
    }
    @Test void progressBelongsToAuthenticatedAccountAndSurvivesShelfRemoval() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);
        var p=call("PUT",progress(n),reader,position(c,2,0),200);
        assertThat(p.get("version").asLong()).isEqualTo(1);
        assertThat(p.get("resumeParagraphIndex").asInt()).isEqualTo(2);
        assertThat(p.get("shelved").asBoolean()).isFalse();
        assertThat(call("GET",progress(n),other,null,200).get("version").asLong()).isZero();
        call("PUT",shelf(n),reader,null,200);call("DELETE",shelf(n),reader,null,204);
        assertThat(call("GET",progress(n),reader,null,200).get("resumeParagraphIndex").asInt()).isEqualTo(2);
        var spoof=new java.util.HashMap<>(position(c,1,1));spoof.put("userId",999);
        call("PUT",progress(n),reader,spoof,400);
    }
    @Test void staleProgressCannotOverwriteAndIntentionalBackwardsReadingIsAllowed() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);
        call("PUT",progress(n),reader,position(c,2,0),200);
        call("PUT",progress(n),reader,position(c,0,0),409);
        assertThat(call("GET",progress(n),reader,null,200).get("resumeParagraphIndex").asInt()).isEqualTo(2);
        assertThat(call("PUT",progress(n),reader,position(c,0,1),200).get("resumeParagraphIndex").asInt()).isZero();
    }
    @Test void paragraphBoundsAndRequestShapeAreChecked() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);
        call("PUT",progress(n),reader,position(c,-1,0),400);call("PUT",progress(n),reader,position(c,3,0),400);
        call("PUT",progress(n),reader,Map.of("chapterId",id(c),"paragraphIndex",0,"version",0),400);
        call("PUT",progress(n),reader,position(c,2,0),200);
    }
    @Test void draftAndWrongNovelCannotBeSavedOrCollected() throws Exception {
        var draft=book();call("PUT",shelf(draft),reader,null,404);call("GET",progress(draft),reader,null,404);
        var n=publishBook(book());var c=threeParagraphs(n);var otherBook=publishBook(book());
        call("PUT",progress(otherBook),reader,position(c,0,0),404);
        var hidden=chapter(otherBook);
        call("PUT",progress(otherBook),reader,Map.of("chapterId",id(hidden),"paragraphIndex",0,"contentRevision","0".repeat(64),"version",0),404);
    }
    @Test void takenDownNovelStaysRemovableWithoutReadLink() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);var body=position(c,1,0);
        call("PUT",shelf(n),reader,null,200);call("PUT",progress(n),reader,body,200);
        call("POST","/api/admin/novels/"+id(n)+"/takedown",admin,Map.of("version",v(n),"reason","测试下架"),200);
        var item=call("GET","/api/me/bookshelf",reader,null,200).get("items").get(0);
        assertThat(item.get("available").asBoolean()).isFalse();assertThat(item.get("reading").get("resumeChapterId").isNull()).isTrue();
        call("PUT",progress(n),reader,body,404);call("PUT",shelf(n),other,null,404);
        call("GET",progress(n),other,null,404);call("DELETE",shelf(n),reader,null,204);
    }
    @Test void removedChapterFallsBackToFirstReadableAndRetainsStoredHistory() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);
        var second=publishChapter(call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("下一章",2,0),201));
        call("PUT",progress(n),reader,position(c,2,0),200);
        call("DELETE","/api/author/chapters/"+id(c)+"?version="+v(c),author,null,204);
        var state=call("GET",progress(n),reader,null,200);
        assertThat(state.get("chapterId").asLong()).isEqualTo(id(c));
        assertThat(state.get("resumeChapterId").asLong()).isEqualTo(id(second));
        assertThat(state.get("resumeParagraphIndex").asInt()).isZero();
    }
    @Test void hiddenLastChapterHasNoResumeTarget() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);
        call("PUT",progress(n),reader,position(c,1,0),200);
        call("POST","/api/admin/chapters/"+id(c)+"/takedown",admin,Map.of("version",v(c),"reason","测试下架"),200);
        assertThat(call("GET",progress(n),reader,null,200).get("resumeChapterId").isNull()).isTrue();
    }
    @Test void bodyRevisionResetsResumeAndRejectsOldClientWrites() throws Exception {
        var n=publishBook(book());var c=threeParagraphs(n);var old=position(c,2,0);
        call("PUT",progress(n),reader,old,200);
        c=call("PUT","/api/author/chapters/"+id(c),author,chapterInput("修订后只有一段",1,v(c)),200);
        assertThat(call("GET",progress(n),reader,null,200).get("resumeParagraphIndex").asInt()).isEqualTo(2);
        c=publishChapter(c);
        var state=call("GET",progress(n),reader,null,200);
        assertThat(state.get("resumeParagraphIndex").asInt()).isZero();
        assertThat(state.get("message").asText()).contains("修订");
        call("PUT",progress(n),reader,old,409);
        call("PUT",progress(n),reader,position(c,0,1),200);
    }
    @Test void bookshelfPaginationAndEmptyBookAreHandled() throws Exception {
        var first=publishBook(book());var second=publishBook(book());
        call("PUT",shelf(first),reader,null,200);call("PUT",shelf(second),reader,null,200);
        var page=call("GET","/api/me/bookshelf?page=2&size=1",reader,null,200);
        assertThat(page.get("total").asInt()).isEqualTo(2);assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("reading").get("resumeChapterId").isNull()).isTrue();
        call("GET","/api/me/bookshelf?page=0",reader,null,400);call("GET","/api/me/bookshelf?size=51",reader,null,400);
    }
}
