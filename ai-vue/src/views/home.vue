<template>
    <div class="home-container">
        <!-- 柔和的背景光晕，营造安静、被包裹的氛围 -->
        <div class="glow glow-1"></div>
        <div class="glow glow-2"></div>

        <div class="content">
            <div class="text">
                <p class="eyebrow">宁渡 · 心理陪伴</p>
                <h2 class="title">
                    一次温暖的对话<br />
                    <span class="highlight-text">化孤独为慰藉</span>
                </h2>
                <p class="description">
                    每个深夜，每个焦虑的时刻，我们都在这里。<br />
                    不必独自承受，让心与心的连接温暖您的每一天。
                </p>
                <div class="hero-actions">
                    <el-button size="large" type="primary" class="btn-primary" @click="goConsultation">
                        开始倾诉，获得陪伴
                    </el-button>
                    <el-button size="large" class="btn-ghost" @click="goDiary">
                        记录心情，释放情感
                    </el-button>
                </div>

                <!-- 用具体能力替代空泛标语，让访客一眼知道这里能做什么 -->
                <div class="features">
                    <div class="feature-item">
                        <div class="feature-dot"></div>
                        <span>随时在线的 AI 陪伴</span>
                    </div>
                    <div class="feature-item">
                        <div class="feature-dot"></div>
                        <span>情绪变化被温柔看见</span>
                    </div>
                    <div class="feature-item">
                        <div class="feature-dot"></div>
                        <span>专业心理科普文章</span>
                    </div>
                </div>
            </div>

            <div class="robot">
                <div class="robot-ring ring-outer"></div>
                <div class="robot-ring ring-inner"></div>
                <el-image style="width: 150px; height: 150px" :src="iconUrl" alt="AI 陪伴助手" class="robot-image" />
            </div>
        </div>
    </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const iconUrl = new URL('@/assets/images/robot-fill.png', import.meta.url).href

// 开始倾诉：未登录先跳登录页
const goConsultation = () => {
    router.push(localStorage.getItem('token') ? '/consultation' : '/auth/login')
}

// 记录心情：未登录先跳登录页
const goDiary = () => {
    router.push(localStorage.getItem('token') ? '/emotion-diary' : '/auth/login')
}
</script>

<style scoped lang="scss">
.home-container {
    position: relative;
    overflow: hidden;
    background: linear-gradient(135deg, #eaf2f9 0%, #f6f9fc 45%, #f2f6fb 100%);
    color: var(--mh-text);
    padding: 5rem 0;
    height: calc(100vh - 285px);
    display: flex;
    align-items: center;
    justify-content: center;

    /* 背景光晕：两团柔和色斑，避免大面积纯色的单调与冷硬 */
    .glow {
        position: absolute;
        border-radius: 50%;
        filter: blur(80px);
        pointer-events: none;
    }

    .glow-1 {
        width: 420px;
        height: 420px;
        background: rgba(143, 180, 212, 0.35);
        top: -120px;
        right: 8%;
    }

    .glow-2 {
        width: 360px;
        height: 360px;
        background: rgba(199, 168, 208, 0.22);
        bottom: -140px;
        left: 4%;
    }

    .content {
        position: relative;
        z-index: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 60px;

        .text {
            width: 520px;

            .eyebrow {
                display: inline-block;
                font-size: 13px;
                font-weight: 600;
                letter-spacing: 2px;
                color: var(--mh-primary);
                background: rgba(255, 255, 255, 0.8);
                border: 1px solid var(--mh-primary-lighter);
                padding: 6px 16px;
                border-radius: var(--mh-radius-full);
                margin-bottom: 22px;
            }

            .title {
                font-size: 44px;
                line-height: 1.35;
                font-weight: 700;
                margin-bottom: 20px;
                letter-spacing: 1px;

                .highlight-text {
                    color: var(--mh-primary);
                    /* 用渐变下划线替代高亮色块，更克制也更耐看 */
                    background-image: linear-gradient(120deg, var(--mh-primary-lighter) 0%, var(--mh-accent-light) 100%);
                    background-repeat: no-repeat;
                    background-size: 100% 12px;
                    background-position: 0 88%;
                    padding-bottom: 4px;
                }
            }

            .description {
                font-size: 16px;
                line-height: 1.9;
                color: var(--mh-text-secondary);
            }

            .hero-actions {
                margin-top: 32px;
                display: flex;
                gap: 14px;

                .btn-primary {
                    padding: 0 28px;
                    height: 46px;
                    font-size: 15px;
                    border-radius: var(--mh-radius-sm);
                    box-shadow: 0 8px 20px rgba(92, 141, 184, 0.25);
                }

                .btn-ghost {
                    padding: 0 28px;
                    height: 46px;
                    font-size: 15px;
                    border-radius: var(--mh-radius-sm);
                    color: var(--mh-primary);
                    border-color: var(--mh-primary-lighter);
                    background: rgba(255, 255, 255, 0.7);
                }
            }

            .features {
                margin-top: 34px;
                display: flex;
                flex-wrap: wrap;
                gap: 10px 26px;

                .feature-item {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    font-size: 13px;
                    color: var(--mh-text-secondary);

                    .feature-dot {
                        width: 6px;
                        height: 6px;
                        border-radius: 50%;
                        background: var(--mh-primary-light);
                        flex-shrink: 0;
                    }
                }
            }
        }

        .robot {
            position: relative;
            display: flex;
            justify-content: center;
            align-items: center;
            width: 260px;
            height: 260px;
            border-radius: 50%;
            border: 1px solid rgba(255, 255, 255, 0.9);
            background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(237, 244, 250, 0.6) 100%);
            box-shadow: 0 20px 50px rgba(92, 141, 184, 0.15), inset 0 1px 0 rgba(255, 255, 255, 0.8);

            .robot-image {
                position: relative;
                z-index: 2;
            }

            /* 双层呼吸圆环：缓慢扩散，呼应「呼吸」这一心理放松的核心意象 */
            .robot-ring {
                position: absolute;
                border-radius: 50%;
                border: 1px solid var(--mh-primary-lighter);
                animation: breathe 5s ease-in-out infinite;
            }

            .ring-outer {
                width: 100%;
                height: 100%;
            }

            .ring-inner {
                width: 76%;
                height: 76%;
                animation-delay: 1.2s;
                border-color: var(--mh-accent-light);
            }
        }
    }
}

@keyframes breathe {
    0%, 100% {
        transform: scale(1);
        opacity: 0.7;
    }
    50% {
        transform: scale(1.06);
        opacity: 0.35;
    }
}

/* 尊重用户的「减少动态效果」系统偏好——焦虑用户对动效更敏感 */
@media (prefers-reduced-motion: reduce) {
    .robot-ring {
        animation: none;
    }
}
</style>
