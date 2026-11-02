import _ from "lodash"

let page_status = {
  // 登录表单字段
  login_form_data: {
    username: '',
    password: '',
    remember_me: false
  }
}

const permission_page_status = _.cloneDeep(page_status)

export {
  permission_page_status
} 