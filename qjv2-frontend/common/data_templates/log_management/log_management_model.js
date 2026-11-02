import _ from "lodash"
import { log_management_page_status } from './page_status/page_status.js'

/**
 * 日志管理模块数据模型
 * 该模型用于管理日志功能的状态和数据
 */
const log_management_model = {
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
		applicantOpenid: '',
		applyRole: ''
	},
	// 加载状态
	loading: false,
	// 详情数据
	detail: {},
	// 页面状态
	page_status: _.cloneDeep(log_management_page_status)
}

export default log_management_model
