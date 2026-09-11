# 原创小说读写平台 · 正式工程

课程与毕设共用的唯一业务工程。v0.1 已实现读者/作者注册、登录、本人资料维护、退出登录、角色入口和服务端鉴权。作者工作台与管理后台目前仅验证身份，其他业务功能继续按需求逐步实现。

## 目录与版本

| 目录 | 内容 |
| --- | --- |
| backend/ | Java 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Spring Security JWT、Flyway 迁移 |
| frontend/ | Vue 3.5.42、Element Plus 2.14.5、Vue Router 4.6.4、Vite 8.3.0、npm 锁文件 |
| database/ | 数据库说明，迁移脚本统一放在后端 resources/db/migration |
| scripts/ | Windows 构建、启动、状态与停止脚本 |
| docs/ | [账号设计与接口](docs/账号模块设计与接口.md)、[验证记录](docs/验证记录_v0.1.md) |

需要 Java 21、Maven 3.9、MySQL 8.0、Node 20.19+（20 系列）或 22.12+、npm。实际验证使用 Node 24；Node 18 无法运行本项目 Vite。脚本不会修改全局工具配置。

## Windows 本地运行

在本目录打开 PowerShell：

```powershell
./scripts/build.ps1
./scripts/dev.ps1 -Action Start
./scripts/dev.ps1 -Action Status
./scripts/dev.ps1 -Action Stop
```

build 运行后端测试、生成 jar、按锁文件安装前端依赖并构建。首次 Start 创建独立 MySQL 数据目录、生成随机数据库/JWT/管理员凭据、执行迁移并启动前后端；再次启动保留数据。Stop 只处理该脚本记录且进程身份匹配的程序，数据库正常关闭。端口占用时不会停止其他程序。

服务正在运行时，先 Stop 再 build，避免 Windows 锁定正在使用的依赖或 jar 文件；脚本会提前检查并提示，不在运行中的工程内重装依赖。

默认 Node 太旧时，build 和 dev 脚本均可用 `-NodeExe` 指定已安装的新版 node.exe；dev 可用 `-MySqlBin` 指定 MySQL bin 目录。首次启动选定的工具路径只记录在 `.runtime/settings.local.json`，之后自动复用。

| 服务 | 开发地址 |
| --- | --- |
| 正式平台页面 | http://127.0.0.1:5173 |
| 后端健康检查 | http://127.0.0.1:8081/api/health |
| 独立 MySQL | 127.0.0.1:13406，数据库 novel_platform |

服务只绑定本机。`.runtime/` 内含数据库、私密配置和日志，不得提交 Git；不要创建 `.runtime/nonexistent-socket-dir`，该路径用于本机 Java NIO 的 TCP 回退参数。

读者和作者在页面自行注册。管理员由本机配置初始化，可查看 `.runtime/settings.local.json` 的 adminUsername/adminPassword 后从普通登录页进入；不要分享此配置文件。

## 手动配置与启动

不使用脚本时先创建空 MySQL 数据库，并设置当前 shell 环境变量：

| 变量 | 用途 |
| --- | --- |
| DB_URL | JDBC MySQL URL，默认本机 13406/novel_platform、UTC |
| DB_USERNAME / DB_PASSWORD | 数据库身份，启动迁移需有本库建表权限 |
| JWT_SECRET | 至少 32 个随机字节的 Base64 编码，无默认值 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 可选的首次管理员初始化，不重置已有账号 |
| SERVER_ADDRESS / SERVER_PORT | 默认 127.0.0.1:8081 |

在 backend 执行 `mvn spring-boot:run`；在 frontend 执行 `npm ci`、`npm run dev`。前端不接收数据库密码或 JWT 签名密钥。Vite 默认代理到后端 8081，改后端端口需同步改代理。

## 验收顺序

1. 注册读者、登录、修改昵称和简介，刷新确认已保存。
2. 退出后访问个人中心应跳转登录；注册作者后出现作者工作台入口。
3. 作者可进入工作台，作品等模块明确标为待实现；读者直接访问作者页面回到个人中心，直接请求作者 API 返回 403。
4. 初始化的管理员登录后出现管理入口；公开注册 ADMIN 必须失败。
5. 停止并重启服务，再登录，已保存资料仍在。

测试命令：backend 执行 `mvn test`（隔离 H2 内存数据库），frontend 执行 `npm run build`。实际 MySQL 与浏览器验证结果见验证记录。业务源码按本项目需求独立编写，未复制实验项目业务代码；第三方依赖遵循各自许可证。
