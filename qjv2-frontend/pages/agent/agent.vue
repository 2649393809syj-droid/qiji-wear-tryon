<template>

	<view>
		<view class="container">
			<view class="header">
				<text class="title">代理审批</text>
				<view class="add-area">
					<button class="add-btn" @click="showAddModal">添加代理</button>
				</view>
			</view>

			<view class="excel-table">
				<view class="excel-row header">
					<view class="excel-cell index">#</view>
					<view class="excel-cell">OpenID</view>
					<view class="excel-cell">姓名</view>
					<view class="excel-cell">状态</view>
					<view class="excel-cell">到期时间</view>
					<view class="excel-cell">申请角色</view>
					<view class="excel-cell">测试次数</view>
					<view class="excel-cell">测试报告次数</view>
					<view class="excel-cell">城市</view>
					<view class="excel-cell">推荐人OpenID</view>
					<view class="excel-cell">当前角色</view>
					<view class="excel-cell">查询权限</view>
					<view class="excel-cell">加入时间</view>
					<view class="excel-cell"></view>
				</view>

				<view v-for="(item, index) in agentList.list" :key="index" class="excel-row">
					<view class="excel-cell index">{{ index + 1 }}</view>
					<view class="excel-cell">{{ item.openid }}</view>
					<view class="excel-cell">{{ item.name || '-' }}</view>
					<view class="excel-cell">{{ agentStatusMap[item.status] || item.status }}</view>
					<view class="excel-cell">{{ formatDate(item.expired_date) }}</view>
					<view class="excel-cell">{{ getApplyRoleName(item) }}</view>
					<view class="excel-cell">{{ item.test_times === -1 ? '无限' : item.test_times }}</view>
					<view class="excel-cell">{{ item.test_report_times }}</view>
					<view class="excel-cell">{{ item.city || '-' }}</view>
					<view class="excel-cell">{{ getFormattedFromOpenid(item) }}</view>
					<view class="excel-cell">{{ getCurrentRoleName(item) }}</view>
					<view class="excel-cell">
						<text :class="Number(item.query_auth) == 1 ? 'auth-yes' : 'auth-no'">
							{{ Number(item.query_auth) == 1 ? '有' : '无' }}
						</text>
					</view>
					<view class="excel-cell">{{ formatDate(item.join_date) }}</view>
					<view class="excel-cell actions">
						<button class="action-btn edit" @click="handleEdit(item)">修改</button>
					</view>
				</view>
			</view>

			<view class="empty" v-if="!loading && agentList.list.length === 0">
				<text>暂无代理数据</text>
			</view>

			<view class="loading" v-if="loading">
				<text>加载中...</text>
			</view>
		</view>

		<!-- 新增代理弹窗 -->
		<view class="modal-overlay" v-if="showModal" @click="hideModal">
			<view class="modal-content" @click.stop>
				<view class="modal-header">
					<text class="modal-title">新增代理</text>
					<text class="modal-close" @click="hideModal">×</text>
				</view>
				<view class="modal-body">
					<view class="form-group">
						<label class="form-label">OpenID *</label>
						<input class="form-input search-input" v-model="newAgent.openid" placeholder="请输入OpenID" />
					</view>
					<view class="form-group">
						<label class="form-label">申请角色 *</label>
						<select class="form-select search-input" v-model="newAgent.applyRole"
							@change="onRoleSelectChange">
							<option value="0">请选择申请角色</option>
							<option value="7">个人代理</option>
							<option value="6">平台代理</option>
							<option value="5">城市代理</option>
						</select>
					</view>
					<view class="form-group" v-if="newAgent.applyRole === 5">
						<label class="form-label">城市 *</label>
						<view class="city-select-container">
							<input class="form-input search-input city-input" v-model="citySearchText"
								@input="onCitySearch" @focus="showCityDropdown = true" @blur="onCityBlur"
								placeholder="请输入城市名称或首拼" />
							<view class="city-dropdown" v-if="showCityDropdown && filteredCities.length > 0">
								<view v-for="city in filteredCities" :key="city.code" class="city-option"
									@click="selectCity(city)">
									<text class="city-name">{{ city.name }}</text>
									<text class="city-pinyin">{{ city.pinyin }}</text>
								</view>
							</view>
						</view>
					</view>
				</view>
				<view class="modal-footer">
					<button class="btn cancel" @click="hideModal">取消</button>
					<button class="btn confirm" @click="handleAddAgent">确认</button>
				</view>
			</view>
		</view>

		<!-- 修改代理弹窗 -->
		<AgentEditModal :visible="showEditModal" :agent="currentEditAgent" @close="hideEditModal"
			@success="onEditSuccess" />
	</view>
