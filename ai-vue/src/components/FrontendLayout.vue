<template>
    <div class="frontend-layout">
        <div class="navbar">
            <div class="navbar-container">
                <div class="brand-section" @click="router.push('/')">
                    <div class="brand-logo-wrap">
                        <el-image style="width: 34px; height: 34px" :src="iconUrl" alt="宁渡心理" />
                    </div>
                    <div class="brand-text">
                        <h1 class="brand-name">宁渡心理</h1>
                        <p class="brand-slogan">陪你走过每一段情绪</p>
                    </div>
                </div>
                <div class="nav-section">
                    <router-link to="/" class="nav-link" exact-active-class="is-active">首页</router-link>
                    <router-link to="/consultation" class="nav-link" v-if="isLoggedIn" active-class="is-active">AI 咨询</router-link>
                    <router-link to="/emotion-diary" class="nav-link" v-if="isLoggedIn" active-class="is-active">情绪日记</router-link>
                    <router-link to="/knowledge" class="nav-link" active-class="is-active">知识库</router-link>
                    <router-link to="/my-submissions" class="nav-link" v-if="isLoggedIn" active-class="is-active">我的投稿</router-link>
                    <el-button v-if="isLoggedIn" class="logout-btn" text @click="handleLogout">退出登录</el-button>
                    <template v-else>
                        <router-link to="/auth/login" class="nav-link">登录</router-link>
                        <el-button type="primary" class="register-btn" @click="router.push('/auth/register')">
                            免费注册
                        </el-button>
                    </template>
                </div>
            </div>
        </div>
        <div class="main-content">
            <!-- keep-alive: 切换页面时保留各页面组件状态（如聊天记录） -->
            <keep-alive>
                <router-view></router-view>
            </keep-alive>
        </div>
        <div class="footer-container">
            <div class="footer-inner">
                <div class="footer-brand">
                    <span class="footer-name">宁渡心理</span>
                    <span class="footer-slogan">用陪伴，渡过每一次情绪低谷</span>
                </div>
                <p class="footer-note">
                    本平台提供心理支持与科普内容，不能替代专业诊疗。如有严重困扰，请联系学校心理咨询中心或专业医疗机构。
                </p>
                <div class="footer-bottom">
                    <p>&copy; 2026 宁渡心理 · AI 心理健康助手</p>
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { logout } from '@/api/admin'
import { useRouter } from 'vue-router'

const router = useRouter()

const iconUrl = new URL('@/assets/images/机器人.png', import.meta.url).href

const isLoggedIn = ref(false)

// 登出
const handleLogout = () => {
    // 先清除本地登录状态（无状态JWT，服务端无需注销）
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    isLoggedIn.value = false
    // 通知后端登出（接口失败也不影响本地退出）
    logout().catch(() => {})
    // 跳转到登录页
    router.push('/auth/login')
}

onMounted(() => {
   isLoggedIn.value = localStorage.getItem('token') !== null
})
</script>
<style scoped lang="scss">
.frontend-layout {
    background-color: var(--mh-bg);
    min-height: 100vh;
    display: flex;
    flex-direction: column;

    .navbar {
        /* 半透明毛玻璃固定在顶部：滚动时内容可透出，保持轻盈不压抑 */
        position: sticky;
        top: 0;
        z-index: 100;
        background: rgba(255, 255, 255, 0.82);
        backdrop-filter: blur(14px);
        border-bottom: 1px solid var(--mh-border-light);
    }

    .navbar-container {
        max-width: 1200px;
        height: 68px;
        margin: 0 auto;
        padding: 0 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .brand-section {
            display: flex;
            align-items: center;
            gap: 12px;
            cursor: pointer;

            .brand-logo-wrap {
                width: 44px;
                height: 44px;
                border-radius: var(--mh-radius-sm);
                display: flex;
                align-items: center;
                justify-content: center;
                background: var(--mh-gradient-soft);
                border: 1px solid var(--mh-border);
            }

            .brand-text {
                .brand-name {
                    font-size: 19px;
                    font-weight: 700;
                    color: var(--mh-text);
                    letter-spacing: 1px;
                    line-height: 1.2;
                }

                .brand-slogan {
                    font-size: 11px;
                    color: var(--mh-text-muted);
                    letter-spacing: 0.5px;
                }
            }
        }

        .nav-section {
            display: flex;
            align-items: center;
            gap: 30px;

            .nav-link {
                position: relative;
                color: var(--mh-text-secondary);
                font-size: 15px;
                font-weight: 500;
                transition: color 0.25s ease;

                /* 悬停/选中的下划线：用伪元素做过渡，比直接改颜色更精致 */
                &::after {
                    content: '';
                    position: absolute;
                    left: 50%;
                    bottom: -6px;
                    width: 0;
                    height: 2px;
                    border-radius: 2px;
                    background: var(--mh-primary);
                    transition: all 0.25s ease;
                    transform: translateX(-50%);
                }

                &:hover {
                    color: var(--mh-primary);

                    &::after {
                        width: 60%;
                    }
                }

                &.is-active {
                    color: var(--mh-primary);
                    font-weight: 600;

                    &::after {
                        width: 60%;
                    }
                }
            }

            .logout-btn {
                color: var(--mh-text-muted);
                font-size: 14px;

                &:hover {
                    color: var(--mh-danger);
                }
            }

            .register-btn {
                border-radius: var(--mh-radius-sm);
                padding: 0 20px;
                height: 38px;
            }
        }
    }

    .main-content {
        flex: 1;
    }

    .footer-container {
        background: linear-gradient(180deg, #eef4f9 0%, #e6eef6 100%);
        color: var(--mh-text-secondary);
        padding: 36px 0 20px;
        margin-top: 60px;
        border-top: 1px solid var(--mh-border);

        .footer-inner {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 20px;
            text-align: center;

            .footer-brand {
                display: flex;
                align-items: baseline;
                justify-content: center;
                gap: 10px;
                margin-bottom: 14px;

                .footer-name {
                    font-size: 17px;
                    font-weight: 700;
                    color: var(--mh-primary);
                    letter-spacing: 1px;
                }

                .footer-slogan {
                    font-size: 12px;
                    color: var(--mh-text-muted);
                }
            }

            /* 心理健康产品的合规提示：明确能力边界与求助渠道 */
            .footer-note {
                font-size: 12px;
                line-height: 1.8;
                color: var(--mh-text-muted);
                max-width: 620px;
                margin: 0 auto 18px;
            }

            .footer-bottom {
                padding-top: 16px;
                border-top: 1px solid var(--mh-border);
                font-size: 12px;
                color: var(--mh-text-muted);
            }
        }
    }
}
</style>
