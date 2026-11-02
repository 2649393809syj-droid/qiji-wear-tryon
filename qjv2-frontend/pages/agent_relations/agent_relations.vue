<template>
	<view>
		<view class="container">
			<view class="header">
				<text class="title">推广关系管理</text>
				<view class="filter-area">
					<input 
						:value="searchParams.agentOpenid" 
						placeholder="输入代理OpenID" 
						class="search-input"
						@input="onSearchChange('agentOpenid', $event)"
					/>
					<input 
						:value="searchParams.userOpenid" 
						placeholder="输入用户OpenID" 
						class="search-input"
						@input="onSearchChange('userOpenid', $event)"
					/>
					<button class="btn search-btn" @click="getAgentUserList">搜索</button>
					<button class="btn reset-btn" @click="resetSearch">重置</button>
				</view>
			</view>
			
			<view class="excel-table">
				<view class="excel-row header">
					<view class="excel-cell index">#</view>
					<view class="excel-cell id-col">ID</view>
					<view class="excel-cell">代理OpenID</view>
					<view class="excel-cell role-col">代理角色</view>
					<view class="excel-cell">用户OpenID</view>
					<view class="excel-cell nick-col">用户昵称</view>
					<view class="excel-cell">关系建立时间</view>
				</view>
				
				<view v-for="(item, index) in relationList.list" :key="index" class="excel-row">
					<view class="excel-cell index">{{ (relationList.currentPage - 1) * relationList.pageSize + index + 1 }}</view>
					<view class="excel-cell id-col">{{ index + 1 }}</view>
					<view class="excel-cell">{{ item.agent_openid || '-' }}</view>
					<view class="excel-cell role-col">{{ item.agent_role_name || '无角色' }}</view>
					<view class="excel-cell">{{ item.user_openid || '-' }}</view>
					<view class="excel-cell nick-col">{{ item.user_nick_name || '未知用户' }}</view>
					<view class="excel-cell">{{ item.created_at ? formatDate(item.created_at) : '-' }}</view>
				</view>
			</view>
			
			<view class="pagination-container">
				<view class="pagination">
					<button 
						class="btn page-btn" 
						:disabled="relationList.currentPage <= 1"
						@click="changePage(relationList.currentPage - 1)"
					>
						上一页
					</button>
					<view class="page-numbers">
						<button 
							v-for="page in getPageNumbers()" 
							:key="page"
							class="btn number-btn"
							:class="{ active: page === relationList.currentPage }"
							@click="changePage(page)"
						>
							{{ page }}
						</button>
					</view>
					<button 
						class="btn page-btn" 
						:disabled="relationList.currentPage >= getTotalPages()"
						@click="changePage(relationList.currentPage + 1)"
					>
						下一页
					</button>
				</view>
				
				<view class="page-info">
					第 {{ relationList.currentPage }} 页，共 {{ getTotalPages() }} 页，总计 {{ relationList.total }} 条
				</view>
			</view>
			
			<view class="empty" v-if="!loading && relationList.list.length === 0">
				<text>暂无推广关系数据</text>
			</view>
			
			<view class="loading" v-if="loading">
				<text>加载中...</text>
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
				searchTimeout: null
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
				relationList: state => ({
					list: state.agent_relations_model.list,
					total: state.agent_relations_model.total,
					currentPage: state.agent_relations_model.currentPage,
					pageSize: state.agent_relations_model.pageSize
				}),
				searchParams: state => state.agent_relations_model.searchParams,
				loading: state => state.agent_relations_model.loading
			})
		},
		methods: {
			...mapActions([
				'fetchAgentRelationsList',
				'updateAgentRelationsSearch',
				'resetAgentRelationsSearch',
				'changeAgentRelationsPage'
			]),
			
			formatDate,
			
			// 加载列表
			async loadList() {
				await this.fetchAgentRelationsList();
			},
			
			// 获取代理用户关系列表
			async getAgentUserList() {
				await this.loadList();
			},
			
			// 计算总页数
			getTotalPages() {
				return Math.max(1, Math.ceil(this.relationList.total / this.relationList.pageSize));
			},
			
			// 获取要显示的页码数组
			getPageNumbers() {
				const totalPages = this.getTotalPages();
				if (totalPages <= 1) return [1];
				
				const current = this.relationList.currentPage;
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
			
			// 搜索输入变化时的防抖处理
			onSearchChange(field, event) {
				const value = event.detail?.value ?? event.target?.value ?? '';
				this.updateAgentRelationsSearch({ key: field, value: value.trim() });
				
				if (this.searchTimeout) {
					clearTimeout(this.searchTimeout);
				}
				this.searchTimeout = setTimeout(() => {
					this.changeAgentRelationsPage(1);
					this.loadList();
				}, 500);
			},
			
			// 切换页码
			async changePage(page) {
				const totalPages = this.getTotalPages();
				if (page < 1 || page > totalPages) {
					return;
				}
				this.changeAgentRelationsPage(page);
				await this.loadList();
			},

			// 重置搜索输入并刷新列表
			async resetSearch() {
				this.resetAgentRelationsSearch();
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
	
	.search-input {
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
		justify-content: center;
		text-align: center;
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
	.id-col {
  min-width: 80px;
  max-width: 80px;
  flex: none;
  justify-content: center;
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
</style>