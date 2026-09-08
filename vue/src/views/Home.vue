
<template>
  <div class="home-page page-shell">
    <section class="home-header card">
      <h2>系统数据概览</h2>
      <p>关键数据统计与趋势监测</p>
    </section>

    <div class="chart-row">
      <section class="home-chart-card card">
        <div class="chart-panel" id="bjbar"></div>
      </section>
      <section class="home-chart-card card">
        <div class="chart-panel" id="gjbar"></div>
      </section>
    </div>

    <section class="home-chart-card card chart-full">
      <div class="chart-panel chart-panel-large" id="manbar"></div>
    </section>

  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.home-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
}

.home-header p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
}

.chart-row {
  display: flex;
  gap: 16px;
}

.home-chart-card {
  flex: 1;
  min-width: 0;
  padding: 16px;
}

.chart-full {
  width: 100%;
}

.chart-panel {
  width: 100%;
  height: 340px;
}

.chart-panel-large {
  height: 400px;
}

@media (max-width: 900px) {
  .chart-row {
    flex-direction: column;
  }
}
</style>

<script setup>
import * as echarts from "echarts";
import {reactive,onMounted} from "vue";
import axios from 'axios';
//
// // 饼图
// let pieOptions = {
//   title: {
//     text: '不同分类下用户发布旅游攻略帖子的数量', // 主标题
//     subtext: '统计维度：攻略分类', // 副标题
//     left: 'center'
//   },
//   tooltip: {
//     trigger: 'item',
//     formatter: '{a} <br/>{b} : {c} ({d}%)'
//   },
//   legend: {
//     orient: 'vertical',
//     left: 'left'
//   },
//   series: [
//     {
//       name: '数量占比', // 鼠标移上去显示内容
//       type: 'pie',
//       radius: '50%',
//       center: ['50%', '60%'],
//       data: [
//         {value: 1048, name: '瑞幸咖啡'}, // 示例数据：name表示维度，value表示对应的值
//         {value: 735, name: '雀巢咖啡'},
//         {value: 580, name: '星巴克咖啡'},
//         {value: 484, name: '栖巢咖啡'},
//         {value: 300, name: '小武哥咖啡'}
//       ]
//     }
//   ]
// }

// const loadPie = () =>{
//   let chartDom = document.getElementById('pie');
//   let myChart = echarts.init(chartDom);
//   myChart.setOption(pieOptions);
// }
onMounted(()=>{
  // loadPie()
  loadManBar()
  loadBjBar();
  loadGjBar();
})


const loadManBar = async () => {
  // loading.value = true; // 修正：使用 ref 的 value
  // error.value = null;
  try {
    // 调用后端接口获取 Man 的数据
    const response = await axios.get('http://localhost:9527/man/selectAll');
    // 检查响应状态和数据结构
    if (response.data.code !== '200' || !Array.isArray(response.data.data)) {
      throw new Error('数据格式异常：' + JSON.stringify(response.data));
    }

    const manList = response.data.data;

    // 检查数据是否为空
    if (manList.length === 0) {
      throw new Error('未找到人员数据');
    }

    // 处理数据以适应 echarts 的格式
    // 1. 提取唯一名称（处理重复名称）
    const uniqueNames = [...new Set(manList.map(man => man.name))];

    // 2. 计算每个名称对应的角色值总和
    const nameRoleMap = new Map();

    manList.forEach(man => {
      const name = man.name || '未知';
      const role = parseInt(man.role) || 0;

      if (!nameRoleMap.has(name)) {
        nameRoleMap.set(name, role);
      } else {
        const currentSum = nameRoleMap.get(name);
        nameRoleMap.set(name, currentSum + role);
      }
    });

    // 3. 转换为 ECharts 所需格式
    const xAxisData = Array.from(nameRoleMap.keys());
    const seriesData = xAxisData.map(name => nameRoleMap.get(name));


    // 配置柱状图
    const barOptions = {
      title: {
        text: '人员数量图',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        formatter: (params) => {
          const param = params[0];
          return `${param.name}<br/>数量: ${param.value}`;
        }
      },
      xAxis: {
        type: 'category',
        name: '人员所属专业',
        nameLocation: 'center', // 将名称放在中间位置
        nameGap: 45, // 名称与坐标轴的距离（根据需要调整）
        nameTextStyle: {
          fontSize: 14, // 名称文字大小
        },

        data: xAxisData,
        axisLabel: {
          rotate: 45, // 标签旋转角度，防止重叠
          interval: 0, // 强制显示所有标签
          fontSize: 10
        }
      },
      yAxis: {
        type: 'value',
        name: '数量',
        min: 0,
        max: Math.max(...seriesData) + 1
      },
      series: [
        {
          name: '角色值',
          data: seriesData,
          type: 'bar',
          color: '#256f7f',
          label: {
            show: true,
            position: 'top'
          }
        }
      ],
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        containLabel: true
      }
    };


    let chartDom = document.getElementById('manbar');
    let myChart = echarts.init(chartDom);
    myChart.setOption(barOptions);
  } catch (error) {
    console.error('获取数据失败:', error);
  }
};



