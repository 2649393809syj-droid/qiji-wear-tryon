import _ from "lodash"
import { agent_config_page_status } from './page_status/page_status.js'

/**
 * 代理配置模块数据模型
 */
const agent_config_model = {
	// 列表数据
	list: [],
	// 总数
	total: 0,
	// 当前页
	currentPage: 1,
	// 每页条数
	pageSize: 10,
	// 搜索条件
	searchParams: {
		dlmc: ''
	},
	// 加载状态
	loading: false,
	// 详情数据
	detail: {},
	// 编辑状态
	edit_status: false,
	// 页面状态
	page_status: _.cloneDeep(agent_config_page_status)
}

export default agent_config_model
