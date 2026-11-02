import _ from "lodash"
import { agent_relations_page_status } from './page_status/page_status.js'

/**
 * 推广关系数据模型
 * 该模型用于管理推广关系功能的状态和数据
 */
const agent_relations_model = {
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
		agentOpenid: '',
		userOpenid: ''
	},
	// 加载状态
	loading: false,
	// 详情数据
	detail: {},
	// 编辑状态
	edit_status: false,
	// 页面状态
	page_status: _.cloneDeep(agent_relations_page_status)
}

export default agent_relations_model