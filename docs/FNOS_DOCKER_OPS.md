# 飞牛 OS Docker 部署与运维手册

> 目标：在飞牛 OS 闲置笔记本上部署 Noto，并能理解 Docker Compose、镜像源、反向代理、更新、备份和常见故障处理。

## 1. 部署结构

Noto 生产环境由 Docker Compose 管理 4 个容器：

| 容器 | 作用 | 是否应公网暴露 |
|------|------|----------------|
| `noto-frontend` | Nginx 静态前端，并把 `/api/` 转发给后端 | 推荐通过反向代理访问；也可直连 IPv6 高端口 |
| `noto-backend` | Spring Boot API 服务 | 否 |
| `noto-db` | PostgreSQL + pgvector 数据库 | 否 |
| `noto-minio` | 附件与图片对象存储 | 否 |

推荐入口：

```text
Internet :443
    ↓
飞牛上的 Lucky / Nginx Proxy Manager / 宿主机 Nginx
    ↓
127.0.0.1:8080
    ↓
noto-frontend
    ↓ Docker 内网
noto-backend / noto-db / noto-minio
```

公网只开放 `80/tcp`、`443/tcp`，维护时再按需开放 `22/tcp`。不要开放 `5432`、`9000`、`9001`、`9086`。

当前飞牛实测可用的直连入口：

```text
Internet IPv6 :18080
    ↓
飞牛宿主机 [::]:18080
    ↓
noto-frontend
    ↓ Docker 内网
noto-backend / noto-db / noto-minio
```

这种方式不改飞牛系统 Nginx，不暴露飞牛后台，但访问地址需要带端口：

```text
http://notoai.cn:18080
```

## 2. 首次准备

### 2.1 开启飞牛 SSH

在飞牛 OS 后台开启 SSH 后，从电脑连接：

```bash
ssh yolo@飞牛局域网IP
```

如果使用 Xshell，登录后所有命令都在 Xshell 中执行。

### 2.2 准备项目目录

```bash
mkdir -p /vol1/1000/docker/noto
cd /vol1/1000/docker/noto
```

如果你的数据盘不是 `/vol1/1000`，先查看：

```bash
ls /vol*
pwd
```

### 2.3 获取代码

网络正常时：

```bash
git clone https://github.com/zl12345678/Noto.git .
```

如果飞牛无法访问 GitHub，从 Windows 打包上传：

```powershell
cd E:\Noto
tar --exclude=.git `
    --exclude=frontend/node_modules `
    --exclude=mobile/node_modules `
    --exclude=backend/target `
    --exclude=.idea `
    --exclude=.vscode `
    -czf $env:TEMP\noto.tar.gz .

scp $env:TEMP\noto.tar.gz yolo@飞牛局域网IP:/vol1/1000/docker/noto/
```

飞牛上解压：

```bash
cd /vol1/1000/docker/noto
tar -xzf noto.tar.gz
rm noto.tar.gz
```

## 3. 配置 Docker 国内镜像源

飞牛拉取 Docker Hub 镜像超时时，先配置镜像源。Docker 使用 `/etc/docker/daemon.json` 持久保存镜像源配置。

项目已提供脚本：

```bash
cd /vol1/1000/docker/noto
bash deploy/fnos-docker-mirror.sh
```

如果默认镜像源仍然超时，切换备用源：

```bash
cd /vol1/1000/docker/noto
bash deploy/fnos-docker-mirror.sh --alternate
```

脚本会备份原 `/etc/docker/daemon.json`、写入镜像源、重启 Docker，并用 `hello-world` 测试拉取。

也可以手动配置：

```bash
sudo mkdir -p /etc/docker

if [ -f /etc/docker/daemon.json ]; then
  sudo cp /etc/docker/daemon.json /etc/docker/daemon.json.bak.$(date +%Y%m%d-%H%M%S)
fi

sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://docker.1ms.run",
    "https://docker.m.daocloud.io"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
docker info | grep -A 10 -i "Registry Mirrors"
```

验证：

```bash
sudo docker pull hello-world
```

如果仍然超时，替换为备用源：

