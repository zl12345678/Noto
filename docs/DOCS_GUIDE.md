# Noto 文档阅读引导

> 目标：用最少文档理解项目。不同角色按不同顺序阅读，不需要从头翻完整个仓库。

## 保留文档

| 文档 | 作用 |
|------|------|
| [../README.md](../README.md) | 项目定位、核心功能、技术栈、快速启动 |
| [DEVELOPMENT.md](./DEVELOPMENT.md) | 本地开发、命令速查、调试、测试和演示检查 |
| [DEPLOYMENT.md](./DEPLOYMENT.md) | Docker 演示、生产部署、IPv6 家庭服务器、备份运维 |
| [FNOS_DOCKER_OPS.md](./FNOS_DOCKER_OPS.md) | 飞牛 OS 部署、Docker 镜像源、反向代理、更新和备份 |
| [AI_SHOWCASE.md](./AI_SHOWCASE.md) | 简历展示、AI 演示脚本、RAG/Agent 评测方法 |
| [MOBILE.md](./MOBILE.md) | 移动 Web / uni-app 客户端开发和打包 |
| [ai-eval-cases.json](./ai-eval-cases.json) | AI 评测用例数据 |

## 按角色阅读

### 面试官 / 招聘方

1. [../README.md](../README.md) - 先看项目是什么、技术栈和完成度。
2. [AI_SHOWCASE.md](./AI_SHOWCASE.md) - 看 3 分钟 AI 演示路径、简历 bullet、追问回答。
3. [DEPLOYMENT.md](./DEPLOYMENT.md) - 如果关注工程化，再看 Docker、HTTPS、备份和安全边界。

### 新接手开发者

1. [../README.md](../README.md) - 建立产品和模块全局认识。
2. [DEVELOPMENT.md](./DEVELOPMENT.md) - 按“三个终端”跑起来。
3. [AI_SHOWCASE.md](./AI_SHOWCASE.md) - 理解 AI/RAG/Agent 为什么是项目重点。
4. [MOBILE.md](./MOBILE.md) - 只有要改移动端时再看。

### 部署 / 运维视角

1. [DEPLOYMENT.md](./DEPLOYMENT.md) - 生产部署、IPv6、Nginx、HTTPS、备份。
2. [FNOS_DOCKER_OPS.md](./FNOS_DOCKER_OPS.md) - 飞牛 OS 上线、镜像源、反代和日常维护。
3. [DEVELOPMENT.md](./DEVELOPMENT.md) - 查 Windows 本机演示和脚本速查。
4. [../deploy/README.md](../deploy/README.md) - 只在需要看部署脚本索引时打开。

### 移动端开发视角

1. [MOBILE.md](./MOBILE.md) - uni-app 工程结构、H5 开发、App 资源构建。
2. [DEVELOPMENT.md](./DEVELOPMENT.md) - 后端和基础设施如何启动。
3. [../README.md](../README.md) - 回看核心业务闭环。

### AI / Agent 接手视角

1. [../README.md](../README.md) - 看 AI 能力在产品中的位置。
2. [AI_SHOWCASE.md](./AI_SHOWCASE.md) - 看 RAG、可确认 Agent、评测协议和演示话术。
3. [DEVELOPMENT.md](./DEVELOPMENT.md) - 跑起本地环境并验证 `/ai/status`。

## 维护规则

- 新文档必须能归入“开发、部署、AI、移动端、阅读引导”之一。
- 阶段性计划、临时检查清单和废弃方案不要长期保留；完成后合并进主题文档或删除。
- README 只放入口和概览，不堆长篇操作细节。
- 脚本、部署目录可以保留短 README，但只做索引，不复制长说明。
