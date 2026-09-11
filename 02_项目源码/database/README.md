# 数据库

正式开发数据库为 MySQL 8，字符集 utf8mb4。第一批只建立 `app_user`，其余业务表随作品、章节等功能的设计逐步增加。

建表脚本唯一来源：`../backend/src/main/resources/db/migration/V1__create_users.sql`。后端启动时由 Flyway 按版本执行，成功后记录到 `flyway_schema_history`；已有迁移不要修改，结构变化增加新版本脚本。

`app_user` 保存账号、密码散列、昵称、简介、角色、启用状态、令牌版本与时间。账号统一转小写，唯一索引保证并发注册也不能产生重复账号。角色限制为 READER / AUTHOR / ADMIN。

本地启动脚本创建独立 MySQL 实例与数据库，不访问既有数据库。测试代码使用隔离 H2 内存库，不能替代实际 MySQL 验证。数据库数据目录、连接凭据与日志均保存在被忽略的 `.runtime/`。
