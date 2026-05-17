import type { Diagnosis } from "../lib/types";

type DiagnosisCardProps = {
  diagnosis: Diagnosis;
};

function formatTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

export function DiagnosisCard({ diagnosis }: DiagnosisCardProps) {
  const recommendations = diagnosis.recommendations ?? [];
  const followUps = diagnosis.followUps ?? [];

  return (
    <article className="panel diagnosis-card">
      <div className="panel-head">
        <div>
          <p className="eyebrow">诊断记录</p>
          <h3>{diagnosis.subsystem}</h3>
        </div>
        <div className="confidence-block">
          <span className="confidence-label">置信度</span>
          <strong className="confidence-value">{Math.round(diagnosis.confidence * 100)}%</strong>
        </div>
      </div>

      <div className="inline-metadata">
        <span>{`影响等级：${diagnosis.impactLevel}`}</span>
        <span>{`生成时间：${formatTime(diagnosis.createdAt)}`}</span>
      </div>

      <p className="lead">{diagnosis.rootCause}</p>

      <div className="two-column-section">
        <section className="subpanel">
          <h4>建议项</h4>
          {recommendations.length === 0 ? (
            <div className="empty-state">当前没有建议项。</div>
          ) : (
            <ul className="list">
              {recommendations.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          )}
        </section>

        <section className="subpanel">
          <h4>后续跟进</h4>
          {followUps.length === 0 ? (
            <div className="empty-state">当前没有后续跟进项。</div>
          ) : (
            <ul className="list">
              {followUps.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </article>
  );
}
