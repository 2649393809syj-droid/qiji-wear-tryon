// 通用变更函数
const permission_mutation = (state, { k, v, i_k }) => {
  if (i_k) {
    state.permission_model[k][i_k] = v
  } else {
    state.permission_model[k] = v
  }
}

// 设置当前用户
const permission_set_user = (state, user) => {
  state.permission_model.current_user = user || {}
}

// 设置 loading
const permission_set_loading = (state, loading) => {
  state.permission_model.loading = loading
}

export default {
  permission_mutation,
  permission_set_user,
  permission_set_loading
} 