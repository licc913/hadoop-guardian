import { useEffect, useState } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { getCurrentUser, logout } from "./lib/api";
import type { CurrentUser } from "./lib/types";
import { IncidentDetailPage } from "./pages/IncidentDetailPage";
import { IncidentListPage } from "./pages/IncidentListPage";
import { InspectionPage } from "./pages/InspectionPage";
import { LlmConsolePage } from "./pages/LlmConsolePage";
import { LoginPage } from "./pages/LoginPage";
import { OperationTasksPage } from "./pages/OperationTasksPage";
import { ParameterOptimizationPage } from "./pages/ParameterOptimizationPage";
import { SettingsPage } from "./pages/SettingsPage";
import { SqlOptimizationPage } from "./pages/SqlOptimizationPage";

export default function App() {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [authLoading, setAuthLoading] = useState(true);

  useEffect(() => {
    async function loadCurrentUser() {
      try {
        const user = await getCurrentUser();
        setCurrentUser(user);
      } catch {
        setCurrentUser(null);
      } finally {
        setAuthLoading(false);
      }
    }

    void loadCurrentUser();
  }, []);

  if (authLoading) {
    return <div className="shell-loading">正在校验登录状态...</div>;
  }

  if (!currentUser) {
    return <LoginPage onLogin={setCurrentUser} />;
  }

  return (
    <div className="shell">
      <div className="shell-glow shell-glow-left" />
      <div className="shell-glow shell-glow-right" />

      <aside className="sidebar">
        <div className="sidebar-top">
          <div className="brand-lockup">
            <div className="brand-chip">数据平台运维台</div>
            <h1>Hadoop Guardian</h1>
            <p className="sidebar-copy">
              面向 HDFS、YARN、Hive on Tez 与 Impala 的智能运维工作台。平台建立在
              Cloudera Manager 之上，聚焦事件研判、证据采集、影响评估与受控处置。
            </p>
          </div>

          <div className="sidebar-signal">
            <span className="status-kicker">运维总控台</span>
            <strong>以信号为先，以研判为核心，以受控执行为边界。</strong>
          </div>
        </div>

        <nav className="nav">
          <NavLink className="nav-link" to="/">
            <span className="nav-index">01</span>
            <span className="nav-body">
              <span className="nav-title">事件指挥中心</span>
              <span className="nav-copy">查看运行态势，聚合风险信号，并进入事件详情。</span>
            </span>
          </NavLink>

          <NavLink className="nav-link" to="/settings">
            <span className="nav-index">02</span>
            <span className="nav-body">
              <span className="nav-title">集成与采集配置</span>
              <span className="nav-copy">管理数据源接入、连通性校验与同步结果。</span>
            </span>
          </NavLink>

          <NavLink className="nav-link" to="/llm-console">
            <span className="nav-index">03</span>
            <span className="nav-body">
              <span className="nav-title">AI 对话工作台</span>
              <span className="nav-copy">开启独立会话，连续追问 SQL、排障、脚本和工具开发问题。</span>
            </span>
          </NavLink>

          <NavLink className="nav-link" to="/sql-optimization">
            <span className="nav-index">04</span>
            <span className="nav-body">
              <span className="nav-title">SQL 优化中心</span>
              <span className="nav-copy">处理 Impala SQL 与 Hive SQL 优化，支持规则扫描、大模型改写与历史追溯。</span>
            </span>
          </NavLink>

          <NavLink className="nav-link" to="/inspections">
            <span className="nav-index">05</span>
            <span className="nav-body">
              <span className="nav-title">巡检报告中心</span>
              <span className="nav-copy">汇总集群状态、日志、事件与知识证据，生成并导出 DOCX 巡检报告。</span>
            </span>
          </NavLink>

          <NavLink className="nav-link" to="/parameter-optimization">
            <span className="nav-index">06</span>
            <span className="nav-body">
              <span className="nav-title">参数优化中心</span>
              <span className="nav-copy">采集配置、版本、日志与知识依据，输出组件参数优化建议。</span>
            </span>
          </NavLink>

          <NavLink className="nav-link" to="/ops-tasks">
            <span className="nav-index">07</span>
            <span className="nav-body">
              <span className="nav-title">任务状态中心</span>
              <span className="nav-copy">统一查看日志采集、巡检报告和参数优化等后台任务状态。</span>
            </span>
          </NavLink>
        </nav>

        <div className="sidebar-foot stack-md">
          <div className="user-card">
            <span className="status-kicker">当前用户</span>
            <strong>{currentUser.displayName}</strong>
            <div className="inline-metadata">
              <span>{currentUser.username}</span>
              <span>{currentUser.role === "ADMIN" ? "管理员" : "运维人员"}</span>
            </div>
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                logout();
                setCurrentUser(null);
              }}
            >
              退出登录
            </button>
          </div>

          <div>
            <p className="eyebrow">系统边界</p>
            <p className="sidebar-copy">
              该系统不替代 Cloudera Manager 的主机管理、服务生命周期或配置下发能力，只负责诊断、指引、审计与受控编排。
            </p>
          </div>
        </div>
      </aside>

      <main className="content">
        <div className="content-frame">
          <Routes>
            <Route path="/" element={<IncidentListPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/llm-console" element={<LlmConsolePage />} />
            <Route path="/sql-optimization" element={<SqlOptimizationPage />} />
            <Route path="/inspections" element={<InspectionPage />} />
            <Route path="/parameter-optimization" element={<ParameterOptimizationPage />} />
            <Route path="/ops-tasks" element={<OperationTasksPage />} />
            <Route path="/incidents/:incidentId" element={<IncidentDetailPage />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}
