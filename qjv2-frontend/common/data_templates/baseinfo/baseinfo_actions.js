const baseinfo_change = ({
	commit
}, newData) => {
	commit('baseinfo_mutation', newData)
}

export default {
	baseinfo_change,
}