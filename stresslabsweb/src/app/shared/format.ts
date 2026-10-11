export function fmt(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return value.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

export function fmtBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) {
    return '—';
  }
  const mb = bytes / (1024 * 1024);
  return mb >= 1024 ? `${fmt(mb / 1024, 2)} GB` : `${fmt(mb, 0)} MB`;
}

export function fmtDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) {
    return '—';
  }
  if (ms < 1000) {
    return `${fmt(ms)} ms`;
  }
  const seconds = ms / 1000;
  return seconds < 60 ? `${fmt(seconds, 1)} s` : `${Math.floor(seconds / 60)} min ${fmt(seconds % 60)} s`;
}
