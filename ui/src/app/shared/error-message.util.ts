import { HttpErrorResponse } from '@angular/common/http';

export function formatUiError(err: unknown, fallback: string): string {
  const message = extractMessage(err);
  const sanitized = sanitizeUiError(message);
  return sanitized || fallback;
}

function extractMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const payload = err.error as unknown;
    if (typeof payload === 'string' && payload.trim()) {
      return payload.trim();
    }
    if (payload && typeof payload === 'object') {
      const record = payload as Record<string, unknown>;
      const message = String(record['message'] ?? record['error'] ?? '').trim();
      if (message) {
        return message;
      }
    }
    if (typeof err.message === 'string' && err.message.trim()) {
      return err.message.trim();
    }
  }

  if (err instanceof Error) {
    return err.message;
  }
  if (typeof err === 'string') {
    return err;
  }
  return '';
}

function sanitizeUiError(raw: string): string {
  if (!raw) {
    return '';
  }

  let message = raw.trim();
  const lowerRaw = message.toLowerCase();
  if (lowerRaw.includes('#131005')
      || lowerRaw.includes('authentication error')
      || (lowerRaw.includes('access denied') && lowerRaw.includes('access token'))) {
    return 'Meta rejected the WhatsApp access token or permissions. Generate a fresh WhatsApp Cloud API token for this phone-number ID and try again.';
  }
  message = message.replace(/ERPNext/gi, 'system');
  message = message.replace(/ErpNext/gi, 'system');
  message = message.replace(/\[\d+\s+[A-Z_ ]+\]\s+during\s+\[[A-Z]+\]\s+to\s+\[[^\]]+\]\s+\[[^\]]+\]:\s*/i, '');
  message = message.replace(/feignclient#[^(]+\([^)]*\):\s*/i, '');
  message = message.replace(/^http failure response for .*?:\s*/i, '');
  message = message.replace(/\s+/g, ' ').trim();

  if (!message || message === 'request_failed' || message === 'bad_request' || message === 'invalid_state') {
    return '';
  }

  return message;
}
