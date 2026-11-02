<template>
	<view>
		<view class="container">
			<view class="header">
				<text class="title">日志管理</text>
				<view class="filter-area">
					<input 
						:value="searchParams.applicantOpenid" 
						placeholder="输入申请人OpenID" 
						class="search-input"
						@input="onSearchChange('applicantOpenid', $event)"
					/>
					<select 
						:value="searchParams.applyRole" 
						class="search-select"
						@change="onSearchChange('applyRole', $event)"
					>
						<option value="">全部角色</option>
						<option value="5">城市代理</option>
						<option value="6">平台代理</option>
						<option value="7">个人代理</option>
						<option value="0">普通用户</option>
					</select>
					<button class="btn search-btn" @click="getLogList">搜索</button>
					<button class="btn reset-btn" @click="resetSearch">重置</button>
				</view>
			</view>
			
			<view class="excel-table">
				<view class="excel-row header">
					<view class="excel-cell index-col">#</view>
					<view class="excel-cell openid-col">申请人OpenID</view>
					<view class="excel-cell nick-col">申请人昵称</view>
					<view class="excel-cell role-col">申请角色</view>
					<view class="excel-cell role-col">原角色</view>
					<view class="excel-cell time-col">审批时间</view>
					<view class="excel-cell change-col">推广关系变动</view>
					<view class="excel-cell action-col">查看详情</view>
				</view>
				
				<view v-for="(item, index) in logList.list" :key="index" class="excel-row">
					<view class="excel-cell index-col">{{ (logList.currentPage - 1) * logList.pageSize + index + 1 }}</view>
					<view class="excel-cell openid-col">{{ item.applicant_openid || '-' }}</view>
					<view class="excel-cell nick-col">{{ item.applicant_nickname || '未知用户' }}</view>
					<view class="excel-cell role-col">{{ item.apply_role_name || getRoleName(item.apply_role) }}</view>
					<view class="excel-cell role-col">{{ item.old_role_name || getRoleName(item.old_role) || '无' }}</view>
					<view class="excel-cell time-col">{{ item.approval_time ? formatDate(item.approval_time) : '-' }}</view>
					<view class="excel-cell change-col">{{ formatPromotionChanges(item.promotion_relation_changes) }}</view>
					<view class="excel-cell action-col">
						<button class="btn detail-btn" @click="showDetailModal(item)">
							查看详情
						</button>
					</view>
				</view>
			</view>
			
			<view class="pagination-container">
				<view class="pagination">
					<button 
						class="btn page-btn" 
						:disabled="logList.currentPage <= 1"
						@click="changePage(logList.currentPage - 1)"
					>
						上一页
					</button>
					<view class="page-numbers">
						<button 
							v-for="page in getPageNumbers()" 
							:key="page"
							class="btn number-btn"
							:class="{ active: page === logList.currentPage }"
							@click="changePage(page)"
						>
							{{ page }}
						</button>
					</view>
					<button 
						class="btn page-btn" 
						:disabled="logList.currentPage >= getTotalPages()"
						@click="changePage(logList.currentPage + 1)"
					>
						下一页
					</button>
				</view>
				
				<view class="page-info">
					第 {{ logList.currentPage }} 页，共 {{ getTotalPages() }} 页，总计 {{ logList.total }} 条
				</view>
			</view>
			
			<view class="empty" v-if="!loading && logList.list.length === 0">
				<text>暂无日志数据</text>
			</view>
			
			<view class="loading" v-if="loading">
				<text>加载中...</text>
			</view>
		</view>
		
		<!-- 详情弹窗 -->
		<view class="modal-overlay" v-if="showModal" @click="closeModal">
			<view class="modal-content" @click.stop>
				<view class="modal-header">
					<text class="modal-title">推广关系变动详情</text>
					<view class="modal-close" @click="closeModal">×</view>
				</view>
				<view class="modal-body">
					<view class="detail-item">
						<text class="detail-label">申请人OpenID:</text>
						<text class="detail-value">{{ currentDetail.applicant_openid || '-' }}</text>
					</view>
					<view class="detail-item">
						<text class="detail-label">申请人昵称:</text>
						<text class="detail-value">{{ currentDetail.applicant_nickname || '未知用户' }}</text>
					</view>
					<view class="detail-item">
						<text class="detail-label">申请角色:</text>
						<text class="detail-value">{{ currentDetail.apply_role_name || getRoleName(currentDetail.apply_role) }}</text>
					</view>
					<view class="detail-item">
						<text class="detail-label">原角色:</text>
						<text class="detail-value">{{ currentDetail.old_role_name || getRoleName(currentDetail.old_role) || '无' }}</text>
					</view>
					<view class="detail-item">
						<text class="detail-label">审批时间:</text>
						<text class="detail-value">{{ currentDetail.approval_time ? formatDate(currentDetail.approval_time) : '-' }}</text>
					</view>
					<view class="detail-item full-width">
						<text class="detail-label">推广关系变动详情:</text>
						<view class="detail-content">
							{{ formatPromotionChanges(currentDetail.promotion_relation_changes) }}
						</view>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { mapState, mapActions } from 'vuex'
	import { formatDate } from '@/common/utils/format.js'
	
	export default {
		data() {
			return {
				searchTimeout: null,
				showModal: false,
				currentDetail: {}
			}
		},
		mounted() {
			this.loadList();
		},
		activated() {
			this.loadList();
		},
		computed: {
			...mapState({
				logList: state => ({
					list: state.log_management_model.list,
					total: state.log_management_model.total,
					currentPage: state.log_management_model.currentPage,
					pageSize: state.log_management_model.pageSize
				}),
				searchParams: state => state.log_management_model.searchParams,
				loading: state => state.log_management_model.loading
			})
		},
		methods: {
			...mapActions([
				'fetchLogList',
				'updateLogSearch',
				'resetLogSearch',
				'changeLogPage'
			]),
			
			formatDate,
			
			// 加载列表
			async loadList() {
				await this.fetchLogList();
			},
			
			// 获取日志列表
			async getLogList() {
				await this.loadList();
			},
			
			// 计算总页数
			getTotalPages() {
				return Math.max(1, Math.ceil(this.logList.total / this.logList.pageSize));
			},
			
			// 获取要显示的页码数组
			getPageNumbers() {
				const totalPages = this.getTotalPages();
				if (totalPages <= 1) return [1];
				
				const current = this.logList.currentPage;
				const delta = 2;
				
				let start = Math.max(1, current - delta);
				let end = Math.min(totalPages, current + delta);
				
				if (end - start < 4) {
					if (start === 1) {
						end = Math.min(5, totalPages);
					} else {
						start = Math.max(1, end - 4);
					}
				}
				
				const pages = [];
				for (let i = start; i <= end; i++) {
					pages.push(i);
				}
				return pages;
			},
			
			// 获取角色名称
			getRoleName(role) {
				if (role === null || role === undefined) return '无';
				const roleMap = {
					'5': '城市代理',
					'6': '平台代理',
					'7': '个人代理',
					'0': '普通用户'
				};
				return roleMap[role] || '未知角色';
			},
			
			// 格式化推广关系变动信息
			formatPromotionChanges(changes) {
				if (!changes) return '-';
				
				try {
					if (typeof changes === 'string') {
						const parsed = JSON.parse(changes);
						if (parsed && typeof parsed === 'object') {
							if (parsed.relation_change) {
								return parsed.relation_change;
							} else if (parsed.action) {
								return parsed.action;
							}
							return '有变动';
						}
					}
					return changes.toString();
				} catch (e) {
					return changes.toString();
				}
			},
			
			// 搜索输入变化时的防抖处理
			onSearchChange(field, event) {
				const value = event.detail?.value ?? event.target?.value ?? '';
				this.updateLogSearch({ key: field, value: typeof value === 'string' ? value.trim() : value });
				
				if (this.searchTimeout) {
					clearTimeout(this.searchTimeout);
				}
				this.searchTimeout = setTimeout(() => {
					this.changeLogPage(1);
					this.loadList();
				}, 500);
			},
			
			// 切换页码
			async changePage(page) {
				const totalPages = this.getTotalPages();
				if (page < 1 || page > totalPages) {
					return;
				}
				this.changeLogPage(page);
				await this.loadList();
			},

			// 重置搜索输入并刷新列表
			async resetSearch() {
				this.resetLogSearch();
				await this.loadList();
			},

			// 显示详情弹窗
			showDetailModal(item) {
				this.currentDetail = item;
				this.showModal = true;
			},

			// 关闭详情弹窗
			closeModal() {
				this.showModal = false;
				this.currentDetail = {};
			}
		}
	}
