<template>
  <div class="admin-page page-shell">
    <section class="admin-filter card">
      <div class="filter-fields">
        <el-input
          v-model="data.name"
          clearable
          placeholder="请输入名称查询"
          :prefix-icon="Search"
          @clear="load"
        />
        <el-input
          v-model="data.username"
          clearable
          placeholder="请输入用户名查询"
          :prefix-icon="Search"
          @clear="load"
        />
      </div>
      <div class="filter-actions">
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>
    </section>

    <section class="admin-toolbar card">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
      <el-button type="danger" :icon="Delete" @click="deleteBatch">批量删除</el-button>
      <el-button type="success" :icon="Download" @click="exportData">批量导出</el-button>
      <el-upload
        class="import-upload"
        :action="`${apiBaseUrl}/admin/import`"
        :headers="uploadHeaders"
        :show-file-list="false"
        :on-success="hanleImportSuccess"
      >
        <el-button type="success" :icon="Upload">批量导入</el-button>
      </el-upload>
    </section>

    <section class="admin-table card">
      <el-table
        :data="data.tableData"
        class="admin-data-table"
        @selection-change="handleSelectionChange"
        :header-cell-style="tableHeaderStyle"
      >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="数据ID" width="180" />
      <el-table-column prop="username" label="用户名" width="180" />
      <el-table-column prop="role" label="角色" width="180"/>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="time" label="创建时间" />

      <el-table-column  label="操作" width="180" >
<!--        拿出行的数据-->
        <template #default="scope">
          <el-button type="primary" :icon="Edit" circle @click="handleEdit(scope.row)"></el-button>
          <el-button type="danger" :icon="Delete" circle @click="del(scope.row.id)"></el-button>
        </template>
      </el-table-column>


    </el-table>
<!--    分页-->
    <div class="pagination-card">
      <el-pagination
          v-model:current-page="data.pageNum"
          v-model:page-size="data.pageSize"
          :page-size="data.pageSize"
          layout="total,sizes, prev, pager, next"
          :page-sizes="[5, 10, 15, 20]"
          :total="data.total"
          @current-change="load"
          @size-change="load"
      />
    </div>
<!--    新增按钮的弹窗，开始-->
    <el-dialog v-model="data.formVisible" title="管理员信息" width="420px" destroy-on-close>
      <el-form ref="formRef" :model="data.form" :rules="data.rules" class="admin-form" label-width="80px">
        <el-form-item prop="username" label="用户名">
          <el-input v-model="data.form.username" autocomplete="off" placeholder="必填项"/>
        </el-form-item>
        <el-form-item prop="name" label="名  称">
          <el-input v-model="data.form.name" autocomplete="off" />
        </el-form-item>
        <el-form-item prop="password" label="密码">
          <el-input v-model="data.form.password" type="password" autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item prop="role" label="角色">
          <el-select v-model="data.form.role" placeholder="请选择角色" class="role-select">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>


      </el-form>
      <template #footer>
        <div class="dialog-footer">
<!--          <el-button @click="data.formVisible = false"></el-button>-->
          <el-button type="primary" @click="save">保存</el-button>
          <el-button @click="data.formVisible = false">取消</el-button>

        </div>
      </template>
    </el-dialog>
    <!--    新增按钮的弹窗,结束-->
    </section>
  </div>

</template>

