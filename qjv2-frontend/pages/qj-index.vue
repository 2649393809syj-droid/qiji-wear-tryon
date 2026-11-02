<template>
	<!-- 主组件 start -->
	<view class="qj-forntend-index">
		<!-- 顶部全局栏 start -->
		<top class="qj-top"></top>
		<!-- 顶部全局栏 end -->
		<view class="qj-container">
			<!-- 左侧公共菜单栏 start -->
			<left_window class="qj-menu"></left_window>
			<!-- 左侧公共菜单栏 end -->
			<!-- 右侧页面内容容器 start -->
			<container class="qj-content"></container>
			<!-- 右侧页面内容容器 end -->
		</view>
	</view>
	<!-- 主组件 end -->
</template>

<script setup>
	import top from './common/top_window.vue'
	import left_window from './common/left_window.vue'
	import container from './common/center_window.vue'
</script>

<style scoped>
	@import url('../static/css/common.css');

	.qj-forntend-index {
		width: 100vw;
		height: 100vh;
		overflow: hidden;
		display: flex;
		flex-direction: column;
	}

	.qj-top {
		height: 6vh;
		width: 100vw;
		/* 添加阴影效果 */
		box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
		z-index: 10;
		/* 确保阴影显示在内容上方 */
		position: relative;
		/* 为z-index生效 */
		background-color: #fff;
		/* 添加背景色使阴影更明显 */
	}

	.qj-container {
		height: 94vh;
		width: 100vw;
		display: flex;
		flex-direction: row;
	}

	.qj-menu {
		height: 100%;
		width: 200px;
		flex-shrink: 0;
		/* 禁止缩小 */
		overflow-y: auto;
		/* 如果内容超出高度可滚动 */
	}

	.qj-content {
		height: 100%;
		/* 高度填满容器 */
		flex: 1;
		/* 占据剩余空间 */
		overflow-y: auto;
		/* 如果内容超出高度可滚动 */
		min-width: 0;
		/* 防止内容溢出 */
	}
</style>