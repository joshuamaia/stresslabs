import { MetricSnapshot, TestResult } from '../../core/models';
import { fmt, fmtDuration } from '../format';
import { COLORS, statusBreakdown } from './chart-options';

describe('statusBreakdown', () => {
  it('separates HTTP 429 from the other 4xx responses of a result', () => {
    const result = {
      status2xx: 80, status3xx: 0, status4xx: 15, status5xx: 2, blockedCount: 10, timeoutCount: 2,
      connectionFailures: 1, successCount: 80,
    } as TestResult;

    const items = statusBreakdown(result);

    expect(items.find(i => i.label === 'HTTP 429')).toEqual({ label: 'HTTP 429', value: 10, color: COLORS.serious });
    expect(items.find(i => i.label === 'Outros 4xx')?.value).toBe(5);
    expect(items.find(i => i.label === '5xx')?.color).toBe(COLORS.critical);
    expect(items.find(i => i.label === 'Timeouts')?.value).toBe(2);
  });

  it('works with cumulative snapshot counters', () => {
    const snapshot = {
      status2xx: 40, status4xx: 9, status5xx: 0, blocked: 9, timeouts: 0, connectionFailures: 0,
    } as MetricSnapshot;

    const items = statusBreakdown(snapshot);

    expect(items[0].value).toBe(40);
    expect(items[1].value).toBe(9);
    expect(items[2].value).toBe(0);
  });

  it('returns no items without data', () => {
    expect(statusBreakdown(null)).toEqual([]);
  });
});

describe('format helpers', () => {
  it('formats numbers and durations', () => {
    expect(fmt(null)).toBe('—');
    expect(fmtDuration(250)).toBe('250 ms');
    expect(fmtDuration(90_000)).toBe('1 min 30 s');
  });
});
