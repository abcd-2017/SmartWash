<template>
  <el-cascader
    v-model="selected"
    :options="options"
    :props="cascaderProps"
    placeholder="请选择省/市/区"
    clearable
    style="width: 100%"
    @change="handleChange"
  />
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const selected = ref([])
const options = ref([])
const cascaderProps = {
  value: 'name',
  label: 'name',
  children: 'children',
  lazy: true,
  lazyLoad: async (node, resolve) => {
    const { level, data } = node
    try {
      const AMap = await getAMap()
      const district = new AMap.DistrictSearch({
        level: level === 0 ? 'province' : level === 1 ? 'city' : 'district',
        subdistrict: 1
      })
      const keyword = level === 0 ? '中国' : data.name
      district.search(keyword, (status, result) => {
        if (status === 'complete') {
          const list = result.districtList[0].districtList || []
          const nodes = list.map(item => ({
            name: item.name,
            level: item.level,
            leaf: level >= 2
          }))
          resolve(nodes)
        } else {
          resolve([])
        }
      })
    } catch {
      resolve([])
    }
  }
}

let AMapInstance = null

async function getAMap() {
  if (AMapInstance) return AMapInstance
  AMapInstance = await AMapLoader.load({
    key: import.meta.env.VITE_AMAP_KEY,
    version: '2.0'
  })
  return AMapInstance
}

// 初始化省份数据
onMounted(async () => {
  try {
    const AMap = await getAMap()
    const district = new AMap.DistrictSearch({
      level: 'province',
      subdistrict: 1
    })
    district.search('中国', (status, result) => {
      if (status === 'complete') {
        const list = result.districtList[0].districtList || []
        options.value = list.map(item => ({
          name: item.name,
          level: item.level,
          leaf: false
        }))
      }
    })
  } catch {
    // 静默失败
  }
})

// 同步外部 v-model
watch(() => props.modelValue, (val) => {
  selected.value = val || []
}, { immediate: true })

function handleChange(val) {
  emit('update:modelValue', val || [])
  emit('change', val || [])
}

// 暴露方法供外部设置值
function setValue(val) {
  selected.value = val || []
}

defineExpose({ setValue })
</script>
