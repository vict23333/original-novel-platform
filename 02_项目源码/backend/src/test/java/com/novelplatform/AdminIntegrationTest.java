package com.novelplatform;

import com.fasterxml.jackson.databind.*;
import com.novelplatform.auth.TokenService;
import com.novelplatform.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements={"DELETE FROM novel_tag_link","DELETE FROM novel_tag","DELETE FROM admin_audit","DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user","DELETE FROM novel_category WHERE id>6"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements={"DELETE FROM novel_tag_link","DELETE FROM novel_tag","DELETE FROM admin_audit","DELETE FROM novel_read_day","DELETE FROM comment_like","DELETE FROM reader_comment","DELETE FROM reading_progress","DELETE FROM bookshelf","DELETE FROM content_review","DELETE FROM chapter","DELETE FROM novel","DELETE FROM app_user","DELETE FROM novel_category WHERE id>6"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AdminIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired com.novelplatform.admin.AdminMapper auditMapper;
    String admin,reader,author; long adminId,readerId;
    @BeforeEach void identities() {
        var a=identity("manager_test","ADMIN");adminId=a.getId();admin=tokens.issue(a).accessToken();
        var r=identity("reader_test","READER");readerId=r.getId();reader=tokens.issue(r).accessToken();
        author=tokens.issue(identity("author_test","AUTHOR")).accessToken();
    }
    AppUser identity(String name,String role) {
        AppUser u=new AppUser();u.setUsername(name);u.setNickname("测试"+role);u.setPasswordHash(encoder.encode("FixturePass123!"));
        u.setBio("");u.setRole(role);u.setEnabled(true);u.setTokenVersion(0L);users.insert(u);return u;
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
    String userPath() { return "/api/admin/users/"+readerId; }
    Map<String,Object> profile(long version) { return Map.of("nickname","新的昵称","bio","新的简介","version",version,"reason","本地测试维护"); }
    Map<String,Object> status(boolean enabled,long version) { return Map.of("enabled",enabled,"version",version,"reason","状态测试"); }
    JsonNode term(String kind,String name) throws Exception { return call("POST","/api/admin/taxonomy/"+kind,admin,Map.of("name",name,"version",0),201); }
    JsonNode book(long category) throws Exception { return call("POST","/api/author/novels",author,Map.of("title","测试归类小说","introduction","原创测试介绍","categoryId",category,"serialStatus","SERIALIZING","version",0),201); }
    JsonNode publish(JsonNode n) throws Exception {
        n=call("POST","/api/author/novels/"+id(n)+"/submit",author,Map.of("version",v(n)),200);
        return call("POST","/api/admin/novels/"+id(n)+"/review",admin,Map.of("version",v(n),"action","APPROVE","reason","测试审核"),200);
    }
    String classifyPath(JsonNode n) { return "/api/admin/novels/"+id(n)+"/classification"; }
    @Test void auditSearchCanReachOlderRecordsWithoutDuplicates() throws Exception {
        for(int i=0;i<61;i++) auditMapper.audit(adminId,"USER",readerId,"PROFILE","历史记录"+i,java.time.LocalDateTime.of(2026,1,1,0,0));
        var first=call("GET","/api/admin/audits/search?size=50",admin,null,200);
        var second=call("GET","/api/admin/audits/search?size=50&page=2",admin,null,200);
        assertThat(first.get("total").asInt()).isEqualTo(61);
        assertThat(first.get("items").size()).isEqualTo(50);
        assertThat(second.get("items").size()).isEqualTo(11);
        Set<Long> ids=new HashSet<>();long previous=Long.MAX_VALUE;
        for(var data:List.of(first,second)) for(var row:data.get("items")) {
            assertThat(row.get("id").asLong()).isLessThan(previous);previous=row.get("id").asLong();ids.add(previous);
        }
        assertThat(ids).hasSize(61);
        assertThat(call("GET","/api/admin/audits/search?page=99",admin,null,200).get("items").size()).isZero();
        assertThat(call("GET","/api/admin/audits",admin,null,200).size()).isEqualTo(50);
    }
    @Test void auditSearchCombinesFiltersAndTreatsWildcardsLiterally() throws Exception {
        auditMapper.audit(adminId,"USER",readerId,"PROFILE","资料修正 100%_",java.time.LocalDateTime.now());
        auditMapper.audit(adminId,"tags",1,"CREATE","标签整理",java.time.LocalDateTime.now());
        var data=call("GET","/api/admin/audits/search?type=USER&action=PROFILE&targetId="+readerId+"&keyword=修正",admin,null,200);
        assertThat(data.get("total").asInt()).isEqualTo(1);
        assertThat(data.get("items").get(0).get("reason").asText()).isEqualTo("资料修正 100%_");
        assertThat(call("GET","/api/admin/audits/search?keyword=_",admin,null,200).get("total").asInt()).isEqualTo(1);
        assertThat(call("GET","/api/admin/audits/search?type=USER&action=CREATE",admin,null,200).get("total").asInt()).isZero();
        assertThat(data.toString()).doesNotContain("password","token","username");
    }
    @Test void auditSearchRejectsInvalidFiltersAndNonAdmins() throws Exception {
        String path="/api/admin/audits/search";
        call("GET",path,null,null,401);call("GET",path,reader,null,403);call("GET",path,author,null,403);
        for(String query:List.of("page=0","page=100001","size=0","size=51","type=ROOT","action=DROP","targetId=0","targetId=-1","targetId=abc","keyword="+"x".repeat(101)))
            call("GET",path+"?"+query,admin,null,400);
    }
    Map<String,Object> classify(long category,List<Long> ids,long version) { return Map.of("categoryId",category,"tagIds",ids,"version",version,"reason","测试归类"); }
    @Test void allManagementEndpointsRequireAdmin() throws Exception {
        for(String path:List.of("/api/admin/users","/api/admin/taxonomy/categories","/api/admin/audits","/api/admin/classification-books")) {
            call("GET",path,null,null,401);call("GET",path,reader,null,403);call("GET",path,author,null,403);
        }
        call("PUT",userPath()+"/profile",reader,profile(0),403);
        call("POST","/api/admin/taxonomy/tags",author,Map.of("name","测试","version",0),403);
    }
    @Test void usersArePaginatedFilteredAndNeverExposeCredentials() throws Exception {
        var data=call("GET","/api/admin/users?role=READER&enabled=true&keyword=reader&size=1",admin,null,200);
        assertThat(data.get("total").asInt()).isEqualTo(1);assertThat(data.get("items").get(0).get("id").asLong()).isEqualTo(readerId);
        assertThat(data.toString()).doesNotContain("password","tokenVersion","passwordHash");
        assertThat(call("GET","/api/admin/users?keyword=%25",admin,null,200).get("total").asInt()).isZero();
        call("GET","/api/admin/users?page=0",admin,null,400);call("GET","/api/admin/users?role=ROOT",admin,null,400);
    }
    @Test void disableInvalidatesTokenAndReenableNeverResurrectsIt() throws Exception {
        call("PUT",userPath()+"/status",admin,status(false,0),200);
        call("GET","/api/users/me",reader,null,401);
        call("POST","/api/auth/login",null,Map.of("username","reader_test","password","FixturePass123!"),401);
        call("PUT",userPath()+"/status",admin,status(true,1),200);
        call("GET","/api/users/me",reader,null,401);
        var login=call("POST","/api/auth/login",null,Map.of("username","reader_test","password","FixturePass123!"),200);
        call("GET","/api/users/me",login.get("accessToken").asText(),null,200);
        assertThat(call("GET","/api/admin/audits",admin,null,200).size()).isEqualTo(2);
    }
    @Test void adminAccountsAndRoleSpoofingAreProtected() throws Exception {
        call("PUT","/api/admin/users/"+adminId+"/status",admin,status(false,0),409);
        call("PUT","/api/admin/users/"+adminId+"/profile",admin,profile(0),409);
        var spoof=new HashMap<>(profile(0));spoof.put("role","ADMIN");
        call("PUT",userPath()+"/profile",admin,spoof,400);
        assertThat(users.selectById(readerId).getRole()).isEqualTo("READER");
    }
    @Test void profileUpdatesDetectStaleVersionAndSelfProfileChanges() throws Exception {
        call("PUT",userPath()+"/profile",admin,profile(0),200);
        call("PUT",userPath()+"/profile",admin,profile(0),409);
        call("PUT","/api/users/me",reader,Map.of("nickname","本人修改","bio","保留本人资料"),200);
        call("PUT",userPath()+"/profile",admin,profile(1),409);
        assertThat(users.selectById(readerId).getNickname()).isEqualTo("本人修改");
    }
    @Test void concurrentUserEditsHaveExactlyOneWinner() throws Exception {
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> action=() -> { start.await();return mvc.perform(put(userPath()+"/profile").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile(0)))).andReturn().getResponse().getStatus(); };
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);
        }
    }
    @Test void taxonomyValidatesDuplicatesAndStaleChanges() throws Exception {
        var t=term("tags","成长");
        call("POST","/api/admin/taxonomy/tags",admin,Map.of("name","成长","version",0),409);
        call("PUT","/api/admin/taxonomy/tags/"+id(t),admin,Map.of("name","冒险","version",0),200);
        call("DELETE","/api/admin/taxonomy/tags/"+id(t)+"?version=0",admin,null,409);
        call("DELETE","/api/admin/taxonomy/tags/"+id(t)+"?version=1",admin,null,204);
        call("GET","/api/admin/taxonomy/unknown",admin,null,400);
        call("POST","/api/admin/taxonomy/categories",admin,Map.of("name"," ","version",0),400);
    }
    @Test void categoryDeletionChecksDraftAndPublishedReferences() throws Exception {
        var c=term("categories","新题材");var n=publish(book(id(c)));
        call("PUT","/api/author/novels/"+id(n),author,Map.of("title","新草稿","introduction","不公开草稿","categoryId",1,"serialStatus","SERIALIZING","version",v(n)),200);
        call("DELETE","/api/admin/taxonomy/categories/"+id(c)+"?version=0",admin,null,409);
        call("DELETE","/api/admin/taxonomy/categories/1?version=0",admin,null,409);
        var unused=term("categories","未使用分类");call("DELETE","/api/admin/taxonomy/categories/"+id(unused)+"?version=0",admin,null,204);
    }
    @Test void classificationUpdatesPublicMetadataWithoutPublishingDraftText() throws Exception {
        var n=publish(book(1)); var t=term("tags","冒险");var c=term("categories","新的题材");
        var draft=call("PUT","/api/author/novels/"+id(n),author,Map.of("title","秘密草稿","introduction","未审核内容","categoryId",1,"serialStatus","SERIALIZING","version",v(n)),200);
        call("PUT",classifyPath(n),admin,classify(id(c),List.of(id(t)),v(draft)),200);
        var pub=call("GET","/api/public/novels/"+id(n),null,null,200);
        assertThat(pub.get("title").asText()).isEqualTo("测试归类小说");assertThat(pub.get("categoryId").asLong()).isEqualTo(id(c));
        assertThat(call("GET","/api/public/novels/"+id(n)+"/tags",null,null,200).get(0).get("name").asText()).isEqualTo("冒险");
        call("DELETE","/api/admin/taxonomy/tags/"+id(t)+"?version=0",admin,null,409);
        call("PUT",classifyPath(n),admin,classify(1,List.of(),v(draft)),409);
        var current=call("GET",classifyPath(n),admin,null,200);
        call("PUT",classifyPath(n),admin,classify(1,List.of(),v(current)),200);
        call("DELETE","/api/admin/taxonomy/tags/"+id(t)+"?version=0",admin,null,204);
    }
    @Test void pendingNovelsAndInvalidTagSetsAreRejectedAtomically() throws Exception {
        var n=book(1);var t=term("tags","测试标签");
        call("PUT",classifyPath(n),admin,classify(1,List.of(id(t),id(t)),v(n)),400);
        call("PUT",classifyPath(n),admin,classify(1,List.of(id(t),999999L),v(n)),404);
        assertThat(call("GET",classifyPath(n),admin,null,200).get("tagIds").size()).isZero();
        call("POST","/api/author/novels/"+id(n)+"/submit",author,Map.of("version",v(n)),200);
        call("PUT",classifyPath(n),admin,classify(1,List.of(id(t)),v(n)+1),409);
    }
    @Test void hiddenNovelsNeverExposeTags() throws Exception {
        var n=book(1);call("GET","/api/public/novels/"+id(n)+"/tags",null,null,404);
        n=publish(n);call("POST","/api/admin/novels/"+id(n)+"/takedown",admin,Map.of("version",v(n),"reason","测试下架"),200);
        call("GET","/api/public/novels/"+id(n)+"/tags",null,null,404);
    }
    @Test void blankReasonsLongNamesAndTagLimitsAreRejected() throws Exception {
        var input=new HashMap<>(profile(0));input.put("reason"," ");call("PUT",userPath()+"/profile",admin,input,400);
        call("POST","/api/admin/taxonomy/tags",admin,Map.of("name","字".repeat(21),"version",0),400);
        var n=book(1);call("PUT",classifyPath(n),admin,classify(1,List.of(1L,2L,3L,4L,5L,6L),0),400);
        call("GET","/api/admin/classification-books?keyword=归类",admin,null,200);
    }
}
