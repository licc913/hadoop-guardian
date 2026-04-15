import { useEffect, useMemo, useState } from "react";
import { askLlmQuestion } from "../lib/api";
import type { LlmChatMessage } from "../lib/types";

type ChatConversation = {
  id: string;
  title: string;
  updatedAt: string;
  messages: LlmChatMessage[];
};

const STORAGE_KEY = "guardian-llm-conversations";
const EMPTY_TITLE = "新建会话";
const EMPTY_PREVIEW = "这里会保留本地会话记录，可继续追问。";

function createConversation(): ChatConversation {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    title: EMPTY_TITLE,
    updatedAt: new Date().toISOString(),
    messages: []
  };
}

function loadConversations(): ChatConversation[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [createConversation()];
    }
    const parsed = JSON.parse(raw) as ChatConversation[];
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return [createConversation()];
    }
    return parsed;
  } catch {
    return [createConversation()];
  }
}

function saveConversations(conversations: ChatConversation[]) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
}

function buildTitle(messages: LlmChatMessage[]) {
  const firstUserMessage = messages.find((message) => message.role === "user" && message.content.trim());
  if (!firstUserMessage) {
    return EMPTY_TITLE;
  }
  const content = firstUserMessage.content.replace(/\s+/g, " ").trim();
  return content.length <= 20 ? content : `${content.slice(0, 20)}...`;
}

function buildPreview(messages: LlmChatMessage[]) {
  const latestMessage = [...messages].reverse().find((message) => message.content.trim());
  if (!latestMessage) {
    return EMPTY_PREVIEW;
  }
  const content = latestMessage.content.replace(/\s+/g, " ").trim();
  return content.length <= 42 ? content : `${content.slice(0, 42)}...`;
}

