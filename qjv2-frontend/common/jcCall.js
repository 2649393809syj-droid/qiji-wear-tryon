/**
 * @param {Object} params
 * @param {String} funcCodes
 * @param {String} funcName
 * @param {String} moduleName
 * @param {String} action 
 * @param {String} excludes
 */

// API 配置
const API_CONFIG = {
	// API 基础地址 
	//BASE_URL: 'https://qjapiv2.foxcodes.cn',
	BASE_URL: 'http://localhost:7077',


	// API 端点
	ENDPOINTS: {
		CALL_FUNC: '/qj/callfunc'
	}
};

// 获取完整的 API URL（内部函数）
function getApiUrl(endpoint) {
	return `${API_CONFIG.BASE_URL}${endpoint}`;
}

var reqUrl = getApiUrl(API_CONFIG.ENDPOINTS.CALL_FUNC) // 请求地址
var baseUrl = API_CONFIG.BASE_URL

/**
 * API调用函数
 */
const jcCall = async function(params, funcCodes, funcName, moduleName, action, excludes, customHeader = {}) {
	let obj = {
		data: {
			errCode: 4
		}
	}
	try {
		// 构建请求参数
		const requestData = params || {};

		// 打印请求信息
		console.log('Request Info:', {
			url: reqUrl,
			headers: {
				'Content-Type': 'application/json',
				'X-Function-Code': funcCodes,
				'X-Function-Name': funcName || '',
				'X-Action': action || '',
				'X-Module-Name': moduleName || '',
				'X-Authorization': 'default-token',
				'X-Mysql-Excludes': excludes || '',
				...customHeader
			},
			data: requestData
		});

		var res = await uni.request({
			url: reqUrl,
			dataType: "json",
			method: "POST",
			header: {
				'Content-Type': 'application/json',
				'X-Function-Code': funcCodes,
				'X-Function-Name': funcName || '',
				'X-Action': action || '',
				'X-Module-Name': moduleName || '',
				'X-Authorization': 'default-token',
				'X-Mysql-Excludes': excludes || '',
				...customHeader
			},
			data: JSON.stringify(requestData),
			withCredentials: true
		})

		// 打印响应信息
		console.log('Response:', res);

		return res
	} catch (err) {
		console.error('请求失败：', err)
	}

	return obj
}

/**
 * 全局请求拦截器配置
 */
uni.addInterceptor('request', {
	invoke(args) {
		// 确保所有请求都带上withCredentials
		args.withCredentials = true;

		// 确保设置标准的跨域请求头
		if (!args.header) {
			args.header = {};
		}

		// 添加时间戳防止缓存问题
		const timestamp = new Date().getTime();
		if (args.url.indexOf('?') > -1) {
			args.url = `${args.url}&_t=${timestamp}`;
		} else {
			args.url = `${args.url}?_t=${timestamp}`;
		}

		return args;
	},
	fail(err) {
		console.error('请求失败:', err);
		return err;
	}
});

export {
	jcCall,
	baseUrl,
	API_CONFIG
}