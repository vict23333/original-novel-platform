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
@Sql(statements={"DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CommentIntegrationTest {
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


    String list(JsonNode n) { return "/api/public/novels/"+id(n)+"/comments"; }
    String replies(JsonNode c) { return "/api/public/comments/"+id(c)+"/replies"; }
    String like(JsonNode c) { return "/api/me/comments/"+id(c)+"/like"; }
    Map<String,Object> input(JsonNode n,JsonNode c,JsonNode reply,String body) {
        var m=new java.util.HashMap<String,Object>();m.put("novelId",id(n));m.put("body",body);
        if(c!=null)m.put("chapterId",id(c));if(reply!=null)m.put("replyToId",id(reply));return m;
    }
    JsonNode comment(JsonNode n,JsonNode c,JsonNode reply,String token) throws Exception {
        return call("POST","/api/me/comments",token,input(n,c,reply,"原创测试评论"),201);
    }
    @Test void guestsReadButCannotWriteOrLike() throws Exception {
        var n=publishBook(book());var c=comment(n,null,null,reader);
        var page=call("GET",list(n),null,null,200);
        assertThat(page.get("total").asInt()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("mine").asBoolean()).isFalse();
        var now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        assertThat(java.time.LocalDateTime.parse(c.get("createdAt").asText())).isBetween(now.minusMinutes(1),now.plusMinutes(1));
        call("POST","/api/me/comments",null,input(n,null,null,"评论"),401);
        call("PUT",like(c),null,null,401);call("DELETE","/api/me/comments/"+id(c),null,null,401);
    }
    @Test void novelAndChapterThreadsAreSeparate() throws Exception {
        var n=publishBook(book());var ch=publishChapter(chapter(n));
        var a=comment(n,null,null,reader);var b=comment(n,ch,null,author);
        assertThat(call("GET",list(n),null,null,200).get("items").get(0).get("id").asLong()).isEqualTo(id(a));
        assertThat(call("GET",list(n)+"?chapterId="+id(ch),null,null,200).get("items").get(0).get("id").asLong()).isEqualTo(id(b));
    }
    @Test void nestedRepliesFlattenUnderRootAndRetainTarget() throws Exception {
        var n=publishBook(book());var root=comment(n,null,null,reader);
        var reply=comment(n,null,root,author);var nested=comment(n,null,reply,other);
        assertThat(nested.get("rootId").asLong()).isEqualTo(id(root));
        assertThat(nested.get("replyToId").asLong()).isEqualTo(id(reply));
        assertThat(call("GET",replies(root),reader,null,200).get("total").asInt()).isEqualTo(2);
        call("GET",replies(reply),reader,null,404);
    }
    @Test void crossBookAndCrossChapterRepliesAreRejected() throws Exception {
        var n=publishBook(book());var ch=publishChapter(chapter(n));var root=comment(n,null,null,reader);
        call("POST","/api/me/comments",reader,input(n,ch,root,"跨评论区"),400);
        var second=publishBook(book());call("POST","/api/me/comments",reader,input(second,null,root,"跨作品"),400);
        call("POST","/api/me/comments",reader,input(second,ch,null,"跨作品章节"),404);
    }
    @Test void draftsAndTakenDownContentCannotLeakComments() throws Exception {
        var draft=book();call("GET",list(draft),null,null,404);
        call("POST","/api/me/comments",reader,input(draft,null,null,"不公开"),404);
        var n=publishBook(book());var ch=chapter(n);
        call("GET",list(n)+"?chapterId="+id(ch),null,null,404);
        var c=comment(n,null,null,reader);
        call("POST","/api/admin/novels/"+id(n)+"/takedown",admin,Map.of("version",v(n),"reason","测试下架"),200);
        call("GET",list(n),null,null,404);call("GET",replies(c),null,null,404);call("PUT",like(c),reader,null,404);
        call("POST","/api/me/comments",reader,input(n,null,null,"下架后评论"),404);
    }
    @Test void removedChapterBlocksItsCommentsButNotNovelThread() throws Exception {
        var n=publishBook(book());var ch=publishChapter(chapter(n));var c=comment(n,ch,null,reader);
        call("DELETE","/api/author/chapters/"+id(ch)+"?version="+v(ch),author,null,204);
        call("GET",list(n)+"?chapterId="+id(ch),null,null,404);
        call("GET",replies(c),null,null,404);call("PUT",like(c),reader,null,404);
        call("GET",list(n),null,null,200);
    }
    @Test void likesAreIdempotentAndBelongToEachAccount() throws Exception {
        var n=publishBook(book());var c=comment(n,null,null,reader);
        call("PUT",like(c),reader,null,200);var liked=call("PUT",like(c),reader,null,200);
        assertThat(liked.get("likeCount").asInt()).isEqualTo(1);assertThat(liked.get("liked").asBoolean()).isTrue();
        assertThat(call("GET",list(n),other,null,200).get("items").get(0).get("liked").asBoolean()).isFalse();
        assertThat(call("PUT",like(c),author,null,200).get("likeCount").asInt()).isEqualTo(2);
        call("DELETE",like(c),reader,null,200);
        assertThat(call("DELETE",like(c),reader,null,200).get("likeCount").asInt()).isEqualTo(1);
    }
    @Test void simultaneousLikesBothSucceedAndCreateOneRecord() throws Exception {
        var n=publishBook(book());var c=comment(n,null,null,reader);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<JsonNode> task=() -> { start.await(); return call("PUT",like(c),reader,null,200); };
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();
            assertThat(first.get().get("likeCount").asInt()).isEqualTo(1);
            assertThat(second.get().get("likeCount").asInt()).isEqualTo(1);
        }
        assertThat(call("GET",list(n),reader,null,200).get("items").get(0).get("likeCount").asInt()).isEqualTo(1);
    }
    @Test void ownerDeleteHidesTextAndKeepsRepliesWithoutNewReplies() throws Exception {
        var n=publishBook(book());var root=comment(n,null,null,reader);var reply=comment(n,null,root,author);
        call("PUT",like(root),author,null,200);
        call("DELETE","/api/me/comments/"+id(root),other,null,403);
        call("DELETE","/api/me/comments/"+id(root),reader,null,204);
        call("DELETE","/api/me/comments/"+id(root),reader,null,204);
        var deleted=call("GET",list(n),reader,null,200).get("items").get(0);
        assertThat(deleted.get("body").asText()).isEmpty();assertThat(deleted.get("deleted").asBoolean()).isTrue();
        assertThat(deleted.get("likeCount").asInt()).isZero();
        assertThat(call("GET",replies(root),null,null,200).get("items").get(0).get("body").asText()).isEqualTo("原创测试评论");
        call("POST","/api/me/comments",author,input(n,null,reply,"根评论已删"),404);
        call("PUT",like(root),reader,null,404);
    }
    @Test void replyDeletionDoesNotExposeDeletedTextOrBreakOtherReplies() throws Exception {
        var n=publishBook(book());var root=comment(n,null,null,reader);var a=comment(n,null,root,author);comment(n,null,a,other);
        call("DELETE","/api/me/comments/"+id(a),author,null,204);
        var rows=call("GET",replies(root),null,null,200).get("items");
        assertThat(rows.get(0).get("body").asText()).isEmpty();
        assertThat(rows.get(1).get("replyToName").asText()).isEqualTo("评论已删除");
    }
    @Test void moderationRequiresAdminAndReasonAndLeavesAuditMetadata() throws Exception {
        var n=publishBook(book());var c=comment(n,null,null,reader);String endpoint="/api/admin/comments/"+id(c);
        call("GET","/api/admin/comments",reader,null,403);call("DELETE",endpoint,author,Map.of("reason","测试"),403);
        call("DELETE",endpoint,admin,Map.of("reason","  "),400);
        call("DELETE",endpoint,admin,Map.of("reason","测试违规处理"),204);
        var row=call("GET","/api/admin/comments?deleted=true&novelId="+id(n),admin,null,200).get("items").get(0);
        assertThat(row.get("body").asText()).isEmpty();assertThat(row.get("deleteReason").asText()).isEqualTo("测试违规处理");
        assertThat(row.get("deletedAt").isNull()).isFalse();
        assertThat(call("GET","/api/admin/comments?deleted=false",admin,null,200).get("total").asInt()).isZero();
    }
    @Test void validationRejectsEmptyLongSpoofedAndMalformedRequests() throws Exception {
        var n=publishBook(book());
        for(String body: new String[]{"", "   ", "　", "字".repeat(501)}) call("POST","/api/me/comments",reader,input(n,null,null,body),400);
        var spoof=input(n,null,null,"文字");spoof.put("userId",999);call("POST","/api/me/comments",reader,spoof,400);
        call("POST","/api/me/comments",reader,input(n,null,null,"字".repeat(500)),201);
        call("GET",list(n)+"?page=0",null,null,400);call("GET",list(n)+"?size=51",null,null,400);
    }
    @Test void paginationSearchAndTextRoundTripWork() throws Exception {
        var n=publishBook(book());var first=comment(n,null,null,reader);comment(n,null,null,author);
        var third=call("POST","/api/me/comments",reader,input(n,null,null,"<script>alert(1)</script>"),201);
        var page=call("GET",list(n)+"?page=2&size=2",null,null,200);
        assertThat(page.get("total").asInt()).isEqualTo(3);assertThat(page.get("items").get(0).get("id").asLong()).isEqualTo(id(first));
        assertThat(third.get("body").asText()).isEqualTo("<script>alert(1)</script>");
        assertThat(call("GET","/api/admin/comments?keyword=alert",admin,null,200).get("total").asInt()).isEqualTo(1);
    }
}
