<template>
    <div class="navbar">
        <div class="flex-box">
            <el-button @click="handleCollapse">
                <el-icon><Expand /></el-icon>
            </el-button>
            <p class="page-title">{{ route.meta.title }}</p>
        </div>
        <div class="flex-box">
            <!-- 通知铃铛：辅导员与管理员都需要知道「有事发生」 -->
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notice-badge">
                <el-button circle text class="notice-btn" title="通知" @click="openNotifications">
                    <el-icon><Bell /></el-icon>
                </el-button>
            </el-badge>

            <el-dropdown @command="handleCommand">
                <div class="flex-box">
                    <el-avatar src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
                    <div class="user-info">
                        <!-- 显示真实登录用户，而非写死的名字 -->
                        <p class="user-name">{{ currentUser.name }}</p>
                        <!-- 标明角色：后台同时存在管理员与辅导员，角色决定可见范围 -->
                        <p class="user-role">{{ currentUser.roleName }}</p>
                    </div>
                    <el-icon><ArrowDown /></el-icon>
                </div>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>

        <!-- 通知列表 -->
        <el-dialog v-model="noticeVisible" title="通知" width="620px">
            <div class="notice-toolbar">
                <span class="notice-summary">
                    {{ unreadCount > 0 ? `有 ${unreadCount} 条未读` : '暂无未读通知' }}
                </span>
                <el-button v-if="unreadCount > 0" text type="primary" @click="handleMarkAll">
                    全部标为已读
                </el-button>
            </div>

            <div v-if="notifications.length === 0" class="notice-empty">
                <el-empty description="暂无通知" :image-size="72" />
            </div>
            <div v-else class="notice-list">
                <div v-for="item in notifications" :key="item.id"
                     class="notice-item" :class="{ unread: item.isRead === 0 }"
                     @click="handleRead(item)">
                    <div class="notice-head">
                        <span class="notice-title">{{ item.title }}</span>
                        <span class="notice-time">{{ dayjs(item.createdAt).format('MM-DD HH:mm') }}</span>
                    </div>
                    <div class="notice-content">{{ item.content }}</div>
                </div>
            </div>
        </el-dialog>
    </div>
</template>
<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { logout, getMyNotifications, getUnreadCount,
         markNotificationRead, markAllNotificationsRead } from '@/api/admin'

const router = useRouter()
const route = useRoute()

/**
 * 当前登录用户信息。
 * 从 localStorage 读取而不是调用接口：登录时已把 userInfo 存下，
 * 后台每个页面都发一次请求只为拿用户名并不值得。
 */
const currentUser = computed(() => {
    const roleNameMap = {
        1: '学生',
        2: '管理员',
        3: '辅导员'
    }
    try {
        const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
        return {
            name: info.displayName || info.nickname || info.username || '未登录',
            roleName: roleNameMap[Number(info.userType)] || '未知角色'
        }
    } catch (e) {
        return { name: '未登录', roleName: '未知角色' }
    }
})

// ===== 站内通知 =====
const noticeVisible = ref(false)
const notifications = ref([])
const unreadCount = ref(0)

/**
 * 拉取未读数。
 * 轻量接口，可定时轮询——辅导员不会一直盯着页面，
 * 但没有主动推送渠道时，轮询是保证「有事能被及时发现」的兜底手段。
 */
const refreshUnread = () => {
  getUnreadCount().then(res => {
    unreadCount.value = Number(res) || 0
  }).catch(() => {
    // 通知属于附加能力，拉取失败静默处理，不打扰用户
  })
}

const openNotifications = () => {
  noticeVisible.value = true
  getMyNotifications(30).then(res => {
    notifications.value = res || []
  }).catch(() => {
    notifications.value = []
  })
}

const handleRead = (item) => {
  if (item.isRead === 1) return
  markNotificationRead(item.id).then(() => {
    item.isRead = 1
    refreshUnread()
  })
}

const handleMarkAll = () => {
  markAllNotificationsRead().then(() => {
    notifications.value.forEach(n => { n.isRead = 1 })
    ElMessage.success('已全部标为已读')
    refreshUnread()
  })
}

let unreadTimer = null

onMounted(() => {
  refreshUnread()
  // 30 秒轮询一次未读数：没有 WebSocket 推送时，这是成本最低的兜底
  unreadTimer = setInterval(refreshUnread, 30000)
})

onUnmounted(() => {
  // 组件卸载时清掉定时器，避免离开后台后仍持续请求
  if (unreadTimer) {
    clearInterval(unreadTimer)
    unreadTimer = null
  }
})

const handleCommand = (command) => {
    console.log(command)
    if (command === 'logout') {
        // 处理退出登录逻辑
        ElMessageBox.confirm('确定退出登录吗？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        }).then(() => {
            // 确认退出登录：先清除本地状态（无状态JWT，服务端无需注销）
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            // 通知后端登出（接口失败也不影响本地退出）
            logout().catch(() => {})
            // 跳转到登录页
            router.push('/auth/login')
        })
    }
}

const handleCollapse = () => {
    useAdminStore().toggleCollapse()
}
</script>
<style lang="scss" scoped>
.navbar {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 15px;
    background: white;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    border-bottom: 1px solid #e5e7eb;
    .flex-box {
        display: flex;
        align-items: center;
        justify-content: center;
    }
    .page-title {
        margin-left: 20px;
        font-size: 26px;
        font-weight: bold;
        color: #1f2937;
    }
}

/* 通知列表（弹窗内容挂到 body 上，不能用 scoped 嵌套在 .navbar 内） */
.notice-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    .notice-summary {
        font-size: 13px;
        color: var(--mh-text-secondary);
    }
}

.notice-empty {
    padding: 10px 0;
}

.notice-list {
    max-height: 52vh;
    overflow-y: auto;

    .notice-item {
        padding: 12px 14px;
        border-radius: var(--mh-radius-sm);
        border: 1px solid var(--mh-border-light);
        margin-bottom: 10px;
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover {
            background: var(--mh-surface-alt);
        }

        /* 未读用左侧色条 + 浅底标识，扫一眼就能分辨 */
        &.unread {
            background: var(--mh-primary-bg);
            border-left: 3px solid var(--mh-primary);
        }

        .notice-head {
            display: flex;
            align-items: baseline;
            justify-content: space-between;
            gap: 12px;
            margin-bottom: 6px;

            .notice-title {
                font-size: 14px;
                font-weight: 600;
                color: var(--mh-text);
            }

            .notice-time {
                flex-shrink: 0;
                font-size: 12px;
                color: var(--mh-text-muted);
            }
        }

        .notice-content {
            font-size: 13px;
            line-height: 1.7;
            color: var(--mh-text-secondary);
            white-space: pre-wrap;
        }
    }
    .user-info {
        margin: 0 8px;
        text-align: left;
        line-height: 1.3;
        .user-name {
            font-size: 14px;
            font-weight: 600;
            color: var(--mh-text);
        }
        .user-role {
            font-size: 11px;
            color: var(--mh-text-muted);
        }
    }

    .notice-badge {
        margin-right: 18px;
        .notice-btn {
            color: var(--mh-text-secondary);
            &:hover {
                color: var(--mh-primary);
            }
        }
    }
}
</style>
