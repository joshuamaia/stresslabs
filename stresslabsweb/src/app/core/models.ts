export type TestStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'FAILED';
export type TestScenario = 'CONSTANT' | 'RAMP_UP' | 'SPIKE' | 'RATE_LIMIT';
export type HttpMethod = 'GET' | 'POST';
export type ClientStrategy = 'IP' | 'HEADER' | 'GLOBAL';

export const ACTIVE_STATUSES: readonly TestStatus[] = ['PENDING', 'RUNNING'];

export interface Execution {
  id: string;
  name: string;
  scenario: TestScenario;
  targetKey: string;
  endpoint: string;
  method: HttpMethod;
  totalRequests: number;
  concurrency: number;
  targetRps: number;
  durationSeconds: number;
  timeoutMs: number;
  rateLimitEnabled: boolean;
  rateLimitSummary: string | null;
  status: TestStatus;
  failureReason: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface StatusEvent {
  executionId: string;
  status: TestStatus;
  failureReason: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface MetricSnapshot {
  executionId: string;
  capturedAt: string;
  elapsedMs: number;
  rps: number;
  targetRps: number;
  concurrency: number;
  maxConcurrency: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  sent: number;
  completed: number;
  success: number;
  errors: number;
  blocked: number;
  timeouts: number;
  connectionFailures: number;
  status2xx: number;
  status4xx: number;
  status5xx: number;
  progress: number;
}

export interface TestResult {
  executionId: string;
  totalRequests: number;
  completedRequests: number;
  successCount: number;
  errorCount: number;
  blockedCount: number;
  httpErrorCount: number;
  connectionFailures: number;
  timeoutCount: number;
  status2xx: number;
  status3xx: number;
  status4xx: number;
  status5xx: number;
  avgRps: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  minLatencyMs: number;
  maxLatencyMs: number;
  maxConcurrency: number;
  durationMs: number;
  successRate: number;
  blockedRate: number;
}

export interface AllowedTarget {
  key: string;
  label: string;
  method: HttpMethod;
  path: string;
  description: string;
  defaultBody: string | null;
}

export interface LoadTestLimits {
  maxRequests: number;
  maxConcurrency: number;
  maxDurationSeconds: number;
  maxRps: number;
  minTimeoutMs: number;
  maxTimeoutMs: number;
}

export interface LoadTestOptions {
  targets: AllowedTarget[];
  scenarios: TestScenario[];
  limits: LoadTestLimits;
  allowedHeaders: string[];
}

export interface StartLoadTestRequest {
  name: string | null;
  scenario: TestScenario;
  targetKey: string;
  method: HttpMethod;
  totalRequests: number;
  concurrency: number;
  targetRps: number;
  durationSeconds: number;
  timeoutMs: number;
  rateLimitEnabled: boolean;
  requestBody: string | null;
  headers: Record<string, string> | null;
}

export interface RateLimitConfig {
  enabled: boolean;
  capacity: number;
  refillTokens: number;
  refillPeriodSeconds: number;
  clientStrategy: ClientStrategy;
  updatedAt?: string;
  summary?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ExecutionSummary {
  execution: Execution;
  result: TestResult | null;
}

export interface ExecutionDetail extends ExecutionSummary {
  snapshots: MetricSnapshot[];
}

export interface ComparisonMetric {
  key: string;
  label: string;
  unit: string;
  valueA: number;
  valueB: number;
  difference: number;
  percentChange: number | null;
  preference: 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'NEUTRAL';
}

export interface Comparison {
  a: ExecutionDetail;
  b: ExecutionDetail;
  metrics: ComparisonMetric[];
}

export interface InfrastructureMetrics {
  capturedAt: string;
  processCpuUsage: number | null;
  systemCpuUsage: number | null;
  availableProcessors: number | null;
  heapUsedBytes: number | null;
  heapMaxBytes: number | null;
  nonHeapUsedBytes: number | null;
  liveThreads: number | null;
  peakThreads: number | null;
  daemonThreads: number | null;
  httpRequestCount: number | null;
  httpMeanLatencyMs: number | null;
  httpMaxLatencyMs: number | null;
  dbActiveConnections: number | null;
  dbIdleConnections: number | null;
  dbPendingConnections: number | null;
  dbMaxConnections: number | null;
  uptimeSeconds: number | null;
}

export const SCENARIO_LABELS: Record<TestScenario, string> = {
  CONSTANT: 'Carga constante',
  RAMP_UP: 'Carga progressiva',
  SPIKE: 'Teste de pico',
  RATE_LIMIT: 'Teste de Rate Limiting',
};

export const STATUS_LABELS: Record<TestStatus, string> = {
  PENDING: 'Pendente',
  RUNNING: 'Em execução',
  COMPLETED: 'Concluído',
  STOPPED: 'Interrompido',
  FAILED: 'Falhou',
};
