CREATE TABLE novel_read_day (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    visitor_key CHAR(64) NOT NULL,
    read_date DATE NOT NULL,
    first_chapter_id BIGINT NOT NULL,
    first_read_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_read_identity_day UNIQUE (visitor_key, novel_id, read_date),
    CONSTRAINT fk_read_novel FOREIGN KEY (novel_id) REFERENCES novel(id),
    CONSTRAINT fk_read_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_read_chapter FOREIGN KEY (first_chapter_id) REFERENCES chapter(id)
);
CREATE INDEX ix_read_date_novel ON novel_read_day(read_date, novel_id);
CREATE INDEX ix_read_novel_date ON novel_read_day(novel_id, read_date);
