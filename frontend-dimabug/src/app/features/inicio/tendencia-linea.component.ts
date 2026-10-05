import { Component, Input } from '@angular/core';
import { ChartConfiguration, ChartData, Plugin, ScriptableContext } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { GraficoEscala } from '../../core/services/inicio.service';

const guiaVertical: Plugin = {
  id: 'guiaVertical',
  afterDatasetsDraw(chart) {
    const activos = chart.tooltip?.getActiveElements() ?? [];
    if (!activos.length) {
      return;
    }
    const x = activos[0].element.x;
    const { top, bottom } = chart.chartArea;
    const ctx = chart.ctx;
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(x, top);
    ctx.lineTo(x, bottom);
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = 'rgba(26, 58, 140, 0.4)';
    ctx.stroke();
    ctx.restore();
  },
};

function rellenoCurva(ctx: ScriptableContext<'line'>): CanvasGradient | string {
  const { chartArea, ctx: canvas } = ctx.chart;
  if (!chartArea) {
    return 'rgba(30, 79, 214, 0.2)';
  }
  const degradado = canvas.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
  degradado.addColorStop(0, 'rgba(30, 79, 214, 0.36)');
  degradado.addColorStop(0.55, 'rgba(26, 58, 140, 0.12)');
  degradado.addColorStop(1, 'rgba(30, 79, 214, 0)');
  return degradado;
}

function trazoCurva(ctx: ScriptableContext<'line'>): CanvasGradient | string {
  const { chartArea, ctx: canvas } = ctx.chart;
  if (!chartArea) {
    return '#1e4fd6';
  }
  const degradado = canvas.createLinearGradient(chartArea.left, 0, chartArea.right, 0);
  degradado.addColorStop(0, '#163070');
  degradado.addColorStop(0.55, '#1e4fd6');
  degradado.addColorStop(1, '#3b6ee8');
  return degradado;
}

@Component({
  selector: 'app-tendencia-linea',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './tendencia-linea.component.html',
  styleUrl: './tendencia-linea.component.css',
})
export class TendenciaLineaComponent {
  readonly tipo = 'line' as const;
  readonly plugins = [guiaVertical];
  data: ChartData<'line'> = { labels: [], datasets: [] };
  options: ChartConfiguration<'line'>['options'] = this.opciones(4);

  @Input({ required: true })
  set grafico(value: GraficoEscala) {
    const valores = value.puntos.map((punto) => punto.total);
    const tope = Math.max(...valores, 1);
    this.options = this.opciones(Math.max(tope + 1, Math.ceil(tope * 1.25)));
    this.data = {
      labels: value.puntos.map((punto) => punto.etiqueta),
      datasets: [
        {
          label: 'Publicados',
          data: valores,
          borderColor: trazoCurva,
          backgroundColor: rellenoCurva,
          pointBackgroundColor: '#ffffff',
          pointBorderColor: '#1e4fd6',
          pointBorderWidth: 2.5,
          pointRadius: 5.5,
          pointHoverRadius: 8,
          pointHoverBackgroundColor: '#1e4fd6',
          pointHoverBorderColor: '#ffffff',
          pointHitRadius: 16,
          fill: 'origin',
          tension: 0.42,
          cubicInterpolationMode: 'monotone',
          borderWidth: 3.5,
          borderCapStyle: 'round',
          borderJoinStyle: 'round',
        },
      ],
    };
  }

  private opciones(sugerido: number): ChartConfiguration<'line'>['options'] {
    return {
      responsive: true,
      maintainAspectRatio: false,
      layout: { padding: { top: 10, right: 10, left: 4, bottom: 4 } },
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        filler: { propagate: false },
        tooltip: {
          mode: 'index',
          intersect: false,
          backgroundColor: '#163070',
          titleColor: '#ffffff',
          bodyColor: '#d7e0f5',
          padding: 12,
          cornerRadius: 10,
          displayColors: false,
          callbacks: {
            label: (item) => ` ${item.parsed.y} publicados`,
          },
        },
      },
      elements: {
        line: { fill: 'origin' },
      },
      scales: {
        y: {
          beginAtZero: true,
          suggestedMax: sugerido,
          ticks: { precision: 0, color: '#7b8aa8', font: { size: 11 }, padding: 8 },
          grid: { color: '#eef2f7' },
          border: { display: false },
        },
        x: {
          ticks: { color: '#5b6b86', font: { size: 12, weight: 600 } },
          grid: { display: false },
          border: { display: false },
        },
      },
    };
  }
}
