import {
  ElMessage
} from 'element-plus'
import _ from 'lodash'

const showMess = (info, type) => {
  ElMessage({
    message: info,
    type: type ? type : 'success',
  })
}

export {
  showMess,
  
}