<template>
  <view class="jr-top">
    <text class="title">奇纪营销管理</text>
    <view class="user-role-switch">
      <text>当前身份：</text>
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
      <el-button class="logout-btn" type="text" @click="logout">退出登录</el-button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useStore } from 'vuex'
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton } from 'element-plus'

const store = useStore()
// 移除router相关代码
const roles = ['管理员', '员工']
const currentUser = computed(() => store.state.permission_model.current_user || {})
const currentRole = computed(() => currentUser.value.role || '员工')

const onRoleChange = (role) => {
  store.commit('permission_set_user', {
    ...currentUser.value,
    role
  })
}

const logout = () => {
  // 获取所有 cookie 名称
  document.cookie.split(';').forEach(function(cookie) {
    const eqPos = cookie.indexOf('=')
    const name = eqPos > -1 ? cookie.substr(0, eqPos).trim() : cookie.trim()
    // 删除不同 path 下的 cookie
    document.cookie = name + '=;expires=' + new Date(0).toUTCString() + ';path=/';
    document.cookie = name + '=;expires=' + new Date(0).toUTCString() + ';path=/;domain=' + location.hostname;
  });
  // 跳转到登录页
  uni.reLaunch({ url: '/pages/login/login' })
}
</script>

<style>
@import url('../../static/css/common.css');

.jr-top {
  background-color: #ebebeb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  color: #000000;
  font-size: 24px;
  font-weight: 500;
}
.user-role-switch {
  display: flex;
  align-items: center;
  font-size: 15px;
}
.role {
  color: #1890ff;
  margin-left: 4px;
  font-weight: 500;
  cursor: pointer;
}
.logout-btn {
  margin-left: 16px;
  color: #f56c6c;
  font-weight: 500;
  cursor: pointer;
}
</style>