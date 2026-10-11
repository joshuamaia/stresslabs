import { MetricSnapshot, TestResult } from '../../core/models';
import { ChartOption } from './echart';

/** Tokens de cor (mesmos de styles.scss). Séries em ordem fixa; status reservados. */
export const COLORS = {
  series: ['#3987e5', '#d95926', '#199e70', '#9085e9'],
  reference: '#6b6a64',
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b',
  surface: '#1a1a19',
  grid: '#2a2a27',
  axis: '#8c8b83',
  text: '#c3c2b7',
};

const baseTextStyle = { color: COLORS.text, fontFamily: 'Inter, system-ui, sans-serif' };

function base(): ChartOption {
  return {
    backgroundColor: 'transparent',
    textStyle: baseTextStyle,
    animationDuration: 400,
    animationDurationUpdate: 300,
    grid: { left: 52, right: 20, top: 36, bottom: 36, containLabel: false },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#242422',
      borderColor: '#3a3a36',
      textStyle: { color: '#f0efec', fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: '#55544f' } },
      valueFormatter: (value: unknown) => typeof value === 'number' ? value.toLocaleString('pt-BR', { maximumFractionDigits: 2 }) : String(value),
    },
    legend: { top: 0, right: 0, textStyle: { color: COLORS.text }, icon: 'roundRect', itemWidth: 12, itemHeight: 4, itemGap: 18 },
  };
}

function elapsedAxis(): object {
  return {
    type: 'value',
    name: 's',
    nameTextStyle: { color: COLORS.axis },
    axisLine: { lineStyle: { color: COLORS.grid } },
    axisLabel: { color: COLORS.axis, formatter: (v: number) => `${v}` },
    splitLine: { show: false },
    min: 0,
  };
}

function valueAxis(name: string): object {
  return {
    type: 'value',
    name,
    nameTextStyle: { color: COLORS.axis, align: 'left' },
    axisLabel: { color: COLORS.axis },
    splitLine: { lineStyle: { color: COLORS.grid } },
    min: 0,
  };
}

function line(name: string, color: string, data: [number, number][], extra: object = {}): object {
  return {
    name, type: 'line', data, showSymbol: false, symbolSize: 8, smooth: false,
    lineStyle: { width: 2, color }, itemStyle: { color }, emphasis: { focus: 'series' }, ...extra,
  };
}

const t = (s: MetricSnapshot) => Math.round(s.elapsedMs / 100) / 10;

/** Gráfico 1: throughput real (respostas/s) vs taxa de envio alvo. */
export function rpsChart(snapshots: MetricSnapshot[]): ChartOption {
  return {
    ...base(),
    xAxis: elapsedAxis(),
    yAxis: valueAxis('req/s'),
    series: [
      line('Throughput (RPS)', COLORS.series[0], snapshots.map(s => [t(s), s.rps]),
        { areaStyle: { color: 'rgba(57,135,229,0.12)' } }),
      line('Taxa alvo', COLORS.reference, snapshots.map(s => [t(s), s.targetRps]),
        { lineStyle: { width: 2, type: 'dashed', color: COLORS.reference } }),
    ],
  };
}

/** Gráfico 2: latência média, p95 e p99 por janela. */
export function latencyChart(snapshots: MetricSnapshot[]): ChartOption {
  return {
    ...base(),
    xAxis: elapsedAxis(),
    yAxis: valueAxis('ms'),
    series: [
      line('Média', COLORS.series[0], snapshots.map(s => [t(s), s.avgLatencyMs])),
      line('p95', COLORS.series[1], snapshots.map(s => [t(s), s.p95LatencyMs])),
      line('p99', COLORS.series[2], snapshots.map(s => [t(s), s.p99LatencyMs])),
    ],
  };
}

/** Gráfico 4: requisições simultâneas em andamento (pico da janela e valor no instante). */
export function concurrencyChart(snapshots: MetricSnapshot[], limit?: number): ChartOption {
  const series: object[] = [
    line('Pico na janela', COLORS.series[3], snapshots.map(s => [t(s), s.maxConcurrency]), { step: 'end' }),
    line('No instante', COLORS.series[0], snapshots.map(s => [t(s), s.concurrency]), { step: 'end' }),
  ];
  if (limit) {
    (series[0] as Record<string, unknown>)['markLine'] = {
      symbol: 'none', silent: true,
      lineStyle: { color: COLORS.reference, type: 'dashed' },
      label: { color: COLORS.axis, formatter: `limite ${limit}`, position: 'insideEndTop' },
      data: [{ yAxis: limit }],
    };
  }
  return { ...base(), xAxis: elapsedAxis(), yAxis: valueAxis('requisições'), series };
}

export interface StatusBreakdown {
  label: string;
  value: number;
  color: string;
}

