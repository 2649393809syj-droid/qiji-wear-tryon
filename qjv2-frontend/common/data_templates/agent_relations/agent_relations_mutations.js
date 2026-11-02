import {
	agent_relations_data_mutation
} from './agent_relations_mutations/append_functions/append_functions.js'
import _ from "lodash"

// 通用变更函数
const agent_relations_mutation = (state, {
	k,
	v,
	i_k
}) => {
	if (i_k) {
		state.agent_relations_model[k][i_k] = v
	} else {
		state.agent_relations_model[k] = v
	}
}

const agent_relations_mutations = {
	// 通用变更
	agent_relations_mutation,
	// 更新推广关系列表
	agent_relations_list_mutation: (state, data) => {
		state.agent_relations_model.list = data
	},
	// 更新总数
	agent_relations_total_mutation: (state, total) => {
		state.agent_relations_model.total = total
	},
	// 更新当前页
	agent_relations_page_mutation: (state, page) => {
		state.agent_relations_model.currentPage = page
	},
	// 更新搜索条件
	agent_relations_search_mutation: (state, { key, value }) => {
		if (key) {
			state.agent_relations_model.searchParams[key] = value
		}
	},
	// 重置搜索条件
	agent_relations_search_reset_mutation: (state) => {
		state.agent_relations_model.searchParams = {
			agentOpenid: '',
			userOpenid: ''
		}
		state.agent_relations_model.currentPage = 1
	},
	// 更新选中的推广关系记录
	agent_relations_selected_mutation: (state, data) => {
		state.agent_relations_model.detail = data
	},
	// 更新加载状态
	agent_relations_loading_mutation: (state, data) => {
		state.agent_relations_model.loading = data
	},
	// 数据变更函数
	agent_relations_data_mutation
}

export default agent_relations_mutations