/**
 * baseinfo模块赋值
 */
const baseinfo_mutation = (state, {
	k,
	v
}) => {
	state.baseinfo_model[k] = v
}

export default {
	baseinfo_mutation,
}