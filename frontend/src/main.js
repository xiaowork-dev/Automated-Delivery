import { createApp } from 'vue'
import { ElAlert, ElButton, ElConfigProvider, ElDatePicker, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu, ElEmpty, ElForm, ElFormItem, ElInput, ElInputNumber, ElOption, ElPagination, ElSelect, ElSkeleton, ElTable, ElTableColumn, ElTag } from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App).use(router)
for (const component of [ElAlert, ElButton, ElConfigProvider, ElDatePicker, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu, ElEmpty, ElForm, ElFormItem, ElInput, ElInputNumber, ElOption, ElPagination, ElSelect, ElSkeleton, ElTable, ElTableColumn, ElTag]) app.use(component)
app.mount('#app')
