ALTER TABLE app_user ADD COLUMN admin_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE novel_category ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE novel_tag (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE novel_tag_link (
    novel_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (novel_id, tag_id),
    CONSTRAINT fk_tag_link_novel FOREIGN KEY (novel_id) REFERENCES novel(id),
    CONSTRAINT fk_tag_link_tag FOREIGN KEY (tag_id) REFERENCES novel_tag(id)
);
CREATE INDEX idx_tag_link_tag ON novel_tag_link(tag_id);

-- 留存管理行为，不保存密码、令牌或完整个人资料快照。
CREATE TABLE admin_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
