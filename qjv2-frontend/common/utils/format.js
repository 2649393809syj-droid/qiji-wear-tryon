/**
 * 格式化日期
 * @param {String|Date} date - 日期字符串或Date对象
 * @param {String} format - 格式化模式，默认为 'YYYY-MM-DD HH:mm:ss'
 * @returns {String} 格式化后的日期字符串
 */
export const formatDate = (date, format = 'YYYY-MM-DD HH:mm:ss') => {
	if (!date) return '-'
	
	let dateObj
	if (typeof date === 'string') {
		dateObj = new Date(date)
	} else if (date instanceof Date) {
		dateObj = date
	} else {
		return '-'
	}
	
	if (isNaN(dateObj.getTime())) {
		return '-'
	}
	
	const year = dateObj.getFullYear()
	const month = String(dateObj.getMonth() + 1).padStart(2, '0')
	const day = String(dateObj.getDate()).padStart(2, '0')
	const hours = String(dateObj.getHours()).padStart(2, '0')
	const minutes = String(dateObj.getMinutes()).padStart(2, '0')
	const seconds = String(dateObj.getSeconds()).padStart(2, '0')
	
	return format
		.replace('YYYY', year)
		.replace('MM', month)
		.replace('DD', day)
		.replace('HH', hours)
		.replace('mm', minutes)
		.replace('ss', seconds)
}

/**
 * 格式化金额
 * @param {Number} amount - 金额
 * @param {Number} decimals - 小数位数，默认为2
 * @returns {String} 格式化后的金额字符串
 */
export const formatAmount = (amount, decimals = 2) => {
	if (amount === null || amount === undefined || isNaN(amount)) {
		return '0.00'
	}
	
	return Number(amount).toFixed(decimals)
}

/**
 * 格式化文件大小
 * @param {Number} bytes - 字节数
 * @returns {String} 格式化后的文件大小字符串
 */
export const formatFileSize = (bytes) => {
	if (bytes === 0) return '0 B'
	
	const k = 1024
	const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
	const i = Math.floor(Math.log(bytes) / Math.log(k))
	
	return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
} 