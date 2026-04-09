type SeverityBadgeProps = {
  value: string;
};

const labelMap: Record<string, string> = {
  CRITICAL: "严重",
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
  OPEN: "待处理",
  DIAGNOSING: "诊断中",
  CLOSED: "已关闭"
};

export function SeverityBadge({ value }: SeverityBadgeProps) {
  return <span className={`badge badge-${value.toLowerCase()}`}>{labelMap[value] ?? value}</span>;
}