</template>

<script>
	import request from '@/common/utils/request.js'
	import {
		formatDate
	} from '@/common/utils/format.js'
	import {
		agentListTemplate,
		agentStatusMap,
		agentRoleMap,
		AGENT_STATUS
	} from '@/common/data_templates/agent/agent_model.js'
	import {
		AGENT,
		MODULE_NAMES,
		ACTION_TYPES
	} from '@/common/funcCodes.js'
	import AgentEditModal from './component/AgentEditModal.vue'

	export default {
		components: {
			AgentEditModal
		},
		data() {
			return {
				agentList: {
					...agentListTemplate
				},
				agentStatusMap,
				agentRoleMap,
				AGENT_STATUS,
				loading: false,
				showModal: false,
				showEditModal: false,
				currentEditAgent: {},
				newAgent: {
					openid: '',
					applyRole: 0,
					city: ''
				},
				citySearchText: '',
				showCityDropdown: false,
				allCities: [],
				filteredCities: []
			}
		},
		computed: {},
		mounted() {
			console.log('getAgentList触发');
			this.getAgentList();
			this.loadCities();
		},
		onLoad() {
			console.log('getAgentList触发');
			this.getAgentList();
			this.loadCities();
		},
		activated() {
			this.getAgentList();
			this.loadCities();
		},
		methods: {
			formatDate,
			getRoleName(roleCode) {
				const code = parseInt(roleCode);
				switch (code) {
					case 5:
						return '城市代理'
					case 6:
						return '平台代理'
					case 7:
						return '个人代理'
					case 0:
						return '普通用户'
					default:
						return '未知'
				}
			},
			getApplyRoleName(item) {
				if (item.apply_role_name && item.apply_role_name !== '未知') {
					return item.apply_role_name
				}
				return this.getRoleName(item.apply_role || item.role);
			},
			getCurrentRoleName(item) {
				if (item.current_role_name && item.current_role_name !== '未知') {
					return item.current_role_name
				}

				let currentRole = item.current_role;

				if ((!currentRole || currentRole === 0) && item.openid) {
					const sameUserRecords = this.agentList.list.filter(record =>
						record.openid === item.openid &&
						record.status === 1 &&
						record.join_date !== item.join_date
					);

					if (sameUserRecords.length > 0) {
						const validRoles = sameUserRecords.map(r => r.current_role || r.apply_role).filter(r => r && r >
						0);
						if (validRoles.length > 0) {
							const maxRole = Math.min(...validRoles);
							currentRole = maxRole;
						}
					}
				}

				return this.getRoleName(currentRole);
			},
			getFormattedFromOpenid(item) {
				if (!item.from_openid || item.from_openid.trim() === '') {
					return '-'
				}
				const openid = item.from_openid.trim();
				if (openid.length > 16) {
					return openid.substring(0, 8) + '...' + openid.substring(openid.length - 4);
				}
				return openid;
			},
			getStatusClass(status) {
				switch (status) {
					case AGENT_STATUS.VALID:
						return 'status-valid'
					case AGENT_STATUS.PENDING:
						return 'status-pending'
					case AGENT_STATUS.REJECTED:
						return 'status-rejected'
					case AGENT_STATUS.SUSPENDED:
						return 'status-suspended'
					case AGENT_STATUS.RENEWAL:
						return 'status-renewal'
					default:
						return ''
				}
			},
			handleEdit(item) {
				this.currentEditAgent = {
					...item
				};
				this.showEditModal = true;
			},
			hideEditModal() {
				this.showEditModal = false;
				this.currentEditAgent = {};
			},
			onEditSuccess() {
				this.hideEditModal();
				this.getAgentList();
			},
			async getAgentList() {
				this.loading = true
				try {
					let params = {
						page_size: this.agentList.page_size || 10,
						offset: ((this.agentList.current_page || 1) - 1) * (this.agentList.page_size || 10)
					};

					console.log('请求参数:', params);

					const res = await request({
						funcCode: AGENT.LIST,
						funcName: 'F_301_4021_ADMIN_GET_AGENT_LIST',
						moduleName: MODULE_NAMES.AGENT,
						action: ACTION_TYPES.QUERY,
						data: params
					})

					console.log('接口响应:', res);

					if (res.errCode === 0) {
						this.agentList.list = res.result?.list || [];
						this.agentList.total = res.result?.totalCount || 0;
						this.agentList.currentPage = res.result?.page || 1;
						this.agentList.pageSize = res.result?.pageSize || 10;

						this.agentList.list = this.agentList.list.map(item => {
							// 调试：打印query_auth字段
							console.log(`代理 ${item.openid} 的 query_auth 值:`, item.query_auth, '类型:',
								typeof item.query_auth);

							if (item.status === 1 && item.current_role !== item.apply_role) {
								return {
									...item,
									current_role: item.apply_role
								};
							}
							return item;
						});

						console.log('Agent list:', JSON.stringify(this.agentList.list));
					} else {
						uni.showToast({
							title: res.errMsg || '获取代理列表失败',
							icon: 'none'
						})
					}
				} catch (error) {
					console.error('获取代理列表失败：', error)
					uni.showToast({
						title: '获取代理列表失败',
						icon: 'none'
					})
				} finally {
					this.loading = false
				}
			},
			showAddModal() {
				this.showModal = true;
				this.newAgent = {
					openid: '',
					applyRole: 0,
					city: ''
				};
				this.citySearchText = '';
				this.showCityDropdown = false;
				this.filteredCities = [];
			},
			hideModal() {
				this.showModal = false;
				this.newAgent = {
					openid: '',
					applyRole: 0,
					city: ''
				};
				this.citySearchText = '';
				this.showCityDropdown = false;
				this.filteredCities = [];
			},
			onRoleSelectChange(e) {
				const roleCode = parseInt(e.target.value);
				this.newAgent.applyRole = roleCode;

				if (this.newAgent.applyRole !== 5) {
					this.newAgent.city = '';
				}
			},
			async loadCities() {
				try {
					const res = await request({
						funcCode: 1124006,
						funcName: 'F_301_4006_ADMIN_GET_ALL_CITIES',
						moduleName: MODULE_NAMES.AGENT,
						action: ACTION_TYPES.QUERY,
						data: {}
					});

					if (res.errCode === 0) {
						this.allCities = res.result?.list || [];
						this.filteredCities = this.allCities;
					} else {
						console.error('获取城市列表失败:', res.errMsg);
						this.loadDefaultCities();
					}
				} catch (error) {
					console.error('获取城市列表失败：', error);
					this.loadDefaultCities();
				}
			},
			loadDefaultCities() {
				this.allCities = [{
						code: '110000',
						name: '北京市',
						pinyin: 'beijing'
					},
					{
						code: '120000',
						name: '天津市',
						pinyin: 'tianjin'
					},
					{
						code: '310000',
						name: '上海市',
						pinyin: 'shanghai'
					},
					{
						code: '500000',
						name: '重庆市',
						pinyin: 'chongqing'
					},
					{
						code: '440100',
						name: '广州市',
						pinyin: 'guangzhou'
					},
					{
						code: '440300',
						name: '深圳市',
						pinyin: 'shenzhen'
					},
					{
						code: '330100',
						name: '杭州市',
						pinyin: 'hangzhou'
					},
					{
						code: '320100',
						name: '南京市',
						pinyin: 'nanjing'
					},
					{
						code: '510100',
						name: '成都市',
						pinyin: 'chengdu'
					},
					{
						code: '420100',
						name: '武汉市',
						pinyin: 'wuhan'
					}
				];
				this.filteredCities = this.allCities;
			},
			onCitySearch() {
				const searchText = this.citySearchText.trim().toLowerCase();
				if (searchText === '') {
					this.filteredCities = this.allCities;
					return;
				}
				this.filteredCities = this.allCities.filter(city =>
					city.name.toLowerCase().includes(searchText) ||
					city.pinyin.toLowerCase().includes(searchText)
				);
			},
			onCityBlur() {
				setTimeout(() => {
					this.showCityDropdown = false;
					this.filteredCities = [];
				}, 100);
			},
			selectCity(city) {
				this.newAgent.city = city.name;
				this.citySearchText = city.name;
				this.showCityDropdown = false;
				this.filteredCities = [];
			},
			async handleAddAgent() {
				if (!this.newAgent.openid) {
					uni.showToast({
						title: '请输入OpenID',
						icon: 'none'
					});
					return;
				}
				if (!this.newAgent.applyRole) {
					uni.showToast({
						title: '请选择申请角色',
						icon: 'none'
					});
					return;
				}

				if (this.newAgent.applyRole === 5 && !this.newAgent.city) {
					uni.showToast({
						title: '城市代理必须填写城市',
						icon: 'none'
					});
					return;
				}

				try {
					const params = {
						openid: this.newAgent.openid,
						current_role: this.newAgent.applyRole,
						city: this.newAgent.city || ''
					};

					console.log('发送新增代理请求，参数:', params);

					const res = await request({
						funcCode: AGENT.ADD,
						funcName: 'F_301_4028_ADMIN_ADD_AGENT',
						moduleName: MODULE_NAMES.AGENT,
						action: 'customize',
						data: params
					});

					console.log('新增代理接口响应:', res);

					if (res.errCode === 0) {
						uni.showToast({
							title: '新增代理成功',
							icon: 'success'
						});
						this.hideModal();
						this.getAgentList();
					} else {
						uni.showToast({
							title: res.errMsg || '新增代理失败',
							icon: 'none'
						});
					}
				} catch (error) {
					console.error('新增代理失败：', error);
					uni.showToast({
						title: '新增代理失败',
						icon: 'none'
					});
				}
			}
		}
	}
