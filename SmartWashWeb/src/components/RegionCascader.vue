<template>
  <div class="region-cascader">
    <el-cascader
      v-model="selected"
      :options="options"
      :props="cascaderProps"
      :placeholder="cascaderPlaceholder"
      clearable
      style="width: 100%"
      @change="handleChange"
    />
    <!-- 加载失败显式反馈：不再静默空列表，引导刷新重试 -->
    <div v-if="loadFailed" class="region-load-error">
      <el-icon :size="12"><WarningFilled /></el-icon>
      <span>行政区划加载失败，请刷新重试</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { WarningFilled } from '@element-plus/icons-vue';
import AMapLoader from '@amap/amap-jsapi-loader';
import logger from '@/utils/logger';

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['update:modelValue', 'change']);

const selected = ref([]);
const options = ref([]);
// 省份列表加载态：加载中/失败落到 placeholder（el-cascader 无内置 loading 属性），失败另给下方提示
const loading = ref(false);
const loadFailed = ref(false);

const cascaderPlaceholder = computed(() => {
  if (loading.value) return '行政区划加载中...';
  if (loadFailed.value) return '行政区划加载失败，请刷新重试';
  return '请选择省/市/区';
});

const cascaderProps = {
  value: 'name',
  label: 'name',
  children: 'children',
  lazy: true,
  lazyLoad: async (node, resolve) => {
    const { level, data } = node;
    try {
      const AMap = await getAMap();
      const district = new AMap.DistrictSearch({
        level: level === 0 ? 'province' : level === 1 ? 'city' : 'district',
        subdistrict: 1,
      });
      const keyword = level === 0 ? '中国' : data.name;
      district.search(keyword, (status, result) => {
        if (status === 'complete') {
          const list = result.districtList[0].districtList || [];
          const nodes = list.map((item) => ({
            name: item.name,
            level: item.level,
            leaf: level >= 2,
          }));
          resolve(nodes);
        } else {
          resolve([]);
        }
      });
    } catch (error) {
      // 静默降级为空列表，但必须留痕：高德未配置（VITE_AMAP_KEY 为空）属已知预期失败，
      // 不弹 toast 防噪音，日志用于区分「该级无下级」与「加载失败」
      logger.warn('行政区查询失败，级联降级为空列表:', `level=${level}`, error);
      resolve([]);
    }
  },
};

let AMapInstance = null;

async function getAMap() {
  if (AMapInstance) return AMapInstance;
  AMapInstance = await AMapLoader.load({
    key: import.meta.env.VITE_AMAP_KEY,
    version: '2.0',
    plugins: ['AMap.DistrictSearch'],
  });
  return AMapInstance;
}

// 查询行政区数据（不用 async Promise executor：搜索结果经回调包装成 Promise 后再返回）
async function searchDistrict(keyword, level) {
  try {
    const AMap = await getAMap();
    const district = new AMap.DistrictSearch({
      level: level === 0 ? 'province' : level === 1 ? 'city' : 'district',
      subdistrict: 1,
    });
    return await new Promise((resolve) => {
      district.search(keyword, (status, result) => {
        if (status === 'complete') {
          const list = result.districtList[0].districtList || [];
          resolve(
            list.map((item) => ({
              name: item.name,
              level: item.level,
              leaf: level >= 2,
              children: [],
            }))
          );
        } else {
          resolve([]);
        }
      });
    });
  } catch (error) {
    // 同 lazyLoad：静默降级但留痕（高德 key 为空属预期失败，不弹 toast）
    logger.warn('行政区查询失败，返回空列表:', `keyword=${keyword}`, `level=${level}`, error);
    return [];
  }
}

function findNode(list, name) {
  return list.find((item) => item.name === name);
}

// 加载完整路径到 options（供外部设置值时使用）
async function loadFullPath(path) {
  if (!path || path.length === 0) return;

  // 确保省份列表已加载
  if (options.value.length === 0) {
    options.value = await searchDistrict('中国', 0);
  }

  // 加载城市
  if (path.length >= 2) {
    const provinceNode = findNode(options.value, path[0]);
    if (provinceNode && (!provinceNode.children || provinceNode.children.length === 0)) {
      provinceNode.children = await searchDistrict(path[0], 1);
    }
  }

  // 加载区县
  if (path.length >= 3) {
    const provinceNode = findNode(options.value, path[0]);
    if (provinceNode && provinceNode.children) {
      const cityNode = findNode(provinceNode.children, path[1]);
      if (cityNode && (!cityNode.children || cityNode.children.length === 0)) {
        cityNode.children = await searchDistrict(path[1], 2);
      }
    }
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    options.value = await searchDistrict('中国', 0);
    // 省份列表不可能合法为空，空结果即视为加载失败（高德 key 未配置/网络异常）
    loadFailed.value = options.value.length === 0;
  } finally {
    loading.value = false;
  }

  // 如果初始有值，加载完整路径
  if (props.modelValue && props.modelValue.length > 0) {
    await loadFullPath(props.modelValue);
    selected.value = [...props.modelValue];
  }
});

// 同步外部 v-model — 当外部设置值时（如地图选点），自动加载路径
watch(
  () => props.modelValue,
  async (val) => {
    if (!val || val.length === 0) {
      selected.value = [];
      return;
    }
    await loadFullPath(val);
    selected.value = [...val];
  }
);

function handleChange(val) {
  emit('update:modelValue', val || []);
  emit('change', val || []);
}

// 暴露方法供外部设置值
async function setValue(val) {
  if (val && val.length > 0) {
    await loadFullPath(val);
  }
  selected.value = val || [];
}

defineExpose({ setValue });
</script>

<style scoped>
/* 失败提示：Micro 档 12px，随表单项流式排布 */
.region-load-error {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-danger);
}
</style>
