<template>
	<view class="modal-overlay" v-if="visible" @click="handleClose">
		<view class="modal-content" @click.stop>
			<view class="modal-header">
				<text class="modal-title">修改代理信息</text>
				<text class="modal-close" @click="handleClose">×</text>
			</view>
			<view class="modal-body">
				<view class="info-section">
					<view class="info-row">
						<text class="info-label">OpenID：</text>
						<text class="info-value">{{ agent.openid }}</text>
					</view>
					<view class="info-row">
						<text class="info-label">姓名：</text>
						<text class="info-value">{{ agent.name || '-' }}</text>
					</view>
					<view class="info-row">
						<text class="info-label">当前状态：</text>
						<text class="info-value" :class="getStatusClass(agent.status)">{{ agentStatusMap[agent.status] || agent.status }}</text>
					</view>
					<view class="info-row">
						<text class="info-label">当前角色：</text>
						<text class="info-value">{{ getRoleName(agent.current_role) }}</text>
					</view>
				</view>

				<view class="divider"></view>

				<!-- 状态操作区域 -->
				<view class="action-section">
					<text class="section-title">审批状态操作</text>
					<view class="action-buttons centered">
						<el-button 
							v-if="agent.status === AGENT_STATUS.PENDING" 
							type="success"
							class="black-text-btn"
							@click="handleApprove"
							:loading="loading"
						>通过审批</el-button>
						<el-button 
							v-if="agent.status === AGENT_STATUS.PENDING" 
							type="danger"
							class="black-text-btn"
							@click="handleReject"
							:loading="loading"
						>拒绝</el-button>
						<el-button 
							v-if="agent.status === AGENT_STATUS.VALID" 
							type="warning"
							class="black-text-btn"
							@click="handleSuspend"
							:loading="loading"
						>暂停</el-button>
						<el-button 
							v-if="agent.status === AGENT_STATUS.SUSPENDED" 
							type="success"
							class="black-text-btn"
							@click="handleResume"
							:loading="loading"
						>恢复有效</el-button>
						<text v-if="!canOperate" class="no-action-hint">当前状态无可用操作</text>
					</view>
				</view>

				<view class="divider"></view>

				<!-- 查询权限设置 -->
				<view class="action-section">
					<text class="section-title">查询权限设置</text>
					<view class="query-auth-buttons">
						<el-button 
							type="success"
							class="black-text-btn"
							@click="setQueryAuth(1)"
							:disabled="queryAuth === 1"
							:loading="loading"
						>授予权限</el-button>
						<el-button 
							type="danger"
							class="black-text-btn"
							@click="setQueryAuth(0)"
							:disabled="queryAuth === 0"
							:loading="loading"
						>撤销权限</el-button>
					</view>
				</view>
			</view>
			<view class="modal-footer">
				<el-button @click="handleClose" :disabled="loading">关闭</el-button>
			</view>
		</view>
	</view>
</template>

<script>
import { ElButton } from 'element-plus'
import request from '@/common/utils/request.js'
import { agentStatusMap, AGENT_STATUS } from '@/common/data_templates/agent/agent_model.js'
import { AGENT, MODULE_NAMES, ACTION_TYPES } from '@/common/funcCodes.js'

