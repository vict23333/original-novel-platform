CREATE TABLE bookshelf (
    user_id BIGINT NOT NULL,
    novel_id BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, novel_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE TABLE reading_progress (
    user_id BIGINT NOT NULL,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    paragraph_index INT NOT NULL,
    content_revision CHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, novel_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (novel_id) REFERENCES novel(id),
    FOREIGN KEY (chapter_id) REFERENCES chapter(id),
    CONSTRAINT ck_progress_paragraph CHECK (paragraph_index >= 0),
    CONSTRAINT ck_progress_version CHECK (version > 0)
);
