import { showMess } from "../../../../utils/utils.js"
import { API_CONFIG } from "../../../../jcCall.js"

// 后端搜索接口地址
const SEARCH_API_URL = `${API_CONFIG.BASE_URL}/qj/admin/search/userBehavior`

/**
 * 获取用户行为列表（支持搜索和分页）
 * @param {Object} state - Vuex state
 * @param {Object} params - 搜索参数 { openid, behaviorType, pageSize, offset }
 */
export async function get_user_behavior_list(state, params = {}) {
  state.user_behavior_model.loading = true
  try {
    const model = state.user_behavior_model
    const requestParams = {
      pageSize: params.pageSize || model.pageSize,
      offset: params.offset !== undefined ? params.offset : (model.currentPage - 1) * model.pageSize,
      openid: params.openid !== undefined ? params.openid : (model.searchParams.openid || ''),
      behaviorType: params.behaviorType !== undefined ? params.behaviorType : (model.searchParams.behaviorType || '')
    }
    
    console.log('获取用户行为列表，请求参数:', requestParams)
    
    const res = await uni.request({
      url: SEARCH_API_URL,
      method: 'POST',
      data: requestParams,
      header: { 'Content-Type': 'application/json' }
    })
    
    console.log('用户行为列表响应:', res.data)
    
    if (res.data.errCode === 0) {
      state.user_behavior_model.list = res.data.result || []
      state.user_behavior_model.total = res.data.total || 0
      return { success: true, data: res.data }
    } else {
      showMess(res.data.errMsg || '获取用户行为列表失败', 'error')
      return { success: false, errMsg: res.data.errMsg }
    }
  } catch (error) {
    console.error('获取用户行为列表失败:', error)
    showMess('获取列表失败，请检查网络连接', 'error')
    return { success: false, error }
  } finally {
    state.user_behavior_model.loading = false
  }
}

/**
 * 新增用户行为记录
 */
export async function add_user_behavior(state, data) {
  state.user_behavior_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/user_behavior/add',
      method: 'POST',
      dataType: "json",
      data: data
    })
    
    if (res.data.errCode == 0) {
      showMess('新增用户行为记录成功')
      // 刷新列表
      await get_user_behavior_list(state)
      return true
    } else {
      showMess(res.data.errMsg, 'error')
      return false
    }
  } catch (error) {
    console.error('新增用户行为记录失败:', error)
    showMess('新增用户行为记录失败', 'error')
    return false
  } finally {
    state.user_behavior_model.loading = false
  }
}

/**
 * 修改用户行为记录
 */
export async function update_user_behavior(state, data) {
  state.user_behavior_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/user_behavior/update',
      method: 'POST',
      dataType: "json",
      data: data
    })
    
    if (res.data.errCode == 0) {
      showMess('修改用户行为记录成功')
      // 刷新列表
      await get_user_behavior_list(state)
      return true
    } else {
      showMess(res.data.errMsg, 'error')
      return false
    }
  } catch (error) {
    console.error('修改用户行为记录失败:', error)
    showMess('修改用户行为记录失败', 'error')
    return false
  } finally {
    state.user_behavior_model.loading = false
  }
}

/**
 * 删除用户行为记录
 */
export async function delete_user_behavior(state, id) {
  state.user_behavior_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/user_behavior/delete/' + id,
      method: 'DELETE',
      dataType: "json"
    })
    
    if (res.data.errCode == 0) {
      showMess('删除用户行为记录成功')
      // 刷新列表
      await get_user_behavior_list(state)
      return true
    } else {
      showMess(res.data.errMsg, 'error')
      return false
    }
  } catch (error) {
    console.error('删除用户行为记录失败:', error)
    showMess('删除用户行为记录失败', 'error')
    return false
  } finally {
    state.user_behavior_model.loading = false
  }
}

/**
 * 搜索用户行为记录
 */
export async function search_user_behavior(state, params) {
  state.user_behavior_model.loading = true
  try {
    const res = await uni.request({
      url: baseUrl + '/user_behavior/query',
      method: 'POST',
      dataType: "json",
      data: params
    })
    
    if (res.data.errCode == 0) {
      state.user_behavior_model.data = res.data.result
    } else {
      showMess(res.data.errMsg, 'error')
    }
  } catch (error) {
    console.error('搜索用户行为记录失败:', error)
    showMess('搜索用户行为记录失败', 'error')
  } finally {
    state.user_behavior_model.loading = false
  }
}

// 用户行为数据变更函数
const user_behavior_data_mutation = async (state, {f, data}) => {
  if (f === 'get') {
    await get_user_behavior_list(state)
  }
  if (f === 'add') {
    await add_user_behavior(state, data)
  }
  if (f === 'update') {
    await update_user_behavior(state, data)
  }
  if (f === 'delete') {
    await delete_user_behavior(state, data)
  }
  if (f === 'search') {
    await search_user_behavior(state, data)
  }
}

export {
  user_behavior_data_mutation
} 