</script>

<style>
	.container {
		padding: 20px;
		background-color: #ffffff;
		min-height: 100vh;
	}

	.header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20px;
		padding: 15px;
		background-color: white;
		border-radius: 8px;
		box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
	}

	.title {
		font-size: 20px;
		font-weight: bold;
		color: #333;
	}

	.add-area {
		display: flex;
		align-items: center;
	}

	.add-btn {
		padding: 10px 20px;
		background-color: #090b09;
		color: rgb(248, 246, 246);
		border: none;
		border-radius: 6px;
		font-size: 14px;
		font-weight: 500;
		cursor: pointer;
		transition: background-color 0.3s;
	}

	.add-btn:hover {
		background-color: #2381c3;
	}

	.excel-table {
		width: 100%;
		border-collapse: collapse;
		border: 1px solid #e0e0e0;
		overflow-x: auto;
		white-space: nowrap;
	}

	.excel-row {
		display: flex;
		flex-direction: row !important;
		border-bottom: 1px solid #e0e0e0;
	}

	.excel-row:last-child {
		border-bottom: none;
	}

	.excel-row:nth-child(even) {
		background-color: #f9f9f9;
	}

	.excel-row:hover {
		background-color: #f5f5f5;
	}

	.excel-cell {
		flex: 1;
		min-width: 100px;
		padding: 8px 12px;
		border-right: 1px solid #e0e0e0;
		display: flex;
		align-items: center;
		overflow: hidden;
		text-overflow: ellipsis;
	}


	.excel-cell:last-child {
		border-right: none;
	}

	.index {
		min-width: 50px;
		flex: none;
		justify-content: center;
		background-color: #f5f5f5;
	}

	.excel-cell.openid,
	.excel-cell.from_openid,
	.excel-cell.current_role {
		min-width: 200px;
		flex: 2;
	}

	.excel-cell.apply_role_name,
	.excel-cell.city {
		min-width: 120px;
		flex: 1.2;
	}

	.excel-cell.actions {
		min-width: 80px;
		flex: 0.8;
	}

	.header {
		background-color: #f5f5f5;
		font-weight: 500;
	}

	.header .excel-cell {
		justify-content: center;
	}

	.actions {
		display: flex;
		gap: 8px;
		justify-content: center;
		align-items: center;
	}

	.action-btn {
		padding: 4px 12px;
		border-radius: 4px;
		font-size: 12px;
		border: 1px solid;
		cursor: pointer;
		background-color: transparent;
		transition: all 0.2s;
	}

	.action-btn.edit {
		color: #333;
		border-color: #007aff;
		background-color: #007aff;
	}

	.action-btn.edit:hover {
		background-color: #0056b3;
		color: #333;
	}

	.auth-yes {
		color: #67c23a;
		font-weight: 500;
	}

	.auth-no {
		color: #999;
	}

	.empty,
	.loading {
		text-align: center;
		padding: 40px 20px;
		color: #666;
	}

	/* 新增代理弹窗样式 */
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
		padding: 20px;
		box-sizing: border-box;
	}

	.modal-content {
		background-color: #fff;
		border-radius: 8px;
		width: 100%;
		max-width: 500px;
		min-width: 320px;
		box-shadow: 0 4px 10px rgba(0, 0, 0, 0.2);
		position: relative;
		max-height: 90vh;
		overflow-y: auto;
		z-index: 10000;
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
		line-height: 1;
		width: 24px;
		height: 24px;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		transition: all 0.3s;
	}

	.modal-close:hover {
		background-color: #f5f5f5;
		color: #666;
	}

	.modal-body {
		padding: 20px;
	}

	.form-group {
		margin-bottom: 15px;
	}

	.form-label {
		display: block;
		margin-bottom: 6px;
		font-size: 14px;
		color: #555;
		font-weight: 500;
	}

	.form-input {
		width: 100%;
		padding: 8px 12px;
		border: 1px solid #ddd;
		border-radius: 4px;
		font-size: 14px;
		box-sizing: border-box;
		background-color: #fff;
		transition: border-color 0.3s;
		line-height: 20px;
		min-height: 36px;
		position: relative;
		z-index: 10;
	}

	.form-input:focus {
		outline: none;
		border-color: #007aff;
		box-shadow: 0 0 0 2px rgba(0, 122, 255, 0.2);
		z-index: 11;
	}

	.form-input::placeholder {
		color: #999;
		font-size: 13px;
	}

	.form-select {
		width: 100%;
		padding: 8px 12px;
		border: 1px solid #ddd;
		border-radius: 4px;
		font-size: 14px;
		box-sizing: border-box;
		background-color: #fff;
		cursor: pointer;
		transition: all 0.3s;
		appearance: none;
		background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6,9 12,15 18,9'%3e%3c/polyline%3e%3c/svg%3e");
		background-repeat: no-repeat;
		background-position: right 12px center;
		background-size: 16px;
		padding-right: 40px;
		min-height: 36px;
	}

	.form-select:hover {
		background-color: #f0f0f0;
		border-color: #007aff;
	}

	.form-select:focus {
		outline: none;
		border-color: #007aff;
		box-shadow: 0 0 0 2px rgba(0, 122, 255, 0.2);
	}

	.modal-footer {
		display: flex;
		justify-content: space-around;
		padding: 15px 20px;
		border-top: 1px solid #eee;
		gap: 15px;
	}

	.btn {
		padding: 8px 15px;
		border-radius: 4px;
		font-size: 14px;
		border: 1px solid;
		cursor: pointer;
		transition: all 0.2s;
		min-width: 70px;
		height: 36px;
		line-height: 20px;
	}

	.btn.cancel {
		background-color: #f5f5f5;
		color: #666;
		border-color: #ccc;
	}

	.btn.cancel:hover {
		background-color: #e8e8e8;
		border-color: #ccc;
	}

	.btn.confirm {
		background-color: #007aff;
		color: white;
		border-color: #007aff;
	}

	.btn.confirm:hover {
		background-color: #0056b3;
		border-color: #0056b3;
	}

	/* 城市选择器样式 */
	.city-select-container {
		position: relative;
		width: 100%;
	}

	.city-input {
		padding-right: 40px;
	}

	.city-dropdown {
		position: absolute;
		top: 100%;
		left: 0;
		width: 100%;
		max-height: 200px;
		overflow-y: auto;
		background-color: #fff;
		border: 1px solid #ddd;
		border-radius: 4px;
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
		z-index: 100;
		margin-top: 2px;
	}

	.city-option {
		padding: 8px 12px;
		cursor: pointer;
		transition: background-color 0.2s;
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.city-option:hover {
		background-color: #f0f0f0;
	}

	.city-name {
		font-size: 14px;
		color: #333;
		flex-grow: 1;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.city-pinyin {
		font-size: 12px;
		color: #999;
		margin-left: 10px;
		flex-shrink: 0;
	}
</style>