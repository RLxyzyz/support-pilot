import { useEffect, useState } from 'react';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import type { GitHubSyncConfig } from '@/types';

/**
 * GitHub Issue 同步配置页（智能服务 → 同步配置）。
 *
 * <p>可配置目标仓库、指定 Issue、单次同步条数、每条 Issue 的评论条数（全部/指定）
 * 与访问 Token，并支持立即同步；保存后定时同步与启动同步都会按此配置执行。
 */
export default function SyncSettings() {
  const { showToast } = useToast();
  const [cfg, setCfg] = useState<GitHubSyncConfig | null>(null);
  const [token, setToken] = useState('');
  const [allComments, setAllComments] = useState(false);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const load = () => {
    api
      .getGitHubSyncConfig()
      .then((c) => {
        setCfg(c);
        setAllComments(c.maxComments === 0);
      })
      .catch((e) => showToast('加载同步配置失败：' + (e as Error).message));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = async () => {
    if (!cfg) return;
    setSaving(true);
    try {
      const r = await api.updateGitHubSyncConfig({
        enabled: cfg.enabled,
        owner: cfg.owner.trim(),
        repo: cfg.repo.trim(),
        issueNumber: cfg.issueNumber ?? null,
        maxIssues: cfg.maxIssues,
        maxComments: allComments ? 0 : cfg.maxComments,
        token: token.trim() || undefined
      });
      setCfg(r);
      setToken('');
      setAllComments(r.maxComments === 0);
      showToast('同步配置已保存');
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const runNow = async () => {
    setSyncing(true);
    try {
      const r = await api.runGitHubSync();
      showToast(r.message || '同步完成');
      load();
    } catch (e) {
      showToast('同步失败：' + (e as Error).message);
    } finally {
      setSyncing(false);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title">同步配置</h1>
          <div className="page-sub">
            GitHub Issue → 工单：仓库、指定 Issue、同步条数与评论条数
          </div>
        </div>
        <button className="btn ghost" onClick={load}>
          刷新
        </button>
      </div>

      <div className="card">
        <div className="panel-head">
          <span className="panel-title">GitHub Issue 同步</span>
          <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--muted-2)' }}>
            {cfg?.lastSyncAt
              ? `最近同步 ${cfg.lastSyncAt}${cfg.lastSyncResult ? ' · ' + cfg.lastSyncResult : ''}`
              : '尚未同步'}
          </span>
        </div>
        <div className="panel-body">
          {!cfg ? (
            <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>加载中…</div>
          ) : (
            <>
              <div className="field">
                <label>启用同步</label>
                <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
                  <input
                    type="checkbox"
                    checked={cfg.enabled}
                    onChange={(e) => setCfg({ ...cfg, enabled: e.target.checked })}
                  />
                  定时（每小时）与启动时自动拉取 Issue
                </label>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div className="field">
                  <label>仓库所有者（owner）</label>
                  <input
                    className="input"
                    placeholder="如 spring-projects"
                    value={cfg.owner}
                    onChange={(e) => setCfg({ ...cfg, owner: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label>仓库名称（repo）</label>
                  <input
                    className="input"
                    placeholder="如 spring-boot"
                    value={cfg.repo}
                    onChange={(e) => setCfg({ ...cfg, repo: e.target.value })}
                  />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div className="field">
                  <label>指定 Issue 编号（留空 = 同步最近多条）</label>
                  <input
                    className="input"
                    type="number"
                    min={1}
                    placeholder="如 42100"
                    value={cfg.issueNumber ?? ''}
                    onChange={(e) =>
                      setCfg({ ...cfg, issueNumber: e.target.value ? Number(e.target.value) : null })
                    }
                  />
                  <div style={{ fontSize: 11, color: 'var(--muted-2)', marginTop: 4 }}>
                    填写后只同步这一条 Issue（含其评论）
                  </div>
                </div>
                <div className="field">
                  <label>单次同步条数（1 - 100）</label>
                  <input
                    className="input"
                    type="number"
                    min={1}
                    max={100}
                    disabled={cfg.issueNumber !== null && cfg.issueNumber !== undefined}
                    value={cfg.maxIssues}
                    onChange={(e) => setCfg({ ...cfg, maxIssues: Number(e.target.value) })}
                  />
                  <div style={{ fontSize: 11, color: 'var(--muted-2)', marginTop: 4 }}>
                    按 Issue 更新时间倒序；指定 Issue 时此项不生效
                  </div>
                </div>
              </div>

              <div className="field">
                <label>每条 Issue 的评论条数</label>
                <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
                  <input
                    type="checkbox"
                    checked={allComments}
                    onChange={(e) => {
                      setAllComments(e.target.checked);
                      if (e.target.checked) setCfg({ ...cfg, maxComments: 0 });
                    }}
                  />
                  拉取全部评论
                </label>
                {!allComments && (
                  <input
                    className="input"
                    type="number"
                    min={1}
                    max={1000}
                    value={cfg.maxComments || 1}
                    onChange={(e) => setCfg({ ...cfg, maxComments: Number(e.target.value) })}
                  />
                )}
              </div>

              <div className="field">
                <label>访问 Token（可选）</label>
                <input
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  placeholder={
                    cfg.hasToken
                      ? `已配置 ${cfg.tokenHint ?? ''}，留空表示不修改`
                      : '留空走匿名 API（60 次/小时），建议填写以提高限额'
                  }
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn primary" disabled={saving} onClick={save}>
                  {saving ? '保存中…' : '保存配置'}
                </button>
                <button className="btn" disabled={syncing || !cfg.enabled} onClick={runNow}>
                  {syncing ? '同步中…' : '立即同步'}
                </button>
              </div>

              <div style={{ marginTop: 12, fontSize: 12, color: 'var(--muted-2)', lineHeight: 1.8 }}>
                同步规则：按 source + source_ref 幂等去重；GitHub 已关闭的 Issue 会置为已解决，
                但不回退人工已解决；已存在工单只增量追加新评论，不覆盖标题、正文与优先级。
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}
