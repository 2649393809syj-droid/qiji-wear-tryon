<template>
  <view class="login-container">
    <view class="login-content">
      <view class="login-card">
        <view class="title">奇纪营销管理</view>
        <view class="login-form">
          <el-input
            v-model="loginForm.username"
            placeholder="账号"
            prefix-icon="User"
            clearable
            class="input"
          />
          <el-input
            v-model="loginForm.password"
            :type="showPassword ? 'text' : 'password'"
            placeholder="密码"
            prefix-icon="Lock"
            clearable
            class="input"
          >
            <template #suffix>
              <el-icon class="password-icon" @click="showPassword = !showPassword">
                <el-icon-view v-if="showPassword" />
                <el-icon-hide v-else />
              </el-icon>
            </template>
          </el-input>
          <view class="row">
            <el-checkbox v-model="loginForm.remember_me">记住我</el-checkbox>
            <el-dropdown @command="onRoleChange">
              <span class="role">{{ currentRole }}</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="role in roles" :key="role" :command="role">
                    {{ role }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </view>
          <button class="login-button" :disabled="loading" @click="handleLogin">
            {{ loading ? '登录中...' : '登录' }}
          </button>
          <button class="register-button" @click="registerDialogVisible = true">注册</button>
          <view class="login-msg" :class="{ success: loginMessage === '登录成功' }" v-if="loginMessage">{{ loginMessage }}</view>
        </view>
      </view>
      <el-dialog v-model="registerDialogVisible" title="用户注册" width="380px">
        <el-form :model="registerForm" label-width="60px">
          <el-form-item label="账号">
            <el-input v-model="registerForm.username" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="registerForm.email" placeholder="请输入邮箱" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="registerDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleRegister" :loading="registerLoading">注册</el-button>
        </template>
        <div v-if="registerMsg" class="register-msg">{{ registerMsg }}</div>
      </el-dialog>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useStore } from 'vuex'
import { ElInput, ElCheckbox, ElDropdown, ElDropdownMenu, ElDropdownItem, ElIcon, ElDialog, ElForm, ElFormItem, ElButton } from 'element-plus'
import { View as ElIconView, Hide as ElIconHide, User, Lock } from '@element-plus/icons-vue'

const store = useStore()
const loginMessage = ref('')
const showPassword = ref(false)

const loginForm = computed(() => store.state.permission_model.page_status.login_form_data)
const loading = computed(() => store.state.permission_model.loading)

const roles = ['管理员', '员工']
const currentUser = computed(() => store.state.permission_model.current_user || {})
const currentRole = computed(() => currentUser.value.role || '管理员')

const onRoleChange = (role) => {
  store.commit('permission_set_user', {
    ...currentUser.value,
    role
  })
}

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    loginMessage.value = '请输入账号和密码'
    return
  }
  loginMessage.value = ''
  // 直接用输入框内容作为用户名
  const username = loginForm.value.username
  const result = await store.dispatch('login', {
    username,
    password: loginForm.value.password
  })
  if (result && result.success) {
    loginMessage.value = '登录成功'
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/qj-index' })
    }, 800)
  } else {
    loginMessage.value = (result && result.message) || '登录失败'
  }
}

// 注册相关
const registerDialogVisible = ref(false)
const registerForm = ref({ username: '', password: '', email: '' })
const registerLoading = ref(false)
const registerMsg = ref('')

const handleRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.password || !registerForm.value.email) {
    registerMsg.value = '请填写完整信息'
    return
  }
  registerLoading.value = true
  registerMsg.value = ''
  try {
    const result = await store.dispatch('register', {
      username: registerForm.value.username,
      password: registerForm.value.password,
      email: registerForm.value.email
    })
    console.log('注册结果', result)
    if (result && result.success) {
      registerMsg.value = result.msg || '注册成功，请登录'
      // 自动填充到登录框
      store.dispatch('permission_change', {
        k: 'page_status',
        i_k: 'login_form_data',
        v: {
          ...loginForm.value,
          username: registerForm.value.username,
          password: registerForm.value.password
        }
      })
      setTimeout(() => {
        registerDialogVisible.value = false
        registerMsg.value = ''
      }, 1200)
    } else {
      registerMsg.value = (result && result.msg) || '注册失败'
    }
  } catch (e) {
    registerMsg.value = '网络异常'
  }
  registerLoading.value = false
}
</script>

<style scoped>
.login-container {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  background-color: #F5F7FA;
  padding: 20px;
}
.login-content {
  width: 100%;
  max-width: 420px;
}
.login-card {
  background: #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  border-radius: 8px;
  padding: 32px 24px 24px 24px;
  max-width: 380px;
  margin: 0 auto;
}
.title {
  font-size: 24px;
  color: #2C405A;
  text-align: center;
  margin-bottom: 24px;
  font-weight: bold;
}
.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 10px;
}
.input {
  width: 100%;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0;
}
.role {
  color: #1890ff;
  margin-left: 8px;
  font-weight: 500;
  cursor: pointer;
}
.login-button {
  width: 100%;
  height: 40px;
  background: #1890ff;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  margin-top: 10px;
}
.register-button {
  width: 100%;
  height: 40px;
  background: #fff;
  color: #1890ff;
  border: 1px solid #1890ff;
  border-radius: 4px;
  font-size: 16px;
  margin-top: 10px;
  margin-bottom: 0;
}
.password-icon {
  cursor: pointer;
  color: #606266;
}
.login-msg {
  text-align: center;
  font-size: 14px;
  color: #f56c6c;
  margin-top: 10px;
  min-height: 20px;
}
.login-msg.success {
  color: #52c41a;
}
.register-msg {
  text-align: center;
  color: #f56c6c;
  margin-top: 10px;
}
</style>