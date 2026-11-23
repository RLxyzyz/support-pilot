import { useEffect, useState } from 'react';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import type {
  SimilarityConfig,
  L0Config,
  L0CategoryConfig,
  L0ReportItem,
  HealthConfig,
  EvalCase,
  EvalCluster,
  EvalRunResult,
  StormItem
} from '@/types';

/**
 * V2.3 智能服务配置：相似检测阈值、L0 自助应答白名单、客户健康分权重、
 * AI 评测用例与回归，以及故障风暴处置入口。
 */
export default function SmartSettings() {
  const { showToast } = useToast();

  const [similarity, setSimilarity] = useState<SimilarityConfig | null>(null);
  const [l0, setL0] = useState<L0Config | null>(null);
  const [l0Items, setL0Items] = useState<L0CategoryConfig[]>([]);
  const [newCategory, setNewCategory] = useState('');
  const [confirmL0, setConfirmL0] = useState(false);
  const [health, setHealth] = useState<HealthConfig | null>(null);
  const [cases, setCases] = useState<EvalCase[]>([]);
  const [clusters, setClusters] = useState<EvalCluster[]>([]);
  const [runResult, setRunResult] = useState<EvalRunResult | null>(null);
  const [l0Report, setL0Report] = useState<L0ReportItem[]>([]);
  const [storms, setStorms] = useState<StormItem[]>([]);
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);

  const loadAll = () => {
    api.getSimilarityConfig().then(setSimilarity).catch(() => {});
    api
      .getL0Config()
      .then((c) => {
        setL0(c);
        setL0Items(c.categories ?? []);
      })
      .catch(() => {});
    api.getHealthConfig().then(setHealth).catch(() => {});
    api.getEvalCases().then(setCases).catch(() => {});
    api.getEvalClusters().then(setClusters).catch(() => {});
    api.getL0Report().then(setL0Report).catch(() => {});
    api.getActiveStorms().then(setStorms).catch(() => {});
  };

  useEffect(() => {
    loadAll();
  }, []);

  const saveSimilarity = async () => {
    if (!similarity) return;
    setSaving(true);
    try {
      const r = await api.updateSimilarityConfig({
        threshold: similarity.threshold,
        enabled: similarity.enabled,
        windowDays: similarity.windowDays
      });
      setSimilarity(r);
      showToast('相似检测配置已保存');
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const addCategory = () => {
    const c = newCategory.trim();
    if (!c) return;
    if (l0Items.some((x) => x.category === c)) {
      showToast('该分类已存在');
      return;
    }
    setL0Items([...l0Items, { category: c, enabled: true, threshold: 0.9 }]);
    setNewCategory('');
  };

  const saveL0 = async () => {
    setSaving(true);
    try {
      const r = await api.updateL0Config(confirmL0, l0Items);
      setL0(r);
      setL0Items(r.categories ?? []);
      setConfirmL0(false);
      showToast('L0 配置已保存');
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const saveHealth = async () => {
    if (!health) return;
    setSaving(true);
    try {
      const r = await api.updateHealthConfig(health);
      setHealth(r);
      showToast('健康分权重已保存');
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const runEval = async () => {
    setRunning(true);
    try {
      const r = await api.runEval();
      setRunResult(r);
      showToast(`评测完成：通过率 ${Math.round(r.passRate * 100)}%`);
    } catch (e) {
      showToast('评测失败：' + (e as Error).message);
    } finally {
      setRunning(false);
    }
  };

  const handleStorm = async (id: number, action: 'upgrade' | 'recover' | 'dismiss') => {
    try {
      if (action === 'upgrade') {
        const r = await api.upgradeStorm(id);
        showToast(`已创建事件单 ${r.eventTicketId}，关联 ${r.related} 单`);
      } else if (action === 'recover') {
        await api.recoverStorm(id);
        showToast('风暴已恢复');
      } else {
        await api.dismissStorm(id);
        showToast('风暴已忽略');
      }
      loadAll();
    } catch (e) {
      showToast('操作失败：' + (e as Error).message);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1 className="page-title">智能服务配置</h1>
          <div className="page-sub">
            相似归组 · L0 自助应答 · 客户健康分 · AI 评测回归
          </div>
        </div>
        <button className="btn ghost" onClick={loadAll}>
          刷新
        </button>
      </div>

      {/* 故障风暴 */}
      <div className="card" style={{ marginBottom: 12 }}>
        <div className="panel-head">
          <span className="panel-title">故障风暴</span>
          <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--muted-2)' }}>
            活跃 {storms.length} 个
          </span>
        </div>
        <div className="panel-body">
          {storms.length === 0 ? (
            <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>当前无活跃风暴</div>
          ) : (
            storms.map((s) => (
              <div
                key={s.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '8px 0',
                  borderBottom: '1px solid var(--line, #eef2f7)'
                }}
              >
                <b>{s.category}</b>
                <span style={{ fontSize: 12, color: 'var(--muted-2)' }}>
                  {s.count} 单 / {s.windowMinutes} 分钟
                </span>
                {s.eventTicketId && <span className="pill soft">事件单 {s.eventTicketId}</span>}
                <span className="spacer" style={{ flex: 1 }}></span>
                {!s.eventTicketId && (
                  <button className="btn primary sm" onClick={() => handleStorm(s.id, 'upgrade')}>
                    升级为事件单
                  </button>
                )}
                <button className="btn sm" onClick={() => handleStorm(s.id, 'recover')}>
                  恢复
                </button>
                <button className="btn ghost sm" onClick={() => handleStorm(s.id, 'dismiss')}>
                  忽略
                </button>
              </div>
            ))
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        {/* 相似检测 */}
        <div className="card">
          <div className="panel-head">
            <span className="panel-title">相似工单检测</span>
          </div>
          <div className="panel-body">
            {!similarity ? (
              <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>加载中…</div>
            ) : (
              <>
                <div className="field">
                  <label>启用检测</label>
                  <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13 }}>
                    <input
                      type="checkbox"
                      checked={similarity.enabled}
                      onChange={(e) => setSimilarity({ ...similarity, enabled: e.target.checked })}
                    />
                    建单时自动检测相似工单
                  </label>
                </div>
                <div className="field">
                  <label>相似度阈值（0.5 - 1.0）</label>
                  <input
                    className="input"
                    type="number"
                    min={0.5}
                    max={1}
                    step={0.01}
                    value={similarity.threshold}
                    onChange={(e) =>
                      setSimilarity({ ...similarity, threshold: Number(e.target.value) })
                    }
                  />
                </div>
                <div className="field">
                  <label>检测窗口（天）</label>
                  <input
                    className="input"
                    type="number"
                    min={1}
                    max={365}
                    value={similarity.windowDays}
                    onChange={(e) =>
                      setSimilarity({ ...similarity, windowDays: Number(e.target.value) })
                    }
                  />
                </div>
                <button className="btn primary sm" onClick={saveSimilarity} disabled={saving}>
                  保存
                </button>
              </>
            )}
          </div>
        </div>

        {/* 健康分权重 */}
        <div className="card">
          <div className="panel-head">
            <span className="panel-title">客户健康分权重</span>
          </div>
          <div className="panel-body">
            {!health ? (
              <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>加载中…</div>
            ) : (
              <>
                {(
                  [
                    ['freqWeight', '工单频率'],
                    ['slaWeight', 'SLA 违约'],
                    ['csatWeight', 'CSAT'],
                    ['sentimentWeight', '负面情绪']
                  ] as const
                ).map(([key, label]) => (
                  <div className="field" key={key}>
                    <label>{label}权重（0 - 100）</label>
                    <input
                      className="input"
                      type="number"
                      min={0}
                      max={100}
                      value={health[key]}
                      onChange={(e) => setHealth({ ...health, [key]: Number(e.target.value) })}
                    />
                  </div>
                ))}
                <button className="btn primary sm" onClick={saveHealth} disabled={saving}>
                  保存
                </button>
              </>
            )}
          </div>
        </div>
      </div>

      {/* L0 白名单 */}
      <div className="card" style={{ marginTop: 12 }}>
        <div className="panel-head">
          <span className="panel-title">L0 自助应答白名单</span>
          {l0 && (
            <span className="pill soft" style={{ marginLeft: 'auto' }}>
              {l0.enabled ? '已启用' : '未启用'}
            </span>
          )}
        </div>
        <div className="panel-body">
          <div className="table-wrap" style={{ marginBottom: 10 }}>
            <table>
              <thead>
                <tr>
                  <th>分类</th>
                  <th style={{ width: 120 }}>启用</th>
                  <th style={{ width: 160 }}>置信度阈值</th>
                  <th style={{ width: 70 }}></th>
                </tr>
              </thead>
              <tbody>
                {l0Items.length === 0 ? (
                  <tr>
                    <td colSpan={4} style={{ color: 'var(--muted-2)' }}>
                      暂无白名单分类
                    </td>
                  </tr>
                ) : (
                  l0Items.map((item, idx) => (
                    <tr key={item.category}>
                      <td>{item.category}</td>
                      <td>
                        <input
                          type="checkbox"
                          checked={item.enabled}
                          onChange={(e) => {
                            const next = [...l0Items];
                            next[idx] = { ...item, enabled: e.target.checked };
                            setL0Items(next);
                          }}
                        />
                      </td>
                      <td>
                        <input
                          className="input"
                          type="number"
                          min={0.5}
                          max={1}
                          step={0.01}
                          value={item.threshold}
                          onChange={(e) => {
                            const next = [...l0Items];
                            next[idx] = { ...item, threshold: Number(e.target.value) };
                            setL0Items(next);
                          }}
                        />
                      </td>
                      <td>
                        <button
                          className="btn ghost sm"
                          onClick={() => setL0Items(l0Items.filter((_, i) => i !== idx))}
                        >
                          删除
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              className="input"
              style={{ maxWidth: 220 }}
              placeholder="新增分类，如：技术故障"
              value={newCategory}
              onChange={(e) => setNewCategory(e.target.value)}
            />
            <button className="btn sm" onClick={addCategory}>
              添加分类
            </button>
            <label style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12 }}>
              <input
                type="checkbox"
                checked={confirmL0}
                onChange={(e) => setConfirmL0(e.target.checked)}
              />
              我确认开启 L0 自动应答（高风险）
            </label>
            <button className="btn primary sm" onClick={saveL0} disabled={saving}>
              保存 L0 配置
            </button>
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
        {/* 评测回归 */}
        <div className="card">
          <div className="panel-head">
            <span className="panel-title">AI 评测回归</span>
            <button
              className="btn primary sm"
              style={{ marginLeft: 'auto' }}
              onClick={runEval}
              disabled={running}
            >
              {running ? '评测中…' : '执行回归'}
            </button>
          </div>
          <div className="panel-body">
            <div style={{ fontSize: 12, marginBottom: 8 }}>
              用例总数：<b>{cases.length}</b>
              {cases.length < 30 && (
                <span style={{ color: '#b45309' }}>（至少 30 条才能执行回归）</span>
              )}
            </div>
            {runResult && (
              <div
                style={{
                  padding: 10,
                  borderRadius: 8,
                  background: '#f8fafc',
                  border: '1px solid #eef2f7',
                  fontSize: 12,
                  marginBottom: 8
                }}
              >
                <div>
                  版本 {runResult.versionLabel} · 通过 {runResult.passed}/{runResult.total} · 通过率{' '}
                  {Math.round(runResult.passRate * 100)}%
                </div>
                {runResult.prevPassRate != null && (
                  <div style={{ color: 'var(--muted-2)' }}>
                    上次 {Math.round(runResult.prevPassRate * 100)}%
                    {runResult.delta != null
                      ? ` · 变化 ${runResult.delta >= 0 ? '+' : ''}${Math.round(runResult.delta * 100)}%`
                      : ''}
                  </div>
                )}
              </div>
            )}
            <div style={{ fontSize: 12, color: 'var(--muted-2)', marginBottom: 6 }}>低分聚类</div>
            {clusters.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无驳回数据</div>
            ) : (
              clusters.map((c) => (
                <div key={c.reason} style={{ display: 'flex', gap: 8, fontSize: 12 }}>
                  <span style={{ flex: 1 }}>{c.reason}</span>
                  <span>{c.count} 条</span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* L0 报表 */}
        <div className="card">
          <div className="panel-head">
            <span className="panel-title">L0 效果报表</span>
          </div>
          <div className="panel-body">
            {l0Report.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无 L0 数据</div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>分类</th>
                      <th>总量</th>
                      <th>解决率</th>
                      <th>误关率</th>
                      <th>转人工率</th>
                    </tr>
                  </thead>
                  <tbody>
                    {l0Report.map((r) => (
                      <tr key={r.category}>
                        <td>{r.category}</td>
                        <td>{r.total}</td>
                        <td>{Math.round(r.resolveRate * 100)}%</td>
                        <td>{Math.round(r.miscloseRate * 100)}%</td>
                        <td>{Math.round(r.transferRate * 100)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 评测用例列表 */}
      <div className="card" style={{ marginTop: 12 }}>
        <div className="panel-head">
          <span className="panel-title">评测用例</span>
          <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--muted-2)' }}>
            共 {cases.length} 条
          </span>
        </div>
        <div className="panel-body">
          {cases.length === 0 ? (
            <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>
              暂无用例，可在工单详情采纳/驳回草稿时沉淀
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 70 }}>ID</th>
                    <th>问题</th>
                    <th style={{ width: 130 }}>驳回原因</th>
                    <th style={{ width: 140 }}>期望要点</th>
                  </tr>
                </thead>
                <tbody>
                  {cases.slice(0, 50).map((c) => (
                    <tr key={c.id}>
                      <td>#{c.id}</td>
                      <td>{c.question}</td>
                      <td>{c.rejectReason || '—'}</td>
                      <td
                        style={{
                          maxWidth: 260,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        {c.expectedPoints || '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
