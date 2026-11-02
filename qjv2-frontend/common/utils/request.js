import { jcCall } from '../jcCall.js'

/**
 * 统一请求函数
 * @param {Object} options - 请求配置
 * @param {Number} options.funcCode - 功能码
 * @param {String} options.funcName - 函数名
 * @param {String} options.moduleName - 模块名
 * @param {String} options.action - 操作类型
 * @param {Object} options.data - 请求数据
 * @param {String} options.excludes - 排除字段
 * @returns {Promise} 响应结果
 */
const request = async (options) => {
	console.log('request.js被调用');
	const {
		funcCode,
		funcName,
		moduleName,
		action,
		data = {},
		excludes = ''
	} = options

	try {
		console.log('jcCall.js被调用');
		const response = await jcCall(data, funcCode, funcName, moduleName, action, excludes)
		
		// 检查响应状态
		if (response.statusCode === 200) {
			return response.data
		} else {
			throw new Error(`HTTP ${response.statusCode}: ${response.errMsg || '请求失败'}`)
		}
	} catch (error) {
		console.error('Request failed:', error)
		throw error
	}
}

export default request 