```bash
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "registry-mirrors": [
    "https://docker.xuanyuan.me",
    "https://docker.1panel.live",
    "https://docker.1ms.run",
    "https://docker.m.daocloud.io"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
sudo docker pull hello-world
```

回滚：

```bash
ls -lh /etc/docker/daemon.json.bak.*
sudo cp /etc/docker/daemon.json.bak.你要恢复的文件 /etc/docker/daemon.json
sudo systemctl restart docker
```

## 4. 配置 Noto 生产环境变量

```bash
cd /vol1/1000/docker/noto
cp deploy/env.prod.example .env
nano .env
```

必须修改：

```env
POSTGRES_PASSWORD=你的数据库强密码
MINIO_ROOT_PASSWORD=你的MinIO强密码
NOTO_JWT_SECRET=至少32位随机字符串
NOTO_DEMO_ENABLED=false
```

飞牛直连或 Cloudflare Tunnel 方式可按需设置前端监听地址：

```env
BACKEND_PORT=19086
FRONTEND_IPV4_HOST=0.0.0.0
FRONTEND_IPV6_HOST=[::]
FRONTEND_PORT=80
```

说明：

- `FRONTEND_IPV4_HOST=0.0.0.0` 让局域网 IPv4 可访问，例如 `http://飞牛局域网IP`。
- `FRONTEND_IPV6_HOST=[::]` 让公网 IPv6 可访问，例如 `http://v6.notoai.cn`。
- `FRONTEND_PORT=80` 让 HTTP 访问不需要填写端口号。
- `BACKEND_PORT=19086` 只绑定本机，避免与已有 `9086` 服务冲突。

生成 JWT 密钥：

```bash
openssl rand -base64 48
```

启用 AI：

```env
NOTO_AI_ENABLED=true
AI_DASHSCOPE_API_KEY=你的百炼Key
```

暂不启用 AI：

```env
NOTO_AI_ENABLED=false
AI_DASHSCOPE_API_KEY=
```

`nano` 保存退出：

```text
Ctrl + O
Enter
Ctrl + X
```

## 5. 启动项目

先检查 `.env`：

```bash
cd /vol1/1000/docker/noto
chmod +x deploy/*.sh
./deploy/prod-check.sh --strict
```

如果提示 Docker 权限不足，先用 `sudo`：

```bash
sudo ./deploy/prod-up.sh
```

长期免 `sudo`：

```bash
sudo usermod -aG docker yolo
exit
```

重新 SSH 登录后验证：

```bash
docker ps
```

以后可直接运行：

```bash
cd /vol1/1000/docker/noto
./deploy/prod-up.sh
```

## 6. 验证服务

默认反向代理模式的本机健康检查：

```bash
curl http://127.0.0.1:8080/api/v1/health
```

飞牛直连 IPv6 高端口模式的健康检查：

```bash
curl -g -6 "http://[::1]:18080/api/v1/health"
curl -g -6 "http://[你的飞牛公网IPv6]:18080/api/v1/health"
```

期望看到：

```json
{"code":0,...}
```

查看容器：

```bash
docker ps --filter name=noto-
```

查看日志：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f frontend
```

## 7. 配置域名与反向代理

如果只想让域名打开项目、不想让域名打开飞牛后台，有两种方式：

| 方式 | 访问地址 | 是否需要反向代理 |
|------|----------|------------------|
| 直连 IPv6 高端口 | `http://notoai.cn:18080` | 不需要 |
| 正式 HTTPS + IPv4 访问 | `https://notoai.cn` | 推荐 Cloudflare Tunnel |
| 自建正式 HTTPS | `https://notoai.cn` | 需要 Lucky / Nginx Proxy Manager / 系统 Nginx |

当前已验证的方案是直连 IPv6 高端口：飞牛后台仍使用局域网地址 `http://飞牛局域网IP:5666`，域名只用于项目。

### 7.1 DNS

在域名解析中配置：

```text
AAAA    notoai.cn    飞牛公网IPv6地址
```

家庭 IPv6 部署通常不要保留错误的 `A` 记录；如果没有可用公网 IPv4，建议只保留 `AAAA`。

外部网络验证：

```bash
ping -6 notoai.cn
```

