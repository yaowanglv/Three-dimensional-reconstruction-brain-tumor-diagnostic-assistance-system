<template>
  <div class="dataview-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-content">
        <div class="title-section">
          <h2>数据可视化分析</h2>
          <p class="subtitle">检测数据统计与用户分析</p>
        </div>
        <div class="actions-section">
          <span class="update-time">数据更新于: {{ lastUpdateTime }}</span>
          <el-button 
            type="primary" 
            size="small" 
            :icon="Refresh" 
            :loading="loadingStatus.tumorType || loadingStatus.userPrediction"
            @click="handleRefresh"
          >
            刷新数据
          </el-button>
        </div>
      </div>
    </div>

    <!-- 第一行：症状统计 + 用户占比 -->
    <el-row :gutter="20" class="row-section">
      <!-- 左侧：症状检测统计柱状图 -->
      <el-col :span="12">
        <el-card class="chart-card" v-loading="loadingStatus.tumorType" element-loading-text="加载真实数据中...">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><Histogram /></el-icon>
                不同结果的检测统计
                <el-tag class="status-tag" size="small" type="success">实时</el-tag>
              </span>
            </div>
          </template>
          <div ref="tumorTypeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 右侧：用户预测占比饼图 -->
      <el-col :span="12">
        <el-card class="chart-card" v-loading="loadingStatus.userPrediction" element-loading-text="加载真实数据中...">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><PieChart /></el-icon>
                不同用户的预测占比
                <el-tag class="status-tag" size="small" type="success">实时</el-tag>
              </span>
            </div>
          </template>
          <div class="pie-chart-wrapper">
            <div ref="userPieChartRef" class="chart-container pie-chart"></div>
            <!-- 详细数值列表 -->
            <div class="pie-data-list">
              <div v-for="(item, index) in userPredictionData" :key="index" class="pie-data-item">
                <span class="user-name">{{ item.userName }}</span>
                <span class="user-count">{{ item.count }}次</span>
                <span class="user-percentage">({{ item.percentage }}%)</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：用户置信度 + 实时日志 -->
    <el-row :gutter="20" class="row-section">
      <!-- 左侧：用户平均置信度雷达图 -->
      <el-col :span="12">
        <el-card class="chart-card" v-loading="loadingStatus.userConfidence" element-loading-text="加载真实数据中...">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><Aim /></el-icon>
                不同用户间的平均置信度
                <el-tag class="status-tag" size="small" type="success">实时</el-tag>
              </span>
            </div>
          </template>
          <div class="radar-wrapper">
            <div ref="userRadarChartRef" class="chart-container radar-chart"></div>
            <!-- 置信度数值显示 -->
            <div class="confidence-list">
              <div v-for="(item, index) in userConfidenceData" :key="index" class="confidence-item">
                <div class="confidence-value">{{ item.value }}%</div>
                <div class="confidence-user">{{ item.name }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：实时预测信息表格 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><List /></el-icon>
                实时预测信息
              </span>
              <el-tag size="small" type="success">实时更新</el-tag>
            </div>
          </template>
          <el-table :data="realtimeLogs" class="realtime-table" height="320" v-loading="logLoading">
            <el-table-column prop="userName" label="用户名" width="100" />
            <el-table-column prop="modelName" label="权重模型" show-overflow-tooltip min-width="120">
              <template #default="{ row }">
                {{ getModelName(row.modelName) }}
              </template>
            </el-table-column>
            <el-table-column prop="confThreshold" label="最小阈值" width="90">
              <template #default="{ row }">
                {{ row.confThreshold?.toFixed(2) || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="aiModel" label="AI助手" width="100">
              <template #default="{ row }">
                <el-tag
                  v-if="formatAiModel(row.aiModel)"
                  size="small"
                  class="ai-model-tag"
                  :class="getAiModelTagClass(row.aiModel)"
                >
                  {{ formatAiModel(row.aiModel) }}
                </el-tag>
                <span v-else class="text-gray">未使用</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间戳" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三行：近十天预测趋势 -->
    <el-row class="row-section">
      <el-col :span="24">
        <el-card class="chart-card" v-loading="loadingStatus.dailyTrend" element-loading-text="加载真实数据中...">
          <template #header>
            <div class="card-header">
              <span>
                <el-icon><TrendCharts /></el-icon>
                近十天总预测量趋势
                <el-tag class="status-tag" size="small" type="success">实时</el-tag>
              </span>
            </div>
          </template>
          <div ref="dailyTrendChartRef" class="chart-container trend-chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Histogram, PieChart, Aim, List, TrendCharts, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request.js'
import { fetchModels } from '@/utils/config'
import * as echarts from 'echarts'

// ==================== 响应式数据 ====================

const tumorTypeChartRef = ref(null)
const userPieChartRef = ref(null)
const userRadarChartRef = ref(null)
const dailyTrendChartRef = ref(null)

const tumorTypeChart = ref(null)
const userPieChart = ref(null)
const userRadarChart = ref(null)
const dailyTrendChart = ref(null)

const userPredictionData = ref([])
const userConfidenceData = ref([])
const realtimeLogs = ref([])
const modelDisplayNameMap = ref({})
const logLoading = ref(false)
const excludedStatsUsers = new Set(['批量分割用户', '三维重建用户'])
const modelConfigPages = ['detect', 'mask', 'multi']

// 加载状态
const loadingStatus = ref({
  tumorType: false,
  userPrediction: false,
  userConfidence: false,
  dailyTrend: false,
  realtimeLogs: false
})

// 数据最后更新时间
const lastUpdateTime = ref('-')

// 定时器
let refreshTimer = null
let statsRefreshTimer = null

// ==================== 图表初始化 ====================

// 初始化症状类型柱状图
const initTumorTypeChart = (data) => {
  if (!tumorTypeChartRef.value) return
  
  tumorTypeChart.value = echarts.init(tumorTypeChartRef.value)
  
  // 定义每个症状类型的渐变色 - 统一主题配色
  const colorMap = {
    '正常': ['#2f8f5b', '#8fd0ad'],         // 绿色 - 健康
    '神经胶质瘤': ['#c2413b', '#e09a95'],   // 红色 - 严重
    '脑膜瘤': ['#b7791f', '#d9b56b'],       // 暖色 - 注意
    '垂体瘤': ['#256f7f', '#7fb0ba']        // 主色 - 一般
  }
  
  // 为每个数据点配置颜色
  const seriesData = data.categories.map((category, index) => {
    const colors = colorMap[category] || ['#256f7f', '#7fb0ba']
    return {
      value: data.values[index],
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: colors[0] },
          { offset: 1, color: colors[1] }
        ]),
        borderRadius: [8, 8, 0, 0],  // 顶部圆角
        shadowColor: 'rgba(0, 0, 0, 0.1)',
        shadowBlur: 8,
        shadowOffsetY: 3
      }
    }
  })
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { 
        type: 'shadow',
        shadowStyle: {
          color: 'rgba(0, 0, 0, 0.05)'
        }
      },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#dce6e3',
      borderWidth: 1,
      textStyle: {
        color: '#1f2933'
      },
      formatter: function(params) {
        const item = params[0]
        return `<div style="font-weight:bold;margin-bottom:5px;">${item.name}</div>
                <div style="display:flex;align-items:center;">
                  <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${item.color.colorStops ? item.color.colorStops[0].color : item.color};margin-right:8px;"></span>
                  检测数量：<b>${item.value}</b>
                </div>`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '5%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: data.categories,
      axisLabel: { 
        rotate: 0,
        fontSize: 13,
        fontWeight: 500,
        color: '#687782',
        interval: 0
      },
      axisLine: {
        lineStyle: {
          color: '#dce6e3'
        }
      },
      axisTick: {
        show: false
      }
    },
    yAxis: {
      type: 'value',
      name: '检测个数',
      nameTextStyle: {
        color: '#687782',
        fontSize: 12
      },
      axisLabel: {
        color: '#687782'
      },
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      },
      splitLine: {
        lineStyle: {
          color: '#dce6e3',
          type: 'dashed'
        }
      }
    },
    series: [{
      data: seriesData,
      type: 'bar',
      barWidth: '55%',
      label: {
        show: true,
        position: 'top',
        fontSize: 14,
        fontWeight: 'bold',
        color: '#1f2933',
        distance: 10
      },
      emphasis: {
        itemStyle: {
          shadowColor: 'rgba(0, 0, 0, 0.2)',
          shadowBlur: 12,
          shadowOffsetY: 5
        }
      }
    }]
  }
  
  tumorTypeChart.value.setOption(option)
}

