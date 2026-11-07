<template>
  <div class="trend-chart">
    <div v-if="hasData" ref="containerRef" class="trend-chart__canvas"></div>
    <div v-else class="trend-chart__empty">
      <el-empty :image-size="60" description="暂无数据" />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
// echarts/core 按需注册（树摇）：趋势图仅需 折线/柱 + 网格/悬浮提示 + Canvas 渲染，
// 避免整包 echarts 进入消费方 chunk（DivUsage.vue 场景特殊用整包，此处不沿用）
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const props = defineProps({
  // 图表数据：[{ date: 'yyyy-MM-dd', value: number }]，调用方从趋势接口行映射而来
  data: { type: Array, default: () => [] },
  // 图表类型：'line' 折线（金额类）| 'bar' 柱图（数量类）
  type: {
    type: String,
    default: 'line',
    validator: (v) => ['line', 'bar'].includes(v),
  },
  // 主色：支持 CSS 变量（如 var(--color-primary)）或任意 CSS 颜色值，默认取设计令牌靛蓝
  color: { type: String, default: 'var(--color-primary)' },
  // tooltip 数值格式化函数（如金额千分位、数量带单位）；缺省原样展示
  valueFormatter: { type: Function, default: null },
});

const containerRef = ref(null);
let chart = null;
let resizeObserver = null;

const hasData = computed(() => Array.isArray(props.data) && props.data.length > 0);

// 减少动态效果：图表动画直接关闭（DESIGN.md 全局降级要求）
const reducedMotion =
  typeof window !== 'undefined' &&
  !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// canvas 无法直接使用 CSS 变量，构建 option 时解析设计令牌一次（取不到时回退令牌字面值）
const readCssVar = (name, fallback) => {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
};
const resolveColor = (value) => {
  if (value && value.startsWith('var(')) {
    return readCssVar(value.slice(4, -1).trim(), '#6366f1');
  }
  return value || '#6366f1';
};

const buildOption = () => {
  const mainColor = resolveColor(props.color);
  const axisLabelColor = readCssVar('--text-muted', '#94a3b8');
  const axisLineColor = readCssVar('--border-color', '#e2e8f0');
  const splitLineColor = readCssVar('--border-subtle', '#f1f5f9');

  return {
    animation: !reducedMotion,
    animationDuration: 320,
    animationDurationUpdate: 220,
    animationEasing: 'cubicOut',
    grid: { left: 8, right: 16, top: 20, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: (params) => {
        const point = Array.isArray(params) ? params[0] : params;
        const value = props.valueFormatter ? props.valueFormatter(point.value) : point.value;
        return `${point.axisValue}<br/>${point.marker}${value}`;
      },
    },
    xAxis: {
      type: 'category',
      data: props.data.map((d) => d.date),
      boundaryGap: props.type === 'bar',
      axisLine: { lineStyle: { color: axisLineColor } },
      axisTick: { show: false },
      axisLabel: { color: axisLabelColor, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: axisLabelColor },
      splitLine: { lineStyle: { color: splitLineColor } },
    },
    series: [
      props.type === 'bar'
        ? {
            type: 'bar',
            data: props.data.map((d) => d.value),
            barMaxWidth: 18,
            itemStyle: { color: mainColor, borderRadius: [4, 4, 0, 0] },
          }
        : {
            type: 'line',
            data: props.data.map((d) => d.value),
            smooth: true,
            showSymbol: false,
            emphasis: { focus: 'series' },
            lineStyle: { width: 2, color: mainColor },
            itemStyle: { color: mainColor },
            // 淡彩底面积：与设计系统「淡彩底语言」一致，低透明度不抢数据主体
            areaStyle: { color: mainColor, opacity: 0.08 },
          },
    ],
  };
};

// 数据/类型/色变化时重建 option；容器首次出现（空态 → 有数据）时初始化实例
const ensureChart = async () => {
  if (!hasData.value) {
    return;
  }
  await nextTick();
  if (!containerRef.value) {
    return;
  }
  if (!chart) {
    chart = echarts.init(containerRef.value);
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => chart?.resize());
      resizeObserver.observe(containerRef.value);
    }
  }
  // notMerge：类型/颜色切换时全量替换，避免残留旧系列配置
  chart.setOption(buildOption(), true);
};

watch(() => [props.data, props.type, props.color], ensureChart);

onMounted(ensureChart);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.trend-chart {
  width: 100%;
}

.trend-chart__canvas {
  width: 100%;
  height: 260px;
}

.trend-chart__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 260px;
}
</style>
