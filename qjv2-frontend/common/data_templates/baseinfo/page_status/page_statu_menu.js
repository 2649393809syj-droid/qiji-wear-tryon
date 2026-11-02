import _ from 'lodash'

import agent from '../../../../pages/agent/agent.vue'
import user_behavior from '../../../../pages/user_behavior/user_behavior.vue'
import agent_relations from '../../../../pages/agent_relations/agent_relations.vue'
import log_management from '../../../../pages/log_management/log_management.vue'
import agent_config from '../../../../pages/agent_config/agent_config.vue'

const menus = [{
		index: '1',
		title: '代理管理',
		icon: 'DataLine'
	},
	{
		index: '2',
		title: '用户行为',
		icon: 'Document'
	},
	{
		index: '3',
		title: '推广关系',
		icon: 'Document'
	},
	{
		index: '4',
		title: '日志管理',
		icon: 'Document'
	},
	{
		index: '5',
		title: '代理配置',
		icon: 'Setting'
	}
]

/**
 * 组件映射
 */
const component_map = {
	1: agent,
	2: user_behavior,
	3: agent_relations,
	4: log_management,
	5: agent_config,
}

export {
	menus,
	component_map
}