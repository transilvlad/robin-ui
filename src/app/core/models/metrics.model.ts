export interface MetricsResponse {
  timestamp: number;
  metrics: Metric[];
}

export interface Metric {
  name: string;
  type: 'counter' | 'gauge' | 'histogram' | 'timer';
  value: number;
  unit?: string;
  tags?: Record<string, string>;
}

export interface TimeSeriesData {
  labels: string[];
  datasets: TimeSeriesDataset[];
}

export interface TimeSeriesDataset {
  label: string;
  data: number[];
  borderColor?: string;
  backgroundColor?: string;
}
