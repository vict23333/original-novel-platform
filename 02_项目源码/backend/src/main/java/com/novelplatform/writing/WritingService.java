package com.novelplatform.writing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novelplatform.content.*;
import com.novelplatform.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
public class WritingService {
    private final ContentService content;
    private final NovelMapper novels;
    private final WritingProjectMapper projects;
    private final WritingCharacterMapper characters;
    public WritingService(ContentService content, NovelMapper novels, WritingProjectMapper projects, WritingCharacterMapper characters) {
        this.content=content;this.novels=novels;this.projects=projects;this.characters=characters;
    }
    public record Workspace(WritingProject project,List<WritingCharacter> characters) {}
    public Workspace workspace(long owner,long novel) {
        content.authorNovel(owner,novel);
        var project=projects.selectById(novel);
        if(project==null){project=new WritingProject();project.setNovelId(novel);project.setOutline("");project.setWorldSetting("");project.setWritingStyle("");project.setVersion(0L);}
        return new Workspace(project,characters.selectList(Wrappers.<WritingCharacter>lambdaQuery().eq(WritingCharacter::getNovelId,novel).orderByAsc(WritingCharacter::getId)));
    }
    private void lock(long owner,long novel) {
        content.authorNovel(owner,novel);novels.lockById(novel);
    }
    private void version(long actual,long expected) {
        if(actual!=expected)throw new BusinessException(CONFLICT,"WRITING_CHANGED","创作资料已更新，请保留输入并重新加载");
    }
    @Transactional public WritingProject save(long owner,long novel,WritingInputs.Project input) {
        lock(owner,novel);var project=projects.selectById(novel);boolean fresh=project==null;
        if(fresh){project=new WritingProject();project.setNovelId(novel);project.setVersion(0L);}
        version(project.getVersion(),input.version());
        project.setOutline(input.outline());project.setWorldSetting(input.worldSetting());project.setWritingStyle(input.writingStyle());project.setVersion(project.getVersion()+1);
        if(fresh)projects.insert(project);else projects.updateById(project);
        return project;
    }
    @Transactional public WritingCharacter saveCharacter(long owner,long novel,Long id,WritingInputs.Character input) {
        lock(owner,novel);
        var item=id==null?new WritingCharacter():character(novel,id);
        version(id==null?0:item.getVersion(),input.version());
        if(id==null && characters.selectCount(Wrappers.<WritingCharacter>lambdaQuery().eq(WritingCharacter::getNovelId,novel))>=100)
            throw new BusinessException(BAD_REQUEST,"CHARACTER_LIMIT","每部作品最多保存 100 张角色卡");
        if(characters.selectCount(Wrappers.<WritingCharacter>lambdaQuery().eq(WritingCharacter::getNovelId,novel)
                .eq(WritingCharacter::getName,input.name().strip()).ne(id!=null,WritingCharacter::getId,id))>0)
            throw new BusinessException(CONFLICT,"DUPLICATE_CHARACTER","本书已有同名角色，请修改姓名或编辑已有角色");
        item.setNovelId(novel);item.setName(input.name().strip());item.setAliases(input.aliases());item.setStoryRole(input.storyRole());item.setProfile(input.profile());item.setVersion(id==null?0L:item.getVersion()+1);
        if(id==null)characters.insert(item);else characters.updateById(item);
        return item;
    }
    public WritingCharacter character(long novel,long id) {
        var item=characters.selectById(id);
        if(item==null || item.getNovelId()!=novel)throw new BusinessException(NOT_FOUND,"NOT_FOUND","角色不存在或不属于本作品");
        return item;
    }
    @Transactional public void deleteCharacter(long owner,long novel,long id,long expected) {
        lock(owner,novel);var item=character(novel,id);version(item.getVersion(),expected);characters.deleteById(id);
    }
}
