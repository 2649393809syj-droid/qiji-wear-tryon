<template>
	<view>
		<view class="container">
			<view class="header">
				<text class="title">代理配置管理</text>
				<view class="filter-area">
					<el-input v-model="localSearchDlmc" placeholder="输入代理名称" class="search-input" clearable
						@keyup.enter="handleSearch" />
					<button class="btn search-btn" @click="handleSearch">搜索</button>
					<button class="btn reset-btn" @click="resetSearch">重置</button>
				</view>
			</view>

			<view class="excel-table">
				<view class="excel-row header">
					<view class="excel-cell index-col">#</view>
					<view class="excel-cell name-col">代理名称</view>
					<view class="excel-cell">品牌店开设权限</view>
					<view class="excel-cell">是否有关联店铺</view>
					<view class="excel-cell">有效期(天)</view>
					<view class="excel-cell">自身形象定位次数</view>
					<view class="excel-cell">自身测试报告次数</view>
					<view class="excel-cell">加盟费用</view>
					<view class="excel-cell">续约费用</view>
					<view class="excel-cell">招募等级</view>
					<view class="excel-cell">招募个数</view>
					<view class="excel-cell">可分配免费定位人数</view>
					<view class="excel-cell">可分配免费报告人数</view>
					<view class="excel-cell action-col">操作</view>
				</view>

				<view v-for="(item, index) in configList.list" :key="index" class="excel-row">
					<view class="excel-cell index-col">
						{{ (configList.currentPage - 1) * configList.pageSize + index + 1 }}
					</view>
					<view class="excel-cell name-col">{{ item.dlmc || '-' }}</view>
					<view class="excel-cell">{{ formatYesNo(item.ppd) }}</view>
					<view class="excel-cell">{{ formatYesNo(item.gldp) }}</view>
					<view class="excel-cell">{{ formatValue(item.valid_days) }}</view>
					<view class="excel-cell">{{ formatValue(item.d11) }}</view>
					<view class="excel-cell">{{ formatValue(item.d12) }}</view>
					<view class="excel-cell">{{ formatMoney(item.jmfy) }}</view>
					<view class="excel-cell">{{ formatMoney(item.xyfy) }}</view>
					<view class="excel-cell">{{ formatValue(item.zmdj) }}</view>
					<view class="excel-cell">{{ formatValue(item.zmgs) }}</view>
					<view class="excel-cell">{{ formatValue(item.d21) }}</view>
					<view class="excel-cell">{{ formatValue(item.d22) }}</view>
					<view class="excel-cell action-col">
						<button class="btn edit-btn" @click="handleEdit(item)">修改</button>
					</view>
				</view>
			</view>

			<view class="pagination-container">
				<view class="pagination">
					<button class="btn page-btn" :disabled="configList.currentPage <= 1"
						@click="changePage(configList.currentPage - 1)">
						上一页
					</button>
					<view class="page-numbers">
						<button v-for="page in getPageNumbers()" :key="page" class="btn number-btn"
							:class="{ active: page === configList.currentPage }" @click="changePage(page)">
							{{ page }}
						</button>
					</view>
					<button class="btn page-btn" :disabled="configList.currentPage >= getTotalPages()"
						@click="changePage(configList.currentPage + 1)">
						下一页
					</button>
				</view>

				<view class="page-info">
					第 {{ configList.currentPage }} 页，共 {{ getTotalPages() }} 页，总计 {{ configList.total }} 条
				</view>
			</view>

			<view class="empty" v-if="!loading && configList.list.length === 0">
				<text>暂无代理配置数据</text>
			</view>

			<view class="loading" v-if="loading">
				<text>加载中...</text>
			</view>
		</view>

		<!-- 修改弹窗组件 -->
		<AgentConfigEditModal :visible="showModal" :configData="editingItem" @close="hideModal"
			@success="onEditSuccess" />
	</view>
</template>