/** Distribuição de respostas; 429 separado dos demais 4xx. */
export function statusBreakdown(source: MetricSnapshot | TestResult | null): StatusBreakdown[] {
  if (!source) {
    return [];
  }
  const isResult = 'successCount' in source;
  const s2xx = isResult ? source.status2xx + source.status3xx : source.status2xx;
  const blocked = isResult ? source.blockedCount : source.blocked;
  const s4xx = source.status4xx - blocked;
  const s5xx = source.status5xx;
  const timeouts = isResult ? source.timeoutCount : source.timeouts;
  const connection = source.connectionFailures;
  return [
    { label: '2xx/3xx', value: s2xx, color: COLORS.good },
    { label: 'HTTP 429', value: blocked, color: COLORS.serious },
    { label: 'Outros 4xx', value: Math.max(0, s4xx), color: COLORS.warning },
    { label: '5xx', value: s5xx, color: COLORS.critical },
    { label: 'Timeouts', value: timeouts, color: COLORS.warning },
    { label: 'Falhas de conexão', value: connection, color: COLORS.critical },
  ];
}

/** Gráfico 3: barras horizontais com rótulo direto (cor + rótulo, nunca só cor). */
export function statusChart(items: StatusBreakdown[]): ChartOption {
  const total = items.reduce((sum, item) => sum + item.value, 0) || 1;
  return {
    ...base(),
    legend: { show: false },
    tooltip: { ...(base()['tooltip'] as object), trigger: 'item' },
    grid: { left: 120, right: 90, top: 8, bottom: 8 },
    xAxis: { type: 'value', show: false, max: (v: { max: number }) => Math.max(v.max, 1) },
    yAxis: {
      type: 'category', inverse: true, data: items.map(i => i.label),
      axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: COLORS.text },
    },
    series: [{
      type: 'bar', barWidth: 14, name: 'Respostas',
      data: items.map(i => ({ value: i.value, itemStyle: { color: i.color, borderRadius: [0, 4, 4, 0] } })),
      label: {
        show: true, position: 'right', color: COLORS.text,
        formatter: (p: { value: number }) => `${p.value.toLocaleString('pt-BR')} · ${((p.value / total) * 100).toFixed(1)}%`,
      },
      showBackground: true, backgroundStyle: { color: '#232321', borderRadius: 4 },
    }],
  };
}

/** Gráfico 5: comparação lado a lado entre duas execuções (mesma unidade por gráfico). */
export function comparisonBarChart(categories: string[], unit: string, nameA: string, valuesA: number[],
                                   nameB: string, valuesB: number[]): ChartOption {
  const bar = (name: string, color: string, data: number[]) => ({
    name, type: 'bar', data, barMaxWidth: 28, barGap: '8%',
    itemStyle: { color, borderRadius: [4, 4, 0, 0], borderColor: COLORS.surface, borderWidth: 1 },
    label: { show: true, position: 'top', color: COLORS.text, fontSize: 11,
      formatter: (p: { value: number }) => p.value.toLocaleString('pt-BR', { maximumFractionDigits: 1 }) },
  });
  return {
    ...base(),
    tooltip: { ...(base()['tooltip'] as object), axisPointer: { type: 'shadow' } },
    grid: { left: 52, right: 16, top: 40, bottom: 32 },
    xAxis: { type: 'category', data: categories, axisLabel: { color: COLORS.text }, axisLine: { lineStyle: { color: COLORS.grid } } },
    yAxis: valueAxis(unit),
    series: [bar(nameA, COLORS.series[0], valuesA), bar(nameB, COLORS.series[1], valuesB)],
  };
}

/** Sobreposição temporal de uma métrica de duas execuções. */
export function overlayChart(unit: string, nameA: string, a: MetricSnapshot[], nameB: string, b: MetricSnapshot[],
                             pick: (s: MetricSnapshot) => number): ChartOption {
  return {
    ...base(),
    xAxis: elapsedAxis(),
    yAxis: valueAxis(unit),
    series: [
      line(nameA, COLORS.series[0], a.map(s => [t(s), pick(s)])),
      line(nameB, COLORS.series[1], b.map(s => [t(s), pick(s)])),
    ],
  };
}

/** Série temporal simples para métricas de infraestrutura. */
export function timeSeriesChart(unit: string, series: { name: string; points: [number, number][] }[]): ChartOption {
  return {
    ...base(),
    xAxis: { type: 'time', axisLabel: { color: COLORS.axis }, axisLine: { lineStyle: { color: COLORS.grid } }, splitLine: { show: false } },
    yAxis: valueAxis(unit),
    series: series.map((s, i) => line(s.name, COLORS.series[i % COLORS.series.length], s.points)),
  };
}
