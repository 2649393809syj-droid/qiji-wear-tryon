import {
	createStore
} from 'vuex'

// *******************************models*********************************************************
import baseinfo_model from './baseinfo/baseinfo_model.js'
import agent_model from './agent/agent_model.js'
import user_behavior_model from './user_behavior/user_behavior_model.js'
import agent_relations_model from './agent_relations/agent_relations_model.js'
import permission_model from './permission/permission_model.js'
import log_management_model from './log_management/log_management_model.js'
import agent_config_model from './agent_config/agent_config_model.js'

// *******************************actions*********************************************************
import baseinfo_actions from './baseinfo/baseinfo_actions.js'
import agent_actions from './agent/agent_actions.js'
import user_behavior_actions from './user_behavior/user_behavior_actions.js'
import agent_relations_actions from './agent_relations/agent_relations_actions.js'
import permission_actions from './permission/permission_actions.js'
import log_management_actions from './log_management/log_management_actions.js'
import agent_config_actions from './agent_config/agent_config_actions.js'

// *******************************mutations*********************************************************
import baseinfo_mutations from './baseinfo/baseinfo_mutations.js'
import agent_mutations from './agent/agent_mutations.js'
import user_behavior_mutations from './user_behavior/user_behavior_mutations.js'
import agent_relations_mutations from './agent_relations/agent_relations_mutations.js'
import permission_mutations from './permission/permission_mutations.js'
import log_management_mutations from './log_management/log_management_mutations.js'
import agent_config_mutations from './agent_config/agent_config_mutations.js'

// 合并 actions
const actions = Object.assign({}, baseinfo_actions, agent_actions, user_behavior_actions, agent_relations_actions, permission_actions, log_management_actions, agent_config_actions)
// 合并 mutations
const mutations = Object.assign({}, baseinfo_mutations, agent_mutations, user_behavior_mutations, agent_relations_mutations, permission_mutations, log_management_mutations, agent_config_mutations)

// 创建一个新的 store 实例
const store = createStore({
	state() {
		return {
			baseinfo_model,
			agent_model,
			user_behavior_model,
			agent_relations_model,
			permission_model,
			log_management_model,
			agent_config_model
		}
	},
	actions,
	mutations
})

export default store