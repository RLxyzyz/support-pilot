/**
 * SupportPilot 本地开发 mock 后端
 * 接口契约与 Spring Boot 真实后端保持一致，前端通过 Vite 代理 /api 访问。
 * 用途：本机无 Java/Postgres 时进行前端联调与演示。
 */
const express = require('express');
const cors = require('cors');
const { tickets, kbDocs, misses, board, agentScenario } = require('./data');

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

// 统一返回体（与后端 Result<T> 对齐）
function ok(data) {
  return { code: 0, message: 'success', data };
}

// ---------- 工单 ----------
app.get('/api/tickets', (req, res) => {
  const { status, priority, keyword, page = 1, size = 20 } = req.query;
  let list = tickets.slice();
  if (status && status !== '全部') {
    list = list.filter((t) => t.status === status);
  }
  if (priority && priority !== '全部') {
    list = list.filter((t) => t.priority === priority);
  }
  if (keyword) {
    const kw = String(keyword).toLowerCase();
    list = list.filter(
      (t) => t.title.toLowerCase().includes(kw) || (t.body || '').toLowerCase().includes(kw)
    );
  }
  const total = list.length;
  const start = (Number(page) - 1) * Number(size);
  const paged = list.slice(start, start + Number(size));
  res.json(ok({ list: paged, total }));
});

app.get('/api/tickets/:id', (req, res) => {
  const id = req.params.id.startsWith('#') ? req.params.id : '#' + req.params.id;
  const t = tickets.find((x) => x.id === id);
  if (!t) return res.status(404).json({ code: 404, message: '工单不存在', data: null });
  res.json(ok(t));
});

app.post('/api/tickets/import', (req, res) => {
  // 模拟从 GitHub 导入，幂等
  res.json(ok({ imported: tickets.length, skipped: 0 }));
});

app.post('/api/tickets', (req, res) => {
  const { title, body, tenant } = req.body || {};
  if (!title) return res.status(400).json({ code: 400, message: '标题不能为空', data: null });
  const id = '#' + (41000 + Math.floor(Math.random() * 999));
  const t = {
    id,
    title,
    body: body || '',
    code: '',
    tenant: tenant || '演示租户',
    product: 'Spring Boot 3.3.0',
    priority: 'P3',
    priorityClass: 'p3',
    status: '待分诊',
    statusClass: 'st-new',
    category: '未分类',
    sla: '剩余 4 小时',
    slaPct: 85,
    slaColor: '#d97706',
    updateTime: '刚刚',
    messages: []
  };
  tickets.unshift(t);
  res.json(ok(t));
});

// ---------- 知识库 ----------
app.get('/api/kb/documents', (req, res) => {
  res.json(ok(kbDocs));
});

app.post('/api/kb/documents', (req, res) => {
  res.json(ok({ accepted: true }));
});

app.post('/api/kb/reindex', (req, res) => {
  res.json(ok({ triggered: true }));
});

app.post('/api/kb/search', (req, res) => {
  res.json(
    ok([
      { id: 1, doc: 'Actuator Health §3.2 探针分组', score: 0.93 },
      { id: 2, doc: '历史工单 #39871（同现象，已解决）', score: 0.88 }
    ])
  );
});

// ---------- 看板 ----------
app.get('/api/metrics/overview', (req, res) => {
  res.json(ok({ ...board, misses }));
});

// ---------- 草稿采纳 / 驳回 ----------
app.post('/api/drafts/:id/approve', (req, res) => {
  res.json(ok({ adopted: true, cost: '耗时 3 分 12 秒 · ¥0.028' }));
});

app.post('/api/drafts/:id/reject', (req, res) => {
  res.json(ok({ rejected: true }));
});

// ---------- Agent SSE 流式 ----------
// 浏览器 EventSource 仅支持 GET，故流式接口用 GET；创建 run 的 POST 接口也保留
app.get('/api/agent/runs', (req, res) => {
  const ticketId = req.query.ticketId;
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no'
  });

  const send = (event, data) => {
    res.write(`event: ${event}\n`);
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  };

  let i = 0;
  const thinkTimer = setInterval(() => {
    if (i < agentScenario.labels.length) {
      send('thinking', { label: agentScenario.labels[i] });
      i++;
    } else {
      clearInterval(thinkTimer);
    }
  }, 430);

  let stepIdx = 0;
  const stepTimer = setInterval(() => {
    if (stepIdx < agentScenario.steps.length) {
      send('step', agentScenario.steps[stepIdx]);
      stepIdx++;
    } else {
      clearInterval(stepTimer);
      setTimeout(() => {
        send('draft', { content: agentScenario.draft, cost: agentScenario.cost });
        send('cites', { items: agentScenario.cites });
        setTimeout(() => {
          send('done', { ticketId: ticketId || '' });
          res.end();
        }, 200);
      }, 300);
    }
  }, 520);

  req.on('close', () => {
    clearInterval(thinkTimer);
    clearInterval(stepTimer);
  });
});

app.post('/api/agent/runs', (req, res) => {
  res.json(ok({ runId: 'run_' + Date.now(), message: '请通过 GET /api/agent/runs?ticketId= 订阅流式输出' }));
});

app.get('/api/agent/runs/:id', (req, res) => {
  res.json(ok({ runId: req.params.id, steps: agentScenario.steps.length, cost: agentScenario.cost }));
});

// 健康检查
app.get('/api/health', (req, res) => res.json(ok({ status: 'UP' })));

app.listen(PORT, () => {
  console.log(`[SupportPilot mock-server] running on http://localhost:${PORT}`);
});
