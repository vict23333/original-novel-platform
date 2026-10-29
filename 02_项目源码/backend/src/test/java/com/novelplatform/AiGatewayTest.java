package com.novelplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelplatform.ai.*;
import com.novelplatform.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class AiGatewayTest {
    HttpServer server;
    ExecutorService executor;
    volatile int status=200;
    volatile String body="{\"choices\":[{\"message\":{\"content\":\"模拟响应正文\"},\"finish_reason\":\"stop\"}]}";
    volatile String requestBody, authorization;
    volatile long delay;
    AtomicInteger requests=new AtomicInteger();
    ObjectMapper json=new ObjectMapper();
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(executor);
        server.createContext("/chat/completions",exchange -> {
            requests.incrementAndGet();authorization=exchange.getRequestHeaders().getFirst("Authorization");
            requestBody=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            try { if(delay>0)Thread.sleep(delay); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] data=body.getBytes(StandardCharsets.UTF_8);
            try { exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("Location","/redirect-target");exchange.sendResponseHeaders(status,data.length);exchange.getResponseBody().write(data); }
            finally { exchange.close(); }
        });server.start();
    }
    @AfterEach void stop() { server.stop(0);executor.shutdownNow(); }
    AiSettings settings(int timeout) { return new AiSettings(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions","test-only-provider-key","test-model",timeout,20,true); }
    AiGateway.Result generate(int timeout) {
        var gateway=new AiGateway(settings(timeout),json);
        try { return gateway.generate("CONTINUE","只在测试中使用的素材","保持视角"); }
        finally { gateway.close(); }
    }
    @Test void sendsCompatibleRequestAndParsesContent() throws Exception {
        assertThat(generate(5).content()).isEqualTo("模拟响应正文");
        var request=json.readTree(requestBody);
        assertThat(request.get("model").asText()).isEqualTo("test-model");
        assertThat(request.get("stream").asBoolean()).isFalse();
        assertThat(request.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(authorization).isEqualTo("Bearer test-only-provider-key");
        assertThat(requestBody).doesNotContain("test-only-provider-key");
    }
    @Test void lengthFinishIsExplicitlyMarked() { body=body.replace("stop","length");assertThat(generate(5).truncated()).isTrue(); }
    @Test void contextualDraftUsesStructuredMaterialsAndTaskInstructions() throws Exception {
        var gateway=new AiGateway(settings(5),json);
        try {gateway.generateContext("DRAFT","{\"task\":\"DRAFT\",\"goal\":\"寻找入口\",\"sources\":[{\"text\":\"前章结尾\"}]}");}
        finally {gateway.close();}
        var request=json.readTree(requestBody);
        assertThat(request.at("/messages/0/content").asText()).contains("当前章为空","不把总纲的后期事件提前");
        var data=json.readTree(request.at("/messages/1/content").asText());
        assertThat(data.at("/context/goal").asText()).isEqualTo("寻找入口");
        assertThat(data.at("/context/sources/0/text").asText()).isEqualTo("前章结尾");
        assertThat(requestBody).doesNotContain("test-only-provider-key");
    }
    @Test void providerErrorsDoNotLeakBodyAndRedirectsAreNotFollowed() {
        status=302;body="private-provider-error-test-only-provider-key";
        assertThatThrownBy(() -> generate(5)).isInstanceOfSatisfying(BusinessException.class,e -> {
            assertThat(e.code).isEqualTo("AI_UNAVAILABLE");assertThat(e.getMessage()).doesNotContain(body,"test-only-provider-key");
        });assertThat(requests.get()).isEqualTo(1);
    }
    @Test void rateLimitGetsActionableError() { status=429;assertThatThrownBy(() -> generate(5)).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.code).isEqualTo("AI_PROVIDER_BUSY")); }
    @Test void deepseekNonThinkingOptionIsRestrictedToOfficialHost() {
        var official=new java.util.HashMap<String,Object>();AiGateway.applyProviderOptions(official,"https://api.deepseek.com/chat/completions");
        assertThat(official.get("thinking")).isEqualTo(java.util.Map.of("type","disabled"));
        var other=new java.util.HashMap<String,Object>();AiGateway.applyProviderOptions(other,"https://api.deepseek.com.example.invalid/chat/completions");
        assertThat(other).isEmpty();
    }
    @Test void authAndBalanceFailuresAreActionableWithoutLeakingProviderBody() {
        body="secret-provider-payload";
        for(int code:new int[]{401,403,402}) {
            status=code;
            assertThatThrownBy(() -> generate(5)).isInstanceOfSatisfying(BusinessException.class,e -> {
                assertThat(e.code).isEqualTo(code==402?"AI_PROVIDER_BALANCE":"AI_PROVIDER_AUTH");
                assertThat(e.getMessage()).doesNotContain(body);
            });
        }
    }
    @Test void malformedEmptyAndOversizedResponsesAreRejected() {
        for(String invalid:new String[]{"not-json","{\"choices\":[]}","x".repeat(131073)}) {
            body=invalid;assertThatThrownBy(() -> generate(5)).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.code).isEqualTo("AI_UNAVAILABLE"));
        }
    }
    @Test void responseTimeoutIsBounded() { delay=2000;assertThatThrownBy(() -> generate(1)).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.code).isEqualTo("AI_TIMEOUT")); }
    @Test void insecureOrCredentialBearingEndpointsAreDisabled() {
        for(String url:new String[]{"http://example.invalid/chat/completions","https://user:password@example.invalid/chat/completions","https://example.invalid/chat?key=value"}) {
            assertThat(new AiSettings(true,url,"test-only-provider-key","test-model",5,20,false).ready()).isFalse();
        }
        assertThat(new AiSettings(true,settings(5).apiUrl(),"test-only-provider-key","test-model",5,20,false).ready()).isFalse();
    }
}
