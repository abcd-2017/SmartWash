<template>
  <div>
    <el-input
      v-model="addressDisplay"
      placeholder="点击选择位置"
      readonly
      clearable
      @clear="handleClear"
      @click="openPicker"
    >
      <template #append>
        <el-button aria-label="打开地图选点" @click="openPicker">
          <el-icon><Location /></el-icon>
        </el-button>
      </template>
    </el-input>

    <el-dialog
      v-model="dialogVisible"
      title="选择位置"
      width="min(800px, 94vw)"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <div class="amap-picker-container">
        <div class="amap-search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索地点..."
            clearable
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch">搜索</el-button>
            </template>
          </el-input>
        </div>
        <div class="amap-map-wrapper">
          <div v-if="!mapError" ref="mapContainer" class="amap-map-container"></div>
          <!-- 地图加载失败占位：不再留 400px 空白，引导改用手动填写经纬度 -->
          <div v-else class="amap-map-error">
            <el-icon :size="28"><WarningFilled /></el-icon>
            <p>地图服务不可用，请手动填写经纬度</p>
          </div>
          <div v-if="searchResults.length" class="amap-search-results">
            <!-- 浮层头：标注条数且可关闭，不再常驻遮挡地图 -->
            <div class="amap-search-results__head">
              <span>搜索结果 {{ searchResults.length }} 条</span>
              <el-button link aria-label="关闭搜索结果" @click="searchResults = []">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
            <div class="amap-search-results__list">
              <div
                v-for="item in searchResults"
                :key="item.id"
                class="amap-search-item"
                role="button"
                tabindex="0"
                @click="selectSearchResult(item)"
                @keydown.enter.prevent="selectSearchResult(item)"
                @keydown.space.prevent="selectSearchResult(item)"
              >
                <div class="amap-search-item-name">{{ item.name }}</div>
                <div class="amap-search-item-address">{{ item.address }}</div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="selectedLocation" class="amap-location-info">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="经度">{{
              selectedLocation.longitude
            }}</el-descriptions-item>
            <el-descriptions-item label="纬度">{{
              selectedLocation.latitude
            }}</el-descriptions-item>
            <el-descriptions-item label="省">{{ selectedLocation.province }}</el-descriptions-item>
            <el-descriptions-item label="市">{{ selectedLocation.city }}</el-descriptions-item>
            <el-descriptions-item label="区">{{ selectedLocation.district }}</el-descriptions-item>
            <el-descriptions-item label="地址">{{ selectedLocation.address }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedLocation" @click="confirmSelection">
          确认选择
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue';
import { Location, WarningFilled, Close } from '@element-plus/icons-vue';
import AMapLoader from '@amap/amap-jsapi-loader';
import logger from '@/utils/logger';

const props = defineProps({
  modelValue: {
    type: Object,
    default: null,
  },
  address: {
    type: String,
    default: '',
  },
  schoolName: {
    type: String,
    default: '',
  },
  city: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['update:modelValue', 'update:address', 'change']);

const dialogVisible = ref(false);
const searchKeyword = ref('');
const searchResults = ref([]);
const selectedLocation = ref(null);
const addressDisplay = ref('');
const mapContainer = ref(null);
const searchCity = ref('');
// 地图初始化失败标志（key 未配置/网络异常）：容器内渲染占位而非留白
const mapError = ref(false);

let mapInstance = null;
let markerInstance = null;
let geocoderInstance = null;
let AMapRef = null;

// 同步外部值 — 只在有坐标时（地图选点确认后）才显示地址
watch(
  () => props.modelValue,
  (val) => {
    if (val && val.longitude && val.latitude) {
      selectedLocation.value = { ...val };
      addressDisplay.value = props.address || '';
    } else {
      selectedLocation.value = null;
      addressDisplay.value = '';
    }
  },
  { immediate: true }
);

async function openPicker() {
  // 构造搜索关键词：城市 + 学校名
  const parts = [];
  if (props.city) parts.push(props.city);
  if (props.schoolName) parts.push(props.schoolName);
  searchKeyword.value = parts.join(' ');
  searchCity.value = props.city || '';

  dialogVisible.value = true;
  await nextTick();
  await initMap();

  // 如果没有已选位置，但有城市信息，先定位到城市
  if (!selectedLocation.value && props.city) {
    geocodeCity(props.city);
  }

  // 如果有搜索关键词，自动搜索
  if (searchKeyword.value.trim()) {
    handleSearch();
  }
}

async function initMap() {
  if (mapInstance) return;

  mapError.value = false;
  try {
    AMapRef = await AMapLoader.load({
      key: import.meta.env.VITE_AMAP_KEY,
      version: '2.0',
      plugins: ['AMap.Geocoder', 'AMap.PlaceSearch', 'AMap.AutoComplete'],
    });

    mapInstance = new AMapRef.Map(mapContainer.value, {
      zoom: 12,
      center: selectedLocation.value
        ? [selectedLocation.value.longitude, selectedLocation.value.latitude]
        : [116.397428, 39.90923],
    });

    geocoderInstance = new AMapRef.Geocoder();

    mapInstance.on('click', async (e) => {
      const lnglat = e.lnglat;
      await updateLocationByLnglat(lnglat.lng, lnglat.lat);
    });

    if (selectedLocation.value) {
      placeMarker(selectedLocation.value.longitude, selectedLocation.value.latitude);
    }
  } catch (error) {
    // 失败在地图容器内留占位反馈，不静默留 400px 空白
    mapError.value = true;
    logger.error('地图初始化失败:', error);
  }
}

// 地理编码城市名 → 经纬度，用于定位地图中心
function geocodeCity(cityName) {
  if (!geocoderInstance || !cityName) return;
  geocoderInstance.getLocation(cityName, (status, result) => {
    if (status === 'complete' && result.geocodes && result.geocodes.length) {
      const loc = result.geocodes[0].location;
      mapInstance.setCenter([loc.lng, loc.lat]);
      mapInstance.setZoom(11);
    }
  });
}

async function updateLocationByLnglat(lng, lat) {
  placeMarker(lng, lat);

  geocoderInstance.getAddress([lng, lat], (status, result) => {
    if (status === 'complete' && result.regeocode) {
      const addr = result.regeocode;
      const addrComponent = addr.addressComponent;
      selectedLocation.value = {
        longitude: lng,
        latitude: lat,
        province: addrComponent.province || '',
        city: addrComponent.city || '',
        district: addrComponent.district || '',
        address: addr.formattedAddress || '',
      };
    } else {
      selectedLocation.value = {
        longitude: lng,
        latitude: lat,
        province: '',
        city: '',
        district: '',
        address: '',
      };
    }
  });
}

function placeMarker(lng, lat) {
  if (markerInstance) {
    markerInstance.setPosition([lng, lat]);
  } else {
    markerInstance = new AMapRef.Marker({
      position: [lng, lat],
      map: mapInstance,
    });
  }
  mapInstance.setCenter([lng, lat]);
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) return;
  // 地图库加载失败时不再触发搜索（避免空引用报错），占位提示已引导手动填写
  if (!AMapRef) return;

  const placeSearch = new AMapRef.PlaceSearch({
    pageSize: 10,
    pageIndex: 1,
    city: searchCity.value || undefined,
  });

  placeSearch.search(searchKeyword.value, (status, result) => {
    if (status === 'complete' && result.poiList) {
      searchResults.value = result.poiList.pois.map((poi) => ({
        id: poi.id,
        name: poi.name,
        address: poi.address,
        lng: poi.location.lng,
        lat: poi.location.lat,
      }));
      // 如果有结果，自动定位到第一个
      if (searchResults.value.length > 0) {
        const first = searchResults.value[0];
        placeMarker(first.lng, first.lat);
        mapInstance.setZoom(14);
      }
    } else {
      searchResults.value = [];
    }
  });
}

function selectSearchResult(item) {
  searchResults.value = [];
  searchKeyword.value = item.name;
  updateLocationByLnglat(item.lng, item.lat);
  mapInstance.setZoom(16);
}

function confirmSelection() {
  if (!selectedLocation.value) return;
  emit('update:modelValue', {
    longitude: selectedLocation.value.longitude,
    latitude: selectedLocation.value.latitude,
  });
  emit('update:address', selectedLocation.value.address);
  emit('change', selectedLocation.value);
  addressDisplay.value = selectedLocation.value.address;
  dialogVisible.value = false;
}

function handleClear() {
  selectedLocation.value = null;
  emit('update:modelValue', null);
  emit('update:address', '');
  emit('change', null);
}

function handleDialogClosed() {
  if (mapInstance) {
    mapInstance.destroy();
    mapInstance = null;
    markerInstance = null;
    geocoderInstance = null;
  }
  searchResults.value = [];
  searchKeyword.value = '';
  searchCity.value = '';
}
</script>

<style scoped>
.amap-picker-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.amap-map-wrapper {
  position: relative;
}

.amap-map-container {
  width: 100%;
  height: 400px;
  border-radius: 8px;
  overflow: hidden;
}

/* 地图加载失败占位：与地图容器等高，弱底色块 + 提示文案 */
.amap-map-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 400px;
  border-radius: 8px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
}

.amap-map-error p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.amap-search-results {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  width: 280px;
  background: var(--bg-card);
  box-shadow: var(--shadow-popover); /* 气泡浮层走阴影词汇表（原字面量不在词汇表内） */
  border-radius: 8px;
  z-index: 10;
}

/* 浮层头固定、列表区滚动：关闭后不再遮挡地图 */
.amap-search-results__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 6px 8px 6px 14px;
  font-size: 12px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-subtle);
}

.amap-search-results__list {
  max-height: 364px;
  overflow-y: auto;
}

.amap-search-item {
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-subtle);
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.amap-search-item:hover {
  background: var(--bg-subtle);
}

.amap-search-item-name {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

.amap-search-item-address {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.amap-location-info {
  margin-top: 4px;
}
</style>
