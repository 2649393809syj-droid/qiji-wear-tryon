import { get_agent_list, approve_agent, reject_agent, suspend_agent } from "./append_functions/append_functions"

const agent_data_mutation = async (state, {f, data}) => {
    if (f === 'get') {
        await get_agent_list(state)
    }
    if (f === 'approve') {
        await approve_agent(state, data)
    }
    if (f == 'reject') {
        await reject_agent(state, data)
    }
    if (f == 'suspend') {
        await suspend_agent(state, data)
    }
}

export default {
    agent_data_mutation
}