const loadBjBar = async () => {
  try {
    // 调用后端接口获取BJ数据
    const response = await axios.get('http://localhost:9527/bj/selectAll');

    // 检查响应状态和数据结构
    if (response.data.code !== '200' || !Array.isArray(response.data.data)) {
      throw new Error('BJ数据格式异常：' + JSON.stringify(response.data));
    }

    const bjDataList = response.data.data;

    // 检查数据是否为空
    if (bjDataList.length === 0) {
      throw new Error('未找到BJ数据');
    }

    // 处理数据：合并相同pri的role值
    const priRoleMap = new Map();

    bjDataList.forEach(item => {
      const pri = item.pri || '未知专业'; // 使用pri作为专业/职业
      const role = parseInt(item.role) || 0; // 使用role作为数量

      if (!priRoleMap.has(pri)) {
        priRoleMap.set(pri, role);
      } else {
        // 累加相同pri的role值
        priRoleMap.set(pri, priRoleMap.get(pri) + role);
      }
    });

    // 转换为ECharts所需格式
    const xAxisData = Array.from(priRoleMap.keys()); // 专业名称
    const seriesData = xAxisData.map(pri => priRoleMap.get(pri)); // 合并后的数量

    // 配置柱状图
    const barOptions = {
      title: {
        text: '备件数量图',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        formatter: (params) => {
          const param = params[0];
          return `${param.name}<br/>数量: ${param.value}`;
        }
      },
      xAxis: {
        type: 'category',
        name: '备件所属专业',
        nameLocation: 'center', // 将名称放在中间位置
        nameGap: 45, // 名称与坐标轴的距离（根据需要调整）
        nameTextStyle: {
          fontSize: 14, // 名称文字大小
        },
        data: xAxisData,
        axisLabel: {
          rotate: 45,
          interval: 0,
          fontSize: 10
        }
      },
      yAxis: {
        type: 'value',
        name: '数量',
        min: 0,
        max: Math.max(...seriesData) + 1
      },
      series: [
        {
          name: '角色数量',
          data: seriesData,
          type: 'bar',
          color: '#256f7f', // 使用统一主题色
          label: {
            show: true,
            position: 'top'
          }
        }
      ],
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        containLabel: true
      }
    };

    // 初始化并渲染图表
    const chartDom = document.getElementById('bjbar');
    const myChart = echarts.init(chartDom);
    myChart.setOption(barOptions);
  } catch (error) {
    console.error('获取BJ数据失败:', error);
  }
};


const loadGjBar = async () => {
  try {
    // 调用后端接口获取工具数据
    const response = await axios.get('http://localhost:9527/ldata/selectAll');

    // 检查响应状态和数据结构
    if (response.data.code !== '200' || !Array.isArray(response.data.data)) {
      throw new Error('Gj数据格式异常：' + JSON.stringify(response.data));
    }

    const gjDataList = response.data.data;

    // 检查数据是否为空
    if (gjDataList.length === 0) {
      throw new Error('未找到工具数据');
    }

    // 处理数据：合并相同责任人(pri)的role值总和
    const priRoleMap = new Map();

    gjDataList.forEach(item => {
      const pri = item.pri || '未知责任人'; // 使用pri作为责任人
      const role = parseInt(item.role) || 0; // 使用role作为数量

      if (!priRoleMap.has(pri)) {
        priRoleMap.set(pri, role);
      } else {
        // 累加相同责任人的数量
        priRoleMap.set(pri, priRoleMap.get(pri) + role);
      }
    });

    // 转换为ECharts所需格式
    const xAxisData = Array.from(priRoleMap.keys()); // 责任人列表
    const seriesData = xAxisData.map(pri => priRoleMap.get(pri)); // 合并后的数量

    // 配置柱状图
    const barOptions = {
      title: {
        text: '工具数量图',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        formatter: (params) => {
          const param = params[0];
          return `${param.name}<br/>数量: ${param.value}`;
        }
      },
      xAxis: {
        type: 'category',
        name: '责任人',  // 横坐标命名为责任人
        nameLocation: 'center',
        nameGap: 45,
        nameTextStyle: {
          fontSize: 14,
        },
        data: xAxisData,
        axisLabel: {
          rotate: 45,
          interval: 0,
          fontSize: 10
        }
      },
      yAxis: {
        type: 'value',
        name: '数量',
        min: 0,
        max: Math.max(...seriesData) + 1
      },
      series: [
        {
          name: '工具数量',
          data: seriesData,
          type: 'bar',
          color: '#256f7f',
          label: {
            show: true,
            position: 'top'
          }
        }
      ],
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        containLabel: true
      }
    };

    // 初始化并渲染图表（注意DOM元素ID需对应）
    const chartDom = document.getElementById('gjbar');
    const myChart = echarts.init(chartDom);
    myChart.setOption(barOptions);
  } catch (error) {
    console.error('获取工具数据失败:', error);
  }
};




</script>
