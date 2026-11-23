import { useEffect, useMemo, useState } from 'react';
import { api, isAdmin } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { SlaCalendar, SlaHoliday, SlaPolicy } from '@/types';

const TIERS = ['金牌', '银牌', '铜牌', '默认'];
const PRIORITIES = ['P1', 'P2', 'P3', 'P4'];

interface PolicyForm {
  tier: string;
  priority: string;
  firstResponseMinutes: number;
  resolveMinutes: number;
  calendarType: string;
  calendarId?: number;
  warningThreshold: number;
  enabled: boolean;
}

/** V2.1 M1：SLA 策略矩阵、计时日历、节假日、重算 / 补算 / 模拟。 */
export default function SlaSettings() {
  const { showToast } = useToast();
  const admin = isAdmin();

  const [policies, setPolicies] = useState<SlaPolicy[]>([]);
  const [calendars, setCalendars] = useState<SlaCalendar[]>([]);
  const [loading, setLoading] = useState(true);

  const [policyOpen, setPolicyOpen] = useState(false);
  const [policyForm, setPolicyForm] = useState<PolicyForm>(emptyPolicy());
  const [saving, setSaving] = useState(false);

  const [calOpen, setCalOpen] = useState(false);
  const [calEditing, setCalEditing] = useState<SlaCalendar | null>(null);
  const [calForm, setCalForm] = useState<SlaCalendar>(emptyCalendar());
  const [calSaving, setCalSaving] = useState(false);

  const [holidayCalendarId, setHolidayCalendarId] = useState<number | null>(null);
  const [holidays, setHolidays] = useState<SlaHoliday[]>([]);
  const [holidayDate, setHolidayDate] = useState('');
  const [holidayName, setHolidayName] = useState('');

  const [simTier, setSimTier] = useState('金牌');
  const [simPriority, setSimPriority] = useState('P1');
  const [simStart, setSimStart] = useState('');
  const [simResult, setSimResult] = useState<Record<string, unknown> | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const [p, c] = await Promise.all([api.getSlaPolicies(), api.getSlaCalendars()]);
      setPolicies(p);
      setCalendars(c);
      if (c.length > 0 && holidayCalendarId == null) setHolidayCalendarId(c[0].id || null);
    } catch (e) {
      showToast('加载 SLA 配置失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (holidayCalendarId == null) {
      setHolidays([]);
      return;
    }
    api
      .getSlaHolidays(holidayCalendarId)
      .then(setHolidays)
      .catch((e) => showToast('加载节假日失败：' + (e as Error).message));
  }, [holidayCalendarId]);

  const sorted = useMemo(() => {
    return [...policies].sort((a, b) => {
      const ti = TIERS.indexOf(a.tier) - TIERS.indexOf(b.tier);
      return ti !== 0 ? ti : PRIORITIES.indexOf(a.priority) - PRIORITIES.indexOf(b.priority);
    });
  }, [policies]);

  const openPolicy = (p?: SlaPolicy) => {
    setPolicyForm(p ? { ...p } : emptyPolicy());
    setPolicyOpen(true);
  };

  const savePolicy = async () => {
    if (policyForm.firstResponseMinutes <= 0 || policyForm.resolveMinutes <= 0) {
      showToast('首响与解决时限必须大于 0');
      return;
    }
    if (policyForm.warningThreshold < 50 || policyForm.warningThreshold > 95) {
      showToast('预警阈值需在 50–95 之间');
      return;
    }
    setSaving(true);
    try {
      await api.upsertSlaPolicy(policyForm as SlaPolicy);
      showToast('策略已保存，点击「重算未解决」后对存量工单生效');
      setPolicyOpen(false);
      load();
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const openCalendar = (c?: SlaCalendar) => {
    setCalEditing(c || null);
    setCalForm(c ? { ...c } : emptyCalendar());
    setCalOpen(true);
  };

  const saveCalendar = async () => {
    if (!calForm.name.trim()) {
      showToast('日历名称不能为空');
      return;
    }
    setCalSaving(true);
    try {
      if (calEditing?.id) {
        await api.updateSlaCalendar(calEditing.id, calForm);
      } else {
        await api.createSlaCalendar(calForm);
      }
      showToast('日历已保存');
      setCalOpen(false);
      load();
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setCalSaving(false);
    }
  };

  const removeCalendar = async (c: SlaCalendar) => {
    if (!c.id || !window.confirm(`确认删除日历「${c.name}」？`)) return;
    try {
      await api.deleteSlaCalendar(c.id);
      showToast('日历已删除');
      load();
    } catch (e) {
      showToast('删除失败：' + (e as Error).message);
    }
  };

  const addHoliday = async () => {
    if (holidayCalendarId == null || !holidayDate) {
      showToast('请选择日期');
      return;
    }
    try {
      await api.addSlaHoliday(holidayCalendarId, holidayDate, holidayName.trim() || undefined);
      setHolidayDate('');
      setHolidayName('');
      setHolidays(await api.getSlaHolidays(holidayCalendarId));
      showToast('节假日已添加');
    } catch (e) {
      showToast('添加失败：' + (e as Error).message);
    }
  };

  const removeHoliday = async (h: SlaHoliday) => {
    if (holidayCalendarId == null) return;
    try {
      await api.removeSlaHoliday(holidayCalendarId, h.holidayDate);
      setHolidays(await api.getSlaHolidays(holidayCalendarId));
      showToast('节假日已删除');
    } catch (e) {
      showToast('删除失败：' + (e as Error).message);
    }
  };

  const recalculate = async () => {
    if (!window.confirm('将按最新策略重算所有未解决工单的到期时间，确认执行？')) return;
    try {
      const r = await api.recalculateSla();
      showToast(`重算完成，更新 ${r.updated} 张工单`);
      load();
    } catch (e) {
      showToast('重算失败：' + (e as Error).message);
    }
  };

  const backfill = async () => {
    if (!window.confirm('将为存量缺失快照的工单补算 SLA，确认执行？')) return;
    try {
      const r = await api.backfillSla();
      showToast(`补算完成，更新 ${r.updated} 张工单`);
      load();
    } catch (e) {
      showToast('补算失败：' + (e as Error).message);
    }
  };

  const simulate = async () => {
    try {
      const start = simStart ? new Date(simStart).toISOString() : undefined;
      setSimResult(await api.simulateSla(simTier, simPriority, start));
    } catch (e) {
      showToast('模拟失败：' + (e as Error).message);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">SLA 策略</div>
          <div className="page-sub">
            按服务等级 × 优先级配置首响与解决时限；新建工单固化快照，策略调整可手动重算未解决工单
          </div>
        </div>
        <div className="spacer"></div>
        {admin && (
          <>
            <button className="btn ghost sm" onClick={recalculate}>
              重算未解决
            </button>
            <button className="btn ghost sm" onClick={backfill}>
              存量补算
            </button>
            <button className="btn primary sm" onClick={() => openPolicy()}>
              新增策略
            </button>
          </>
        )}
      </div>

      <div className="table-wrap" style={{ marginBottom: 20 }}>
        <table>
          <thead>
            <tr>
              <th style={{ width: 90 }}>服务等级</th>
              <th style={{ width: 70 }}>优先级</th>
              <th style={{ width: 100 }}>首响（分钟）</th>
              <th style={{ width: 100 }}>解决（分钟）</th>
              <th style={{ width: 100 }}>日历类型</th>
              <th style={{ width: 140 }}>日历</th>
              <th style={{ width: 90 }}>预警阈值</th>
              <th style={{ width: 70 }}>启用</th>
              {admin && <th style={{ width: 80 }}>操作</th>}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={admin ? 9 : 8}>
                  <div className="empty">加载中…</div>
                </td>
              </tr>
            ) : sorted.length === 0 ? (
              <tr>
                <td colSpan={admin ? 9 : 8}>
                  <div className="empty">暂无策略，未配置时将回落到全局默认时限</div>
                </td>
              </tr>
            ) : (
              sorted.map((p) => (
                <tr key={`${p.tier}-${p.priority}`}>
                  <td>{p.tier}</td>
                  <td>
                    <span className="pill soft">{p.priority}</span>
                  </td>
                  <td>{p.firstResponseMinutes}</td>
                  <td>{p.resolveMinutes}</td>
                  <td>{p.calendarType === 'WORKTIME' ? '工作日历' : '自然日'}</td>
                  <td>{calendarName(calendars, p.calendarId)}</td>
                  <td>{p.warningThreshold}%</td>
                  <td>{p.enabled ? '是' : '否'}</td>
                  {admin && (
                    <td>
                      <button className="btn ghost sm" onClick={() => openPolicy(p)}>
                        编辑
                      </button>
                    </td>
                  )}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="page-head" style={{ marginBottom: 10 }}>
        <div>
          <div className="page-title" style={{ fontSize: 15 }}>
            计时日历
          </div>
          <div className="page-sub">工作日历仅在工作日的工作时段内计时，周末与节假日暂停</div>
        </div>
        <div className="spacer"></div>
        {admin && (
          <button className="btn ghost sm" onClick={() => openCalendar()}>
            新增日历
          </button>
        )}
      </div>

      <div className="table-wrap" style={{ marginBottom: 20 }}>
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th style={{ width: 120 }}>工作日</th>
              <th style={{ width: 160 }}>工作时段</th>
              <th style={{ width: 140 }}>时区</th>
              <th style={{ width: 70 }}>启用</th>
              {admin && <th style={{ width: 140 }}>操作</th>}
            </tr>
          </thead>
          <tbody>
            {calendars.length === 0 ? (
              <tr>
                <td colSpan={admin ? 6 : 5}>
                  <div className="empty">暂无日历</div>
                </td>
              </tr>
            ) : (
              calendars.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.workdays}</td>
                  <td>{c.segments}</td>
                  <td>{c.timezone}</td>
                  <td>{c.enabled ? '是' : '否'}</td>
                  {admin && (
                    <td>
                      <button className="btn ghost sm" onClick={() => openCalendar(c)}>
                        编辑
                      </button>
                      <button className="btn ghost sm" onClick={() => removeCalendar(c)}>
                        删除
                      </button>
                    </td>
                  )}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="page-head" style={{ marginBottom: 10 }}>
        <div>
          <div className="page-title" style={{ fontSize: 15 }}>
            节假日
          </div>
          <div className="page-sub">节假日当天不计入工作日历计时</div>
        </div>
        <div className="spacer"></div>
        <select
          className="input"
          style={{ width: 180 }}
          value={holidayCalendarId ?? ''}
          onChange={(e) => setHolidayCalendarId(e.target.value ? Number(e.target.value) : null)}
        >
          <option value="">选择日历</option>
          {calendars.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      <div className="table-wrap" style={{ marginBottom: 20 }}>
        <table>
          <thead>
            <tr>
              <th style={{ width: 160 }}>日期</th>
              <th>说明</th>
              {admin && <th style={{ width: 90 }}>操作</th>}
            </tr>
          </thead>
          <tbody>
            {holidayCalendarId == null ? (
              <tr>
                <td colSpan={admin ? 3 : 2}>
                  <div className="empty">请选择日历</div>
                </td>
              </tr>
            ) : holidays.length === 0 ? (
              <tr>
                <td colSpan={admin ? 3 : 2}>
                  <div className="empty">暂无节假日</div>
                </td>
              </tr>
            ) : (
              holidays.map((h) => (
                <tr key={h.id}>
                  <td>{h.holidayDate}</td>
                  <td>{h.name || '—'}</td>
                  {admin && (
                    <td>
                      <button className="btn ghost sm" onClick={() => removeHoliday(h)}>
                        删除
                      </button>
                    </td>
                  )}
                </tr>
              ))
            )}
          </tbody>
        </table>
        {admin && holidayCalendarId != null && (
          <div style={{ display: 'flex', gap: 10, padding: 12, alignItems: 'center' }}>
            <input
              className="input"
              type="date"
              style={{ width: 160 }}
              value={holidayDate}
              onChange={(e) => setHolidayDate(e.target.value)}
            />
            <input
              className="input"
              placeholder="说明（可选）"
              style={{ flex: 1 }}
              value={holidayName}
              onChange={(e) => setHolidayName(e.target.value)}
            />
            <button className="btn primary sm" onClick={addHoliday}>
              添加节假日
            </button>
          </div>
        )}
      </div>

      <div className="page-head" style={{ marginBottom: 10 }}>
        <div>
          <div className="page-title" style={{ fontSize: 15 }}>
            模拟计算
          </div>
          <div className="page-sub">按当前策略试算解决时限与消耗百分比，不会写入任何工单</div>
        </div>
      </div>
      <div className="table-wrap" style={{ padding: 14 }}>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          <select className="input" style={{ width: 120 }} value={simTier} onChange={(e) => setSimTier(e.target.value)}>
            {TIERS.filter((t) => t !== '默认').map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
          <select
            className="input"
            style={{ width: 100 }}
            value={simPriority}
            onChange={(e) => setSimPriority(e.target.value)}
          >
            {PRIORITIES.map((p) => (
              <option key={p}>{p}</option>
            ))}
          </select>
          <input
            className="input"
            style={{ width: 220 }}
            type="datetime-local"
            value={simStart}
            onChange={(e) => setSimStart(e.target.value)}
          />
          <button className="btn primary sm" onClick={simulate}>
            计算
          </button>
        </div>
        {simResult && (
          <div style={{ marginTop: 12, fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.9 }}>
            <div>命中策略：{String(simResult.source || '—')}（等级 {String(simResult.tier)} / {String(simResult.priority)}）</div>
            <div>
              首响 {String(simResult.firstResponseMinutes)} 分钟，解决 {String(simResult.resolveMinutes)} 分钟，
              日历 {String(simResult.calendarType)}
            </div>
            <div>到期时间：{String(simResult.resolveDueAt || '—')}</div>
            <div>
              当前消耗：{String(simResult.percent)}%（{String(simResult.consumedSeconds)} / {String(simResult.totalSeconds)} 秒）
              {simResult.breached ? '，已超时' : ''}
            </div>
          </div>
        )}
      </div>

      <Modal
        open={policyOpen}
        onClose={() => setPolicyOpen(false)}
        title={policies.some((p) => p.tier === policyForm.tier && p.priority === policyForm.priority) ? '编辑策略' : '新增策略'}
        subtitle="同一「服务等级 × 优先级」保存后覆盖原策略"
        icon="◷"
        footer={
          <>
            <span className="note">仅影响新建工单，存量需手动重算</span>
            <button className="btn ghost sm" onClick={() => setPolicyOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={savePolicy} disabled={saving}>
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="field">
          <label>服务等级</label>
          <select
            className="input"
            value={policyForm.tier}
            onChange={(e) => setPolicyForm({ ...policyForm, tier: e.target.value })}
          >
            {TIERS.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>优先级</label>
          <select
            className="input"
            value={policyForm.priority}
            onChange={(e) => setPolicyForm({ ...policyForm, priority: e.target.value })}
          >
            {PRIORITIES.map((p) => (
              <option key={p}>{p}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>首响时限（分钟）</label>
          <input
            className="input"
            type="number"
            value={policyForm.firstResponseMinutes}
            onChange={(e) => setPolicyForm({ ...policyForm, firstResponseMinutes: Number(e.target.value) })}
          />
        </div>
        <div className="field">
          <label>解决时限（分钟）</label>
          <input
            className="input"
            type="number"
            value={policyForm.resolveMinutes}
            onChange={(e) => setPolicyForm({ ...policyForm, resolveMinutes: Number(e.target.value) })}
          />
        </div>
        <div className="field">
          <label>计时日历</label>
          <select
            className="input"
            value={policyForm.calendarType}
            onChange={(e) => setPolicyForm({ ...policyForm, calendarType: e.target.value })}
          >
            <option value="NATURAL">自然日</option>
            <option value="WORKTIME">工作日历</option>
          </select>
        </div>
        {policyForm.calendarType === 'WORKTIME' && (
          <div className="field">
            <label>选择日历</label>
            <select
              className="input"
              value={policyForm.calendarId ?? ''}
              onChange={(e) =>
                setPolicyForm({ ...policyForm, calendarId: e.target.value ? Number(e.target.value) : undefined })
              }
            >
              <option value="">默认日历</option>
              {calendars.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        )}
        <div className="field">
          <label>预警阈值（50–95%）</label>
          <input
            className="input"
            type="number"
            value={policyForm.warningThreshold}
            onChange={(e) => setPolicyForm({ ...policyForm, warningThreshold: Number(e.target.value) })}
          />
        </div>
        <div className="field">
          <label className="chk">
            <input
              type="checkbox"
              checked={policyForm.enabled}
              onChange={(e) => setPolicyForm({ ...policyForm, enabled: e.target.checked })}
            />
            启用该策略
          </label>
        </div>
      </Modal>

      <Modal
        open={calOpen}
        onClose={() => setCalOpen(false)}
        title={calEditing ? '编辑计时日历' : '新增计时日历'}
        subtitle="工作日 1=周一 … 7=周日；时段可多段，如 09:00-12:00,13:30-18:00"
        icon="◷"
        footer={
          <>
            <button className="btn ghost sm" onClick={() => setCalOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={saveCalendar} disabled={calSaving}>
              {calSaving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="field">
          <label>
            名称 <span className="req">*</span>
          </label>
          <input
            className="input"
            value={calForm.name}
            onChange={(e) => setCalForm({ ...calForm, name: e.target.value })}
            placeholder="如：标准工作日历"
          />
        </div>
        <div className="field">
          <label>工作日</label>
          <input
            className="input"
            value={calForm.workdays}
            onChange={(e) => setCalForm({ ...calForm, workdays: e.target.value })}
            placeholder="1,2,3,4,5"
          />
        </div>
        <div className="field">
          <label>工作时段</label>
          <input
            className="input"
            value={calForm.segments}
            onChange={(e) => setCalForm({ ...calForm, segments: e.target.value })}
            placeholder="09:00-18:00"
          />
        </div>
        <div className="field">
          <label>时区</label>
          <input
            className="input"
            value={calForm.timezone}
            onChange={(e) => setCalForm({ ...calForm, timezone: e.target.value })}
            placeholder="Asia/Shanghai"
          />
        </div>
        <div className="field">
          <label className="chk">
            <input
              type="checkbox"
              checked={calForm.enabled}
              onChange={(e) => setCalForm({ ...calForm, enabled: e.target.checked })}
            />
            启用该日历
          </label>
        </div>
      </Modal>
    </>
  );
}

function emptyPolicy(): PolicyForm {
  return {
    tier: '金牌',
    priority: 'P1',
    firstResponseMinutes: 30,
    resolveMinutes: 240,
    calendarType: 'NATURAL',
    calendarId: undefined,
    warningThreshold: 80,
    enabled: true
  };
}

function emptyCalendar(): SlaCalendar {
  return {
    name: '',
    workdays: '1,2,3,4,5',
    segments: '09:00-18:00',
    timezone: 'Asia/Shanghai',
    enabled: true
  };
}

function calendarName(calendars: SlaCalendar[], id?: number): string {
  if (id == null) return '默认';
  return calendars.find((c) => c.id === id)?.name || '#' + id;
}
