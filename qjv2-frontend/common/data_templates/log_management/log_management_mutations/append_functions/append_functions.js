import { showMess } from "../../../../utils/utils"
import { API_CONFIG } from "../../../../jcCall.js"

// 后端搜索接口地址
const SEARCH_API_URL = `${API_CONFIG.BASE_URL}/qj/admin/search/promotionLogs`

/**
 * 获取日志列表（支持搜索和分页）
 * @param {Object} state - Vuex state
 * @param {Object} params - 搜索参数
 */
export async function get_log_list(state, params = {}) {
  state.log_management_model.loading = true
  try {
    const model = state.log_management_model
    const requestParams = {
      pageSize: params.pageSize || model.pageSize,
      offset: params.offset !== undefined ? params.offset : (model.currentPage - 1) * model.pageSize,
      applicantOpenid: params.applicantOpenid !== undefined ? params.applicantOpenid : (model.searchParams.applicantOpenid || ''),
      applyRole: params.applyRole !== undefined ? params.applyRole : (model.searchParams.applyRole || '')
    }
    
    console.log('获取日志列表，请求参数:', requestParams)
    
    const res = await uni.request({
      url: SEARCH_API_URL,
      method: 'POST',
      data: requestParams,
      header: { 'Content-Type': 'application/json' }
    })
    
    console.log('日志列表响应:', res.data)
    
    if (res.data.errCode === 0) {
      state.log_management_model.list = res.data.result || []
      state.log_management_model.total = res.data.total || 0
      return { success: true, data: res.data }
    } else {
      showMess(res.data.errMsg || '获取日志列表失败', 'error')
      return { success: false, errMsg: res.data.errMsg }
    }
  } catch (error) {
    console.error('获取日志列表失败:', error)
    showMess('获取列表失败，请检查网络连接', 'error')
    return { success: false, error }
  } finally {
    state.log_management_model.loading = false
  }
}

// 保留旧函数名以兼容
async function get_log_data_function(state, params) {
  return await get_log_list(state, params)
}

async function search_logs_function(state, payload) {
  return await get_log_list(state, payload)
}

export {
  get_log_data_function,
  search_logs_function
}
