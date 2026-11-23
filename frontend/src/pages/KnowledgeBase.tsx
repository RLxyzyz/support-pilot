import { useCallback, useEffect, useState } from 'react';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { KbDoc } from '@/types';

function statusColor(s: string) {
  if (s === '已索引') return '#059669';
  if (s === '索引中' || s === '待索引' || s === '审核中') return '#d97706';
  return '#dc2626';
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 10px',
  border: '1px solid var(--border, #d9dee7)',
  borderRadius: 6,
  fontSize: 12.5,
  background: 'var(--bg, #fff)',
  color: 'var(--ink, #1f2937)',
  boxSizing: 'border-box'
};

export default function KnowledgeBase() {
  const { showToast } = useToast();
  const [docs, setDocs] = useState<KbDoc[]>([]);
  const [total, setTotal] = useState(0);
  const [productLines, setProductLines] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const PAGE_SIZE = 20;

  const [uploadOpen, setUploadOpen] = useState(false);
  const [name, setName] = useState('');
  const [productLine, setProductLine] = useState('');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [productLineFilter, setProductLineFilter] = useState('全部');
  const [busyId, setBusyId] = useState<number | null>(null);

  const loadDocs = useCallback((p = page) => {
    const pl = productLineFilter === '全部' ? undefined : productLineFilter;
    api
      .getKbDocs(pl, p, PAGE_SIZE)
      .then((res) => {
        setDocs(res.list);
        setTotal(res.total);
        setProductLines(res.productLines || []);
        // 当前页被删空时自动回退一页
        if (res.list.length === 0 && res.total > 0 && p > 1) {
          setPage(p - 1);
        }
      })
      .catch((e) => showToast('加载知识库失败：' + e.message));
  }, [showToast, productLineFilter, page]);

  useEffect(() => {
    loadDocs(page);
  }, [page, loadDocs]);

  const reindex = async () => {
    try {
      const res = await api.reindexKb();
      showToast(res.message || `已重建索引（${res.indexed} 片）`);
      loadDocs();
    } catch (e) {
      showToast('重建索引失败：' + (e as Error).message);
    }
  };

  const syncTickets = async () => {
    setSyncing(true);
    try {
      const res = await api.syncResolvedTickets();
      showToast(`同步完成：入库 ${res.ingested} · 跳过 ${res.skipped} · 失败 ${res.failed}（共 ${res.total}）`);
      loadDocs();
    } catch (e) {
      showToast('同步失败：' + (e as Error).message);
    } finally {
      setSyncing(false);
    }
  };

  const onDelete = async (d: KbDoc) => {
    if (!window.confirm(`确认删除文档「${d.name}」？删除后不可恢复（同时清理向量与分片）。`)) return;
    setBusyId(d.id);
    try {
      const r = await api.deleteKbDoc(d.id);
      showToast(r.deleted ? `已删除（清理 ${r.chunks ?? 0} 个分片）` : r.message || '删除失败');
      loadDocs();
    } catch (e) {
      showToast('删除失败：' + (e as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const onReindexDoc = async (d: KbDoc) => {
    setBusyId(d.id);
    try {
      const r = await api.reindexKbDoc(d.id);
      showToast(r.message || `已重建（${r.indexed} 片）`);
      loadDocs();
    } catch (e) {
      showToast('重建失败：' + (e as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const submitUpload = async () => {
    if (!content.trim()) {
      showToast('请输入文档内容');
      return;
    }
    setSubmitting(true);
    try {
      const res = await api.uploadKbDoc({
        name: name.trim() || '未命名文档',
        content,
        productLine: productLine.trim() || undefined
      });
      showToast(`已导入：切分 ${res.chunks} 片 · 状态 ${res.status}${res.message ? ' · ' + res.message : ''}`);
      setUploadOpen(false);
      setName('');
      setProductLine('');
      setContent('');
      setPage(1);
      loadDocs(1);
    } catch (e) {
      showToast('上传失败：' + (e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const plOptions = productLineFilter !== '全部' && !productLines.includes(productLineFilter)
    ? [...productLines, productLineFilter]
    : productLines;

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">知识库</div>
          <div className="page-sub">{total} 篇文档 · 向量库 Milvus</div>
        </div>
        <div className="spacer"></div>
        <button className="btn" onClick={reindex}>
          重建索引
        </button>
        <button className="btn" onClick={syncTickets} disabled={syncing}>
          {syncing ? '同步中…' : '同步已解决工单'}
        </button>
        <button className="btn primary" onClick={() => setUploadOpen(true)}>
          + 上传文档
        </button>
      </div>

      <div className="kb-head">
        <select
          className="select"
          value={productLineFilter}
          onChange={(e) => {
            setProductLineFilter(e.target.value);
            setPage(1);
          }}
          style={{ width: 180, height: 30, padding: '0 8px' }}
        >
          <option value="全部">全部产品线</option>
          {plOptions.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <span style={{ fontSize: 11.5, color: 'var(--muted-2)', marginLeft: 'auto' }}>
          索引覆盖 {total} 篇文档
        </span>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>文档</th>
              <th style={{ width: 130 }}>产品线</th>
              <th style={{ width: 84 }}>分块数</th>
              <th style={{ width: 110 }}>近 7 天命中</th>
              <th style={{ width: 104 }}>状态</th>
              <th style={{ width: 92 }}>更新</th>
              <th style={{ width: 150 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {docs.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', color: 'var(--muted-2)', padding: 32 }}>
                  暂无文档，点击右上角「上传文档」导入知识并向量化
                </td>
              </tr>
            ) : (
              docs.map((d, i) => {
                const color = statusColor(d.status);
                return (
                  <tr key={i} style={{ cursor: 'default' }}>
                    <td>
                      <div style={{ fontWeight: 600, fontSize: 12.5 }}>{d.name}</div>
                      <div
                        style={{
                          fontSize: 11,
                          color: 'var(--muted-2)',
                          fontFamily: 'ui-monospace,Menlo,monospace'
                        }}
                      >
                        {d.path}
                      </div>
                    </td>
                    <td style={{ color: 'var(--ink-2)' }}>{d.productLine}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums' }}>{d.chunks}</td>
                    <td style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>{d.hits}</td>
                    <td>
                      <span style={{ fontSize: 11.5, color }}>
                        <span className="kb-dot" style={{ background: color }}></span>
                        {d.status}
                      </span>
                    </td>
                    <td style={{ color: 'var(--muted-2)', fontSize: 11.5 }}>今天</td>
                    <td>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <button className="btn sm" disabled={busyId === d.id} onClick={() => onReindexDoc(d)}>
                          {busyId === d.id ? '…' : '重建索引'}
                        </button>
                        <button
                          className="btn sm"
                          disabled={busyId === d.id}
                          onClick={() => onDelete(d)}
                          style={{ color: '#dc2626' }}
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {total > 0 && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 8, marginTop: 12 }}>
          <span style={{ fontSize: 11.5, color: 'var(--muted-2)' }}>
            共 {total} 篇 · 第 {page} / {totalPages} 页
          </span>
          <button className="btn sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>
            上一页
          </button>
          <button className="btn sm" disabled={page >= totalPages} onClick={() => setPage(page + 1)}>
            下一页
          </button>
        </div>
      )}

      <Modal
        open={uploadOpen}
        title="上传文档"
        subtitle="粘贴文档正文，系统将自动切分并向量化写入 Milvus"
        width={560}
        onClose={() => !submitting && setUploadOpen(false)}
        footer={
          <>
            <button className="btn" onClick={() => setUploadOpen(false)} disabled={submitting}>
              取消
            </button>
            <button className="btn primary" onClick={submitUpload} disabled={submitting}>
              {submitting ? '导入中…' : '导入并向量化'}
            </button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <label style={{ display: 'block', fontSize: 12, color: 'var(--muted-2)', marginBottom: 4 }}>文档名称</label>
            <input
              style={inputStyle}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="例如：Actuator Health 健康检查指南"
            />
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 12, color: 'var(--muted-2)', marginBottom: 4 }}>产品线（可选）</label>
            <input
              style={inputStyle}
              value={productLine}
              onChange={(e) => setProductLine(e.target.value)}
              placeholder="例如：Spring Boot 3.3"
            />
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 12, color: 'var(--muted-2)', marginBottom: 4 }}>文档正文</label>
            <textarea
              style={{ ...inputStyle, minHeight: 220, resize: 'vertical', fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 12 }}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="粘贴文档正文，支持多段落（空行分段）…"
            />
          </div>
        </div>
      </Modal>
    </>
  );
}
