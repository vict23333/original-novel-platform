CREATE TABLE reader_comment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    root_id BIGINT NULL,
    reply_to_id BIGINT NULL,
    body VARCHAR(500) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by BIGINT NULL,
    delete_reason VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (novel_id) REFERENCES novel(id),
    FOREIGN KEY (chapter_id) REFERENCES chapter(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (root_id) REFERENCES reader_comment(id),
    FOREIGN KEY (reply_to_id) REFERENCES reader_comment(id),
    FOREIGN KEY (deleted_by) REFERENCES app_user(id),
    CONSTRAINT ck_comment_reply CHECK ((root_id IS NULL AND reply_to_id IS NULL) OR (root_id IS NOT NULL AND reply_to_id IS NOT NULL))
);
CREATE INDEX ix_comment_scope ON reader_comment(novel_id, chapter_id, root_id, id);
CREATE INDEX ix_comment_root ON reader_comment(root_id, id);
CREATE TABLE comment_like (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (comment_id, user_id),
    FOREIGN KEY (comment_id) REFERENCES reader_comment(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
