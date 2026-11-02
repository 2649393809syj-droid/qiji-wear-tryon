// 通用变更函数
const agent_config_mutation = (state, { k, v, i_k }) => {
  if (i_k) {
    state.agent_config_model[k][i_k] = v
  } else {
    state.agent_config_model[k] = v
  }
}

const agent_config_mutations = {
  // 通用变更
  agent_config_mutation,
  // 更新列表
  agent_config_list_mutation: (state, data) => {
    state.agent_config_model.list = data
  },
  // 更新总数
  agent_config_total_mutation: (state, total) => {
    state.agent_config_model.total = total
  },
  // 更新当前页
  agent_config_page_mutation: (state, page) => {
    state.agent_config_model.currentPage = page
  },
  // 更新搜索条件
  agent_config_search_mutation: (state, { key, value }) => {
    if (key) {
      state.agent_config_model.searchParams[key] = value
    }
  },
  // 重置搜索条件
  agent_config_search_reset_mutation: (state) => {
    state.agent_config_model.searchParams = {
      dlmc: ''
    }
    state.agent_config_model.currentPage = 1
  },
  // 更新加载状态
  agent_config_loading_mutation: (state, data) => {
    state.agent_config_model.loading = data
  },
  // 更新详情
  agent_config_detail_mutation: (state, data) => {
    state.agent_config_model.detail = data
  }
}

export default agent_config_mutations
