import { get_agent_config_list, update_agent_config } from './agent_config_mutations/append_functions/append_functions.js'

// 通用状态变更动作
const agent_config_change = ({ commit }, newData) => {
  commit('agent_config_mutation', newData)
}

// 获取代理配置列表
const fetchAgentConfigList = async ({ state, commit }, params = {}) => {
  return await get_agent_config_list(state, params)
}

// 更新代理配置
const updateAgentConfig = async ({ state, commit }, data) => {
  const result = await update_agent_config(state, data)
  if (result.success) {
    // 更新成功后刷新列表
    await get_agent_config_list(state)
  }
  return result
}

// 更新搜索条件
const updateAgentConfigSearch = ({ commit }, { key, value }) => {
  commit('agent_config_search_mutation', { key, value })
}

// 重置搜索条件
const resetAgentConfigSearch = ({ commit }) => {
  commit('agent_config_search_reset_mutation')
}

// 切换页码
const changeAgentConfigPage = ({ commit }, page) => {
  commit('agent_config_page_mutation', page)
}

export default {
  agent_config_change,
  fetchAgentConfigList,
  updateAgentConfig,
  updateAgentConfigSearch,
  resetAgentConfigSearch,
  changeAgentConfigPage
}