如果不通，检查路由器 IPv6 防火墙、飞牛防火墙、运营商是否允许入站。

### 7.2 不用反向代理：IPv6 高端口直连

`.env`：

```env
FRONTEND_IPV6_HOST=[::]
FRONTEND_PORT=18080
```

启动：

```bash
cd /vol1/1000/docker/noto
sudo ./deploy/prod-up.sh
```

确认监听：

```bash
sudo ss -lntp | grep 18080
```

应看到类似：

```text
[::]:18080
```

飞牛本机验证：

```bash
curl -g -6 "http://[::1]:18080/api/v1/health"
curl -g -6 "http://[你的飞牛公网IPv6]:18080/api/v1/health"
```

外网验证建议使用手机 4G/5G，关闭 Wi-Fi 后访问：

```text
http://notoai.cn:18080/api/v1/health
```

如果飞牛本机可访问公网 IPv6，但手机流量不可访问，通常是路由器或光猫的 IPv6 入站策略未放行 `18080/tcp`。

### 7.3 Lucky / Nginx Proxy Manager

如果反代工具运行在飞牛宿主机或 host 网络中：

```text
域名：notoai.cn
目标：http://127.0.0.1:8080
证书：Let's Encrypt
WebSocket/SSE：开启或关闭缓存
```

如果反代工具是普通 Docker bridge 网络容器，容器里的 `127.0.0.1` 指的是反代容器本身，不是飞牛宿主机。此时推荐把反代容器改成 host 网络，或者让反代容器与 Noto 进入同一个 Docker 网络后访问 `http://noto-frontend:80`。

浏览器验证：

```text
https://notoai.cn
https://notoai.cn/api/v1/health
```

飞牛 OS 自带的 `/usr/trim/nginx` 会服务系统管理入口，并可能在重载时恢复主配置。不要长期依赖手工改 `/usr/trim/nginx/conf/nginx.conf`。更稳的做法是在飞牛自带反向代理 UI、Lucky 或 Nginx Proxy Manager 中新增站点：

```text
域名：notoai.cn
协议：http
目标主机：127.0.0.1
目标端口：8080
```

如果从外网访问超时，先确认：

- DNS 中不要保留错误的 `A` 记录；家庭 IPv6 部署通常只需要 `AAAA`。
- 使用直连高端口时，路由器 IPv6 防火墙放行飞牛这台设备的 `18080/tcp`。
- 使用正式 HTTPS 反向代理时，路由器 IPv6 防火墙放行飞牛这台设备的 `80/tcp` 和 `443/tcp`。
- 飞牛防火墙放行对应端口。
- 本机验证必须先通过：`curl -g -6 "http://[::1]:18080/api/v1/health"` 或 `curl http://127.0.0.1:8080/api/v1/health`。

### 7.4 Cloudflare Tunnel：IPv4/IPv6 都可访问

适用目标：

```text
https://notoai.cn        IPv4 / IPv6 都能访问
http://飞牛局域网IP:5666  飞牛后台只在局域网访问
```

Cloudflare Tunnel 的流量路径：

```text
用户 IPv4/IPv6
    ↓
Cloudflare 边缘节点
    ↓
cloudflared 隧道
    ↓
飞牛本地 Noto
```

这种方式不需要家里有公网 IPv4，也不需要路由器开放 `80/443` 入站。缺点是访问会经过 Cloudflare，中转速度受 Cloudflare 线路影响。

#### 7.4.1 接入域名到 Cloudflare

1. 在 Cloudflare 添加站点：

```text
notoai.cn
```

2. Cloudflare 会给出两个 Nameserver，例如：

```text
ainsley.ns.cloudflare.com
brodie.ns.cloudflare.com
```

3. 到域名注册商处修改 DNS 服务器。以阿里云为例，不是在“云解析 DNS”里改 A/AAAA 记录，而是进入：

```text
域名注册控制台
-> 域名列表
-> notoai.cn
-> 管理
-> DNS 修改 / DNS服务器修改
```

4. 把原 DNS 服务器替换为 Cloudflare 给出的两个 Nameserver。

5. 在 Windows 上验证 NS 是否开始生效：

