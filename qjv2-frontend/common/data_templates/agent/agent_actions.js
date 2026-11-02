const agent_change = ({commit}, newData) => {
    commit('agent_mutation', newData)
}

const agent_data_change = ({commit}, newData) => {
    commit('agent_data_mutation', newData)
}

const agent_approve_change = async ({commit}, newData) => {
    const result = await commit('agent_approve_mutation', newData)
    return result
}

export default {
    agent_change,
    agent_data_change,
    agent_approve_change
}