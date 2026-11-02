<template>
	<view class="modal-overlay" v-if="visible" @click="handleClose">
		<view class="modal-content" @click.stop>
			<view class="modal-header">
				<text class="modal-title">修改代理配置 - {{ configData.dlmc }}</text>
				<text class="modal-close" @click="handleClose">×</text>
			</view>
			<view class="modal-body">
				<view class="form-grid">
					<!-- 左侧三个字段 -->
					<view class="form-column">
						<view class="form-group">
							<text class="form-label">加盟费用 (元)</text>
							<view class="input-wrapper">
								<el-input
									v-model="editForm.jmfy"
									type="number"
									placeholder="请输入"
									clearable
								/>
								<text class="form-hint">代理加盟费用</text>
							</view>
						</view>
						
						<view class="form-group">
							<text class="form-label">续约费用 (元)</text>
							<view class="input-wrapper">
								<el-input
									v-model="editForm.xyfy"
									type="number"
									placeholder="请输入"
									clearable
								/>
								<text class="form-hint">代理续约费用</text>
							</view>
						</view>
						
						<view class="form-group">
							<text class="form-label">招募个数</text>
							<view class="input-wrapper">
								<el-input
									v-model="editForm.zmgs"
									type="number"
									placeholder="-1为无限"
									clearable
								/>
								<text class="form-hint">下级代理数量</text>
							</view>
						</view>
					</view>
					
					<!-- 分割线 -->
					<view class="column-divider"></view>
					
					<!-- 右侧两个字段 -->
					<view class="form-column">
						<view class="form-group">
							<text class="form-label">免费定位人数</text>
							<view class="input-wrapper">
								<el-input
									v-model="editForm.d21"
									type="number"
									placeholder="-1为无限"
									clearable
								/>
								<text class="form-hint">-1表示无限次</text>
							</view>
						</view>
						
						<view class="form-group">
							<text class="form-label">免费报告人数</text>
							<view class="input-wrapper">
								<el-input
									v-model="editForm.d22"
									type="number"
									placeholder="-1为无限"
									clearable
								/>
								<text class="form-hint">-1表示无限次</text>
							</view>
						</view>
					</view>
				</view>
			</view>
			<view class="modal-footer">
				<el-button @click="handleClose" :disabled="loading">取消</el-button>
				<el-button type="primary" @click="handleSave" :loading="loading">保存</el-button>
			</view>
		</view>
	</view>
</template>

<script>
import { ElInput, ElButton } from 'element-plus'
import { mapActions } from 'vuex'

export default {
	name: 'AgentConfigEditModal',
	components: {
		ElInput,
		ElButton
	},
	props: {
		visible: {
			type: Boolean,
			default: false
		},
		configData: {
			type: Object,
			default: () => ({})
		}
	},
	data() {
		return {
			loading: false,
			editForm: {
				jmfy: '',
				xyfy: '',
				zmgs: '',
				d21: '',
				d22: ''
			}
		}
	},
	watch: {
		configData: {
			handler(newVal) {
				if (newVal) {
					this.editForm = {
						jmfy: newVal.jmfy !== null && newVal.jmfy !== undefined ? String(newVal.jmfy) : '',
						xyfy: newVal.xyfy !== null && newVal.xyfy !== undefined ? String(newVal.xyfy) : '',
						zmgs: newVal.zmgs !== null && newVal.zmgs !== undefined ? String(newVal.zmgs) : '',
						d21: newVal.d21 !== null && newVal.d21 !== undefined ? String(newVal.d21) : '',
						d22: newVal.d22 !== null && newVal.d22 !== undefined ? String(newVal.d22) : ''
					}
				}
			},
			immediate: true,
			deep: true
		},
		visible(newVal) {
			if (newVal && this.configData) {
				this.editForm = {
					jmfy: this.configData.jmfy !== null && this.configData.jmfy !== undefined ? String(this.configData.jmfy) : '',
					xyfy: this.configData.xyfy !== null && this.configData.xyfy !== undefined ? String(this.configData.xyfy) : '',
					zmgs: this.configData.zmgs !== null && this.configData.zmgs !== undefined ? String(this.configData.zmgs) : '',
					d21: this.configData.d21 !== null && this.configData.d21 !== undefined ? String(this.configData.d21) : '',
					d22: this.configData.d22 !== null && this.configData.d22 !== undefined ? String(this.configData.d22) : ''
				}
			}
		}
	},
	methods: {
		...mapActions(['updateAgentConfig']),
		
		handleClose() {
			this.$emit('close')
		},
		
		async handleSave() {
			this.loading = true
			try {
				const result = await this.updateAgentConfig({
					dlmc: this.configData.dlmc,
					jmfy: this.editForm.jmfy,
					xyfy: this.editForm.xyfy,
					zmgs: this.editForm.zmgs,
					d21: this.editForm.d21,
					d22: this.editForm.d22
				})
				
				if (result.success) {
					uni.showToast({ title: '保存成功', icon: 'success' })
					this.$emit('success')
				}
			} finally {
				this.loading = false
			}
		}
	}
}
</script>

<style scoped>
.modal-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.5);
	display: flex;
	justify-content: center;
	align-items: center;
	z-index: 9999;
}

.modal-content {
	background-color: #fff;
	border-radius: 8px;
	width: 90%;
	max-width: 650px;
	box-shadow: 0 4px 10px rgba(0, 0, 0, 0.2);
	max-height: 90vh;
	overflow-y: auto;
}

.modal-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 15px 20px;
	border-bottom: 1px solid #eee;
}

.modal-title {
	font-size: 18px;
	font-weight: bold;
	color: #333;
}

.modal-close {
	font-size: 24px;
	color: #999;
	cursor: pointer;
}

.modal-body {
	padding: 20px;
}

.form-grid {
	display: flex;
	gap: 20px;
}

.form-column {
	flex: 1;
}

.column-divider {
	width: 1px;
	background-color: #e0e0e0;
	margin: 0 10px;
}

.form-group {
	margin-bottom: 18px;
}

.form-label {
	display: block;
	margin-bottom: 8px;
	font-weight: 500;
	color: #333;
	font-size: 14px;
}

.input-wrapper {
	display: flex;
	align-items: center;
	gap: 10px;
}

.input-wrapper .el-input {
	flex: 1;
}

.form-hint {
	font-size: 12px;
	color: #999;
	white-space: nowrap;
}

.modal-footer {
	padding: 15px 20px;
	border-top: 1px solid #eee;
	display: flex;
	justify-content: flex-end;
	gap: 10px;
}
</style>