```cmd
nslookup -type=NS notoai.cn 223.5.5.5
nslookup -type=NS notoai.cn 1.1.1.1
nslookup -type=NS notoai.cn 8.8.8.8
```

看到 Cloudflare 的 Nameserver 后，说明该 DNS 服务器已经生效。不同公共 DNS 生效时间可能不同，通常几分钟到几小时，最长可能 24-48 小时。

#### 7.4.2 创建 Tunnel

Cloudflare 控制台进入：

```text
Zero Trust
-> Networks
-> Tunnels
-> Create Tunnel
```

选择：

```text
Cloudflared
```

Tunnel 名称可用：

```text
noto
```

创建后选择运行环境：

```text
Docker
```

页面会给出一段包含 token 的命令。token 只保存在本机或飞牛上，不要提交到 Git，不要截图公开。

#### 7.4.3 在飞牛 Docker 中运行 cloudflared

在飞牛终端执行：

```bash
mkdir -p /vol1/1000/docker/cloudflared
cd /vol1/1000/docker/cloudflared
nano docker-compose.yml
```

写入：

```yaml
services:
  cloudflared:
    image: cloudflare/cloudflared:latest
    container_name: cloudflared
    restart: unless-stopped
    network_mode: host
    command: tunnel --no-autoupdate run --token 你的TUNNEL_TOKEN
```

启动：

```bash
sudo docker compose up -d
```

查看状态：

```bash
sudo docker ps | grep cloudflared
sudo docker logs -f cloudflared
```

Cloudflare Tunnel 页面应显示：

```text
Healthy
```

#### 7.4.4 添加公网域名规则

进入：

```text
Zero Trust
-> Networks
-> Tunnels
-> noto
-> Routes / Public Hostname
-> Add route / Add a public hostname
```

填写：

```text
Subdomain：留空
Domain：notoai.cn
Path：留空
Service URL：http://127.0.0.1:80
```

如果 `http://127.0.0.1:80` 保存后访问失败，并且 Noto 当前只监听 IPv6，可改为：

```text
Service URL：http://[::1]:80
```

当前项目若使用飞牛直连端口 80，应在 `.env` 中设置：

```env
FRONTEND_IPV4_HOST=0.0.0.0
FRONTEND_IPV6_HOST=[::]
FRONTEND_PORT=80
```

然后重启：

```bash
cd /vol1/1000/docker/noto
sudo bash deploy/prod-up.sh
```

飞牛本机验证：

```bash
curl -g -6 "http://[::1]/api/v1/health"
```

#### 7.4.5 Cloudflare DNS 检查

Cloudflare 中进入：

```text
notoai.cn
-> DNS
-> Records
```

应看到 Tunnel 自动生成的记录，通常类似：

```text
Type：CNAME
Name：notoai.cn 或 @
Target：xxxx.cfargotunnel.com
Proxy status：Proxied / 橙云
```

如果还有旧的 `A` 或 `AAAA` 记录指向家庭宽带地址，主域名走 Tunnel 时建议删除，避免与 Tunnel 规则冲突。

如果需要保留 IPv6 直连备用入口，可单独添加：

```text
Type：AAAA
Name：v6
Value：飞牛公网IPv6
Proxy status：DNS only / 灰云
```

这样：

```text
https://notoai.cn      走 Cloudflare Tunnel，IPv4/IPv6 都可访问
http://v6.notoai.cn    走家庭 IPv6 直连备用
```

#### 7.4.6 验证访问

刷新 Windows DNS 缓存：

```cmd
ipconfig /flushdns
```

检查 NS：

```cmd
nslookup -type=NS notoai.cn
```

浏览器访问：

```text
https://notoai.cn/api/v1/health
https://notoai.cn
```

如果 Windows 命令行 `curl` 报证书吊销检查超时，优先用浏览器验证；也可以换手机 4G/5G 测试。

#### 7.4.7 常见问题

如果 Cloudflare 添加 Public Hostname 时 `Domain` 下拉里没有 `notoai.cn`，说明域名还没有成功接入 Cloudflare，需要先等待站点状态变成 `Active`。

如果 Tunnel 状态不是 `Healthy`，查看飞牛日志：

```bash
sudo docker logs --tail=100 cloudflared
```

