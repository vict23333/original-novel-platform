package com.novelplatform.discovery;

import com.novelplatform.common.BusinessException;
import com.novelplatform.content.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.novelplatform.discovery.DiscoveryViews.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class DiscoveryService {
    private final DiscoveryMapper data;
    private final NovelMapper novels;
    private final ChapterMapper chapters;
    private final Clock clock;
    private final byte[] secret;
    public DiscoveryService(DiscoveryMapper data,NovelMapper novels,ChapterMapper chapters,Clock clock,
                            @Value("${app.jwt-secret}") String secret) {
        this.data=data;this.novels=novels;this.chapters=chapters;this.clock=clock;
        this.secret=secret.getBytes(StandardCharsets.UTF_8);
    }
    private BusinessException missing() { return new BusinessException(NOT_FOUND,"NOT_FOUND","作品不存在或当前不可访问"); }
    private BusinessException bad(String message) { return new BusinessException(BAD_REQUEST,"INVALID_FILTER",message); }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))); }
    private String order(String sort) {
        return switch(sort) {
            case "POPULAR" -> "week_reads DESC,collections DESC,n.id DESC";
            case "COLLECTED" -> "collections DESC,week_reads DESC,n.id DESC";
            case "NEWEST" -> "first_published_at DESC,n.id DESC";
            case "UPDATED" -> "last_updated_at DESC,n.id DESC";
            default -> throw bad("请选择有效榜单或排序方式");
        };
    }
    @Transactional(readOnly=true) public ContentViews.Page<Book> search(String keyword,Long categoryId,String serial,String sort,int page,int size,boolean readable) {
        if(keyword.length()>100 || page<1 || page>100000 || size<1 || size>50 || (categoryId!=null && categoryId<1)) throw bad("搜索条件或页码超出范围");
        if(serial!=null && serial.isBlank()) serial=null;
        if(serial!=null && !Set.of("SERIALIZING","COMPLETED").contains(serial)) throw bad("请选择连载或完结状态");
        String ordering=order(sort);
        String term=keyword.isBlank()?null:"%"+keyword.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        LocalDate day=today();
        return new ContentViews.Page<>(data.books(term,categoryId,serial,null,null,readable,ordering,day.minusDays(6),day,size,(long)(page-1)*size),page,size,
            data.count(term,categoryId,serial,null,null,readable));
    }
    @Transactional(readOnly=true) public Details details(long id) {
        LocalDate day=today();
        List<Book> selected=data.books(null,null,null,id,null,false,order("POPULAR"),day.minusDays(6),day,1,0);
        if(selected.isEmpty()) throw missing();
        Book book=selected.getFirst();
        return new Details(book,data.books(null,book.categoryId(),null,null,id,true,order("POPULAR"),day.minusDays(6),day,4,0));
    }
    @Transactional(readOnly=true) public Stats stats(long owner,Long novelId) {
        String title="全部作品";
        if(novelId!=null) {
            Novel novel=novels.selectById(novelId);
            if(novel==null || novel.getAuthorId()!=owner) throw missing();
            title=novel.getTitle();
        }
        LocalDate day=today(),from=day.minusDays(6);
        Map<LocalDate,Long> values=new HashMap<>();
        data.trend(owner,novelId,from,day).forEach(v -> values.put(v.date(),v.reads()));
        List<Day> trend=from.datesUntil(day.plusDays(1)).map(d -> new Day(d,values.getOrDefault(d,0L))).toList();
        return new Stats(title,data.totals(owner,novelId,from,day),trend);
    }
    // 与内容审核共用先小说后章节的加锁顺序，防止刚下架的章节继续被计数。
    @Transactional(isolation=Isolation.READ_COMMITTED) public ReadReceipt record(Long userId,long chapterId,String anonymousId) {
        if(userId==null && (anonymousId==null || !anonymousId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
            throw bad("游客阅读标识无效");
        Chapter found=chapters.selectById(chapterId);
        if(found==null) throw missing();
        Novel novel=novels.lockById(found.getNovelId());
        Chapter chapter=chapters.lockById(chapterId);
        if(novel==null || !novel.getPublished() || novel.getTakenDown() || chapter==null || chapter.getDeleted() || !chapter.getPublished() || chapter.getTakenDown()) throw missing();
        String key=identity(userId==null?"guest:"+anonymousId.toLowerCase(Locale.ROOT):"user:"+userId);
        Instant instant=clock.instant();LocalDate day=instant.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();boolean counted=data.recorded(novel.getId(),key,day)==0;
        if(counted) data.record(novel.getId(),userId,key,day,chapterId,LocalDateTime.ofInstant(instant,ZoneOffset.UTC));
        return new ReadReceipt(counted,day);
    }
    private String identity(String identity) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("novel-reading-v1:"+identity).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException e) { throw new IllegalStateException("Reading identity unavailable",e); }
    }
}
