import {
	jcCall,
	API_CONFIG
} from '../../jcCall.js'
// 登录动作
const login = async ({ commit }, { username, password }) => {
  console.log('【login】事件触发', username, password)
  commit('permission_set_loading', true)
  let result = { success: false }
  try {
    const res = await uni.request({
      url: `${API_CONFIG.BASE_URL}/auth/login`,
      method: 'POST',
      data: { username, password },
      header: {
        'Content-Type': 'application/json',
        'X-Client-Type': 'manager',
        'X-Function-Code': '1124015',
        'X-Function-Name': 'F_301_4015_CHECK_TEST_QUALIFICATION',
        'X-Action': 'customize',
        'X-Module-Name': 'mysql_qj_marketing_v2_agents',
        'X-Mysql-Excludes': '',
        'X-Authorization': ''
      }
    })
    console.log('【login】请求返回', res)
    const data = res.data || (Array.isArray(res) ? res[1]?.data : undefined)
    console.log('【login】data内容:', data)
    console.log('【login】data类型:', typeof data)
    console.log('【login】data.errCode:', data?.errCode, '类型:', typeof data?.errCode)
    console.log('【login】data.token:', data?.token)
    const isSuccess = data && (data.errCode === 0 || data.code === 0) && data.token
    console.log('【login】判断条件:', isSuccess)
    if (isSuccess) {
      console.log('【login】进入成功分支')
      // 设置Cookie用于跨域认证
      document.cookie = `jwt_token=${data.token}; path=/; max-age=86400; SameSite=Strict`
      // 设置本地存储
      uni.setStorageSync('jwt_token', data.token)
      commit('permission_set_user', data.user || { username })
      result = { success: true, user: data.user }
      console.log('【login】成功分支result:', result)
    } else {
      console.log('【login】进入失败分支')
      result = { success: false, message: data?.msg || '登录失败' }
      console.log('【login】失败分支result:', result)
    }
    console.log('【login】最终result:', result)
  } catch (e) {
    console.error('【login】异常', e)
    result = { success: false, message: '网络异常' }
  }
  commit('permission_set_loading', false)
  return result
}

// 登出动作
const logout = ({
	commit
}) => {
	// 清除本地存储
	uni.removeStorageSync('jwt_token')
	// 清除Cookie
	document.cookie = 'jwt_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
	commit('permission_set_user', {})
}

// 通用状态变更
const permission_change = ({
	commit
}, newData) => {
	commit('permission_mutation', newData)
}

// 注册动作
const register = async ({ commit }, { username, password, email }) => {
  console.log('【register】事件触发', username, password, email)
  commit('permission_set_loading', true)
  let result = { success: false }
  try {
    const res = await uni.request({
      url: `${API_CONFIG.BASE_URL}/auth/register`,
      method: 'POST',
      data: { username, password, email },
      header: {
        'Content-Type': 'application/json',
        'X-Client-Type': 'manager',
        'X-Function-Code': '1124015',
        'X-Function-Name': 'F_301_4015_CHECK_TEST_QUALIFICATION',
        'X-Action': 'customize',
        'X-Module-Name': 'mysql_qj_marketing_v2_agents',
        'X-Mysql-Excludes': '',
        'X-Authorization': ''
      }
    })
    console.log('【register】请求返回', res)
    const data = res.data || (Array.isArray(res) ? res[1]?.data : undefined)
    console.log('【register】data内容:', data)
    console.log('【register】data类型:', typeof data)
    console.log('【register】data.errCode:', data?.errCode, '类型:', typeof data?.errCode)
    
    // 简化判断逻辑
    const isSuccess = data && data.errCode === 0
    console.log('【register】简化判断条件:', isSuccess)
    
    if (isSuccess) {
      console.log('【register】进入成功分支')
      result = { success: true, msg: '注册成功', username, password }
      console.log('【register】成功分支result:', result)
    } else {
      console.log('【register】进入失败分支')
      result = { success: false, msg: (data && (data.msg || data.errMsg)) || '注册失败' }
      console.log('【register】失败分支result:', result)
    }
    console.log('【register】最终result:', result)
  } catch (e) {
    console.error('【register】异常', e)
    result = { success: false, msg: '网络异常' }
  }
  commit('permission_set_loading', false)
  return result
}

export default {
	login,
	logout,
	permission_change,
	register
}