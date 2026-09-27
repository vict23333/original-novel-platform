package com.novelplatform.admin;

import com.novelplatform.common.BusinessException;
import com.novelplatform.content.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static org.springframework.http.HttpStatus.*;
import static com.novelplatform.admin.AdminModels.*;

@Service
public class AdminService {
    private final AdminMapper mapper;
    private final NovelMapper novels;
    public AdminService(AdminMapper mapper, NovelMapper novels) { this.mapper=mapper; this.novels=novels; }
    private BusinessException conflict(String message) { return new BusinessException(CONFLICT,"ADMIN_CONFLICT",message); }
    private void version(long actual,long expected) { if(actual!=expected) throw conflict("数据已变化，请重新加载后再操作"); }
    private <T> T exists(T value) { if(value==null) throw new BusinessException(NOT_FOUND,"NOT_FOUND","记录不存在"); return value; }
    private String table(String kind) {
        return switch(kind) { case "categories" -> "novel_category"; case "tags" -> "novel_tag";
            default -> throw new BusinessException(BAD_REQUEST,"INVALID_KIND","未知分类类型"); };
    }
    public ContentViews.Page<UserRow> users(String keyword,String role,Boolean enabled,int page,int size) {
        if(keyword.length()>50 || !List.of("","READER","AUTHOR","ADMIN").contains(role) || page<1 || page>100000 || size<1 || size>50)
            throw new BusinessException(BAD_REQUEST,"INVALID_FILTER","筛选条件或分页不正确");
        return new ContentViews.Page<>(mapper.users(keyword.strip(),role,enabled,size,(long)(page-1)*size),page,size,mapper.userCount(keyword.strip(),role,enabled));
    }
    public ContentViews.Page<BookRow> books(String keyword,int page) {
        if(keyword.length()>50 || page<1 || page>100000) throw new BusinessException(BAD_REQUEST,"INVALID_FILTER","查询条件不正确");
        return new ContentViews.Page<>(mapper.books(keyword.strip(),12,(long)(page-1)*12),page,12,mapper.bookCount(keyword.strip()));
    }
    private UserRow editableUser(long actor,long id,long expected) {
        var user=exists(mapper.lockUser(id)); version(user.version(),expected);
        if(user.id()==actor || "ADMIN".equals(user.role())) throw conflict("管理员账号仅可查看，请在个人中心维护自己的资料");
        return user;
    }
    @Transactional public UserRow profile(long actor,long id,Profile input) {
        editableUser(actor,id,input.version()); mapper.profile(id,input.nickname().strip(),input.bio().strip(),now());
        audit(actor,"USER",id,"PROFILE",input.reason().strip()); return mapper.lockUser(id);
    }
    @Transactional public UserRow status(long actor,long id,Status input) {
        var user=editableUser(actor,id,input.version());
        if(user.enabled()==input.enabled()) return user;
        mapper.status(id,input.enabled(),now()); audit(actor,"USER",id,input.enabled()?"ENABLE":"DISABLE",input.reason().strip());
        return mapper.lockUser(id);
    }
    public List<Term> terms(String kind) { table(kind); return kind.equals("categories")?mapper.categories():mapper.tags(); }
    @Transactional public Term create(long actor,String kind,TermInput input) {
        String target=table(kind); if(input.version()!=0) throw conflict("新记录版本应为 0");
        mapper.createTerm(target,input.name().strip()); var term=mapper.byName(target,input.name().strip());
        audit(actor,kind,term.id(),"CREATE",term.name()); return term;
    }
    @Transactional public Term rename(long actor,String kind,long id,TermInput input) {
        String target=table(kind); var term=exists(mapper.lockTerm(target,id)); version(term.version(),input.version());
        mapper.renameTerm(target,id,input.name().strip()); audit(actor,kind,id,"RENAME",term.name()+" → "+input.name().strip());
        return mapper.lockTerm(target,id);
    }
    @Transactional public void delete(long actor,String kind,long id,long expected) {
        String target=table(kind); var term=exists(mapper.lockTerm(target,id)); version(term.version(),expected);
        long used=kind.equals("categories")?mapper.categoryUse(id):mapper.tagUse(id);
        if(used>0) throw conflict("仍有作品使用此项，请先调整作品归类后再删除");
        mapper.deleteTerm(target,id); audit(actor,kind,id,"DELETE",term.name());
    }
    private Assignment assignment(Novel n) {
        return new Assignment(n.getId(),n.getTitle(),n.getReviewStatus(),n.getVersion(),n.getCategoryId(),n.getPublicCategoryId(),mapper.tagIds(n.getId()));
    }
    public Assignment assignment(long id) { return assignment(exists(novels.selectById(id))); }
    @Transactional public Assignment classify(long actor,long id,Classify input) {
        var n=exists(novels.lockById(id)); version(n.getVersion(),input.version());
        if("PENDING".equals(n.getReviewStatus())) throw conflict("作品正在审核，请先完成审核再调整归类");
        exists(mapper.lockTerm("novel_category",input.categoryId()));
        var ids=new TreeSet<>(input.tagIds());
        if(ids.size()!=input.tagIds().size()) throw new BusinessException(BAD_REQUEST,"DUPLICATE_TAG","不能重复选择标签");
        // 先锁作品，再按编号锁标签，防止并发删除与关联操作交错。
        for(long tag:ids) exists(mapper.lockTerm("novel_tag",tag));
        long previous=n.getCategoryId(); var previousTags=mapper.tagIds(id);
        n.setCategoryId(input.categoryId()); if(n.getPublished()) n.setPublicCategoryId(input.categoryId());
        n.setVersion(n.getVersion()+1); n.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC)); novels.updateById(n);
        mapper.clearTags(id); for(long tag:ids) mapper.link(id,tag);
        audit(actor,"NOVEL",id,"CLASSIFY",input.reason().strip()+" | 分类 "+previous+"→"+input.categoryId()+" 标签 "+previousTags+"→"+ids);
        return assignment(n);
    }
    public List<Term> publicTags(long id) {
        var n=exists(novels.selectById(id));
        if(!n.getPublished() || n.getTakenDown()) throw new BusinessException(NOT_FOUND,"NOT_FOUND","作品不可访问");
        return mapper.bookTags(id);
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private void audit(long actor,String type,long id,String action,String reason) { mapper.audit(actor,type,id,action,reason,now()); }
    public List<Audit> audits() { return mapper.audits(); }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ContentViews.Page<Audit> searchAudits(String keyword,String type,String action,Long targetId,int page,int size) {
        if(keyword.length()>100 || !List.of("","USER","NOVEL","categories","tags").contains(type)
                || !List.of("","PROFILE","ENABLE","DISABLE","CREATE","RENAME","DELETE","CLASSIFY").contains(action)
                || (targetId!=null && targetId<1) || page<1 || page>100000 || size<1 || size>50)
            throw new BusinessException(BAD_REQUEST,"INVALID_FILTER","记录筛选条件或分页不正确");
        String search=keyword.strip();
        return new ContentViews.Page<>(mapper.searchAudits(search,type,action,targetId,size,(long)(page-1)*size),
                page,size,mapper.auditCount(search,type,action,targetId));
    }
}
