<template>
	<!-- 公共菜单栏 start -->
	<ElMenu class="jc-menu" background-color="#fdffff" active-text-color="#0084ff" text-color="#000000"
		:default-active="index" @select="handleSelect" active-background-color="#e8f9ff">
		<template v-for="menu in menus" :key="menu.index">
			<!-- 有子菜单 -->
			<template v-if="menu.children">
				<el-sub-menu :index="menu.index">
					<template #title>
						<el-icon>
							<component :is="menu.icon" />
						</el-icon>
						<span>{{menu.title}}</span>
					</template>
					<template v-for="item in menu.children" :key="item.index">
						<!-- 二级菜单有子菜单 -->
						<template v-if="item.children&&item.children.length">
							<el-sub-menu :index="item.index">
								<template #title>
									<span>{{item.title}}</span>
								</template>
								<template v-for="third_child in item.children" :key="third_child.index">
									<el-menu-item :index="third_child.index">
										<span>{{third_child.title}}</span>
									</el-menu-item>
								</template>
							</el-sub-menu>
						</template>
						<!-- 二级菜单没有子菜单 -->
						<template v-else>
							<el-menu-item :index="item.index">
								<span>{{item.title}}</span>
							</el-menu-item>
						</template>
					</template>
				</el-sub-menu>
			</template>
			<!-- 没有子菜单 -->
			<template v-else>
				<el-menu-item :index="menu.index">
					<el-icon>
						<component :is="menu.icon" />
					</el-icon>
					<span>{{menu.title}}</span>
				</el-menu-item>
			</template>
		</template>
	</ElMenu>
	<!-- 公共菜单栏 end -->
</template>

<script setup>
	import { menus } from '../../common/data_templates/baseinfo/page_status/page_statu_menu';
	import { computed } from 'vue';
	import { useStore } from 'vuex';
	import { ElMenu } from 'element-plus'
	// 导入需要的图标
	import {  Menu, Document, Setting, User, HomeFilled, DataLine, Tools } from '@element-plus/icons-vue'

	const store = useStore();
	// 从 Vuex 获取当前选中的菜单 index
	const index = computed(() => store.state.baseinfo_model.active_page)

	// 菜单选择事件
	function handleSelect(selectedIndex) {
		store.dispatch('baseinfo_change', {
			k: 'active_page',
			v: selectedIndex
		})
	}
</script>

<style scoped>
	.jr-menu {
		height: 100vh;
		width: 200px;
		overflow-y: auto;
	}

	.el-menu-item.is-active {
		background-color: #e6f7ff !important;
		font-weight: bold;
	}

	/* 图标样式 */
	.el-icon {
		margin-right: 8px;
		width: 24px;
		height: 24px;
		display: inline-flex;
		align-items: center;
		justify-content: center;
	}
</style>