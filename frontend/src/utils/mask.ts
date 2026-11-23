/**
 * 联系人 PII 脱敏（PRD 15.4：按最小必要原则展示）。
 * 仅用于展示；编辑表单仍使用原始值，避免回写脱敏后的脏数据。
 */

/** a***@example.com */
export function maskEmail(email?: string | null): string {
  if (!email) return '—';
  const at = email.indexOf('@');
  if (at <= 0) return '***';
  const local = email.slice(0, at);
  const domain = email.slice(at);
  if (local.length <= 2) return local.charAt(0) + '***' + domain;
  return local.charAt(0) + '***' + local.charAt(local.length - 1) + domain;
}

/** 138****1234（≥7 位保留前 3 后 4，其余保留前 2 后 2） */
export function maskPhone(phone?: string | null): string {
  if (!phone) return '—';
  const digits = phone.replace(/\s/g, '');
  if (digits.length >= 7) return digits.slice(0, 3) + '****' + digits.slice(-4);
  if (digits.length > 2) return digits.slice(0, 1) + '***' + digits.slice(-1);
  return '***';
}