<script>
	import {
		mapState,
		mapActions
	} from 'vuex'
	import {
		ElInput
	} from 'element-plus'
	import AgentConfigEditModal from './component/AgentConfigEditModal.vue'

	export default {
		components: {
			ElInput,
			AgentConfigEditModal
		},
		data() {
			return {
				localSearchDlmc: '',
				showModal: false,
				editingItem: {}
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
				configList: state => ({
					list: state.agent_config_model.list,
					total: state.agent_config_model.total,
					currentPage: state.agent_config_model.currentPage,
					pageSize: state.agent_config_model.pageSize
				}),
				searchParams: state => state.agent_config_model.searchParams,
				loading: state => state.agent_config_model.loading
			})
		},
		methods: {
			...mapActions([
				'fetchAgentConfigList',
				'updateAgentConfig',
				'updateAgentConfigSearch',
				'resetAgentConfigSearch',
				'changeAgentConfigPage'
			]),

			// 加载列表
			async loadList() {
				await this.fetchAgentConfigList();
			},

			// 获取配置列表
			async getConfigList() {
				await this.loadList();
			},

			// 格式化数值显示
			formatValue(val) {
				if (val === null || val === undefined || val === '') return '-';
				if (val == -1 || val === '-1') return '无限';
				return val;
			},

			// 格式化金额显示
			formatMoney(val) {
				if (val === null || val === undefined || val === '') return '-';
				return '¥' + parseFloat(val).toFixed(2);
			},

			// 格式化有/无显示 (1=有, 0=无)
			formatYesNo(val) {
				if (val === null || val === undefined || val === '') return '-';
				return val == 1 ? '有' : '无';
			},

			// 计算总页数
			getTotalPages() {
				return Math.max(1, Math.ceil(this.configList.total / this.configList.pageSize));
			},

			// 获取要显示的页码数组
			getPageNumbers() {
				const totalPages = this.getTotalPages();
				if (totalPages <= 1) return [1];

				const current = this.configList.currentPage;
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

			// 搜索
			async handleSearch() {
				this.updateAgentConfigSearch({
					key: 'dlmc',
					value: this.localSearchDlmc.trim()
				});
				this.changeAgentConfigPage(1);
				await this.loadList();
			},

			// 切换页码
			async changePage(page) {
				const totalPages = this.getTotalPages();
				if (page < 1 || page > totalPages) {
					return;
				}
				this.changeAgentConfigPage(page);
				await this.loadList();
			},

			// 重置搜索输入并刷新列表
			async resetSearch() {
				this.localSearchDlmc = '';
				this.resetAgentConfigSearch();
				await this.loadList();
			},

			// 打开编辑弹窗
			handleEdit(item) {
				this.editingItem = {
					...item
				};
				this.showModal = true;
			},

			// 关闭弹窗
			hideModal() {
				this.showModal = false;
				this.editingItem = {};
			},

			// 编辑成功回调
			async onEditSuccess() {
				this.hideModal();
				await this.loadList();
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
		box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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

	.search-input {
		padding: 8px 12px;
		border: 1px solid #ddd;
		border-radius: 4px;
		font-size: 14px;
		min-width: 150px;
		height: 36px;
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
		background-color: #007aff;
		color: white;
		border-color: #007aff;
	}

	.reset-btn {
		background-color: #fff;
		color: #666;
		border-color: #ddd;
	}

	.excel-table {
		width: 100%;
		border: 1px solid #e0e0e0;
		overflow-x: auto;
		flex: 1;
		margin-bottom: 20px;
		border-radius: 4px;
	}

	.excel-row {
		display: flex;
		flex-direction: row;
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

	.excel-row.header {
		background-color: #f5f5f5;
		font-weight: 500;
	}

	.excel-cell {
		flex: 1;
		min-width: 80px;
		padding: 10px 8px;
		border-right: 1px solid #e0e0e0;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 13px;
	}

	.excel-cell:last-child {
		border-right: none;
	}

	.index-col {
		min-width: 40px;
		max-width: 50px;
		flex: none;
	}

	.name-col {
		min-width: 100px;
	}

	.action-col {
		min-width: 80px;
		max-width: 100px;
		flex: none;
	}

	.edit-btn {
		padding: 5px 12px;
		font-size: 12px;
		background-color: #007aff;
		color: white;
		border: 1px solid #007aff;
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

	.pagination-container {
		padding: 20px 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
	}

	.pagination {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.page-numbers {
		display: flex;
		gap: 5px;
	}

	.number-btn {
		min-width: 35px;
		padding: 5px 10px;
		color: #333;
		border-color: #ddd;
	}

	.number-btn.active {
		background-color: #007aff;
		color: white;
		border-color: #007aff;
	}

	.page-info {
		color: #666;
		font-size: 14px;
	}

	.empty,
	.loading {
		text-align: center;
		padding: 40px;
		color: #999;
	}

	.search-input {
		width: 200px;
	}
</style>