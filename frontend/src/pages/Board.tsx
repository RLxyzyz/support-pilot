import { useEffect, useState } from 'react';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import type { BoardData, OpsNameCount, DraftFeedbackStats, L0ReportItem, EvalAdoption } from '@/types';

type RangeKey = '7' | '30' | 'custom';

/** 本地日期串 → ISO-8601（endOfDay 时取当天 23:59:59） */
function toISO(d: string, endOfDay: boolean): string {
  return new Date(d + (endOfDay ? 'T23:59:59' : 'T00:00:00')).toISOString();
}

function fmtMinutes(min: number): string {
  if (!min) return '—';
  if (min < 60) return `${Math.round(min)} 分钟`;
  const h = Math.floor(min / 60);
  const m = Math.round(min % 60);
  return m ? `${h} 小时 ${m} 分` : `${h} 小时`;
}

function fmtHours(h: number): string {
  if (!h) return '—';
  if (h < 1) return `${Math.round(h * 60)} 分钟`;
  return `${(Math.round(h * 10) / 10).toFixed(1)} 小时`;
}

/** 驳回原因码 → 中文展示 */
function reasonLabel(r: string): string {
  switch (r) {
    case 'inaccurate':
      return '内容不准';
    case 'incomplete':
      return '不完整';
    case 'tone':
      return '语气不当';
    case 'no_ref':
      return '引用错误';
    case 'other':
      return '其它';
    default:
      return r || '未分类';
  }
}

