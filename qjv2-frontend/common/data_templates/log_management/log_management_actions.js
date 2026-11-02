import { get_log_list } from './log_management_mutations/append_functions/append_functions.js'

// 通用状态变更动作
const log_management_change = ({ commit }, newData) => {
  commit('log_management_mutation', newData)
}

// 获取日志列表
const fetchLogList = async ({ state, commit }, params = {}) => {
  return await get_log_list(state, params)
}

// 更新搜索条件
const updateLogSearch = ({ commit }, { key, value }) => {
  commit('log_management_search_mutation', { key, value })
}

// 重置搜索条件
const resetLogSearch = ({ commit }) => {
  commit('log_management_search_reset_mutation')
}

// 切换页码
const changeLogPage = ({ commit }, page) => {
  commit('log_management_page_mutation', page)
}

export default {
  log_management_change,
  fetchLogList,
  updateLogSearch,
  resetLogSearch,
  changeLogPage
}