如果 `https://notoai.cn` 返回 502，优先检查 Public Hostname 的 Service URL：

```text
http://127.0.0.1:80
http://[::1]:80
```

如果 `nslookup -type=NS notoai.cn 223.5.5.5` 仍返回阿里云 NS，而 `1.1.1.1` 或 `8.8.8.8` 已返回 Cloudflare NS，说明 DNS 正在传播，继续等待即可。

## 8. Docker Compose 基础理解

项目启动命令实际合并了两个 Compose 文件：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build
```

含义：

| 参数 | 说明 |
|------|------|
| `docker-compose.yml` | 定义数据库、后端、MinIO、前端怎么构建和连接 |
| `deploy/docker-compose.prod.yml` | 生产覆盖：隐藏 db/minio 端口；前端默认绑定到 `127.0.0.1:8080` 与 `[::1]:8080`，也可通过 `FRONTEND_IPV4_HOST/FRONTEND_IPV6_HOST/FRONTEND_PORT` 改为公网或局域网入口；后端只绑定到本机端口 |
| `up -d` | 后台启动 |
| `--build` | 启动前重新构建前后端镜像 |

常用命令：

```bash
# 查看容器
docker ps

# 查看所有容器，包括退出的
docker ps -a

# 查看镜像
docker images

# 查看日志
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f

# 停止但保留数据卷
./deploy/prod-down.sh

# 停止并删除数据卷，危险，会清空数据库和 MinIO 数据
./deploy/prod-down.sh --volumes
```

## 9. 更新项目

如果飞牛能访问 GitHub：

```bash
cd /vol1/1000/docker/noto
./deploy/prod-update.sh
```

如果代码是从 Windows 上传的：

1. Windows 重新打包上传。
2. 飞牛解压覆盖项目文件。
3. 执行：

```bash
cd /vol1/1000/docker/noto
sudo ./deploy/prod-up.sh
```

更新前建议先备份数据库。

## 10. 备份与恢复

### 10.1 手动备份数据库

```bash
cd /vol1/1000/docker/noto
mkdir -p /vol1/1000/backups/noto
./deploy/prod-backup.sh /vol1/1000/backups/noto
```

备份文件形如：

```text
noto-20260630-153000.sql.gz
```

### 10.2 定时备份

编辑计划任务：

```bash
crontab -e
```

每天凌晨 3 点备份：

```cron
0 3 * * * cd /vol1/1000/docker/noto && ./deploy/prod-backup.sh /vol1/1000/backups/noto >> /vol1/1000/backups/noto/backup.log 2>&1
```

### 10.3 MinIO 文件备份

数据库备份只包含结构化数据。上传的图片、附件在 Docker volume `noto_minio_data` 中，也应纳入飞牛快照、同步任务或额外备份策略。

查看卷：

```bash
docker volume ls | grep noto
```

## 11. 常见故障

### 11.1 `permission denied while trying to connect to the Docker daemon socket`

当前用户没有 Docker 权限。

临时解决：

```bash
sudo ./deploy/prod-up.sh
```

长期解决：

```bash
sudo usermod -aG docker yolo
exit
```

重新登录后验证：

```bash
docker ps
```

### 11.2 `context deadline exceeded` 或拉镜像超时

说明访问 Docker Hub 或镜像源失败。

处理顺序：

1. 配置第 3 节的国内镜像源。
2. `sudo docker pull hello-world` 验证。
3. 再执行 `sudo ./deploy/prod-up.sh`。

### 11.3 `failed to listen on TCP socket: address already in use`

先查看生产 Compose 解析后的端口：

```bash
cd /vol1/1000/docker/noto
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml config | sed -n '/backend:/,/frontend:/p'
```

生产覆盖文件应使用 `ports: !override`，避免把主 `docker-compose.yml` 的端口和生产端口同时追加到同一个容器上。如果只是宿主机已有服务占用了端口，可以在 `.env` 中调整：

```env
BACKEND_PORT=19086
FRONTEND_PORT=8080
```

前端容器通过 Docker 内网访问后端，公网入口只需要反向代理到 `FRONTEND_PORT`。

如果不用反向代理、直接通过双栈 80 端口访问：

```env
FRONTEND_IPV4_HOST=0.0.0.0
FRONTEND_IPV6_HOST=[::]
FRONTEND_PORT=80
```

### 11.4 Maven 下载依赖超时

后端 Docker 构建会在容器里执行 Maven。如果日志中出现 `repo.maven.apache.org:443 failed to respond`，说明 Maven Central 访问超时。

项目的 [backend/Dockerfile](../backend/Dockerfile) 已内置 [backend/docker/maven-settings.xml](../backend/docker/maven-settings.xml)，构建时会使用阿里云 Maven 镜像。修改该配置后，重新执行：

```bash
cd /vol1/1000/docker/noto
sudo ./deploy/prod-up.sh
```

### 11.5 `prod-check` 提示 JWT 或密码不合格

编辑 `.env`：

```bash
nano .env
```

确认：

```env
NOTO_JWT_SECRET=至少32位随机字符串
POSTGRES_PASSWORD=不是默认值
MINIO_ROOT_PASSWORD=不是默认值
NOTO_DEMO_ENABLED=false
```

如果需要对外演示，同时自己也要长期使用，建议启用独立演示账号：

```env
NOTO_DEMO_ENABLED=true
NOTO_DEMO_USERNAME=demo
NOTO_DEMO_PASSWORD=改成演示账号密码
NOTO_DEMO_EMAIL=demo@noto.local
NOTO_DEMO_NICKNAME=演示账号
NOTO_DEMO_CREATE_USER=true
```

这样演示数据会写入 `demo`，`admin` 留给自己使用。演示账号已存在时，重启不会覆盖它的密码。

### 11.6 健康检查不通

如果使用直连 IPv6 高端口，优先测试：

```bash
curl -g -6 "http://[::1]:18080/api/v1/health"
```

如果它正常，而 `curl http://127.0.0.1:8080/api/v1/health` 不通，说明当前已经不是默认 `8080` 入口，这是正常的。

