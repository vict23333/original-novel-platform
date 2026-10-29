import { createApp } from 'vue'
import { ElButton, ElInput, ElAlert, ElRadioGroup, ElRadioButton, ElLoading } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/radio-group/style/css'
import 'element-plus/es/components/radio-button/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import './style.css'
import './content.css'
import './experience.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
for (const [name, component] of Object.entries({ ElButton, ElInput, ElAlert, ElRadioGroup, ElRadioButton })) app.component(name, component)
app.use(ElLoading).use(router).mount('#app')
