<template>
	<!-- 程序主要内容(容器) start -->
	<view class="jr-content">
		<!-- 程序主要内容(内容) start -->
		<view class="jr-content-inner">
			<!-- 页面内容 -->
			<view class="p-c-i-content">
				<keep-alive>
					<component ref="component_ref" :is="component_map[index]">
					</component>
				</keep-alive>
			</view>
		</view>
		<!-- 程序主要内容(内容) end -->
	</view>
	<!-- 程序主要内容(容器) end -->
</template>

<script setup>
	import { useStore } from 'vuex'
	import { component_map } from '../../common/data_templates/baseinfo/page_status/page_statu_menu'
	import { computed, watch, ref, onMounted, nextTick } from 'vue'

	const store = useStore()
	const index = computed(() => store.state.baseinfo_model.active_page)
	const component_ref = ref(null)
</script>

<style scoped>
	.jr-content {
		height: 92vh;
		width: 85vw;
		padding: 1vh 1vw;
	}

	.jr-content-inner {
		height: 100%;
		width: 100%;
	}

	.p-c-i-content {
		height: 85vh;
		width: 84vw;
	}
</style>