// 初始化用户占比饼图
const initUserPieChart = (data) => {
  if (!userPieChartRef.value) return
  
  userPieChart.value = echarts.init(userPieChartRef.value)
  
  const pieData = data
    .filter(item => !excludedStatsUsers.has(String(item.userName || '').trim()))
    .map(item => ({
    name: item.userName,
    value: item.count
  }))
  
  const option = {
    color: ['#256f7f', '#2f8f5b', '#b7791f', '#c2413b', '#64748b', '#7f6a5d'],
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}次 ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      top: 'center'
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['60%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 10,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: {
        show: false
      },
      emphasis: {
        label: {
          show: true,
          fontSize: 14,
          fontWeight: 'bold'
        }
      },
      data: pieData
    }]
  }
  
  userPieChart.value.setOption(option)
}

// 初始化用户置信度雷达图
const initUserRadarChart = (data) => {
  if (!userRadarChartRef.value) return
  
  userRadarChart.value = echarts.init(userRadarChartRef.value)
  const confidenceRows = (data?.indicators || [])
    .map((indicator, index) => ({
      indicator,
      value: Number(data?.values?.[index]) || 0
    }))
    .filter(item => !excludedStatsUsers.has(String(item.indicator?.name || '').trim()))
  const confidenceValues = confidenceRows.map(item => item.value)
  const minConfidence = confidenceValues.length ? Math.min(...confidenceValues) : 0
  const maxConfidence = confidenceValues.length ? Math.max(...confidenceValues) : 100
  const radarMin = minConfidence >= 60 ? Math.max(0, Math.floor(minConfidence / 5) * 5 - 5) : 0
  const radarMax = maxConfidence >= 95 ? 100 : Math.min(100, Math.ceil(maxConfidence / 5) * 5 + 5)
  const radarIndicators = confidenceRows.map(item => ({
    ...item.indicator,
    min: radarMin,
    max: radarMax
  }))
  
  const option = {
    tooltip: {
      formatter: (params) => {
        const values = params.value || []
        return values
          .map((value, index) => {
            const name = radarIndicators[index]?.name || `用户${index + 1}`
            return `${name}：${Number(value || 0).toFixed(2)}%`
          })
          .join('<br/>')
      }
    },
    radar: {
      indicator: radarIndicators,
      radius: '65%',
      center: ['50%', '50%'],
      splitNumber: 4,
      axisName: {
        color: '#52606d',
        fontSize: 12
      },
      splitLine: {
        lineStyle: {
          color: ['#edf3f1', '#dce6e3', '#c7d6d1', '#aabfba']
        }
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(248, 251, 250, 0.9)', 'rgba(232, 243, 242, 0.6)']
        }
      }
    },
    series: [{
      type: 'radar',
      data: [{
        value: confidenceValues,
        name: '平均置信度',
        areaStyle: {
          color: 'rgba(37, 111, 127, 0.18)'
        },
        lineStyle: {
          color: '#256f7f',
          width: 2.5
        },
        itemStyle: {
          color: '#2f8f5b'
        }
      }]
    }]
  }
  
  userRadarChart.value.setOption(option)
  
  // 同步更新置信度列表数据
  userConfidenceData.value = confidenceRows.map((item, index) => ({
    name: item.indicator.name,
    value: (confidenceValues[index] ?? 0).toFixed(2)
  }))
}

