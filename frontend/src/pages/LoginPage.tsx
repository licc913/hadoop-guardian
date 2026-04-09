import { useState } from "react";
import { login } from "../lib/api";
import type { CurrentUser } from "../lib/types";

type LoginPageProps = {
  onLogin: (user: CurrentUser) => void;
};

export function LoginPage({ onLogin }: LoginPageProps) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const user = await login(username, password);
      onLogin(user);
    } catch {
      setError("登录失败，请检查用户名和密码。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-shell">
      <section className="login-panel">
        <div className="login-grid">
          <div className="login-copy">
            <p className="eyebrow">安全接入</p>
            <h1>登录 Hadoop Guardian</h1>
            <p className="lead">
              登录后可按当前角色查看事件、发起诊断任务，并管理平台的集成接入与采集配置。
            </p>

            <div className="login-callout">
              <span className="status-kicker">值班控制台</span>
              <strong>把集群遥测、事件研判和受控响应收拢到一个工作台中。</strong>
            </div>

            <div className="subpanel">
              <strong>默认开发账号</strong>
              <ul className="list">
                <li>管理员：`admin / admin123`</li>
                <li>运维人员：`operator / operator123`</li>
              </ul>
              <p className="compact-lead">
                如果不只是本地开发环境，请先通过环境变量覆盖默认账号和签名密钥。
              </p>
            </div>
          </div>

          <form className="login-form stack-lg" onSubmit={handleSubmit}>
            <label>
              <span>用户名</span>
              <input value={username} onChange={(event) => setUsername(event.target.value)} />
            </label>

            <label>
              <span>密码</span>
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
            </label>

            <button className="primary-button" disabled={submitting} type="submit">
              {submitting ? "登录中..." : "登录"}
            </button>

            {error ? <div className="error-message">{error}</div> : null}
          </form>
        </div>
      </section>
    </div>
  );
}
