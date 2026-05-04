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
    version: '2.0',
    plugins: ['AMap.DistrictSearch']
  })
  return AMapInstance
}

// 查询行政区数据
function searchDistrict(keyword, level) {
  return new Promise(async (resolve) => {
    try {
      const AMap = await getAMap()
      const district = new AMap.DistrictSearch({
        level: level === 0 ? 'province' : level === 1 ? 'city' : 'district',
        subdistrict: 1
      })
      district.search(keyword, (status, result) => {
        if (status === 'complete') {
          const list = result.districtList[0].districtList || []
          resolve(list.map(item => ({
            name: item.name,
            level: item.level,
            leaf: level >= 2,
            children: []
          })))
        } else {
          resolve([])
        }
      })
    } catch {
      resolve([])
    }
  })
}

// 在 options 树中查找节点
function findNode(list, name) {
  return list.find(item => item.name === name)
}

// 加载完整路径到 options（供外部设置值时使用）
async function loadFullPath(path) {
  if (!path || path.length === 0) return

  // 确保省份列表已加载
  if (options.value.length === 0) {
    options.value = await searchDistrict('中国', 0)
  }

  // 加载城市
  if (path.length >= 2) {
    const provinceNode = findNode(options.value, path[0])
    if (provinceNode && (!provinceNode.children || provinceNode.children.length === 0)) {
      provinceNode.children = await searchDistrict(path[0], 1)
    }
  }

  // 加载区县
  if (path.length >= 3) {
    const provinceNode = findNode(options.value, path[0])
    if (provinceNode && provinceNode.children) {
      const cityNode = findNode(provinceNode.children, path[1])
      if (cityNode && (!cityNode.children || cityNode.children.length === 0)) {
        cityNode.children = await searchDistrict(path[1], 2)
      }
    }
  }
}

// 初始化省份数据
onMounted(async () => {
  options.value = await searchDistrict('中国', 0)

  // 如果初始有值，加载完整路径
  if (props.modelValue && props.modelValue.length > 0) {
    await loadFullPath(props.modelValue)
    selected.value = [...props.modelValue]
  }
})

// 同步外部 v-model — 当外部设置值时（如地图选点），自动加载路径
watch(() => props.modelValue, async (val) => {
  if (!val || val.length === 0) {
    selected.value = []
    return
  }
  await loadFullPath(val)
  selected.value = [...val]
})

function handleChange(val) {
  emit('update:modelValue', val || [])
  emit('change', val || [])
}

// 暴露方法供外部设置值
async function setValue(val) {
  if (val && val.length > 0) {
    await loadFullPath(val)
  }
  selected.value = val || []
}

defineExpose({ setValue })
</script>
