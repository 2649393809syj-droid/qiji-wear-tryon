import { get_user_behavior_list } from './user_behavior_mutations/append_functions/append_functions.js'

// 通用状态变更动作
const user_behavior_change = ({ commit }, newData) => {
	commit('user_behavior_mutation', newData)
}

// 获取用户行为列表
const fetchUserBehaviorList = async ({ state, commit }, params = {}) => {
	return await get_user_behavior_list(state, params)
}

// 更新搜索条件
const updateUserBehaviorSearch = ({ commit }, { key, value }) => {
	commit('user_behavior_search_mutation', { key, value })
}

// 重置搜索条件
const resetUserBehaviorSearch = ({ commit }) => {
	commit('user_behavior_search_reset_mutation')
}

// 切换页码
const changeUserBehaviorPage = ({ commit }, page) => {
	commit('user_behavior_page_mutation', page)
}

export default {
	user_behavior_change,
	fetchUserBehaviorList,
	updateUserBehaviorSearch,
	resetUserBehaviorSearch,
	changeUserBehaviorPage
}