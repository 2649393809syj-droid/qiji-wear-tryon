import _ from "lodash"

let page_status = {
    // 表单数据
    form_data: {
        openid: '',
        name: '',
        status: '',
        role: '',
        expired_date: ''
    },
    // 对话框状态
    dialog_visible: false,
}

const agent_page_status = _.cloneDeep(page_status)

export {
    agent_page_status
}