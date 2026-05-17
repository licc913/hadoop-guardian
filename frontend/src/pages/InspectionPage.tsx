import { useEffect, useMemo, useState } from "react";
import { LoadingButton } from "../components/LoadingButton";
import {
  createClusterInspectionReport,
  downloadClusterInspectionReportDocx,
  getClusterInspectionReport,
  getClusterInspectionReports
} from "../lib/api";
import type { ClusterInspectionReport } from "../lib/types";

function formatTime(value: string | null) {
  if (!value) {
    return "未记录";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function isGenerating(status: string) {
  const normalized = (status || "").toUpperCase();
  return normalized === "PENDING" || normalized === "RUNNING";
}

function statusLabel(status: string) {
  switch ((status || "").toUpperCase()) {
    case "PENDING":
    case "RUNNING":
      return "生成中";
    case "COMPLETED":
      return "已完成";
    case "FAILED":
      return "失败";
    default:
      return status || "未知";
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  window.URL.revokeObjectURL(url);
}

export function InspectionPage() {
  const [reports, setReports] = useState<ClusterInspectionReport[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedReport, setSelectedReport] = useState<ClusterInspectionReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadReports();
  }, []);

  useEffect(() => {
    if (selectedId == null) {
      setSelectedReport(reports[0] ?? null);
      return;
    }
    const local = reports.find((item) => item.id === selectedId);
    if (local) {
      setSelectedReport(local);
    }
  }, [reports, selectedId]);

  useEffect(() => {
    if (!selectedReport || !isGenerating(selectedReport.status)) {
      return;
    }
    const timer = window.setInterval(() => {
      void refreshReport(selectedReport.id, false);
    }, 4000);
    return () => window.clearInterval(timer);
  }, [selectedReport]);

  async function loadReports() {
    setLoading(true);
    setError(null);
    try {
      const result = await getClusterInspectionReports();
      setReports(result);
      const initialId = selectedId ?? result[0]?.id ?? null;
      setSelectedId(initialId);
      if (initialId != null) {
        const detail = await getClusterInspectionReport(initialId);
        mergeReport(detail);
      } else {
        setSelectedReport(null);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "巡检报告加载失败");
    } finally {
      setLoading(false);
    }
  }

  function mergeReport(report: ClusterInspectionReport) {
    setReports((current) => {
      const next = [report, ...current.filter((item) => item.id !== report.id)];
      next.sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
      return next;
    });
    if (selectedId == null || selectedId === report.id) {
      setSelectedReport(report);
    }
  }

  async function refreshReport(reportId: number, surfaceError = true) {
    try {
      const detail = await getClusterInspectionReport(reportId);
      mergeReport(detail);
      if (!isGenerating(detail.status) && selectedId === reportId) {
        setSelectedReport(detail);
      }
    } catch (cause) {
      if (surfaceError) {
        setError(cause instanceof Error ? cause.message : "巡检报告详情加载失败");
      }
    }
  }

  async function handleSelect(reportId: number) {
    setSelectedId(reportId);
    setError(null);
    await refreshReport(reportId);
  }

  async function handleCreate() {
    setCreating(true);
    setError(null);
    try {
      const created = await createClusterInspectionReport();
      mergeReport(created);
      setSelectedId(created.id);
      setSelectedReport(created);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "巡检报告生成失败");
    } finally {
      setCreating(false);
    }
  }

  async function handleExport() {
    if (!selectedReport || selectedReport.status !== "COMPLETED") {
      return;
    }
    setExporting(true);
    setError(null);
    try {
      const blob = await downloadClusterInspectionReportDocx(selectedReport.id);
      downloadBlob(blob, `${selectedReport.reportTitle}.docx`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "DOCX 导出失败");
    } finally {
      setExporting(false);
    }
  }

  const currentSummary = useMemo(() => {
    if (!selectedReport) {
      return "当前还没有巡检报告。";
    }
    if (isGenerating(selectedReport.status)) {
      return "巡检任务已入队，系统正在分章节调用大模型并汇总 CM、服务日志、JMX 与知识库证据。";
    }
    if (selectedReport.status === "FAILED") {
      return selectedReport.errorMessage || "巡检报告生成失败，请检查后端日志和大模型配置。";
    }
    return selectedReport.summary;
  }, [selectedReport]);

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">集群巡检</p>
          <h2>基于 CM 当前状态、角色日志、JMX、未关闭事件与知识库生成正式巡检报告</h2>
          <p className="lead">
            巡检报告由后端异步生成，前端负责发起任务、轮询状态和导出结果，避免大模型长耗时直接拖慢页面。
          </p>
          <div className="detail-actions">
            <LoadingButton className="primary-button" loading={creating} loadingText="正在提交巡检任务" onClick={() => void handleCreate()}>
              生成巡检报告
            </LoadingButton>
            <LoadingButton
              className="secondary-button"
              disabled={!selectedReport || selectedReport.status !== "COMPLETED" || exporting}
              loading={exporting}
              loadingText="正在导出 DOCX"
              onClick={() => void handleExport()}
            >
              导出 DOCX
            </LoadingButton>
          </div>
        </div>

        <div className="hero-side validation-grid">
          <div className="status-card">
            <span className="status-kicker">巡检历史</span>
            <strong>{reports.length}</strong>
            <div className="status-list">
              <span>{selectedReport ? `当前报告: ${selectedReport.reportTitle}` : "当前报告: 无"}</span>
              <span>{selectedReport ? `状态: ${statusLabel(selectedReport.status)}` : "状态: 未生成"}</span>
            </div>
          </div>
          <div className="status-card warm-card">
            <span className="status-kicker">当前摘要</span>
            <ul className="tight-list">
              <li>{currentSummary}</li>
            </ul>
          </div>
        </div>
      </section>

      {error ? <div className="error-message">{error}</div> : null}

      <section className="chat-layout">
        <aside className="panel chat-sidebar">
          <div className="panel-head">
            <div>
              <p className="eyebrow">巡检历史</p>
              <h3>报告列表</h3>
            </div>
          </div>

          {loading ? (
            <div className="empty-state">正在加载巡检报告...</div>
          ) : reports.length === 0 ? (
            <div className="empty-state">当前还没有巡检报告，先生成一份。</div>
          ) : (
            <div className="stack-sm">
              {reports.map((report) => (
                <article
                  key={report.id}
                  className={`chat-thread ${report.id === (selectedId ?? selectedReport?.id) ? "chat-thread-active" : ""}`}
                >
                  <button className="chat-thread-main" onClick={() => void handleSelect(report.id)} type="button">
                    <div className="chat-thread-head">
                      <strong>{report.reportTitle}</strong>
                      <span>{statusLabel(report.status)}</span>
                    </div>
                    <span>{report.summary}</span>
                    <span className="chat-thread-meta">{formatTime(report.createdAt)}</span>
                  </button>
                </article>
              ))}
            </div>
          )}
        </aside>

        <section className="panel chat-main">
          <div className="panel-head">
            <div>
              <p className="eyebrow">报告详情</p>
              <h3>{selectedReport?.reportTitle ?? "尚未生成巡检报告"}</h3>
            </div>
            <div className="inline-metadata">
              <span>{selectedReport ? `集群 ${selectedReport.clusterName}` : "集群 -"}</span>
              <span>{selectedReport ? `状态 ${statusLabel(selectedReport.status)}` : "状态 -"}</span>
              <span>{selectedReport ? `生成时间 ${formatTime(selectedReport.createdAt)}` : "生成时间 -"}</span>
            </div>
          </div>

          {selectedReport ? (
            <div className="chat-transcript">
              <article className="chat-bubble chat-bubble-assistant">
                <span className="chat-role">巡检摘要</span>
                <pre className="chat-content">{selectedReport.summary}</pre>
              </article>
              {selectedReport.errorMessage ? (
                <article className="chat-bubble chat-bubble-assistant">
                  <span className="chat-role">错误信息</span>
                  <pre className="chat-content">{selectedReport.errorMessage}</pre>
                </article>
              ) : null}
              <article className="chat-bubble chat-bubble-assistant">
                <span className="chat-role">Markdown 报告</span>
                <pre className="chat-content">{selectedReport.markdownContent}</pre>
              </article>
            </div>
          ) : (
            <div className="empty-state">当前没有可展示的巡检报告。</div>
          )}
        </section>
      </section>
    </div>
  );
}
