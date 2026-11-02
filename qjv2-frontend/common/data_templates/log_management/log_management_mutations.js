import { get_log_data_function, search_logs_function } from './log_management_mutations/append_functions/append_functions.js'

// 通用变更函数
const log_management_mutation = (state, {
  k,
  v,
  i_k
}) => {
  if (i_k) {
    state.log_management_model[k][i_k] = v
  } else {
    state.log_management_model[k] = v
  }
}

const log_management_mutations = {
  // 通用变更
  log_management_mutation,
  // 更新日志列表
  log_management_list_mutation: (state, data) => {
    state.log_management_model.list = data
  },
  // 更新总数
  log_management_total_mutation: (state, total) => {
    state.log_management_model.total = total
  },
  // 更新当前页
  log_management_page_mutation: (state, page) => {
    state.log_management_model.currentPage = page
  },
  // 更新搜索条件
  log_management_search_mutation: (state, { key, value }) => {
    if (key) {
      state.log_management_model.searchParams[key] = value
    }
  },
  // 重置搜索条件
  log_management_search_reset_mutation: (state) => {
    state.log_management_model.searchParams = {
      applicantOpenid: '',
      applyRole: ''
    }
    state.log_management_model.currentPage = 1
  },
  // 更新加载状态
  log_management_loading_mutation: (state, data) => {
    state.log_management_model.loading = data
  },
  // 旧函数兼容
  get_log_data_function,
  search_logs_function
}

export default log_management_mutations
