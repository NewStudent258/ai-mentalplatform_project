<template>
    <div class="auth-layout">
        <div class="left-section">
            <!-- 柔和光晕，营造静谧氛围，避免大面积纯色渐变显得单调 -->
            <div class="glow glow-1"></div>
            <div class="glow glow-2"></div>

            <div class="content">
                <p class="brand-eyebrow">宁渡心理</p>
                <h2 class="title">愿每一次倾诉<br />都被温柔接住</h2>
                <p class="text">每个深夜，每个焦虑的时刻，我们都在这里。<br />不必独自承受，让心与心的连接温暖您的每一天。</p>
                <div class="robot">
                    <div class="robot-ring"></div>
                    <el-image style="width: 90px;height:90px" :src="iconUrl" alt="宁渡心理" />
                </div>
            </div>
        </div>
        <div class="right-section">
            <router-view></router-view>
        </div>
    </div>
</template>
<script setup>

const iconUrl = new URL('@/assets/images/robot-fill.png', import.meta.url).href
</script>
<style scoped lang="scss">
.auth-layout {
    display: flex;

    .left-section {
        position: relative;
        overflow: hidden;
        display: flex;
        justify-content: center;
        align-items: center;
        flex: 1;
        background: var(--mh-gradient);
        height: 100vh;

        .glow {
            position: absolute;
            border-radius: 50%;
            filter: blur(70px);
            pointer-events: none;
        }

        .glow-1 {
            width: 340px;
            height: 340px;
            background: rgba(255, 255, 255, 0.18);
            top: -90px;
            left: -60px;
        }

        .glow-2 {
            width: 300px;
            height: 300px;
            background: rgba(199, 168, 208, 0.28);
            bottom: -100px;
            right: -50px;
        }

        .content {
            position: relative;
            z-index: 1;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;

            .brand-eyebrow {
                font-size: 13px;
                font-weight: 600;
                letter-spacing: 3px;
                color: rgba(255, 255, 255, 0.9);
                border: 1px solid rgba(255, 255, 255, 0.4);
                padding: 5px 18px;
                border-radius: var(--mh-radius-full);
                margin-bottom: 24px;
            }

            .title {
                margin-bottom: 20px;
                font-size: 36px;
                line-height: 1.45;
                font-weight: 700;
                color: #fff;
                text-align: center;
                letter-spacing: 2px;
            }

            .text {
                margin-bottom: 36px;
                width: 440px;
                font-size: 15px;
                line-height: 1.9;
                color: rgba(255, 255, 255, 0.88);
                text-align: center;
            }

            .robot {
                position: relative;
                display: flex;
                justify-content: center;
                align-items: center;
                width: 160px;
                height: 160px;
                border-radius: 50%;
                border: 1px solid rgba(255, 255, 255, 0.35);
                background: linear-gradient(135deg, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0.08) 100%);
                box-shadow: 0 15px 40px rgba(0, 0, 0, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.4);

                .robot-ring {
                    position: absolute;
                    width: 122%;
                    height: 122%;
                    border-radius: 50%;
                    border: 1px solid rgba(255, 255, 255, 0.28);
                    animation: auth-breathe 5s ease-in-out infinite;
                }
            }
        }
    }

    .right-section {
        display: flex;
        justify-content: center;
        align-items: center;
        flex: 1;
        height: 100vh;
        background-color: var(--mh-surface);
    }
}

@keyframes auth-breathe {
    0%, 100% {
        transform: scale(1);
        opacity: 0.6;
    }
    50% {
        transform: scale(1.05);
        opacity: 0.28;
    }
}

@media (prefers-reduced-motion: reduce) {
    .robot-ring {
        animation: none;
    }
}
</style>
