import { get_agent_relations_list } from './agent_relations_mutations/append_functions/append_functions.js'

// 通用状态变更动作
const agent_relations_change = ({ commit }, newData) => {
	commit('agent_relations_mutation', newData)
}

// 获取推广关系列表
const fetchAgentRelationsList = async ({ state, commit }, params = {}) => {
	return await get_agent_relations_list(state, params)
}

// 更新搜索条件
const updateAgentRelationsSearch = ({ commit }, { key, value }) => {
	commit('agent_relations_search_mutation', { key, value })
}

// 重置搜索条件
const resetAgentRelationsSearch = ({ commit }) => {
	commit('agent_relations_search_reset_mutation')
}

// 切换页码
const changeAgentRelationsPage = ({ commit }, page) => {
	commit('agent_relations_page_mutation', page)
}

export default {
	agent_relations_change,
	fetchAgentRelationsList,
	updateAgentRelationsSearch,
	resetAgentRelationsSearch,
	changeAgentRelationsPage
}