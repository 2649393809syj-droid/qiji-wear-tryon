import _ from "lodash"
import { permission_page_status } from './page_status/page_status.js'

/**
 * 登录/权限模块数据模型
 */
const permission_model = {}

// 登录表单数据
permission_model['page_status'] = _.cloneDeep(permission_page_status)
// 当前用户信息
permission_model['current_user'] = {}
// 加载状态
permission_model['loading'] = false

export default permission_model 