查看容器状态：

```bash
docker ps -a --filter name=noto-
```

看后端日志：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs --tail=200 backend
```

看前端日志：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs --tail=200 frontend
```

如果数据库日志出现 `/docker-entrypoint-initdb.d/01-schema.sql: Permission denied`，说明 `建表SQL.sql` 权限过窄。执行：

```bash
cd /vol1/1000/docker/noto
chmod 644 建表SQL.sql
cat 建表SQL.sql | sudo docker exec -i noto-db psql -v ON_ERROR_STOP=1 -U postgres -d noto_zhihui
sudo ./deploy/prod-up.sh
```

### 11.7 域名能解析但外网访问失败

依次检查：

1. `AAAA` 是否指向飞牛公网 IPv6。
2. 使用直连入口时，路由器 IPv6 防火墙是否放行对应端口，例如 `80/tcp` 或 `18080/tcp`。
3. 使用 `https://notoai.cn` 时，路由器 IPv6 防火墙是否放行 `80/443`。
4. 飞牛防火墙是否放行对应端口。
5. 反代目标是否为 `http://127.0.0.1:8080`。
6. 如果反代在 Docker bridge 网络中，不要使用容器内的 `127.0.0.1` 指向宿主机。

## 12. 上线完成检查清单

- [ ] `./deploy/prod-check.sh --strict` 通过。
- [ ] `docker ps --filter name=noto-` 中 4 个容器都在运行。
- [ ] 默认反代模式：`curl http://127.0.0.1:8080/api/v1/health` 返回 `code:0`。
- [ ] 双栈直连模式：`curl http://127.0.0.1/api/v1/health` 与 `curl -g -6 "http://[::1]/api/v1/health"` 返回 `code:0`。
- [ ] Cloudflare Tunnel 模式：`https://notoai.cn/api/v1/health` 返回 `code:0`。
- [ ] 自用生产环境：`NOTO_DEMO_ENABLED=false`；公开演示环境：使用独立 `demo` 账号，不把演示数据写入 `admin`。
- [ ] PostgreSQL、MinIO、后端端口未暴露公网。
- [ ] 已配置数据库定时备份。
- [ ] 记录好 `.env` 的密钥和密码，不提交到 Git。
