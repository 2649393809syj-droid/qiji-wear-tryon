import _ from "lodash"
import { agent_page_status } from './page_status/page_status.js'

// 代理状态常量
export const AGENT_STATUS = {
	VALID: 1,      // 有效
	PENDING: 2,    // 未审批
	REJECTED: 3,   // 未通过
	SUSPENDED: 4,  // 暂停
	RENEWAL: 5     // 待续费
}

// 代理角色类型 - 与数据库存储的编码保持一致
export const AGENT_ROLE = {
	PERSONAL: 10,   // 个人代理 - 数据库编码10
	PLATFORM: 11,   // 平台代理 - 数据库编码11
	CITY: 12        // 城市代理 - 数据库编码12
}

// 代理状态描述
export const agentStatusMap = {
	[AGENT_STATUS.VALID]: '有效',
	[AGENT_STATUS.PENDING]: '未审批',
	[AGENT_STATUS.REJECTED]: '未通过',
	[AGENT_STATUS.SUSPENDED]: '暂停',
	[AGENT_STATUS.RENEWAL]: '待续费'
}

// 代理角色描述
export const agentRoleMap = {
	[AGENT_ROLE.PERSONAL]: '个人代理',
	[AGENT_ROLE.PLATFORM]: '平台代理',
	[AGENT_ROLE.CITY]: '城市代理'
}

// 代理数据模板
export const agentTemplate = {
	id: '',
	openid: '',
	name: '',
	status: AGENT_STATUS.PENDING,
	expired_date: '',
	role: AGENT_ROLE.PERSONAL,
	current_role: '',
	test_times: 0,
	test_report_times: 0,
	city: '',
	from_openid: '',
	join_date: '',
	create_time: '',
	update_time: ''
}

// 代理列表数据模板
export const agentListTemplate = {
	list: [],
	total: 0,
	page_size: 10,
	current_page: 1
}

/**
 * 代理管理数据模型
 * 该模型用于管理代理功能的状态和数据
 */
const agent_model = {
	'agent_list': [], // 存储代理列表数据
	'agent_dialog': null, // 代理对话框状态
	'selected_agent': {
		openid: '',
		name: '',
		status: '',
		role: '',
		expired_date: ''
	},
	'approve_data': {
		openid: '',
		status: '',
		expired_date: ''
	},
	'approve_result': null,  // 存储审批结果
	'loading': false
}

// 代理列表数据
agent_model['data'] = []
// 代理搜索条件
agent_model['search_condition'] = {
	keyword: '',
	page: 1,
	pageSize: 10
}
// 代理详情数据
agent_model['detail'] = {}
// 编辑状态
agent_model['edit_status'] = false
// 引入页面状态
agent_model['page_status'] = _.cloneDeep(agent_page_status)

export default agent_model