<script setup>
import {computed, reactive,ref} from "vue";
import {Delete, Download, Edit, Plus, RefreshLeft, Search, Upload} from "@element-plus/icons-vue";
import axios from "axios";
import request from "@/utils/request.js";
import { getAccessToken } from "@/utils/auth";
import {ElMessage, ElMessageBox} from "element-plus";
const tableHeaderStyle = {
  fontWeight: 600,
  color: 'var(--app-text)',
  backgroundColor: 'var(--app-surface-soft)'
}
const apiBaseUrl = 'http://localhost:9527'
const uploadHeaders = computed(() => {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const data=reactive({
  role:null,
  time:null,
  name:null,
  pageNum:1,
  username:null,
  pageSize:5,
  total:0,
  tableData:[],
  formVisible:false,
  form:{},
  // 定义校验规则，数组
  rules:{
    username:[
      { required:true,message:'请填写用户名',trigger:'blur'}
    ],
    name:[
      { required:true,message:'请填写名称',trigger:'blur'}
    ],
    password:[
      { required:true,message:'请填写密码',trigger:'blur'}
    ],
    role:[
      { required:true,message:'请选择角色',trigger:'change'}
    ]

  },
  rows:[],
  ids:[]
})

const  formRef=ref()
// request.get('/admin/selectAll').then(res=>{
//   if(res.code =='200'){
//     console.log(res)
//   }else {
//     ElMessage.error(res.msg)
//   }

const load = () => {
  request.get('/admin/selectPage', {
    params: {
      pageNum: data.pageNum,
      pageSize: data.pageSize,
      username:data.username,
      name:data.name,
      role:data.role,
      time:data.time,

    }
  }).then(res => {
    if (res.code === '200') {
      data.tableData = res.data.list
      data.total = res.data.total
    } else {
      ElMessage.error(res.msg)
    }
  })
}
load()


const reset=()=>{
  data.name=null
  data.username=null
  load()
}

const handleAdd =()=>{
  data.formVisible=true
  data.form={
    password: '123456',
    role: 'user'
  }
}



// const add = () => {
//   // formRef 是表单的引用
//   formRef.value.validate((valid) => {
//     if (valid) {   // 验证通过的情况下
//       request.post('/admin/add', data.form).then(res => {
//         if (res.code === '200') {
//           data.formVisible = false
//           ElMessage.success('新增成功')
//           load()
//         } else {
//           ElMessage.error(res.msg)
//         }
//       })
//     }
//   })
// }
//
const add = () => {
  formRef.value.validate((valid) => {
    if (!valid) return;

    data.form.password = data.form.password || '123456'
    data.form.role = data.form.role || 'user'

    request.post('/admin/add', data.form)
        .then(res => {
          if (res.code === '200') {
            data.formVisible = false;
            ElMessage.success('新增成功');
            load();
            resetForm();
          } else {
            // 处理特定错误类型
            if (res.msg && res.msg.includes('已存在')) {
              ElMessage.error('该账户名已存在，请使用其他名称');
            } else {
              ElMessage.error(res.msg || '操作失败，请重试');
            }
          }
        })
        .catch(error => {
          console.error('新增账户请求失败:', error);
          ElMessage.error('账号重复，请重新输入');
        });
  });
};

const resetForm = () => {
  data.form = {
    username: '',
    password: '123456',
    role: 'user',
    // 其他字段...
  };
};


const  handleEdit = (row)=>{
  data.form=JSON.parse(JSON.stringify(row))  //深度拷贝，独立两个数据
  data.formVisible=true
}

const update =()=> {
  formRef.value.validate((valid) => {
    if (valid) {
      request.put('/admin/update', data.form).then(res => {
        if (res.code === '200') {
          data.formVisible = false;
          ElMessage.success('修改成功');
          load();
        } else {
          // 处理业务错误（如用户名重复）
          ElMessage.error(res.msg)
        }
      })
    }
  })}

//   定义保存
const save=()=>{
  data.form.id ? update(): add()  //三元表达式
}


// 定义删除
const del=(id)=>{
  ElMessageBox.confirm('删除后无法恢复，确认删除吗','删除确认',{type:"warning"}).then(res=>{
    request.delete('/admin/delete/'+id).then(res => {
      if (res.code === '200') {

        ElMessage.success('删除成功');
        load();
      } else {
        // 处理业务错误（如用户名重复）
        ElMessage.error(res.msg)
      }
    })
  }).catch(err=>{})
}

//批量选中
const handleSelectionChange = (rows) => {   //rows即实际选择的数组
  data.rows =rows
  data.ids=data.rows.map(v => v.id) //map可以把对象的数组，转换成一个纯数字的数组，[1,2,3]
}

// 批量删除
const  deleteBatch  = () => {   //rows即实际选择的数组
  if(data.rows.length ===0){
    ElMessage.warning('请选择数据')
    return
  }

  ElMessageBox.confirm('删除后无法恢复，确认删除吗','删除确认',{type:"warning"}).then(res=>{
    request.delete('/admin/deleteBatch',{data:data.rows}).then(res => {//传入rows数组
      if (res.code === '200') {

        ElMessage.success('批量删除成功');
        load();
      } else {
        // 处理业务错误（如用户名重复）
        ElMessage.error(res.msg)
      }
    })
  }).catch(err=>{})
}

const exportData = () => {
  let idsStr = data.ids.join(",")  // 把数组转换成  字符串  [1,2,3]  ->  "1,2,3"
  let url = `${apiBaseUrl}/admin/export?username=${data.username === null ? '' : data.username}`
      + `&name=${data.name === null ? '' : data.name}`
      + `&ids=${idsStr}`
  const token = getAccessToken()
  if (token) {
    url += `&token=${encodeURIComponent(token)}`
  }
  window.open(url)
}

const hanleImportSuccess=(res)=>{
  if (res.code === '200') {

    ElMessage.success('批量导入数据成功');
    load();
  } else {
    // 处理业务错误（如用户名重复）
    ElMessage.error(res.msg)
  }
}


</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-filter,
.admin-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.filter-fields {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(2, minmax(220px, 280px));
  gap: 12px;
}

.filter-actions,
.admin-toolbar {
  flex-wrap: wrap;
}

.admin-toolbar {
  justify-content: flex-start;
}

.import-upload {
  display: inline-block;
}

.admin-table {
  padding: 0;
  overflow: hidden;
}

.admin-data-table {
  width: 100%;
}

.pagination-card {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
  border-top: 1px solid var(--app-border);
  background: var(--app-surface);
}

.admin-form {
  padding: 12px 12px 0 0;
}

.role-select {
  width: 100%;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 760px) {
  .admin-filter {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-fields {
    grid-template-columns: 1fr;
  }

  .filter-actions {
    display: flex;
  }
}
</style>
