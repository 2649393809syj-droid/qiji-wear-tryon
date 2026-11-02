import { showMess } from "../../../../utils/utils"
import { API_CONFIG } from "../../../../jcCall.js"

// 后端接口地址
const API_BASE_URL = `${API_CONFIG.BASE_URL}/qj/admin/agentConfig`

/**
 * 获取代理配置列表
 * @param {Object} state - Vuex state
 * @param {Object} params - 请求参数
 */
export async function get_agent_config_list(state, params = {}) {
  state.agent_config_model.loading = true
  try {
    const model = state.agent_config_model
    const requestParams = {
      pageSize: params.pageSize || model.pageSize,
      offset: params.offset !== undefined ? params.offset : (model.currentPage - 1) * model.pageSize,
      dlmc: params.dlmc !== undefined ? params.dlmc : (model.searchParams.dlmc || '')
    }
    
    console.log('获取代理配置列表，请求参数:', requestParams)
    
    const res = await uni.request({
      url: `${API_BASE_URL}/list`,
      method: 'POST',
      data: requestParams,
      header: { 'Content-Type': 'application/json' }
    })
    
    console.log('代理配置列表响应:', res.data)
    
    if (res.data.errCode === 0) {
      state.agent_config_model.list = res.data.result || []
      state.agent_config_model.total = res.data.total || 0
      return { success: true, data: res.data }
    } else {
      showMess(res.data.errMsg || '获取代理配置列表失败', 'error')
      return { success: false, errMsg: res.data.errMsg }
    }
  } catch (error) {
    console.error('获取代理配置列表失败:', error)
    showMess('获取列表失败，请检查网络连接', 'error')
    return { success: false, error }
  } finally {
    state.agent_config_model.loading = false
  }
}

/**
 * 更新代理配置
 * @param {Object} state - Vuex state
 * @param {Object} data - 更新数据
 */
export async function update_agent_config(state, data) {
  state.agent_config_model.loading = true
  try {
    console.log('更新代理配置，请求参数:', data)
    
    const res = await uni.request({
      url: `${API_BASE_URL}/update`,
      method: 'POST',
      data: data,
      header: { 'Content-Type': 'application/json' }
    })
    
    console.log('更新代理配置响应:', res.data)
    
    if (res.data.errCode === 0) {
      showMess('更新成功', 'success')
      return { success: true, data: res.data }
    } else {
      showMess(res.data.errMsg || '更新失败', 'error')
      return { success: false, errMsg: res.data.errMsg }
    }
  } catch (error) {
    console.error('更新代理配置失败:', error)
    showMess('更新失败，请检查网络连接', 'error')
    return { success: false, error }
  } finally {
    state.agent_config_model.loading = false
  }
}
