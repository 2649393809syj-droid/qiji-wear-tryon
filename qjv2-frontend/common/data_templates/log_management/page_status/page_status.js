import _ from "lodash"

let page_status = {
  // 表单字段
  form_data: {
    log_type: '',
    start_date: '',
    end_date: '',
    keyword: ''
  },
  // 分页参数
  pagination: {
    current: 1,
    pageSize: 10,
    total: 0
  },
  // 控制状态
  dialog_visible: false,
  search_keyword: ''
}

const log_management_page_status = _.cloneDeep(page_status)

export {
  log_management_page_status
}
