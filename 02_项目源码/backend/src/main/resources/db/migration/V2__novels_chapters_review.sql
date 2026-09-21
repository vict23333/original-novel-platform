CREATE TABLE novel_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);
INSERT INTO novel_category (name) VALUES ('玄幻'), ('都市'), ('悬疑'), ('科幻'), ('历史'), ('其他');

CREATE TABLE novel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title VARCHAR(50) NOT NULL,
    introduction VARCHAR(1000) NOT NULL,
    category_id BIGINT NOT NULL,
    serial_status VARCHAR(12) NOT NULL DEFAULT 'SERIALIZING',
    version BIGINT NOT NULL DEFAULT 0,
    review_status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    review_reason VARCHAR(500) NOT NULL DEFAULT '',
    published BOOLEAN NOT NULL DEFAULT FALSE,
    taken_down BOOLEAN NOT NULL DEFAULT FALSE,
    public_title VARCHAR(50),
    public_introduction VARCHAR(1000),
    public_category_id BIGINT,
    public_serial_status VARCHAR(12),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_novel_author FOREIGN KEY (author_id) REFERENCES app_user(id),
    CONSTRAINT fk_novel_category FOREIGN KEY (category_id) REFERENCES novel_category(id),
    CONSTRAINT fk_novel_public_category FOREIGN KEY (public_category_id) REFERENCES novel_category(id),
    CONSTRAINT ck_novel_serial CHECK (serial_status IN ('SERIALIZING', 'COMPLETED')),
    CONSTRAINT ck_novel_review CHECK (review_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_novel_author ON novel(author_id, id);
CREATE INDEX idx_novel_review ON novel(review_status, id);
CREATE INDEX idx_novel_public ON novel(published, taken_down, public_category_id, id);

CREATE TABLE chapter (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_number INT,
    title VARCHAR(100) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    review_status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    review_reason VARCHAR(500) NOT NULL DEFAULT '',
    published BOOLEAN NOT NULL DEFAULT FALSE,
    taken_down BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    public_title VARCHAR(100),
    public_content MEDIUMTEXT,
    public_chapter_number INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chapter_novel FOREIGN KEY (novel_id) REFERENCES novel(id),
    CONSTRAINT uk_chapter_number UNIQUE (novel_id, chapter_number),
    CONSTRAINT uk_public_chapter_number UNIQUE (novel_id, public_chapter_number),
    CONSTRAINT ck_chapter_review CHECK (review_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_chapter_review ON chapter(review_status, deleted, id);

CREATE TABLE content_review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT,
    content_version BIGINT NOT NULL,
    action VARCHAR(12) NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    snapshot MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_novel FOREIGN KEY (novel_id) REFERENCES novel(id),
    CONSTRAINT fk_review_chapter FOREIGN KEY (chapter_id) REFERENCES chapter(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES app_user(id)
);
CREATE INDEX idx_review_novel ON content_review(novel_id, id);
