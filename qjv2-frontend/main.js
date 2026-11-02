import { createSSRApp } from 'vue'
import App from './App.vue'
import store from './common/data_templates/store.js'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

export function createApp() {
    const app = createSSRApp(App)
    app.use(ElementPlus)
    app.use(store)
    
    // 注册所有图标
    for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
        app.component(key, component)
    }
    
    return { app }
}