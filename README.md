# Hadoop Guardian

`Hadoop Guardian` 是构建在 `Cloudera Manager` 之上的 Hadoop 运维诊断与处置系统，面向 `HDFS`、`YARN`、`Hive on Tez` 和 `Impala`。

## 项目结构

- `backend/`：Spring Boot 后端服务
- `frontend/`：React + TypeScript 运维工作台
- `sql/`：数据库建表和测试数据脚本
- `docs/`：设计和架构文档
- `deploy/`：部署相关占位文件
- `scripts/`：本地辅助脚本

## 当前已实现范围

当前版本已经打通一条最小闭环：

- 同步 `Cloudera Manager` 告警配置
- 从数据库读取事件列表
- 查看事件详情
- 读取诊断结果
- 人工创建诊断任务
- 展示动作建议、审批、执行和复盘测试闭环
- 维护 `CM / 日志 / JMX / 脚本` 数据源配置

后端使用 `Spring Data JPA` 持久化事件和诊断数据。默认可用本地 `H2` 启动，也支持切换到 `PostgreSQL`。
前端已经移除本地 mock 回退逻辑，后端不可用时会直接显示明确错误态或空态。

## 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认接口地址：

```text
http://localhost:8080
```

默认本地数据库为：

```text
backend/data/hadoop-guardian-live
```

如果使用 PostgreSQL：

```bash
cd backend
set SPRING_PROFILES_ACTIVE=postgres
set GUARDIAN_DB_URL=jdbc:postgresql://localhost:5432/guardian
set GUARDIAN_DB_USERNAME=guardian
set GUARDIAN_DB_PASSWORD=guardian
mvn spring-boot:run
```

## 启用 Cloudera Manager 接入

```bash
cd backend
set GUARDIAN_CM_ENABLED=true
set GUARDIAN_CM_BASE_URL=http://your-cm-host:7180
set GUARDIAN_CM_API_VERSION=v51
set GUARDIAN_CM_USERNAME=admin
set GUARDIAN_CM_PASSWORD=your-password
set GUARDIAN_CM_CLUSTER_NAME=your-cluster-name
mvn spring-boot:run
```

然后调用：

```bash
curl -X POST http://localhost:8080/api/integrations/cloudera-manager/sync-alerts
```

## 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端地址：

```text
http://localhost:5173
```

## 本地脚本

项目根目录下提供了 PowerShell 辅助脚本：

```powershell
cd D:\hadoop-guardian
.\scripts\start-backend.ps1 -Rebuild
.\scripts\stop-backend.ps1
.\scripts\status.ps1
.\scripts\reset-demo-data.ps1
```

说明：

- `start-backend.ps1`：重新打包并以 `postgres` profile 启动后端
- `stop-backend.ps1`：停止 `8080` 上的后端进程
- `status.ps1`：查看前端、后端、PostgreSQL 的本地状态
- `reset-demo-data.ps1`：清空并重新导入干净的 UTF-8 演示数据

## 下一步重点

1. 按官方 `Cloudera Manager v51` `events` 接口重写同步逻辑。
2. 用规则诊断器替换当前模板化诊断，优先 `YARN` 和 `HDFS`。
3. 把审批、执行、审计链路从测试闭环推进到真实可执行闭环。
4. 补齐认证、权限控制和密码安全存储。
