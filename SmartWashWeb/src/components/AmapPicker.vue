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
        <el-button @click="openPicker">
          <el-icon><Location /></el-icon>
        </el-button>
      </template>
    </el-input>

    <el-dialog
      v-model="dialogVisible"
      title="选择位置"
      width="800px"
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
          <div ref="mapContainer" class="amap-map-container"></div>
          <div v-if="searchResults.length" class="amap-search-results">
            <div
              v-for="item in searchResults"
              :key="item.id"
              class="amap-search-item"
              @click="selectSearchResult(item)"
            >
              <div class="amap-search-item-name">{{ item.name }}</div>
              <div class="amap-search-item-address">{{ item.address }}</div>
            </div>
          </div>
        </div>
        <div v-if="selectedLocation" class="amap-location-info">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="经度">{{ selectedLocation.longitude }}</el-descriptions-item>
            <el-descriptions-item label="纬度">{{ selectedLocation.latitude }}</el-descriptions-item>
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
import { ref, nextTick, watch } from 'vue'
import { Location } from '@element-plus/icons-vue'
import AMapLoader from '@amap/amap-jsapi-loader'

const props = defineProps({
  modelValue: {
    type: Object,
    default: null
  },
  address: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'update:address', 'change'])

const dialogVisible = ref(false)
const searchKeyword = ref('')
const searchResults = ref([])
const selectedLocation = ref(null)
const addressDisplay = ref('')
const mapContainer = ref(null)

let mapInstance = null
let markerInstance = null
let geocoderInstance = null
let AMapRef = null

// 同步外部值
watch(() => props.modelValue, (val) => {
  if (val && val.longitude && val.latitude) {
    selectedLocation.value = { ...val }
  }
}, { immediate: true })

watch(() => props.address, (val) => {
  addressDisplay.value = val || ''
}, { immediate: true })

async function openPicker() {
  dialogVisible.value = true
  await nextTick()
  await initMap()
}

async function initMap() {
  if (mapInstance) return

  AMapRef = await AMapLoader.load({
    key: import.meta.env.VITE_AMAP_KEY,
    version: '2.0',
    plugins: ['AMap.Geocoder', 'AMap.PlaceSearch', 'AMap.AutoComplete']
  })

  mapInstance = new AMapRef.Map(mapContainer.value, {
    zoom: 12,
    center: selectedLocation.value
      ? [selectedLocation.value.longitude, selectedLocation.value.latitude]
      : [116.397428, 39.90923] // 默认北京
  })

  geocoderInstance = new AMapRef.Geocoder()

  // 点击地图选点
  mapInstance.on('click', async (e) => {
    const lnglat = e.lnglat
    await updateLocationByLnglat(lnglat.lng, lnglat.lat)
  })

  // 如果有初始位置，标记
  if (selectedLocation.value) {
    placeMarker(selectedLocation.value.longitude, selectedLocation.value.latitude)
  }
}

async function updateLocationByLnglat(lng, lat) {
  placeMarker(lng, lat)

  // 逆地理编码获取地址信息
  geocoderInstance.getAddress([lng, lat], (status, result) => {
    if (status === 'complete' && result.regeocode) {
      const addr = result.regeocode
      const addrComponent = addr.addressComponent
      selectedLocation.value = {
        longitude: lng,
        latitude: lat,
        province: addrComponent.province || '',
        city: addrComponent.city || '',
        district: addrComponent.district || '',
        address: addr.formattedAddress || ''
      }
    } else {
      selectedLocation.value = {
        longitude: lng,
        latitude: lat,
        province: '',
        city: '',
        district: '',
        address: ''
      }
    }
  })
}

function placeMarker(lng, lat) {
  if (markerInstance) {
    markerInstance.setPosition([lng, lat])
  } else {
    markerInstance = new AMapRef.Marker({
      position: [lng, lat],
      map: mapInstance
    })
  }
  mapInstance.setCenter([lng, lat])
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) return

  const placeSearch = new AMapRef.PlaceSearch({
    pageSize: 10,
    pageIndex: 1
  })

  placeSearch.search(searchKeyword.value, (status, result) => {
    if (status === 'complete' && result.poiList) {
      searchResults.value = result.poiList.pois.map(poi => ({
        id: poi.id,
        name: poi.name,
        address: poi.address,
        lng: poi.location.lng,
        lat: poi.location.lat
      }))
    } else {
      searchResults.value = []
    }
  })
}

function selectSearchResult(item) {
  searchResults.value = []
  searchKeyword.value = item.name
  updateLocationByLnglat(item.lng, item.lat)
  mapInstance.setZoom(16)
}

function confirmSelection() {
  if (!selectedLocation.value) return
  emit('update:modelValue', {
    longitude: selectedLocation.value.longitude,
    latitude: selectedLocation.value.latitude
  })
  emit('update:address', selectedLocation.value.address)
  emit('change', selectedLocation.value)
  addressDisplay.value = selectedLocation.value.address
  dialogVisible.value = false
}

function handleClear() {
  selectedLocation.value = null
  emit('update:modelValue', null)
  emit('update:address', '')
  emit('change', null)
}

function handleDialogClosed() {
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
    markerInstance = null
    geocoderInstance = null
  }
  searchResults.value = []
  searchKeyword.value = ''
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

.amap-search-results {
  position: absolute;
  top: 0;
  left: 0;
  width: 280px;
  max-height: 400px;
  overflow-y: auto;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  z-index: 10;
}

.amap-search-item {
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.2s;
}

.amap-search-item:hover {
  background: #f8fafc;
}

.amap-search-item-name {
  font-size: 14px;
  color: #0f172a;
  font-weight: 500;
}

.amap-search-item-address {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.amap-location-info {
  margin-top: 4px;
}
</style>
