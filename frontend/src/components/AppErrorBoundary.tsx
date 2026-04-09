import type { ReactNode } from "react";
import { Component } from "react";

type AppErrorBoundaryProps = {
  children: ReactNode;
};

type AppErrorBoundaryState = {
  hasError: boolean;
};

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = {
    hasError: false
  };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.error("frontend render error", error);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="panel empty-state">
          页面渲染失败。请先刷新页面；如果仍然没有内容，请返回首页后重试。
        </div>
      );
    }

    return this.props.children;
  }
}
