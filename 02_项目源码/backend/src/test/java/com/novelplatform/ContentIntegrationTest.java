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
@Sql(statements={"DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ContentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired TokenService tokens;
    @Autowired ReviewRecordMapper reviews;
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

    @Test void publicationRequiresBothApprovals() throws Exception {
        JsonNode n=book(),c=chapter(n);c=publishChapter(c);
        call("GET","/api/public/novels/"+id(n),null,null,404);
        call("GET","/api/public/chapters/"+id(c),null,null,404);
        n=publishBook(n);
        var publicBook=call("GET","/api/public/novels/"+id(n),null,null,200);
        assertThat(publicBook.has("version")).isFalse();assertThat(publicBook.has("reviewReason")).isFalse();
        assertThat(call("GET","/api/public/chapters/"+id(c),null,null,200).get("content").asText()).isEqualTo("初版正文");
        assertThat(call("GET","/api/public/novels/"+id(n)+"/chapters",null,null,200).get("total").asInt()).isEqualTo(1);
    }
    @Test void novelApprovalDoesNotApproveChapter() throws Exception {
        var n=publishBook(book());var c=submit("chapters",chapter(n));
        call("GET","/api/public/chapters/"+id(c),null,null,404);
        assertThat(call("GET","/api/public/novels/"+id(n)+"/chapters",null,null,200).get("total").asInt()).isZero();
    }
    @Test void rolesAreEnforcedByServer() throws Exception {
        call("POST","/api/author/novels",null,novelInput("测试",0),401);
        call("POST","/api/author/novels",reader,novelInput("测试",0),403);
        call("GET","/api/admin/novels",author,null,403);
        call("GET","/api/admin/chapters",reader,null,403);
        call("GET","/api/public/categories",null,null,200);
    }
    @Test void otherAuthorCannotReadMutateOrSubmitOwnedResources() throws Exception {
        var n=book();var c=chapter(n);
        String np="/api/author/novels/"+id(n),cp="/api/author/chapters/"+id(c);
        call("GET",np,other,null,404);call("PUT",np,other,novelInput("窃取",v(n)),404);
        call("POST",np+"/submit",other,Map.of("version",v(n)),404);
        call("GET",np+"/reviews",other,null,404);call("GET",np+"/chapters",other,null,404);
        call("GET",cp,other,null,404);call("PUT",cp,other,chapterInput("窃取",1,v(c)),404);
        call("POST",cp+"/submit",other,Map.of("version",v(c)),404);call("DELETE",cp+"?version="+v(c),other,null,404);
        assertThat(call("GET","/api/author/novels",other,null,200).get("total").asInt()).isZero();
    }
    @Test void staleSaveCannotOverwriteNewerDraft() throws Exception {
        var n=book();var c=chapter(n);
        call("PUT","/api/author/chapters/"+id(c),author,chapterInput("新版",1,v(c)),200);
        call("PUT","/api/author/chapters/"+id(c),author,chapterInput("旧版覆盖",1,v(c)),409);
        assertThat(call("GET","/api/author/chapters/"+id(c),author,null,200).get("content").asText()).isEqualTo("新版");
        call("PUT","/api/author/novels/"+id(n),author,novelInput("新书名",v(n)),200);
        call("POST","/api/author/novels/"+id(n)+"/submit",author,Map.of("version",v(n)),409);
    }
    @Test void pendingContentCannotBeEditedDeletedOrResubmitted() throws Exception {
        var n=submit("novels",book());var c=submit("chapters",chapter(n));
        call("PUT","/api/author/novels/"+id(n),author,novelInput("改名",v(n)),409);
        call("PUT","/api/author/chapters/"+id(c),author,chapterInput("篡改",1,v(c)),409);
        call("DELETE","/api/author/chapters/"+id(c)+"?version="+v(c),author,null,409);
        call("POST","/api/author/chapters/"+id(c)+"/submit",author,Map.of("version",v(c)),409);
    }
    @Test void emptyDraftIsSavedButNotSubmitted() throws Exception {
        var n=book();var c=call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("  \n",1,0),201);
        call("POST","/api/author/chapters/"+id(c)+"/submit",author,Map.of("version",v(c)),400);
    }
    @Test void revisionDoesNotLeakUntilApprovalIncludingChapterOrder() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        c=call("PUT","/api/author/chapters/"+id(c),author,chapterInput("秘密修订",2,v(c)),200);
        c=submit("chapters",c);
        var old=call("GET","/api/public/chapters/"+id(c),null,null,200);
        assertThat(old.get("content").asText()).isEqualTo("初版正文");assertThat(old.get("chapterNumber").asInt()).isEqualTo(1);
        c=approve("chapters",c);
        var current=call("GET","/api/public/chapters/"+id(c),null,null,200);
        assertThat(current.get("content").asText()).isEqualTo("秘密修订");assertThat(current.get("chapterNumber").asInt()).isEqualTo(2);
    }
    @Test void novelRevisionKeepsPublicFieldsAndSearchConsistent() throws Exception {
        var n=publishBook(book());
        n=call("PUT","/api/author/novels/"+id(n),author,Map.of("title","修订书名","introduction","新简介","categoryId",2,"serialStatus","COMPLETED","version",v(n)),200);
        assertThat(call("GET","/api/public/novels?keyword=修订",null,null,200).get("total").asInt()).isZero();
        var old=call("GET","/api/public/novels/"+id(n),null,null,200);
        assertThat(old.get("title").asText()).isEqualTo("测试小说");assertThat(old.get("serialStatus").asText()).isEqualTo("SERIALIZING");
        n=publishBook(n);
        assertThat(call("GET","/api/public/novels?keyword=修订&categoryId=2",null,null,200).get("total").asInt()).isEqualTo(1);
    }
    @Test void rejectionNeedsReasonAndSupportsResubmission() throws Exception {
        var n=submit("novels",book());
        var path="/api/admin/novels/"+id(n)+"/review";
        call("POST",path,admin,Map.of("version",v(n),"action","REJECT","reason","  "),400);
        n=call("POST",path,admin,Map.of("version",v(n),"action","REJECT","reason","请完善简介"),200);
        assertThat(n.get("reviewReason").asText()).isEqualTo("请完善简介");
        n=call("PUT","/api/author/novels/"+id(n),author,novelInput("修订后作品",v(n)),200);
        publishBook(n);
        var history=call("GET","/api/author/novels/"+id(n)+"/reviews",author,null,200);
        assertThat(history.size()).isEqualTo(2);assertThat(history.get(1).get("reason").asText()).isEqualTo("请完善简介");
    }
    @Test void duplicateReviewHasNoSecondAuditRecord() throws Exception {
        var n=submit("novels",book());approve("novels",n);
        call("POST","/api/admin/novels/"+id(n)+"/review",admin,Map.of("version",v(n),"action","APPROVE","reason",""),409);
        assertThat(reviews.selectCount(null)).isEqualTo(1);
    }
    @Test void takedownNovelBlocksEveryPublicEntryAndCanBeReapproved() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        call("POST","/api/admin/novels/"+id(n)+"/takedown",admin,Map.of("version",v(n),"reason",""),400);
        n=call("POST","/api/admin/novels/"+id(n)+"/takedown",admin,Map.of("version",v(n),"reason","待整改"),200);
        call("GET","/api/public/novels/"+id(n),null,null,404);call("GET","/api/public/novels/"+id(n)+"/chapters",null,null,404);
        call("GET","/api/public/chapters/"+id(c),null,null,404);
        assertThat(call("GET","/api/public/novels",null,null,200).get("total").asInt()).isZero();
        n=submit("novels",n);call("GET","/api/public/chapters/"+id(c),null,null,404);
        approve("novels",n);call("GET","/api/public/chapters/"+id(c),null,null,200);
    }
    @Test void chapterTakedownDoesNotHideNovel() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        call("POST","/api/admin/chapters/"+id(c)+"/takedown",admin,Map.of("version",v(c),"reason","章节待整改"),200);
        call("GET","/api/public/novels/"+id(n),null,null,200);call("GET","/api/public/chapters/"+id(c),null,null,404);
        assertThat(call("GET","/api/public/novels/"+id(n)+"/chapters",null,null,200).get("total").asInt()).isZero();
    }
    @Test void deletedChapterCannotBeReadAndNumberCanBeReused() throws Exception {
        var n=publishBook(book());var c=publishChapter(chapter(n));
        call("DELETE","/api/author/chapters/"+id(c)+"?version="+v(c),author,null,204);
        call("GET","/api/public/chapters/"+id(c),null,null,404);call("GET","/api/admin/chapters/"+id(c),admin,null,404);
        var newChapter=chapter(n);assertThat(id(newChapter)).isNotEqualTo(id(c));
        assertThat(reviews.selectCount(null)).isEqualTo(2);
    }
    @Test void duplicateNumbersAndInvalidInputAreRejected() throws Exception {
        var n=book();chapter(n);
        call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("重复",1,0),409);
        call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("字".repeat(20001),2,0),400);
        call("POST","/api/author/novels",author,Map.of("title","书名","introduction","简介","categoryId",999999,"serialStatus","SERIALIZING","version",0),400);
        call("GET","/api/public/novels?page=0",null,null,400);call("GET","/api/public/novels?page=abc",null,null,400);
        call("GET","/api/admin/novels?state=INVALID",admin,null,400);
    }
    @Test void publicPaginationFiltersUnpublishedContent() throws Exception {
        book();publishBook(book());publishBook(book());
        var first=call("GET","/api/public/novels?size=1",null,null,200);
        var second=call("GET","/api/public/novels?size=1&page=2",null,null,200);
        assertThat(first.get("total").asInt()).isEqualTo(2);assertThat(first.get("items").get(0).get("id")).isNotEqualTo(second.get("items").get(0).get("id"));
    }
    @Test void conflictingPublicOrderRollsBackApproval() throws Exception {
        var n=publishBook(book());var one=publishChapter(chapter(n));
        one=call("PUT","/api/author/chapters/"+id(one),author,chapterInput("新顺序",2,v(one)),200);
        var two=submit("chapters",chapter(n));
        call("POST","/api/admin/chapters/"+id(two)+"/review",admin,Map.of("version",v(two),"action","APPROVE","reason",""),409);
        assertThat(reviews.selectCount(null)).isEqualTo(2);
        publishChapter(one);approve("chapters",two);
    }

    @Test void chapterNavigationSkipsDraftsAndTakenDownChapters() throws Exception {
        var n=publishBook(book());var first=publishChapter(chapter(n));
        call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("未公开",2,0),201);
        var last=call("POST","/api/author/novels/"+id(n)+"/chapters",author,chapterInput("公开末章",3,0),201);
        last=publishChapter(last);
        var view=call("GET","/api/public/chapters/"+id(first),null,null,200);
        assertThat(view.get("nextChapterId").asLong()).isEqualTo(id(last));assertThat(view.get("previousChapterId").isNull()).isTrue();
        call("POST","/api/admin/chapters/"+id(last)+"/takedown",admin,Map.of("version",v(last),"reason","隐藏末章"),200);
        assertThat(call("GET","/api/public/chapters/"+id(first),null,null,200).get("nextChapterId").isNull()).isTrue();
    }
    @Test void searchSupportsAuthorAndTreatsWildcardAsLiteral() throws Exception {
        publishBook(book());
        assertThat(call("GET","/api/public/novels?keyword=AUTHOR",null,null,200).get("total").asInt()).isEqualTo(1);
        assertThat(call("GET","/api/public/novels?keyword=_",null,null,200).get("total").asInt()).isZero();
    }
}
