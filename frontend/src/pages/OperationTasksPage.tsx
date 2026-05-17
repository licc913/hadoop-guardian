import { useEffect, useState } from "react";
import { LoadingButton } from "../components/LoadingButton";
import { getLlmCallRecords, getOperationTasks } from "../lib/api";
import type { LlmCallRecord, OperationTaskStatusResponse } from "../lib/types";

function formatTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function formatDuration(ms?: number | null) {
  if (ms == null || ms < 0) return "-";
  if (ms < 1000) return `${ms}ms`;
  return `${Math.round(ms / 1000)}s`;
}

function statusLabel(status: string) {
  if (status === "RUNNING") return "运行中";
  if (status === "PENDING") return "等待中";
  if (status === "COMPLETED") return "已完成";
  if (status === "FAILED") return "失败";
  return status || "-";
}

function taskTypeLabel(taskType: string) {
  if (taskType === "CM_LOG_COLLECTION") return "CM 服务日志采集";
  if (taskType === "CLUSTER_INSPECTION") return "巡检报告";
  if (taskType === "PARAMETER_OPTIMIZATION") return "参数优化";
  return taskType;
}

function featureLabel(feature: string) {
  if (feature === "DIAGNOSIS") return "事件诊断";
  if (feature === "INSPECTION") return "巡检报告";
  if (feature === "SQL_OPTIMIZATION") return "SQL 优化";
  if (feature === "PARAMETER_OPTIMIZATION") return "参数优化";
  return feature || "-";
}

export function OperationTasksPage() {
  const [data, setData] = useState<OperationTaskStatusResponse | null>(null);
  const [llmCalls, setLlmCalls] = useState<LlmCallRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try {
      const [taskData, callData] = await Promise.all([
        getOperationTasks(),
        getLlmCallRecords(12).catch(() => [] as LlmCallRecord[])
      ]);
      setData(taskData);
      setLlmCalls(callData);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "任务状态加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 15000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">任务状态中心</p>
          <h2>统一查看采集、巡检、参数优化和大模型调用状态</h2>
          <p className="lead">
            这里用于查看后台长任务是否正在运行、是否失败，以及大模型调用是否超时或返回异常。
          </p>
          <div className="detail-actions">
            <LoadingButton className="primary-button" loading={loading} loadingText="正在刷新任务状态" onClick={() => void load()}>
              刷新状态
            </LoadingButton>
          </div>
        </div>
        <div className="hero-side validation-grid">
          <div className="status-card">
            <span className="status-kicker">运行中</span>
            <strong>{data?.runningCount ?? 0}</strong>
            <div className="status-list">
              <span>{`失败：${data?.failedCount ?? 0}`}</span>
              <span>{`最近任务：${data?.recentCount ?? 0}`}</span>
            </div>
          </div>
          <div className="status-card warm-card">
            <span className="status-kicker">刷新时间</span>
            <strong>{formatTime(data?.generatedAt)}</strong>
            {error ? <p className="compact-lead">{error}</p> : null}
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">最近任务</p>
            <h3>后台任务记录</h3>
          </div>
        </div>
        {!data || data.recentTasks.length === 0 ? (
          <div className="empty-state">暂无后台任务记录。</div>
        ) : (
          <div className="table-shell">
            <table className="detail-table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>状态</th>
                  <th>标题</th>
                  <th>消息</th>
                  <th>开始时间</th>
                  <th>更新时间</th>
                  <th>耗时</th>
                </tr>
              </thead>
              <tbody>
                {data.recentTasks.map((task) => (
                  <tr key={`${task.taskType}-${task.taskId}`}>
                    <td>{taskTypeLabel(task.taskType)}</td>
                    <td>{statusLabel(task.status)}</td>
                    <td>{task.title}</td>
                    <td>{task.message}</td>
                    <td>{formatTime(task.startedAt)}</td>
                    <td>{formatTime(task.updatedAt)}</td>
                    <td>{formatDuration(task.durationMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">大模型调用</p>
            <h3>最近模型请求记录</h3>
          </div>
        </div>
        {llmCalls.length === 0 ? (
          <div className="empty-state">暂无大模型调用记录。</div>
        ) : (
          <div className="table-shell">
            <table className="detail-table">
              <thead>
                <tr>
                  <th>功能</th>
                  <th>状态</th>
                  <th>模型</th>
                  <th>输入字符</th>
                  <th>返回字符</th>
                  <th>耗时</th>
                  <th>创建时间</th>
                  <th>错误信息</th>
                </tr>
              </thead>
              <tbody>
                {llmCalls.map((call) => (
                  <tr key={call.id}>
                    <td>{featureLabel(call.feature)}</td>
                    <td>{statusLabel(call.status)}</td>
                    <td>{call.model || "-"}</td>
                    <td>{call.promptChars}</td>
                    <td>{call.responseChars}</td>
                    <td>{formatDuration(call.durationMs)}</td>
                    <td>{formatTime(call.createdAt)}</td>
                    <td>{call.errorMessage || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
