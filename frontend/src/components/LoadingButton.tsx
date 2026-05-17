import type { ButtonHTMLAttributes, ReactNode } from "react";

type LoadingButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  loading?: boolean;
  loadingText?: ReactNode;
};

export function LoadingButton({
  children,
  className = "button",
  disabled,
  loading = false,
  loadingText,
  type = "button",
  ...props
}: LoadingButtonProps) {
  const busyClass = loading ? " is-busy" : "";

  return (
    <button
      {...props}
      aria-busy={loading}
      className={`${className}${busyClass}`}
      disabled={disabled || loading}
      type={type}
    >
      {loading ? (
        <span className="loading-button-content">
          <span className="loading-button-pulse" aria-hidden="true">
            <span />
            <span />
            <span />
          </span>
          <span>{loadingText ?? children}</span>
        </span>
      ) : (
        children
      )}
    </button>
  );
}
