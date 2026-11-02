import { showMess } from "../../../../utils/utils"
import { baseUrl } from "../../../../jcCall.js"

// 获取代理列表
export async function get_agent_list(state) {
    state.agent_model.loading = true
    try {
        const res = await uni.request({
            url: baseUrl + '/agent/list',
            method: 'GET',
            dataType: "json"
        })
        
        if (res.data.errCode == 0) {
            state.agent_model.agent_list = res.data.result
        } else {
            showMess(res.data.errMsg, 'error')
        }
    } catch (error) {
        console.error('获取代理列表失败:', error)
        showMess('获取代理列表失败', 'error')
    } finally {
        state.agent_model.loading = false
    }
}

// 审批通过代理
export async function approve_agent(state, data) {
    state.agent_model.loading = true
    try {
        const res = await uni.request({
            url: baseUrl + '/agent/approve',
            method: 'POST',
            dataType: "json",
            data: {
                openid: data.openid,
                status: data.status,
                expired_date: data.expired_date
            }
        })
        if (res.data.errCode == 0) {
            showMess('审批通过成功')
            // 刷新列表
            await get_agent_list(state)
        } else {
            showMess(res.data.errMsg, 'error')
        }
    } catch (error) {
        console.error('审批失败:', error)
        showMess('审批失败', 'error')
    } finally {
        state.agent_model.loading = false
    }
}

// 拒绝代理
export async function reject_agent(state, data) {
    state.agent_model.loading = true
    try {
        const res = await uni.request({
            url: baseUrl + '/agent/reject',
            method: 'POST',
            dataType: "json",
            data: {
                openid: data.openid
            }
        })
        
        if (res.data.errCode == 0) {
            showMess('拒绝成功')
            // 刷新列表
            await get_agent_list(state)
        } else {
            showMess(res.data.errMsg, 'error')
        }
    } catch (error) {
        console.error('拒绝失败:', error)
        showMess('拒绝失败', 'error')
    } finally {
        state.agent_model.loading = false
    }
}

// 暂停代理
export async function suspend_agent(state, data) {
    state.agent_model.loading = true
    try {
        const res = await uni.request({
            url: baseUrl + '/agent/suspend',
            method: 'POST',
            dataType: "json",
            data: {
                openid: data.openid,
                status: data.status
            }
        })
        
        if (res.data.errCode == 0) {
            showMess('暂停成功')
            // 刷新列表
            await get_agent_list(state)
        } else {
            showMess(res.data.errMsg, 'error')
        }
    } catch (error) {
        console.error('暂停失败:', error)
        showMess('暂停失败', 'error')
    } finally {
        state.agent_model.loading = false
    }
}

// 代理审批处理
export async function agent_approve(state, data) {
    state.agent_model.loading = true
    try {
        const res = await uni.request({
            url: baseUrl + '/agent/approve',
            method: 'POST',
            dataType: "json",
            data: {
                openid: data.openid,
                status: data.status,
                expired_date: data.expired_date
            }
        })
        if (res.data.errCode === 0) {
            state.agent_model.approve_result = res.data.result
            showMess('审批处理成功')
            return res.data.result  // 返回结果
        } else {
            showMess(res.data.errMsg || '审批失败', 'error')
            return null
        }
    } catch (error) {
        console.error('审批处理失败:', error)
        showMess('审批处理失败', 'error')
        return null
    } finally {
        state.agent_model.loading = false
    }
}