export default function Board() {
  const { showToast } = useToast();
  const [data, setData] = useState<BoardData | null>(null);
  const [range, setRange] = useState<RangeKey>('7');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [loading, setLoading] = useState(false);
  const [fb, setFb] = useState<DraftFeedbackStats | null>(null);
  // V2.3 智能服务指标
  const [l0Report, setL0Report] = useState<L0ReportItem[]>([]);
  const [adoption, setAdoption] = useState<EvalAdoption | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      let days: number | undefined;
      let start: string | undefined;
      let end: string | undefined;
      if (range === '7') days = 7;
      else if (range === '30') days = 30;
      else {
        if (customStart) start = toISO(customStart, false);
        if (customEnd) end = toISO(customEnd, true);
      }
      const d = await api.getMetrics(days, start, end);
      setData(d);
    } catch (e) {
      showToast('加载看板失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range, customStart, customEnd]);

  // 草稿反馈统计为累计口径，挂载时拉一次即可（失败静默）
  useEffect(() => {
    api.getDraftFeedbackStats().then(setFb).catch(() => {});
    api.getL0Report().then(setL0Report).catch(() => {});
    api.getEvalAdoption().then(setAdoption).catch(() => {});
  }, []);

  if (!data) return <div className="empty">加载中…</div>;

  const maxTrend = Math.max(...data.trend.map((t) => t.value), 1);
  const rate = data.adoption.rate;
  const donutBg = `conic-gradient(#2563eb 0 ${rate}%, #e2e8f0 ${rate}% 100%)`;
  const maxMiss = Math.max(...data.misses.map((m) => m.weight), 1);
  const ops = data.ops;
  const fbMax = fb ? Math.max(...fb.reasons.map((x) => x.count), 1) : 1;

  // 导出当前看板数据为 CSV（KPI / 趋势 / 未命中问题 / 运营指标），带 BOM 以兼容 Excel 中文
  const exportCsv = () => {
    const lines: string[] = [];
    lines.push('指标,数值,单位,环比');
    data.kpi.forEach((k) => {
      lines.push([k.label, String(k.value), k.unit ?? '', k.delta ?? ''].map(csvQuote).join(','));
    });
    lines.push('');
    lines.push('日期,新建工单数');
    data.trend.forEach((t) => {
      lines.push([t.day, String(t.value)].map(csvQuote).join(','));
    });
    if (ops) {
      lines.push('');
      lines.push('运营指标,数值');
      lines.push(['SLA 达成率(%)', String(ops.slaRate)].map(csvQuote).join(','));
      lines.push(['平均首次响应(分钟)', String(ops.frtMinutes)].map(csvQuote).join(','));
      lines.push(['平均解决时长(小时)', String(ops.mttrHours)].map(csvQuote).join(','));
      lines.push(['AI 节省时长(分钟)', String(ops.aiSavedMinutes)].map(csvQuote).join(','));
      lines.push(['AI 成本(元)', String(ops.aiCost)].map(csvQuote).join(','));
      lines.push(['知识库命中率(%)', String(ops.kbHitRate)].map(csvQuote).join(','));
      lines.push(['CSAT 平均分', String(ops.csatAvg)].map(csvQuote).join(','));
      lines.push(['CSAT 评价数', String(ops.csatCount)].map(csvQuote).join(','));
    }
    lines.push('');
    lines.push('未命中问题,次数,权重');
    data.misses.forEach((m) => {
      lines.push([m.question, String(m.count), String(m.weight)].map(csvQuote).join(','));
    });
    const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `support-pilot-报表-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast('已导出报表 CSV');
  };

  function csvQuote(v: string) {
    return `"${(v ?? '').replace(/"/g, '""')}"`;
  }

  const rangeLabel = range === '7' ? '近 7 天' : range === '30' ? '近 30 天' : '自定义';

  // V2.3：L0 汇总与采纳率
  const l0Total = l0Report.reduce((s, x) => s + x.total, 0);
  const l0Resolved = l0Report.reduce((s, x) => s + x.resolved, 0);
  const l0Reopened = l0Report.reduce((s, x) => s + x.reopened, 0);
  const l0Transferred = l0Report.reduce((s, x) => s + x.transferred, 0);
  const l0Rate = (a: number, b: number) => (b === 0 ? 0 : Math.round((a / b) * 1000) / 10);

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">效果看板</div>
          <div className="page-sub">{rangeLabel} · 数据每 10 分钟更新</div>
        </div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginRight: 12 }}>
          {(['7', '30', 'custom'] as RangeKey[]).map((r) => (
            <button
              key={r}
              className={'btn sm' + (range === r ? ' primary' : '')}
              onClick={() => setRange(r)}
            >
              {r === '7' ? '近 7 天' : r === '30' ? '近 30 天' : '自定义'}
            </button>
          ))}
        </div>
        <button className="btn" onClick={exportCsv}>
          导出报表
        </button>
      </div>

      {range === 'custom' && (
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', margin: '0 0 12px', fontSize: 12 }}>
          <span style={{ color: 'var(--muted-2)' }}>起始</span>
          <input
            type="date"
            value={customStart}
            onChange={(e) => setCustomStart(e.target.value)}
            className="input"
            style={{ height: 28, width: 150 }}
          />
          <span style={{ color: 'var(--muted-2)' }}>结束</span>
          <input
            type="date"
            value={customEnd}
            onChange={(e) => setCustomEnd(e.target.value)}
            className="input"
            style={{ height: 28, width: 150 }}
          />
          <button className="btn sm" onClick={load} disabled={loading}>
            刷新
          </button>
        </div>
      )}

      <div className="kpi-grid">
        {data.kpi.map((k, i) => (
          <div className="card kpi" key={i}>
            <div className="kpi-label">{k.label}</div>
            <div className="kpi-val">
              {k.value}
              {k.unit && <span>{k.unit}</span>}
            </div>
            <div className={'kpi-delta ' + k.trend}>{k.delta}</div>
          </div>
        ))}
      </div>

      {/* V2.3 M4：L0 自助应答指标 */}
      <div className="panel-head" style={{ marginTop: 16 }}>
        <span className="panel-title">L0 自助应答</span>
        <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 'auto' }}>累计口径</span>
      </div>
      <div className="kpi-grid">
        <div className="card kpi">
          <div className="kpi-label">自助解决率</div>
          <div className="kpi-val">
            {l0Total ? l0Rate(l0Resolved, l0Total) : '—'}
            <span>%</span>
          </div>
          <div className="kpi-delta up">已确认 {l0Resolved} / {l0Total} 单</div>
        </div>
        <div className="card kpi">
          <div className="kpi-label">误关率</div>
          <div className="kpi-val">
            {l0Total ? l0Rate(l0Reopened, l0Total) : '—'}
            <span>%</span>
          </div>
          <div className="kpi-delta">客户重开 {l0Reopened} 单</div>
        </div>
        <div className="card kpi">
          <div className="kpi-label">转人工率</div>
          <div className="kpi-val">
            {l0Total ? l0Rate(l0Transferred, l0Total) : '—'}
            <span>%</span>
          </div>
          <div className="kpi-delta">转人工 {l0Transferred} 单</div>
        </div>
        <div className="card kpi">
          <div className="kpi-label">AI 草稿采纳</div>
          <div className="kpi-val">
            {adoption && adoption.total
              ? Math.round(
                  (adoption.byCategory.reduce((s, x) => s + x.adopted, 0) / adoption.total) * 100
                )
              : '—'}
            <span>%</span>
          </div>
          <div className="kpi-delta up">草稿总数 {adoption?.total ?? 0}</div>
        </div>
      </div>

      {adoption && adoption.byCategory.length > 0 && (
        <div className="card" style={{ marginTop: 12, padding: 14 }}>
          <div className="panel-head" style={{ marginBottom: 8 }}>
            <span className="panel-title">分类采纳率</span>
          </div>
          {adoption.byCategory.map((x) => (
            <div
              key={x.key}
              style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6, fontSize: 12 }}
            >
              <span style={{ width: 120, overflow: 'hidden', textOverflow: 'ellipsis' }}>{x.key}</span>
              <div style={{ flex: 1, height: 8, background: '#eef2f7', borderRadius: 4 }}>
                <div
                  style={{
                    width: `${Math.round(x.adoptionRate * 100)}%`,
                    height: 8,
                    background: '#2563eb',
                    borderRadius: 4
                  }}
                />
              </div>
              <span style={{ width: 90, textAlign: 'right', color: 'var(--muted-2)' }}>
                {Math.round(x.adoptionRate * 100)}% ({x.adopted}/{x.total})
              </span>
            </div>
          ))}
        </div>
      )}

      {ops && (
        <>
          <div className="panel-head" style={{ marginTop: 16 }}>
            <span className="panel-title">运营指标</span>
            <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 'auto' }}>{rangeLabel}</span>
          </div>
          <div className="kpi-grid">
            <div className="card kpi">
              <div className="kpi-label">SLA 达成率</div>
              <div className="kpi-val">
                {ops.resolvedCount ? ops.slaRate : '—'}
                <span>%</span>
              </div>
              <div className="kpi-delta up">范围内已解决 {ops.resolvedCount} 单</div>
            </div>
            <div className="card kpi">
              <div className="kpi-label">平均首次响应 FRT</div>
              <div className="kpi-val">
                {ops.respondedCount ? fmtMinutes(ops.frtMinutes) : '—'}
              </div>
              <div className="kpi-delta up">有响应 {ops.respondedCount} 单</div>
            </div>
            <div className="card kpi">
              <div className="kpi-label">平均解决时长 MTTR</div>
              <div className="kpi-val">
                {ops.resolvedCount ? fmtHours(ops.mttrHours) : '—'}
              </div>
              <div className="kpi-delta up">已解决 {ops.resolvedCount} 单</div>
            </div>
            <div className="card kpi">
              <div className="kpi-label">AI 节省时长</div>
              <div className="kpi-val">
                {ops.aiSavedMinutes ? fmtMinutes(ops.aiSavedMinutes) : '—'}
              </div>
              <div className="kpi-delta up">采纳草稿估算</div>
            </div>
            <div className="card kpi">
              <div className="kpi-label">AI 成本</div>
              <div className="kpi-val">
                ¥{ops.aiCost ? ops.aiCost.toFixed(2) : '0.00'}
              </div>
              <div className="kpi-delta up">范围内 token 用量</div>
            </div>
            <div className="card kpi">
              <div className="kpi-label">知识库命中率</div>
              <div className="kpi-val">
                {ops.kbHitRate ? ops.kbHitRate + '%' : '—'}
              </div>
              <div className="kpi-delta up">RAG 命中率</div>
            </div>
            <div className="card kpi">
              <div className="kpi-label">客户满意度</div>
              <div className="kpi-val">
                {ops.csatCount ? ops.csatAvg.toFixed(1) + ' / 5' : '—'}
              </div>
              <div className="kpi-delta up">{ops.csatCount} 条评价</div>
            </div>
          </div>

          <div className="board-grid">
            <BarPanel title="处理人负载 Top5" items={ops.assigneeLoad} color="#2563eb" />
            <BarPanel title="分类分布" items={ops.categoryDist} color="#059669" />
          </div>

          {fb && (
            <div className="card" style={{ marginTop: 12 }}>
              <div className="panel-head">
                <span className="panel-title">草稿反馈</span>
                <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 'auto' }}>累计</span>
              </div>
              <div className="panel-body">
                <div style={{ display: 'flex', gap: 28, marginBottom: 14, flexWrap: 'wrap' }}>
                  <div>
                    采纳 <b style={{ color: 'var(--ok)' }}>{fb.adopted}</b>
                  </div>
                  <div>
                    驳回 <b style={{ color: '#dc2626' }}>{fb.rejected}</b>
                  </div>
                  <div>
                    编辑后采纳 <b>{fb.edited}</b>
                  </div>
                </div>
                {fb.reasons.length > 0 ? (
                  fb.reasons.map((r, i) => (
                    <div className="miss-row" key={i}>
                      <span className="miss-cnt">{r.count}</span>
                      <span style={{ color: 'var(--ink-2)' }}>{reasonLabel(r.reason)}</span>
                      <span className="miss-bar">
                        <i style={{ width: (r.count / fbMax) * 100 + '%', background: '#dc2626' }}></i>
                      </span>
                    </div>
                  ))
                ) : (
                  <div className="empty" style={{ padding: '12px 0' }}>
                    暂无驳回记录
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      <div className="board-grid">
        <div className="card">
          <div className="panel-head">
            <span className="panel-title">新建工单（条）</span>
            <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 'auto' }}>{rangeLabel}</span>
          </div>
          <div className="chart">
            {data.trend.map((t, i) => (
              <div className="bar-col" key={i}>
                <span className="bar-val">{t.value}</span>
                <div
                  className={'bar' + (i === data.trend.length - 1 ? ' last' : '')}
                  style={{ height: (t.value / maxTrend) * 100 + '%' }}
                ></div>
                <span className="bar-day">{t.day}</span>
              </div>
            ))}
          </div>
          <div style={{ height: 10 }}></div>
        </div>

        <div className="card">
          <div className="panel-head">
            <span className="panel-title">草稿采纳构成</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 20, padding: '20px 18px' }}>
            <div
              style={{
                position: 'relative',
                width: 104,
                height: 104,
                borderRadius: '50%',
                background: donutBg
              }}
            >
              <div
                style={{
                  position: 'absolute',
                  inset: 22,
                  borderRadius: '50%',
                  background: '#fff',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <b style={{ fontSize: 20, fontWeight: 700, letterSpacing: '-1px' }}>{rate}%</b>
                <span style={{ fontSize: 10, color: 'var(--muted-2)' }}>采纳率</span>
              </div>
            </div>
            <div className="legend">
              {data.adoption.items.map((it, i) => (
                <div key={i}>
                  <i style={{ background: it.color }}></i>
                  {it.label} <b>{it.pct}%</b>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="panel-head">
          <span className="panel-title">Top 检索未命中问题</span>
          <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 'auto' }}>建议补充知识库</span>
        </div>
        <div className="panel-body">
          {data.misses.map((m, i) => (
            <div className="miss-row" key={i}>
              <span className="miss-cnt">{m.count}</span>
              <span style={{ color: 'var(--ink-2)' }}>{m.question}</span>
              <span className="miss-bar">
                <i style={{ width: (m.weight / maxMiss) * 100 + '%' }}></i>
              </span>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

function BarPanel({ title, items, color }: { title: string; items: OpsNameCount[]; color: string }) {
  const max = Math.max(...items.map((i) => i.count), 1);
  return (
    <div className="card">
      <div className="panel-head">
        <span className="panel-title">{title}</span>
      </div>
      <div className="panel-body">
        {items.length === 0 ? (
          <div className="empty" style={{ padding: '20px 0' }}>暂无数据</div>
        ) : (
          items.map((it, i) => (
            <div className="miss-row" key={i}>
              <span className="miss-cnt">{it.count}</span>
              <span style={{ color: 'var(--ink-2)' }}>{it.name || '未分类'}</span>
              <span className="miss-bar">
                <i style={{ width: (it.count / max) * 100 + '%', background: color }}></i>
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
