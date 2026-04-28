<template>
    <el-aside :width="isCollapse ? '64px' : '264px'">
        <el-menu
            :collapse="isCollapse"
            :collapse-transition="false"
            default-active="2"
            class="menu-style"
        >
            <div class="brand">
                <el-image style="width: 50px; height: 50px; margin-right: 10px;" :src="iconUrl" alt="logo" />
                <div v-show="!isCollapse" class="info-card">
                    <h1 class="brand-title">心理健康AI助手</h1>
                    <p class="brand-subtitle">管理后台</p>
                </div>
            </div>
            <el-menu-item @click="selectMenu" v-for="item in visibleMenus" :key="item.path" :index="item.path">
                <el-icon><component :is="item.meta.icon" /></el-icon>
                <span>{{ item.meta.title }}</span>
            </el-menu-item>
        </el-menu>
    </el-aside>
</template>
<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
const router = useRouter()

const iconUrl = new URL('@/assets/images/机器人.png', import.meta.url).href

const isCollapse = computed(() => useAdminStore().isCollapse)

/**
 * 当前登录用户的角色（1 学生 / 2 管理员 / 3 辅导员）。
 * 从 localStorage 的 userInfo 读取——与路由守卫使用的是同一份登录态。
 */
const currentRole = computed(() => {
    try {
        const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
        return Number(info.userType)
    } catch (e) {
        return null
    }
})

/**
 * 按角色过滤菜单。
 * 管理员与辅导员的职责不同、可见数据范围不同，不能共用同一套菜单——
 * 例如「知识文章」「投稿审核」属于管理员职责，辅导员不需要也不应看到。
 */
const visibleMenus = computed(() => {
    const children = router.options.routes[0]?.children || []
    return children.filter(item => {
        const roles = item.meta?.roles
        // 未声明 roles 的页面默认对所有后台角色可见，避免新增页面时被误隐藏
        if (!roles || roles.length === 0) return true
        return roles.includes(currentRole.value)
    })
})

const selectMenu = (key) => {
    const currentRoute = router.options.routes[0]
    router.push(`${currentRoute.path}/${key.index}`)
}

</script>
<style lang="scss" scoped>
.menu-style {
    height: 100%;
    .brand {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 10px;
        background-color: #fff;
        border-bottom: 1px solid #e5e7eb;
        .info-card {
            .brand-title {
                font-size: 20px;
                font-weight: bold;
                margin-bottom: 5px;
                color: #1f2937;
            }
            .brand-subtitle {
                font-size: 14px;
                color: #6b7280;
            }
        }
    }
}

</style>
