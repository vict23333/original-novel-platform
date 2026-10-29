package com.novelplatform.writing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novelplatform.content.*;
import com.novelplatform.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class WritingContext {
    private final ContentService content;
    private final WritingService writing;
    private final ChapterMapper chapters;
    private final ObjectMapper json;
    public WritingContext(ContentService content,WritingService writing,ChapterMapper chapters,ObjectMapper json) {
        this.content=content;this.writing=writing;this.chapters=chapters;this.json=json;
    }
    public record Previous(long id,int chapterNumber,String title,long version) {}
    public record Source(String kind,String label,Long id,long version,String text,int omittedCharacters) {}
    public record Payload(String task,int chapterNumber,String goal,String authorInstructions,String currentText,List<Source> sources) {}
    public record Preview(String digest,int inputCharacters,Payload payload) {}
    public List<Previous> previous(long owner,long novel,int before) {
        content.authorNovel(owner,novel);
        if(before<1 || before>100000)throw bad("章节序号超出范围");
        return chapters.selectList(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,novel).eq(Chapter::getDeleted,false)
                .lt(Chapter::getChapterNumber,before).select(Chapter::getId,Chapter::getChapterNumber,Chapter::getTitle,Chapter::getVersion)
                .orderByDesc(Chapter::getChapterNumber).last("LIMIT 50")).stream()
                .map(c->new Previous(c.getId(),c.getChapterNumber(),c.getTitle(),c.getVersion())).toList();
    }
    @Transactional(readOnly=true) public Preview build(long owner,long novel,WritingInputs.Task task) {
        var book=content.authorNovel(owner,novel);
        if(task.chapterId()!=null) {
            var chapter=content.authorChapter(owner,task.chapterId());
            if(chapter.getNovelId()!=novel)throw missing();
            if(task.version()==null || !task.version().equals(chapter.getVersion()) || "PENDING".equals(chapter.getReviewStatus()))
                throw changed();
        } else if(task.version()!=null && task.version()!=0)throw changed();
        if("DRAFT".equals(task.mode())) {
            if(task.goal().isBlank())throw bad("请先填写本章目标，再按目标起草");
            if(!task.text().isBlank())throw bad("已有正文请使用承接续写，避免重复起草");
        } else if(task.text().isBlank())throw bad("请先填写正文或选中需要处理的文字");
        unique(task.characterIds());if(task.previousChapterIds()!=null)unique(task.previousChapterIds());
        var workspace=writing.workspace(owner,novel);var project=workspace.project();
        List<Source> sources=new ArrayList<>();
        sources.add(new Source("NOVEL","作品简介",novel,book.getVersion(),book.getTitle()+"\n"+book.getIntroduction(),0));
        sources.add(new Source("OUTLINE","全书大纲",novel,project.getVersion(),project.getOutline(),0));
        sources.add(new Source("WORLD","世界与规则",novel,project.getVersion(),project.getWorldSetting(),0));
        sources.add(new Source("STYLE","文风与视角",novel,project.getVersion(),project.getWritingStyle(),0));
        for(long id:task.characterIds()) {
            var character=writing.character(novel,id);
            sources.add(new Source("CHARACTER",character.getName(),id,character.getVersion(),
                    "姓名："+character.getName()+"\n别名："+character.getAliases()+"\n身份："+character.getStoryRole()+"\n人设："+character.getProfile(),0));
        }
        List<Chapter> preceding;
        if(task.previousChapterIds()==null) {
            preceding=chapters.selectList(Wrappers.<Chapter>lambdaQuery().eq(Chapter::getNovelId,novel).eq(Chapter::getDeleted,false)
                    .lt(Chapter::getChapterNumber,task.chapterNumber()).ne(task.chapterId()!=null,Chapter::getId,task.chapterId())
                    .orderByDesc(Chapter::getChapterNumber).last("LIMIT 2"));
        } else {
            preceding=new ArrayList<>();
            for(long id:task.previousChapterIds()) {
                var chapter=content.authorChapter(owner,id);
                if(chapter.getNovelId()!=novel || Objects.equals(chapter.getId(),task.chapterId()))throw missing();
                if(chapter.getChapterNumber()>=task.chapterNumber())throw bad("只能引用当前章节之前的章节");
                preceding.add(chapter);
            }
        }
        preceding.sort(Comparator.comparing(Chapter::getChapterNumber));
        for(var chapter:preceding) {
            String text=chapter.getContent();int start=Math.max(0,text.length()-2000);
            if(start>0 && Character.isLowSurrogate(text.charAt(start)))start++;
            sources.add(new Source("CHAPTER","第 "+chapter.getChapterNumber()+" 章 · "+chapter.getTitle(),chapter.getId(),chapter.getVersion(),text.substring(start),start));
        }
        // 当前保存版本也参与指纹；生成期间被其他页面保存时不能采用旧结果。
        if(task.chapterId()!=null)sources.add(new Source("CURRENT","当前章节保存版本",task.chapterId(),task.version(),"",0));
        var payload=new Payload(task.mode(),task.chapterNumber(),task.goal(),task.instructions(),task.text(),List.copyOf(sources));
        try {
            String encoded=json.writeValueAsString(payload);
            if(encoded.length()>32000)throw bad("引用资料超过 32000 字符，请减少人物或缩短本次素材后重新预览");
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)));
            return new Preview(digest,encoded.length(),payload);
        }catch(BusinessException e){throw e;}
        catch(Exception e){throw new IllegalStateException("Cannot prepare writing context",e);}
    }
    public String encode(Preview preview) {
        try{return json.writeValueAsString(preview.payload());}catch(Exception e){throw new IllegalStateException(e);}
    }
    private void unique(List<Long> ids){if(new HashSet<>(ids).size()!=ids.size())throw bad("引用条目不能重复");}
    private BusinessException bad(String message){return new BusinessException(BAD_REQUEST,"INVALID_CONTEXT",message);}
    private BusinessException missing(){return new BusinessException(NOT_FOUND,"NOT_FOUND","引用不存在或不属于本作品");}
    private BusinessException changed(){return new BusinessException(CONFLICT,"WRITING_CHANGED","章节或创作资料已变化，请重新预览引用内容");}
}
