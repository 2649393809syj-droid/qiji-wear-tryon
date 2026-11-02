import agent_data_mutation from './agent_mutations/agent_mutations_1.js'
import agent_approve_mutation from './agent_mutations/agent_mutations_2.js'

const agent_mutation = (state, {
	k,
	v,
	i_k
}) => {
	if (i_k) {
		state.agent_agent[k][i_k] = v
	} else {
		state.agent_agent[k] = v
	}
}

export default {
	agent_mutation,
	...agent_data_mutation,
	...agent_approve_mutation
}