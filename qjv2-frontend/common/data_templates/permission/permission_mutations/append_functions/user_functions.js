// 获取本地保存的登录凭据
function getSavedCredentials() {
  try {
    const creds = uni.getStorageSync('user_credentials')
    if (creds && creds.username && creds.password) {
      return creds
    }
  } catch (e) {}
  return null
}

// 清除本地保存的登录凭据
function clearSavedCredentials() {
  uni.removeStorageSync('user_credentials')
}

export {
  getSavedCredentials,
  clearSavedCredentials
} 