export default {
	name: 'AgentEditModal',
	components: {
		ElButton
	},
	props: {
		visible: {
			type: Boolean,
			default: false
		},
		agent: {
			type: Object,
			default: () => ({})
		}
	},
	data() {
		return {
			agentStatusMap,
			AGENT_STATUS,
			loading: false,
			queryAuth: 0
		}
	},
	computed: {
		canOperate() {
			return this.agent.status === AGENT_STATUS.PENDING || 
				   this.agent.status === AGENT_STATUS.VALID || 
				   this.agent.status === AGENT_STATUS.SUSPENDED;
		}
	},
	watch: {
		agent: {
			handler(newVal) {
				if (newVal && newVal.query_auth !== undefined) {
					this.queryAuth = newVal.query_auth || 0;
				}
			},
			immediate: true,
			deep: true
		},
		visible(newVal) {
			if (newVal && this.agent) {
				this.queryAuth = this.agent.query_auth || 0;
			}
		}
	},
	methods: {
		getRoleName(roleCode) {
			const code = parseInt(roleCode);
			switch(code) {
				case 5: return '城市代理'
				case 6: return '平台代理'
				case 7: return '个人代理'
				case 0: return '普通用户'
				default: return '未知'
			}
		},
		getStatusClass(status) {
			switch(status) {
				case AGENT_STATUS.VALID: return 'status-valid'
				case AGENT_STATUS.PENDING: return 'status-pending'
				case AGENT_STATUS.REJECTED: return 'status-rejected'
				case AGENT_STATUS.SUSPENDED: return 'status-suspended'
				default: return ''
			}
		},
		handleClose() {
			this.$emit('close');
		},
		async handleApprove() {
			this.loading = true;
			try {
				const now = new Date();
				now.setFullYear(now.getFullYear() + 1);
				const expiredDate = now.toISOString().slice(0, 19).replace('T', ' ');

				const res = await request({
					funcCode: AGENT.APPROVE,
					funcName: 'F_301_4022_ADMIN_APPROVE_AGENT',
					moduleName: MODULE_NAMES.AGENT,
					action: ACTION_TYPES.UPDATE,
					data: {
						status: 1,
						expired_date: expiredDate,
						openid: this.agent.openid
					}
				});

				if (res.errCode === 0) {
					// 更新current_role
					await request({
						funcCode: 1124005,
						funcName: 'F_301_4005_AGENT_UPDATE_CURRENT_ROLE',
						moduleName: MODULE_NAMES.AGENT,
						action: ACTION_TYPES.UPDATE,
						data: {
							current_role: this.agent.apply_role,
							openid: this.agent.openid
						}
					});
					
					uni.showToast({ title: '审批通过', icon: 'success' });
					this.$emit('success');
				} else {
					uni.showToast({ title: res.errMsg || '审批失败', icon: 'none' });
				}
			} catch (error) {
				console.error('审批失败：', error);
				uni.showToast({ title: '审批失败', icon: 'none' });
			} finally {
				this.loading = false;
			}
		},
		async handleReject() {
			this.loading = true;
			try {
				const res = await request({
					funcCode: AGENT.REJECT,
					funcName: 'F_301_4023_ADMIN_REJECT_AGENT',
					moduleName: MODULE_NAMES.AGENT,
					action: ACTION_TYPES.UPDATE,
					data: { openid: this.agent.openid }
				});
				
				if (res.errCode === 0) {
					uni.showToast({ title: '已拒绝', icon: 'success' });
					this.$emit('success');
				} else {
					uni.showToast({ title: res.errMsg || '操作失败', icon: 'none' });
				}
			} catch (error) {
				console.error('操作失败：', error);
				uni.showToast({ title: '操作失败', icon: 'none' });
			} finally {
				this.loading = false;
			}
		},
		async handleSuspend() {
			this.loading = true;
			try {
				const res = await request({
					funcCode: AGENT.SUSPEND,
					funcName: 'F_301_4006_AGENT_UPDATE_STATUS',
					moduleName: MODULE_NAMES.AGENT,
					action: ACTION_TYPES.UPDATE,
					data: {
						status: AGENT_STATUS.SUSPENDED,
						openid: this.agent.openid
					}
				});
				
				if (res.errCode === 0) {
					uni.showToast({ title: '已暂停', icon: 'success' });
					this.$emit('success');
				} else {
					uni.showToast({ title: res.errMsg || '操作失败', icon: 'none' });
				}
			} catch (error) {
				console.error('暂停失败：', error);
				uni.showToast({ title: '暂停失败', icon: 'none' });
			} finally {
				this.loading = false;
			}
		},
		async handleResume() {
			this.loading = true;
			try {
				let expiredDate = this.agent.expired_date;
				if (expiredDate && expiredDate.includes('T')) {
					expiredDate = expiredDate.slice(0, 19).replace('T', ' ');
				}
				
				const res = await request({
					funcCode: AGENT.APPROVE,
					funcName: 'F_301_4022_ADMIN_APPROVE_AGENT',
					moduleName: MODULE_NAMES.AGENT,
					action: ACTION_TYPES.UPDATE,
					data: {
						status: AGENT_STATUS.VALID,
						expired_date: expiredDate,
						openid: this.agent.openid
					}
				});
				
				if (res.errCode === 0) {
					uni.showToast({ title: '已恢复有效', icon: 'success' });
					this.$emit('success');
				} else {
					uni.showToast({ title: res.errMsg || '操作失败', icon: 'none' });
				}
			} catch (error) {
				console.error('恢复有效失败：', error);
				uni.showToast({ title: '恢复有效失败', icon: 'none' });
			} finally {
				this.loading = false;
			}
		},
		async setQueryAuth(value) {
			this.loading = true;
			try {
				const res = await request({
					funcCode: 1124052, // F_301_4052_AGENT_UPDATE_QUERY_AUTH
					funcName: 'F_301_4052_AGENT_UPDATE_QUERY_AUTH',
					moduleName: MODULE_NAMES.AGENT,
					action: ACTION_TYPES.UPDATE,
					data: {
						query_auth: value,
						openid: this.agent.openid
					}
				});
				
				if (res.errCode === 0) {
					this.queryAuth = value;
					uni.showToast({ 
						title: value === 1 ? '已授予查询权限' : '已撤销查询权限', 
						icon: 'success' 
					});
					this.$emit('success');
				} else {
					uni.showToast({ title: res.errMsg || '操作失败', icon: 'none' });
				}
			} catch (error) {
				console.error('更新查询权限失败：', error);
				uni.showToast({ title: '更新查询权限失败', icon: 'none' });
			} finally {
				this.loading = false;
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
	max-width: 500px;
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

.info-section {
	margin-bottom: 10px;
}

.info-row {
	display: flex;
	margin-bottom: 10px;
	align-items: center;
}

.info-label {
	width: 90px;
	color: #666;
	font-size: 14px;
}

.info-value {
	flex: 1;
	color: #333;
	font-size: 14px;
}

.divider {
	height: 1px;
	background-color: #eee;
	margin: 15px 0;
}

.action-section {
	margin-bottom: 10px;
}

.section-title {
	font-size: 15px;
	font-weight: 500;
	color: #333;
	margin-bottom: 12px;
	display: block;
}

.action-buttons {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
}

.action-buttons.centered {
	justify-content: center;
}

.black-text-btn {
	color: #333 !important;
}

.action-btn {
	padding: 8px 16px;
	border-radius: 4px;
	font-size: 13px;
	border: 1px solid;
	cursor: pointer;
	transition: all 0.2s;
}

.action-btn.approve, .action-btn.resume, .action-btn.grant {
	color: #333;
	border-color: #4cd964;
	background-color: transparent;
}

.action-btn.approve:hover, .action-btn.resume:hover, .action-btn.grant:hover {
	background-color: #4cd964;
	color: #333;
}

.action-btn.reject, .action-btn.suspend, .action-btn.revoke {
	color: #333;
	border-color: #dd524d;
	background-color: transparent;
}

.action-btn.reject:hover, .action-btn.suspend:hover, .action-btn.revoke:hover {
	background-color: #dd524d;
	color: #333;
}

.action-btn.active {
	opacity: 0.5;
	cursor: not-allowed;
}

.action-btn:disabled {
	opacity: 0.5;
	cursor: not-allowed;
}

.no-action-hint {
	color: #999;
	font-size: 13px;
}

.query-auth-section {
	display: flex;
	flex-direction: column;
	gap: 12px;
}

.query-auth-status {
	display: flex;
	align-items: center;
}

.query-auth-buttons {
	display: flex;
	gap: 10px;
	justify-content: center;
}

.auth-yes {
	color: #4cd964;
	font-weight: 500;
}

.auth-no {
	color: #999;
}

.status-valid { color: #333; }
.status-pending { color: #333; }
.status-rejected { color: #333; }
.status-suspended { color: #333; }

.modal-footer {
	padding: 15px 20px;
	border-top: 1px solid #eee;
	display: flex;
	justify-content: center;
}

.btn.close-btn {
	padding: 8px 30px;
	background-color: #f5f5f5;
	color: #666;
	border: 1px solid #ccc;
	border-radius: 4px;
	cursor: pointer;
}

.btn.close-btn:hover {
	background-color: #e8e8e8;
}
</style>