</script>

<style>
	.container {
		padding: 20px;
		background-color: #ffffff;
		min-height: 100vh;
		display: flex;
		flex-direction: column;
	}
	
	.header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20px;
		padding: 15px;
		background-color: white;
		border-radius: 8px;
		box-shadow: 0 2px 4px rgba(0,0,0,0.1);
		flex-wrap: wrap;
		gap: 10px;
	}
	
	.title {
		font-size: 20px;
		font-weight: bold;
		color: #333;
	}
	
	.filter-area {
		display: flex;
		align-items: center;
		gap: 10px;
		flex-wrap: wrap;
	}
	
	.search-input, .search-select {
		padding: 8px 12px;
		border: 1px solid #ddd;
		border-radius: 4px;
		font-size: 14px;
		min-width: 150px;
		height: 36px;
		line-height: 20px;
	}
	
	.btn {
		padding: 8px 15px;
		border-radius: 4px;
		font-size: 14px;
		border: 1px solid;
		cursor: pointer;
		background-color: transparent;
	}
	
	.search-btn {
		color: #007aff;
		border-color: #007aff;
		background-color: #007aff;
		color: white;
	}
	
	.page-btn {
		color: #007aff;
		border-color: #007aff;
	}
	
	.page-btn:disabled {
		color: #ccc;
		border-color: #ccc;
		cursor: not-allowed;
	}
	
	.excel-table {
		width: 100%;
		border-collapse: collapse;
		border: 1px solid #e0e0e0;
		overflow-x: auto;
		white-space: nowrap;
		flex: 1;
		margin-bottom: 20px;
		border-radius: 4px;
		overflow: hidden;
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
		min-width: 120px;
		padding: 8px 12px;
		border-right: 1px solid #e0e0e0;
		display: flex;
		align-items: center;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	
	/* 列宽度控制 */
	.index-col {
		min-width: 35px;
		max-width: 40px;
		justify-content: center;
	}
	
	.openid-col {
		min-width: 200px;
		justify-content: center;
	}
	
	.nick-col {
		min-width: 100px;
		justify-content: center;
	}
	
	.role-col {
		min-width: 100px;
		justify-content: center;
	}
	
	.time-col {
		min-width: 150px;
		justify-content: center;
	}
	
	.change-col {
		min-width: 350px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		line-height: 1.4;
		align-items: center;
		padding: 8px 12px;
	}
	
	.action-col {
		min-width: 100px;
		max-width: 100px;
		flex: none;
		justify-content: center;
	}
	
	.detail-btn {
		padding: 6px 12px;
		font-size: 12px;
		background-color: #007aff;
		color: white;
		border: 1px solid #007aff;
		border-radius: 4px;
		cursor: pointer;
		transition: all 0.2s ease;
	}
	
	.detail-btn:hover {
		background-color: #0056cc;
		border-color: #0056cc;
	}
	
	.detail-btn:active {
		transform: scale(0.95);
	}
	
	.excel-cell:last-child {
		border-right: none;
	}
	
	.index-col {
		min-width: 50px;
		flex: none;
		justify-content: center;
		background-color: #f5f5f5;
	}
	
	.header {
		background-color: #f5f5f5;
		font-weight: 500;
	}
	
	.header .excel-cell {
		justify-content: center;
	}
	
	.pagination-container {
		padding: 20px 0;
		background-color: #fff;
		border-top: 1px solid #eee;
		margin-top: auto;
	}
	
	.pagination {
		display: flex;
		justify-content: center;
		align-items: center;
		gap: 10px;
		margin-bottom: 10px;
	}
	
	.page-numbers {
		display: flex;
		gap: 5px;
		margin: 0 10px;
		flex-wrap: wrap;
		justify-content: center;
	}
	
	.page-btn {
		min-width: 80px;
		height: 36px;
		line-height: 34px;
		padding: 0 15px;
		border: 1px solid #007aff;
		border-radius: 4px;
		background-color: #fff;
		color: #007aff;
		font-size: 14px;
	}
	
	.page-btn:disabled {
		color: #ccc;
		border-color: #ddd;
		cursor: not-allowed;
	}
	
	.number-btn {
		min-width: 36px;
		height: 36px;
		line-height: 34px;
		padding: 0;
		border: 1px solid #ddd;
		border-radius: 4px;
		background-color: #fff;
		color: #666;
		font-size: 14px;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	
	.number-btn.active {
		background-color: #007aff;
		border-color: #007aff;
		color: #fff;
	}
	
	.page-info {
		text-align: center;
		color: #666;
		font-size: 14px;
	}
	
	.empty, .loading {
		text-align: center;
		padding: 40px 20px;
		color: #666;
	}
	
	.role-col {
		min-width: 100px;
		max-width: 100px;
		flex: none;
		justify-content: center;
	}
	
	.nick-col {
		min-width: 100px;
		max-width: 100px;
		flex: none;
		justify-content: center;
	}
	
	.reset-btn {
		margin-left: 10px;
		color: #666;
		border-color: #ccc;
		background-color: #f5f5f5;
	}
	
	.reset-btn:hover {
		color: #fff;
		background-color: #ff6666;
		border-color: #ff6666;
	}

	/* 详情弹窗样式 */
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
		z-index: 1000;
	}

	.modal-content {
		background-color: #fff;
		border-radius: 8px;
		box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
		width: 90%;
		max-width: 600px;
		max-height: 90%;
		display: flex;
		flex-direction: column;
		overflow: hidden;
	}

	.modal-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 15px 20px;
		border-bottom: 1px solid #eee;
		background-color: #f5f5f5;
	}

	.modal-title {
		font-size: 18px;
		font-weight: bold;
		color: #333;
	}

	.modal-close {
		font-size: 24px;
		color: #666;
		cursor: pointer;
	}

	.modal-body {
		padding: 20px;
		overflow-y: auto;
		flex-grow: 1;
	}

	.detail-item {
		margin-bottom: 15px;
		display: flex;
		align-items: center;
	}

	.detail-label {
		font-size: 14px;
		color: #555;
		min-width: 120px;
		margin-right: 10px;
	}

	.detail-value {
		font-size: 14px;
		color: #333;
		flex-grow: 1;
		word-break: break-all;
	}

	.detail-content {
		font-size: 14px;
		color: #333;
		white-space: pre-wrap; /* 保留换行和空格 */
		word-break: break-all;
		line-height: 1.6;
	}

	.full-width {
		width: 100%;
	}

	/* 详情占位符样式 */
	.detail-placeholder {
		display: inline-block;
		padding: 6px 12px;
		background-color: rgba(0, 122, 255, 0.1);
		color: rgba(0, 122, 255, 0.8);
		border: 1px solid rgba(0, 122, 255, 0.3);
		border-radius: 4px;
		cursor: pointer;
		font-size: 12px;
		text-align: center;
		transition: all 0.2s ease;
		user-select: none;
	}

	.detail-placeholder:hover {
		background-color: rgba(0, 122, 255, 0.2);
		color: rgba(0, 122, 255, 1);
		border-color: rgba(0, 122, 255, 0.5);
	}

	.detail-placeholder:active {
		transform: scale(0.95);
	}
</style>