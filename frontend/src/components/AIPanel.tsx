import { useEffect, useRef, useState } from 'react';
import { api, streamAgent } from '@/api/client';
import { useToast } from '@/components/Toast';
import type { AgentStep } from '@/types';

type Phase = 'idle' | 'running' | 'draft' | 'adopted';

/** 驳回原因预设（与后端 feedback_reason 取值对齐） */
const REJECT_REASONS = [
  { v: 'inaccurate', t: '内容不准' },
  { v: 'incomplete', t: '不完整' },
  { v: 'tone', t: '语气不当' },
  { v: 'no_ref', t: '引用错误' },
  { v: 'other', t: '其它' }
];

interface Props {
  ticketId: string;
  onAdopted: () => void;
}

export default function AIPanel({ ticketId, onAdopted }: Props) {
  const { showToast } = useToast();
  const [phase, setPhase] = useState<Phase>('idle');
  const [thinkingLabel, setThinkingLabel] = useState('');
  const [steps, setSteps] = useState<AgentStep[]>([]);
  const [draftFull, setDraftFull] = useState('');
  const [draftValue, setDraftValue] = useState('');
  const [cites, setCites] = useState<string[]>([]);
  const [cost, setCost] = useState('');
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectComment, setRejectComment] = useState('');
  const [rejecting, setRejecting] = useState(false);
  const closeRef = useRef<(() => void) | null>(null);

  // 切换工单时重置
  useEffect(() => {
    reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticketId]);

  // 组件卸载关闭流
  useEffect(() => () => closeRef.current?.(), []);

  function reset() {
    closeRef.current?.();
    closeRef.current = null;
    setPhase('idle');
    setThinkingLabel('');
    setSteps([]);
    setDraftFull('');
    setDraftValue('');
    setCites([]);
    setCost('');
    setRejectOpen(false);
    setRejectReason('');
    setRejectComment('');
    setRejecting(false);
  }

  // 草稿逐字打字动画
  useEffect(() => {
    if (!draftFull) return;
    setDraftValue('');
    let i = 0;
    const timer = window.setInterval(() => {
      i += 2;
      if (i >= draftFull.length) {
        window.clearInterval(timer);
        setDraftValue(draftFull);
        setPhase('draft');
      } else {
        setDraftValue(draftFull.slice(0, i));
      }
    }, 6);
    return () => window.clearInterval(timer);
  }, [draftFull]);

  function start() {
    if (phase === 'running') return;
    reset();
    setPhase('running');
    const draftId = 'd_' + ticketId;
    closeRef.current = streamAgent(ticketId, {
      onThinking: (label) => setThinkingLabel(label),
      onStep: (step) => setSteps((prev) => [...prev, step]),
      onDraft: ({ content, cost: c, draftId }) => {
        setCost(c);
        setDraftFull(content);
        // 优先用服务端落库返回的 draftId（数字），兜底 d_{ticketId}
        if (draftId) (start as any)._draftId = draftId;
      },
      onCites: (items) => setCites(items),
      onDone: () => {
        closeRef.current = null;
      },
      onError: () => {
        // 流正常结束也会触发 error，忽略
      }
    });
    // 保留 draftId 给采纳/驳回用（闭包）
    (start as any)._draftId = draftId;
  }

  async function adopt() {
    const draftId = (start as any)._draftId || ('d_' + ticketId);
    // 采纳用户最终确认的内容（可能已在草稿框中编辑过），随请求提交给后端落库为回复消息
    const content = (draftValue || draftFull || '').trim();
    if (!content) {
      showToast('草稿内容为空，无法采纳');
      return;
    }
    try {
      const r = await api.approveDraft(draftId, content);
      setPhase('adopted');
      onAdopted();
      showToast(`已回写工单 ${ticketId} · SLA 计时已停止 · 成本 ¥0.028`);
      if (r?.cost) setCost(r.cost);
    } catch (e) {
      showToast('采纳失败：' + (e as Error).message);
    }
  }

  function openReject() {
    setRejectReason('');
    setRejectComment('');
    setRejectOpen(true);
  }

  function cancelReject() {
    setRejectOpen(false);
    setRejectReason('');
    setRejectComment('');
  }

  async function confirmReject() {
    if (!rejectReason) {
      showToast('请选择驳回原因');
      return;
    }
    const draftId = (start as any)._draftId || ('d_' + ticketId);
    setRejecting(true);
    try {
      await api.rejectDraft(draftId, rejectReason, rejectComment.trim());
      showToast('已驳回，原因已记录到评测样本池');
      reset();
    } catch (e) {
      showToast('驳回失败：' + (e as Error).message);
    } finally {
      setRejecting(false);
    }
  }

  const edited = draftValue.trim() !== draftFull.trim();

  return (
    <div className="card ai-panel" style={{ overflow: 'hidden' }}>
      <div className="ai-head">
        <span className="ai-dot"></span>
        <span className="ai-name">AI Copilot</span>
        <span className="ai-sub">工单处理助手</span>
        <span className="ai-model">qwen-max · prompt v1.3</span>
      </div>

      {phase === 'idle' && (
        <div className="ai-empty">
          <div className="ai-empty-ico">✦</div>
          <p>
            将读取工单全文与会话记录，检索官方文档与相似历史工单，
            <br />
            并查询客户订阅与版本信息，生成可审核的回复草稿。
          </p>
          <button className="btn primary" onClick={start}>
            ✦ 交给 AI 处理
          </button>
          <div style={{ fontSize: 10.5, color: 'var(--muted-2)', marginTop: 10 }}>
            生成内容需人工审核后才能发送给客户
          </div>
        </div>
      )}

      {(phase === 'running' || phase === 'draft' || phase === 'adopted') && (
        <>
          {phase === 'running' && (
            <div className="thinking">
              <div className="dots">
                <i></i>
                <i></i>
                <i></i>
              </div>
              <span>{thinkingLabel || '正在读取工单与会话记录…'}</span>
            </div>
          )}
          <div className="ai-steps">
            {steps.map((s, idx) => (
              <div className="step" key={idx}>
                <div className="step-h">
                  <span className={'step-tag ' + s.cls}>{s.tag}</span>
                  <span className="step-time">{s.time}</span>
                </div>
                <div className="step-body" dangerouslySetInnerHTML={{ __html: s.html }} />
              </div>
            ))}
          </div>

          {draftFull && (
            <div className="draft-wrap">
              <div className="draft-label">回复草稿 · 可编辑</div>
              <textarea
                className="draft-area"
                spellCheck={false}
                value={draftValue}
                readOnly={phase === 'adopted'}
                onChange={(e) => setDraftValue(e.target.value)}
                style={phase === 'adopted' ? { background: '#fcfdfe' } : undefined}
              />
              <div className="cites">
                {cites.map((c, i) => (
                  <div
                    className="cite"
                    key={i}
                    onClick={() => showToast('已定位到：' + c.split(' · ')[0])}
                  >
                    <b>[{i + 1}]</b>
                    <span>{c}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {phase === 'draft' &&
            (rejectOpen ? (
              <div className="ai-foot" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 8 }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 12, color: 'var(--muted-2)' }}>驳回原因</span>
                  <select
                    className="input"
                    style={{ height: 28, width: 140 }}
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                  >
                    <option value="">请选择…</option>
                    {REJECT_REASONS.map((r) => (
                      <option key={r.v} value={r.v}>
                        {r.t}
                      </option>
                    ))}
                  </select>
                  <button className="btn sm" onClick={cancelReject} disabled={rejecting}>
                    取消
                  </button>
                  <button className="btn danger sm" onClick={confirmReject} disabled={rejecting}>
                    {rejecting ? '提交中…' : '确认驳回'}
                  </button>
                </div>
                <input
                  className="input"
                  style={{ height: 28 }}
                  placeholder="补充评语（选填，将进入评测样本）"
                  value={rejectComment}
                  onChange={(e) => setRejectComment(e.target.value)}
                />
              </div>
            ) : (
              <div className="ai-foot">
                <button className="btn primary sm" onClick={adopt}>
                  采纳并回复客户
                </button>
                <button className="btn sm" onClick={openReject}>
                  驳回
                </button>
                <div className="spacer"></div>
                <span className="cost">
                  {cost}
                  {edited ? ' · 已修改' : ''}
                </span>
              </div>
            ))}

          {phase === 'adopted' && (
            <div className="ai-foot">
              <span style={{ fontSize: 12, color: 'var(--ok)', fontWeight: 600 }}>✓ 已采纳并回写工单</span>
              <div className="spacer"></div>
              <span className="cost">{cost || '耗时 3 分 12 秒 · ¥0.028'}</span>
            </div>
          )}
        </>
      )}
    </div>
  );
}
