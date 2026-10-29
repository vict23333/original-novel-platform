-- 作者私有创作资料，不进入公开快照或阅读接口。
CREATE TABLE writing_project (
    novel_id BIGINT NOT NULL PRIMARY KEY,
    outline TEXT NOT NULL,
    world_setting TEXT NOT NULL,
    writing_style VARCHAR(1000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_writing_project FOREIGN KEY (novel_id) REFERENCES novel(id) ON DELETE CASCADE
);
CREATE TABLE writing_character (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    aliases VARCHAR(100) NOT NULL,
    story_role VARCHAR(100) NOT NULL,
    profile VARCHAR(1000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_writing_character FOREIGN KEY (novel_id) REFERENCES novel(id) ON DELETE CASCADE,
    CONSTRAINT uq_writing_character UNIQUE (novel_id, name)
);
CREATE TABLE writing_chapter_note (
    chapter_id BIGINT NOT NULL PRIMARY KEY,
    goal VARCHAR(2000) NOT NULL,
    CONSTRAINT fk_writing_note FOREIGN KEY (chapter_id) REFERENCES chapter(id) ON DELETE CASCADE
);