// 初始化日趋势曲线图
const initDailyTrendChart = (data) => {
  if (!dailyTrendChartRef.value) return
  
  dailyTrendChart.value = echarts.init(dailyTrendChartRef.value)
  const dates = data?.dates || []
  const counts = (data?.counts || []).map(count => Math.trunc(Number(count) || 0))
  const trendLineColor = new echarts.graphic.LinearGradient(0, 0, 1, 0, [
    { offset: 0, color: '#b7791f' },
    { offset: 1, color: '#9a5b00' }
  ])
  
  const option = {
    tooltip: {
      trigger: 'item',
      borderColor: '#dce6e3',
      axisPointer: {
        type: 'line',
        label: { backgroundColor: '#9a5b00' }
      },
      formatter: (params) => {
        const value = Math.trunc(Number(params.value) || 0)
        return `${params.name}<br/>检测量：${value} 次`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates
    },
    yAxis: {
      type: 'value',
      name: '检测量',
      minInterval: 1,
      axisLabel: {
        formatter: (value) => Math.trunc(Number(value) || 0)
      }
    },
    series: [{
      name: '检测量',
      type: 'line',
      smooth: true,
      lineStyle: {
        width: 3,
        color: trendLineColor
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(255, 241, 214, 0.82)' },
          { offset: 0.55, color: 'rgba(183, 121, 31, 0.16)' },
          { offset: 1, color: 'rgba(255, 241, 214, 0.04)' }
        ])
      },
      data: counts,
      symbol: 'circle',
      symbolSize: 8,
      itemStyle: {
        color: '#9a5b00',
        borderColor: '#ffffff',
        borderWidth: 2
      },
      emphasis: {
        label: {
          show: true,
          position: 'top',
          color: '#9a5b00',
          fontWeight: 700,
          formatter: (params) => Math.trunc(Number(params.value) || 0)
        }
      }
    }]
  }
  
  dailyTrendChart.value.setOption(option)
}

