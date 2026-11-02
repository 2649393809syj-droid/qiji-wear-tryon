import { showMess } from "../../../../utils/utils.js"
import { API_CONFIG } from "../../../../jcCall.js"

// 后端搜索接口地址
const SEARCH_API_URL = `${API_CONFIG.BASE_URL}/qj/admin/search/agentRelations`

/**
 * 获取推广关系列表（支持搜索和分页）
 * @param {Object} state - Vuex state
 * @param {Object} params - 搜索参数
 */
export async function get_agent_relations_list(state, params = {}) {
  state.agent_relations_model.loading = true
  try {
    const model = state.agent_relations_model
    const requestParams = {
      pageSize: params.pageSize || model.pageSize,
      offset: params.offset !== undefined ? params.offset : (model.currentPage - 1) * model.pageSize,
      agentOpenid: params.agentOpenid !== undefined ? params.agentOpenid : (model.searchParams.agentOpenid || ''),
      userOpenid: params.userOpenid !== undefined ? params.userOpenid : (model.searchParams.userOpenid || '')
    }
    
    console.log('获取推广关系列表，请求参数:', requestParams)
    
    const res = await uni.request({
      url: SEARCH_API_URL,
      method: 'POST',
      data: requestParams,
      header: { 'Content-Type': 'application/json' }
    })
    
    console.log('推广关系列表响应:', res.data)
    
    if (res.data.errCode === 0) {
      state.agent_relations_model.list = res.data.result || []
      state.agent_relations_model.total = res.data.total || 0
      return { success: true, data: res.data }
    } else {
      showMess(res.data.errMsg || '获取推广关系列表失败', 'error')
      return { success: false, errMsg: res.data.errMsg }
    }
  } catch (error) {
    console.error('获取推广关系列表失败:', error)
    showMess('获取列表失败，请检查网络连接', 'error')
    return { success: false, error }
  } finally {
    state.agent_relations_model.loading = false
  }
}

/**
 * 新增推广关系记录
 */
export async function add_agent_relations(state, data) {
  state.agent_relations_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/agent_relations/add',
      method: 'POST',
      dataType: "json",
      data: data
    })
    
    if (res.data.errCode == 0) {
      showMess('新增推广关系记录成功')
      // 刷新列表
      await get_agent_relations_list(state)
      return true
    } else {
      showMess(res.data.errMsg, 'error')
      return false
    }
  } catch (error) {
    console.error('新增推广关系记录失败:', error)
    showMess('新增推广关系记录失败', 'error')
    return false
  } finally {
    state.agent_relations_model.loading = false
  }
}

/**
 * 修改推广关系记录
 */
export async function update_agent_relations(state, data) {
  state.agent_relations_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/agent_relations/update',
      method: 'POST',
      dataType: "json",
      data: data
    })
    
    if (res.data.errCode == 0) {
      showMess('修改推广关系记录成功')
      // 刷新列表
      await get_agent_relations_list(state)
      return true
    } else {
      showMess(res.data.errMsg, 'error')
      return false
    }
  } catch (error) {
    console.error('修改推广关系记录失败:', error)
    showMess('修改推广关系记录失败', 'error')
    return false
  } finally {
    state.agent_relations_model.loading = false
  }
}

/**
 * 删除推广关系记录
 */
export async function delete_agent_relations(state, id) {
  state.agent_relations_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/agent_relations/delete/' + id,
      method: 'DELETE',
      dataType: "json"
    })
    
    if (res.data.errCode == 0) {
      showMess('删除推广关系记录成功')
      // 刷新列表
      await get_agent_relations_list(state)
      return true
    } else {
      showMess(res.data.errMsg, 'error')
      return false
    }
  } catch (error) {
    console.error('删除推广关系记录失败:', error)
    showMess('删除推广关系记录失败', 'error')
    return false
  } finally {
    state.agent_relations_model.loading = false
  }
}

/**
 * 搜索推广关系记录
 */
export async function search_agent_relations(state, params) {
  state.agent_relations_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/agent_relations/query',
      method: 'POST',
      dataType: "json",
      data: params
    })
    
    if (res.data.errCode == 0) {
      state.agent_relations_model.data = res.data.result
    } else {
      showMess(res.data.errMsg, 'error')
    }
  } catch (error) {
    console.error('搜索推广关系记录失败:', error)
    showMess('搜索推广关系记录失败', 'error')
  } finally {
    state.agent_relations_model.loading = false
  }
}

// 推广关系数据变更函数
const agent_relations_data_mutation = async (state, {f, data}) => {
  if (f === 'get') {
    await get_agent_relations_list(state)
  }
  if (f === 'add') {
    await add_agent_relations(state, data)
  }
  if (f === 'update') {
    await update_agent_relations(state, data)
  }
  if (f === 'delete') {
    await delete_agent_relations(state, data)
  }
  if (f === 'search') {
    await search_agent_relations(state, data)
  }
}

export {
  agent_relations_data_mutation
} 