export function LlmConsolePage() {
  const [conversations, setConversations] = useState<ChatConversation[]>(() => loadConversations());
  const [activeId, setActiveId] = useState<string>(() => loadConversations()[0].id);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    saveConversations(conversations);
  }, [conversations]);

  useEffect(() => {
    if (!conversations.some((conversation) => conversation.id === activeId)) {
      setActiveId(conversations[0]?.id ?? createConversation().id);
    }
  }, [activeId, conversations]);

  const activeConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === activeId) ?? conversations[0],
    [activeId, conversations]
  );

  const totalMessages = useMemo(
    () => conversations.reduce((count, conversation) => count + conversation.messages.length, 0),
    [conversations]
  );

  function updateConversation(id: string, updater: (conversation: ChatConversation) => ChatConversation) {
    setConversations((current) =>
      current.map((conversation) => (conversation.id === id ? updater(conversation) : conversation))
    );
  }

  function handleNewConversation() {
    const nextConversation = createConversation();
    setConversations((current) => [nextConversation, ...current]);
    setActiveId(nextConversation.id);
    setDraft("");
    setError(null);
  }

  function handleDeleteConversation(id: string) {
    setConversations((current) => {
      const remaining = current.filter((conversation) => conversation.id !== id);
      if (remaining.length === 0) {
        const fallback = createConversation();
        setActiveId(fallback.id);
        return [fallback];
      }
      if (activeId === id) {
        setActiveId(remaining[0].id);
      }
      return remaining;
    });
  }

  async function handleSend() {
    const question = draft.trim();
    if (!question || !activeConversation) {
      return;
    }

    setSending(true);
    setError(null);

    const currentId = activeConversation.id;
    const history = activeConversation.messages;
    const nextUserMessage: LlmChatMessage = {
      role: "user",
      content: question
    };

    updateConversation(currentId, (conversation) => {
      const nextMessages = [...conversation.messages, nextUserMessage];
      return {
        ...conversation,
        messages: nextMessages,
        title: buildTitle(nextMessages),
        updatedAt: new Date().toISOString()
      };
    });

    setDraft("");

    try {
      const response = await askLlmQuestion(question, history);
      const replyMessage: LlmChatMessage = {
        role: "assistant",
        content: response.success ? response.answer : `${response.message}\n${response.answer}`.trim()
      };

      updateConversation(currentId, (conversation) => ({
        ...conversation,
        messages: [...conversation.messages, replyMessage],
        updatedAt: response.respondedAt
      }));
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "大模型问答失败，请稍后重试。";
      setError(message);
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">AI 对话工作台</p>
          <h2>为排障、SQL、脚本和工具开发保留一个可持续追问的独立窗口</h2>
          <p className="lead">
            这里支持新建会话、查看历史会话和连续追问。对话记录保存在当前浏览器中，不会影响事件详情页和其它业务页面。
          </p>
        </div>
        <div className="hero-side validation-grid">
          <div className="status-card">
            <span className="status-kicker">当前会话</span>
            <strong>{conversations.length}</strong>
            <div className="status-list">
              <span>{`选中：${activeConversation?.title ?? EMPTY_TITLE}`}</span>
              <span>{`消息数：${activeConversation?.messages.length ?? 0}`}</span>
            </div>
          </div>
          <div className="status-card warm-card">
            <span className="status-kicker">默认能力</span>
            <ul className="tight-list">
              <li>Hadoop 平台排障与运维处理</li>
              <li>SQL 编写、优化与执行分析</li>
              <li>Shell、Python、Java 工具开发</li>
            </ul>
          </div>
        </div>
      </section>

      <section className="chat-layout">
        <aside className="panel chat-sidebar">
          <div className="panel-head">
            <div>
              <p className="eyebrow">历史会话</p>
              <h3>会话列表</h3>
            </div>
            <button className="secondary-button" type="button" onClick={handleNewConversation}>
              新建会话
            </button>
          </div>

          <div className="chat-summary-grid">
            <div className="metric-pill">{`会话 ${conversations.length} 个`}</div>
            <div className="metric-pill">{`消息 ${totalMessages} 条`}</div>
          </div>

          <div className="stack-sm">
            {conversations.map((conversation) => (
              <article
                key={conversation.id}
                className={`chat-thread ${conversation.id === activeId ? "chat-thread-active" : ""}`}
              >
                <button className="chat-thread-main" type="button" onClick={() => setActiveId(conversation.id)}>
                  <div className="chat-thread-head">
                    <strong>{conversation.title}</strong>
                    <span>{`${conversation.messages.length} 条消息`}</span>
                  </div>
                  <span>{buildPreview(conversation.messages)}</span>
                  <span className="chat-thread-meta">
                    {new Date(conversation.updatedAt).toLocaleString("zh-CN", { hour12: false })}
                  </span>
                </button>
                <button
                  className="chat-thread-delete"
                  type="button"
                  onClick={() => handleDeleteConversation(conversation.id)}
                >
                  删除
                </button>
              </article>
            ))}
          </div>
        </aside>

        <section className="panel chat-main">
          <div className="panel-head">
            <div>
              <p className="eyebrow">当前会话</p>
              <h3>{activeConversation?.title ?? EMPTY_TITLE}</h3>
            </div>
            <div className="inline-metadata">
              <span>{`消息 ${activeConversation?.messages.length ?? 0} 条`}</span>
              <span>{sending ? "模型处理中" : "可继续追问"}</span>
            </div>
          </div>

          <div className="chat-transcript">
            {activeConversation?.messages.length ? (
              activeConversation.messages.map((message, index) => (
                <article key={`${message.role}-${index}`} className={`chat-bubble chat-bubble-${message.role}`}>
                  <span className="chat-role">{message.role === "user" ? "我" : "AI 助手"}</span>
                  <pre className="chat-content">{message.content}</pre>
                </article>
              ))
            ) : (
              <div className="empty-state">
                这里还没有消息。你可以直接输入日志片段、SQL、异常现象或工具开发需求开始提问。
              </div>
            )}
          </div>

          <div className="chat-composer">
            <textarea
              className="app-textarea chat-textarea"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="输入你的问题。支持连续追问，也可以粘贴 SQL、角色日志、接口响应或脚本需求。"
            />
            <div className="detail-actions">
              <button className="primary-button" type="button" disabled={sending} onClick={() => void handleSend()}>
                {sending ? "发送中..." : "发送提问"}
              </button>
            </div>
            {error ? <div className="error-message">{error}</div> : null}
          </div>
        </section>
      </section>
    </div>
  );
}
