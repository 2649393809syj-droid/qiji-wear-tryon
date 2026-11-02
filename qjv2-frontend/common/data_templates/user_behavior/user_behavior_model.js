import _ from "lodash"
import { user_behavior_page_status } from './page_status/page_status.js'

// 行为类型映射
export const BEHAVIOR_TYPE_MAP = {
	'payment': '支付',
	'1': '免费测试',
	'2': '免费报告',
	'3': '缴费测试',
	'4': '缴费报告',
	'5': '申请城市代理',
	'6': '申请平台代理',
	'7': '申请个人代理',
	'100': '其他有效消费'
}

/**
 * 用户行为数据模型
 * 该模型用于管理用户行为功能的状态和数据
 */
const user_behavior_model = {
	// 列表数据
	list: [],
	// 总数
	total: 0,
	// 当前页
	currentPage: 1,
	// 每页条数
	pageSize: 16,
	// 搜索条件
	searchParams: {
		openid: '',
		behaviorType: ''
	},
	// 加载状态
	loading: false,
	// 详情数据
	detail: {},
	// 编辑状态
	edit_status: false,
	// 页面状态
	page_status: _.cloneDeep(user_behavior_page_status)
}

export default user_behavior_model