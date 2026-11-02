import _ from "lodash"

let page_status = {
  // 搜索条件
  search_condition: {
    keyword: '',
    page: 1,
    pageSize: 10
  },
  // 分页参数
  pagination: {
    current: 1,
    pageSize: 10,
    total: 0
  },
  // 编辑状态
  edit_status: false,
  // 对话框可见性
  dialog_visible: false
}

const user_behavior_page_status = _.cloneDeep(page_status)

export {
  user_behavior_page_status
} 