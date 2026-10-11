import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { BarChart, LineChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([LineChart, BarChart, GridComponent, LegendComponent, TooltipComponent, MarkLineComponent,
  CanvasRenderer]);

export type ChartOption = echarts.EChartsCoreOption;

/** Wrapper mínimo do Apache ECharts (import modular), redimensionável e reativo a Signals. */
@Component({
  selector: 'app-chart',
  template: `<div #host class="chart" [style.height.px]="height()" role="img" [attr.aria-label]="label()"></div>`,
  styles: `:host { display: block; } .chart { width: 100%; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EChart {
  readonly options = input.required<ChartOption>();
  readonly height = input(280);
  readonly label = input('Gráfico');

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private chart?: echarts.ECharts;

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      this.chart = echarts.init(this.host().nativeElement, undefined, { renderer: 'canvas' });
      this.chart.setOption(this.options());
      const observer = new ResizeObserver(() => this.chart?.resize());
      observer.observe(this.host().nativeElement);
      destroyRef.onDestroy(() => {
        observer.disconnect();
        this.chart?.dispose();
      });
    });
    effect(() => {
      const options = this.options();
      // Merge padrão do ECharts preserva as animações entre atualizações
      this.chart?.setOption(options, { lazyUpdate: true });
    });
  }
}
