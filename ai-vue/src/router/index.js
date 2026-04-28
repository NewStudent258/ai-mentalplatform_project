import { createRouter, createWebHistory } from 'vue-router'
import BackendLayout from '@/components/BackendLayout.vue'
import AuthLayout from '@/components/AuthLayout.vue'
import FrontendLayout from '@/components/FrontendLayout.vue'


// 后台角色：2 系统管理员，3 辅导员/咨询师
// 通过 meta.roles 声明每个页面允许哪些角色访问，Sidebar 据此过滤菜单。
const ADMIN = 2
const COUNSELOR = 3

/**
 * 各角色的默认落地页。
 * <p>
 * 管理员关心平台运营数据，辅导员关心学生的危机工单——两者登录后应直接进入
 * 自己的工作台，而不是先落到一个自己无权访问的页面再被弹走。
 */
const defaultBackRoute = (role) => (role === COUNSELOR ? '/back/crisis' : '/back/dashboard')

// 路由配置
const backendRoutes = [
    {
        path: '/back',
        redirect: '/back/dashboard',
        component: BackendLayout,
        children: [
            {
                path: 'dashboard',
                component: () => import('@/views/dashboard.vue'),
                meta: {
                    title: '数据分析',
                    icon: 'PieChart',
                    roles: [ADMIN]
                }
            },
            {
                path: 'knowledge',
                component: () => import('@/views/knowledge.vue'),
                meta: {
                    title: '知识文章',
                    icon: 'ChatLineSquare',
                    roles: [ADMIN]
                }
            },
            {
                path: 'review',
                component: () => import('@/views/knowledgeReview.vue'),
                meta: {
                    title: '投稿审核',
                    icon: 'DocumentChecked',
                    roles: [ADMIN]
                }
            },
            {
                path: 'crisis',
                component: () => import('@/views/crisisWorkOrders.vue'),
                meta: {
                    title: '危机工单',
                    icon: 'Warning',
                    roles: [COUNSELOR]
                }
            },
            {
                path: 'consultations',
                component: () => import('@/views/consultations.vue'),
                meta: {
                    title: '咨询记录',
                    icon: 'Message',
                    roles: [COUNSELOR]
                }
            },
            {
                path: 'emotional',
                component: () => import('@/views/emotional.vue'),
                meta: {
                    title: '情绪日志',
                    icon: 'User',
                    roles: [COUNSELOR]
                }
            }
            // 说明：「咨询记录」「情绪日志」的 roles 为 [COUNSELOR] 而非管理员。
            // 这两页包含学生个体敏感数据（对话内容、情绪日记正文），
            // 按最小必要知悉原则只应由承担干预职责的辅导员查看，
            // 系统管理员仅接触聚合统计（数据分析页），不接触个体明细。
        ]
    },
    {
        path: '/auth',
        component: AuthLayout,
        children: [
            {
                path: 'login',
                component: () => import('@/views/login.vue'),
                meta: {
                    title: '登录'
                }
            },
            {
                path: 'register',
                component: () => import('@/views/register.vue'),
                meta: {
                    title: '注册'
                }
            }
        ]
    }
]

const frontendRoutes = [
    {
        path: '/',
        component: FrontendLayout,
        children: [
            {
                path: '',
                component: () => import('@/views/home.vue')
            },
            {
                path: 'consultation',
                component: () => import('@/views/consultation.vue')
            },
            {
                path: 'emotion-diary',
                component: () => import('@/views/emotionDiary.vue')
            },
            {
                path: 'knowledge',
                component: () => import('@/views/frontendKnowledge.vue')
            },
            {
                path: 'my-submissions',
                component: () => import('@/views/mySubmissions.vue')
            },
            {
                path: 'knowledge/article/:id',
                component: () => import('@/views/articleDetail.vue'),
                props: true
            }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes: [ ...backendRoutes, ...frontendRoutes]
})

// 路由前置守卫
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    // 当前用户是否登录
    if (token) {
        const userInfo = JSON.parse(localStorage.getItem('userInfo'))
        const role = Number(userInfo?.userType)
        // 后台用户：系统管理员(2) 与 辅导员(3) 都进入后台，
        // 具体能看哪些菜单由 Sidebar 按 meta.roles 过滤
        if (role === 2 || role === 3) {
            if (!to.path.startsWith('/back')) {
                return next(defaultBackRoute(role))
            }
            // 校验目标页面是否允许当前角色访问。
            // 这一步不能省：Sidebar 只是「不显示」无权菜单，用户仍可直接改地址栏访问，
            // 那样会拿到后端返回的权限错误，表现为「数据加载失败」这种难以定位的现象。
            // 在此统一拦截并重定向到该角色的工作台，前端导航与后端权限才不会脱节。
            const allowedRoles = to.meta?.roles
            if (allowedRoles && allowedRoles.length > 0 && !allowedRoles.includes(role)) {
                return next(defaultBackRoute(role))
            }
            next()
        } else if (role === 1){
            // 用户端账号只能访问前台路由
            if (to.path.startsWith('/back') || to.path.startsWith('/auth')) {
                next('/')
            } else {
                next()
            }
        } else {
            // 角色缺失或未知：按未登录处理，避免绕过守卫
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            next(to.path.startsWith('/back') ? '/auth/login' : '/')
        }
    } else {
        if (to.path.startsWith('/back')) {
            // 如果是访问后台页面，那么跳转岛登录页
            next('/auth/login')
        } else {
            next()
        }
    }
})

export default router
