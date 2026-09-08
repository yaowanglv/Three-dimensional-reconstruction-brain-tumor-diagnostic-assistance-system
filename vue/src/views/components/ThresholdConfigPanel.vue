<template>
  <div class="threshold-config-panel">
    <div class="panel-head">
      <h3 class="panel-title">{{ label }}</h3>
      <el-tag type="info">0.05 - 0.95</el-tag>
    </div>
    <div class="threshold-row">
      <el-slider
        :model-value="modelValue"
        :min="0.05"
        :max="0.95"
        :step="0.1"
        show-input
        :show-input-controls="false"
        :format-tooltip="formatThreshold"
        @update:model-value="updateValue"
      />
    </div>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: Number, default: 0.45 },
  label: { type: String, default: '默认置信度阈值' }
})

const emit = defineEmits(['update:modelValue'])

const updateValue = (value) => {
  emit('update:modelValue', Number(value))
}

const formatThreshold = (value) => Number(value).toFixed(2)
</script>

<style scoped>
.threshold-config-panel {
  padding-top: 18px;
  border-top: 1px solid var(--app-border);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
}

.threshold-row {
  max-width: 760px;
}
</style>