// ==================== 数据加载 ====================

// 更新最后更新时间
const updateLastUpdateTime = () => {
  lastUpdateTime.value = new Date().toLocaleString('zh-CN')
}

// 加载症状类型统计
const loadTumorTypeStats = async () => {
  loadingStatus.value.tumorType = true
  try {
    const res = await request.get('/dataview/stats/tumorType')
    if (res.code === '200') {
      nextTick(() => {
        initTumorTypeChart(res.data)
      })
    } else {
      ElMessage.warning('症状统计数据加载失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载症状统计失败', error)
    ElMessage.error('无法连接到服务器，请检查后端服务')
  } finally {
    loadingStatus.value.tumorType = false
  }
}

// 加载用户预测占比
const loadUserPredictionStats = async () => {
  loadingStatus.value.userPrediction = true
  try {
    const res = await request.get('/dataview/stats/userPrediction')
    if (res.code === '200') {
      const filteredData = (res.data || []).filter(item => !excludedStatsUsers.has(String(item.userName || '').trim()))
      userPredictionData.value = filteredData
      nextTick(() => {
        initUserPieChart(filteredData)
      })
    } else {
      ElMessage.warning('用户预测数据加载失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载用户占比失败', error)
  } finally {
    loadingStatus.value.userPrediction = false
  }
}

// 加载用户置信度统计
const loadUserConfidenceStats = async () => {
  loadingStatus.value.userConfidence = true
  try {
    const res = await request.get('/dataview/stats/userConfidence')
    if (res.code === '200') {
      nextTick(() => {
        initUserRadarChart(res.data)
      })
    } else {
      ElMessage.warning('置信度数据加载失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载置信度统计失败', error)
  } finally {
    loadingStatus.value.userConfidence = false
  }
}

// 加载日趋势统计
const loadDailyTrend = async () => {
  loadingStatus.value.dailyTrend = true
  try {
    const res = await request.get('/dataview/stats/dailyTrend?days=10')
    if (res.code === '200') {
      nextTick(() => {
        initDailyTrendChart(res.data)
      })
    } else {
      ElMessage.warning('趋势数据加载失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载趋势数据失败', error)
  } finally {
    loadingStatus.value.dailyTrend = false
  }
}

// 加载实时日志
const normalizeModelKey = (value) => {
  return String(value || '').trim().replace(/\\/g, '/').toLowerCase()
}

const setModelMapValue = (map, key, displayName, overwrite = true) => {
  const normalizedKey = normalizeModelKey(key)
  if (!normalizedKey || !displayName) return
  if (overwrite || !map[normalizedKey]) {
    map[normalizedKey] = displayName
  }
}

const loadModelDisplayNames = async () => {
  try {
    const responses = await Promise.all(modelConfigPages.map(pageType => fetchModels(pageType)))
    const displayNameMap = {}

    responses.forEach(res => {
      if (res.code !== '200') return
      ;(res.data || []).forEach(model => {
        const displayName = String(model.displayName || model.modelName || '').trim()
        const modelName = String(model.modelName || '').trim()
        if (!displayName) return

        setModelMapValue(displayNameMap, model.modelPath, displayName)
        if (modelName && !['best.pt', 'last.pt'].includes(modelName.toLowerCase())) {
          setModelMapValue(displayNameMap, modelName, displayName, false)
        }
      })
    })

    modelDisplayNameMap.value = displayNameMap
  } catch (error) {
    console.error('加载模型显示名称失败', error)
  }
}

const loadRealtimeLogs = async () => {
  loadingStatus.value.realtimeLogs = true
  try {
    const res = await request.get('/dataview/logs/realtime?limit=20')
    if (res.code === '200') {
      realtimeLogs.value = res.data
    } else {
      ElMessage.warning('实时日志加载失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载实时日志失败', error)
  } finally {
    loadingStatus.value.realtimeLogs = false
  }
}

// 加载所有统计数据
const loadStatsData = async () => {
  await Promise.all([
    loadTumorTypeStats(),
    loadUserPredictionStats(),
    loadUserConfidenceStats(),
    loadDailyTrend()
  ])
  updateLastUpdateTime()
}

// 加载所有数据
const loadAllData = async () => {
  await Promise.all([
    loadStatsData(),
    loadModelDisplayNames(),
    loadRealtimeLogs()
  ])
}

// 手动刷新数据
const handleRefresh = () => {
  ElMessage.info('正在刷新数据...')
  loadAllData().then(() => {
    ElMessage.success('数据刷新成功')
  })
}

// ==================== 工具函数 ====================

// 获取模型文件名
const getModelName = (fullPath) => {
  if (!fullPath) return '-'
  const configuredName = modelDisplayNameMap.value[normalizeModelKey(fullPath)]
  if (configuredName) return configuredName

  const parts = String(fullPath).split(/[\\/]/).filter(Boolean)
  if (!parts.length) return '-'

  const fileName = parts[parts.length - 1]
  const parentName = parts[parts.length - 2]
  const grandParentName = parts[parts.length - 3]
  const lowerFileName = fileName.toLowerCase()

  if ((lowerFileName === 'best.pt' || lowerFileName === 'last.pt') && parentName) {
    const fallbackName = parentName.toLowerCase() === 'weights' && grandParentName ? grandParentName : parentName
    return modelDisplayNameMap.value[normalizeModelKey(fileName)] ||
      modelDisplayNameMap.value[normalizeModelKey(fallbackName)] ||
      fallbackName
  }

  return modelDisplayNameMap.value[normalizeModelKey(fileName)] || fileName
}

// 格式化AI模型名称
const formatAiModel = (model) => {
  if (!model) return ''
  const rawModel = String(model).trim().replace(/[.。]+$/, '')
  const key = rawModel.toLowerCase()
  const mapping = {
    'deepseek': 'Deepseek-V4',
    'deepseek-v4': 'Deepseek-V4',
    'glm': '',
    'kimi': 'Kimi-k2.6',
    'doubao': '豆包'
  }
  return Object.prototype.hasOwnProperty.call(mapping, key) ? mapping[key] : rawModel
}

const getAiModelTagClass = (model) => {
  const rawModel = String(model || '').trim().replace(/[.。]+$/, '')
  const key = rawModel.toLowerCase()
  if (key === 'deepseek' || key === 'deepseek-v4') return 'ai-model-deepseek'
  if (key === 'kimi') return 'ai-model-kimi'
  if (key === 'doubao') return 'ai-model-doubao'
  return 'ai-model-default'
}

// 格式化日期时间
const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadAllData()
  
  // 设置定时刷新统计数据（每60秒）
  statsRefreshTimer = setInterval(() => {
    loadStatsData()
  }, 60000)
  
  // 设置定时刷新实时日志（每30秒）
  refreshTimer = setInterval(() => {
    loadRealtimeLogs()
  }, 30000)
  
  // 窗口大小改变时重新渲染图表
  window.addEventListener('resize', () => {
    tumorTypeChart.value?.resize()
    userPieChart.value?.resize()
    userRadarChart.value?.resize()
    dailyTrendChart.value?.resize()
  })
})

onUnmounted(() => {
  // 清除定时器
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  if (statsRefreshTimer) {
    clearInterval(statsRefreshTimer)
  }
  
  // 销毁图表实例
  tumorTypeChart.value?.dispose()
  userPieChart.value?.dispose()
  userRadarChart.value?.dispose()
  dailyTrendChart.value?.dispose()
})
</script>

<style scoped>
.dataview-container {
  padding: 20px;
  min-height: calc(100vh - 60px);
  background: var(--app-bg);
}

.page-header {
  margin-bottom: 18px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--app-surface);
  padding: 20px 24px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.title-section {
  text-align: left;
}

.page-header h2 {
  margin: 0 0 8px 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
}

.subtitle {
  color: var(--app-text-muted);
  margin: 0;
  font-size: 14px;
}

.actions-section {
  display: flex;
  align-items: center;
  gap: 15px;
}

.update-time {
  color: var(--app-text-muted);
  font-size: 13px;
}

.row-section {
  margin-bottom: 20px;
}

.chart-card {
  height: 100%;
  border-color: var(--app-border);
  box-shadow: var(--app-shadow);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--app-text);
  font-weight: bold;
}

.card-header .el-icon {
  margin-right: 8px;
}

.status-tag {
  margin-left: 8px;
}

.realtime-table {
  width: 100%;
}

.ai-model-tag {
  border: 0;
  font-weight: 600;
}

.ai-model-deepseek {
  background: #e8f3f6;
  color: #256f7f;
}

.ai-model-kimi {
  background: #fff1d6;
  color: #9a5b00;
}

.ai-model-doubao {
  background: #ebf7ef;
  color: #2f8f5b;
}

.ai-model-default {
  background: #edf1f5;
  color: #52606d;
}

.chart-container {
  width: 100%;
  height: 300px;
}

/* 饼图布局 */
.pie-chart-wrapper {
  display: flex;
  align-items: center;
}

.pie-chart {
  flex: 1;
}

.pie-data-list {
  width: 150px;
  padding-left: 20px;
  border-left: 1px solid var(--app-border);
}

.pie-data-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 13px;
}

.user-name {
  color: var(--app-text-muted);
}

.user-count {
  color: var(--app-primary);
  font-weight: bold;
}

.user-percentage {
  color: var(--app-text-muted);
  font-size: 12px;
}

/* 雷达图布局 */
.radar-wrapper {
  display: flex;
  align-items: center;
}

.radar-chart {
  flex: 1;
}

.confidence-list {
  width: 120px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.confidence-item {
  text-align: center;
}

.confidence-value {
  font-size: 20px;
  font-weight: bold;
  color: var(--app-primary);
}

.confidence-user {
  font-size: 14px;
  color: var(--app-text-muted);
  margin-top: 4px;
}

/* 趋势图 */
.trend-chart {
  height: 280px;
}

/* 表格样式 */
.text-gray {
  color: var(--app-text-muted);
}

:deep(.el-card__header) {
  padding: 12px 20px;
  background-color: var(--app-surface-soft);
  border-bottom-color: var(--app-border);
}

:deep(.el-card__body) {
  padding: 20px;
}

@media (max-width: 900px) {
  .header-content,
  .actions-section {
    align-items: flex-start;
    flex-direction: column;
  }

  :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }

  .pie-chart-wrapper,
  .radar-wrapper {
    align-items: stretch;
    flex-direction: column;
  }

  .pie-data-list,
  .confidence-list {
    width: 100%;
    padding-left: 0;
    border-left: 0;
  }
}
</style>
