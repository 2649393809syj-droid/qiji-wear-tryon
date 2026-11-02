import {
	user_behavior_data_mutation
} from './user_behavior_mutations/append_functions/append_functions.js'
import _ from "lodash"

// 通用变更函数
const user_behavior_mutation = (state, {
	k,
	v,
	i_k
}) => {
	if (i_k) {
		state.user_behavior_model[k][i_k] = v
	} else {
		state.user_behavior_model[k] = v
	}
}

const user_behavior_mutations = {
	// 通用变更
	user_behavior_mutation,
	// 更新用户行为列表
	user_behavior_list_mutation: (state, data) => {
		state.user_behavior_model.list = data
	},
	// 更新总数
	user_behavior_total_mutation: (state, total) => {
		state.user_behavior_model.total = total
	},
	// 更新当前页
	user_behavior_page_mutation: (state, page) => {
		state.user_behavior_model.currentPage = page
	},
	// 更新搜索条件
	user_behavior_search_mutation: (state, { key, value }) => {
		if (key) {
			state.user_behavior_model.searchParams[key] = value
		}
	},
	// 重置搜索条件
	user_behavior_search_reset_mutation: (state) => {
		state.user_behavior_model.searchParams = {
			openid: '',
			behaviorType: ''
		}
		state.user_behavior_model.currentPage = 1
	},
	// 更新选中的用户行为记录
	user_behavior_selected_mutation: (state, data) => {
		state.user_behavior_model.detail = data
	},
	// 更新加载状态
	user_behavior_loading_mutation: (state, data) => {
		state.user_behavior_model.loading = data
	},
	// 数据变更函数
	user_behavior_data_mutation
}

export default user_behavior_mutations