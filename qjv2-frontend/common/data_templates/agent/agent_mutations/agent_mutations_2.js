import { agent_approve } from "./append_functions/append_functions"

const agent_approve_mutation = async (state, payload) => {
    const response = await agent_approve(state, payload)
    if (!Array.isArray(state.agent_model.approve_result)) {
        state.agent_model.approve_result = []
    }
    state.agent_model.approve_result.push(response)
    console.log('mutation 更新后的状态:', state.agent_model)
    return response
}

export default {
    agent_approve_mutation
}