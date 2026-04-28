<template>
    <div class="consultation-container">
        <div class="sidebar">
            <!-- AI助手信息 -->
             <div class="ai-assistant-info">
                <div class="breathing-circle">
                    <el-image :src="iconUrl" style="width: 25px;height:25px" alt="AI助手" />
                </div>
                <h3 class="assistant-name">宁渡 AI 助手</h3>
                <div class="online-status">
                    <div class="status-dot"></div>
                    在线服务中
                </div>
             </div>
             <!-- 情绪花园 -->
             <div class="emotion-garden">
                <div class="garden-header">
                    <div class="garden-title">情绪花园</div>
                </div>
                <!-- 分值圆环按情绪水平变色：低落时偏冷紫，平和时偏青绿 -->
                <div class="emotion-info" :class="'level-' + getIntensityClass(currentEmotion.emotionScore)">
                    <div class="emotion-name">{{ currentEmotion.primaryEmotion }}</div>
                    <div class="emotion-score">{{ currentEmotion.emotionScore }}</div>
                </div>
                <div class="warm-tips">
                    <div class="emotion-status-text">
                        <span class="status-label">今天感觉</span>
                        <span class="status-emotion">{{ currentEmotion.isNegative ? '需要关注' : '很不错' }}</span>
                    </div>
                    <div class="emotion-intensity">
                        <span class="intensity-dots">
                            <span v-for="dot in 3" :key="dot" class="dot" :class="{'active': getIntensityClass(currentEmotion.emotionScore) >= dot}"></span>
                        </span>
                        <span class="intensity-text">
                            {{ getRiskText(currentEmotion.riskLevel) }}
                        </span>
                    </div>
                    <!-- 温暖建议卡片 -->
                     <div class="warm-suggestion" v-if="currentEmotion.suggestion">
                        <div class="suggestion-icon">💝</div>
                        <div class="suggestion-content">
                            <div class="suggestion-title">给你的小建议</div>
                            <div class="suggestion-text">{{ currentEmotion.suggestion }}</div>
                        </div>
                     </div>
                     <!-- 治愈行动 -->
                      <div class="healing-actions" v-if="currentEmotion.improvementSuggestions.length > 0">
                        <div class="actions-title">治愈小行动</div>
                        <div class="actions-list">
                            <div v-for="action in currentEmotion.improvementSuggestions" :key="action" class="action-item">
                                <div class="action-icon">✨</div>
                                <div class="action-text">{{ action }}</div>
                            </div>
                        </div>
                      </div>
                      <!-- 风险提示 -->
                    <div class="risk-notice" v-if="currentEmotion.isNegative && currentEmotion.riskLevel > 1">
                        <div class="notice-icon">🤗</div>
                        <div class="notice-content">
                            <div class="notice-title">温馨提示</div>
                            <div class="notice-text">{{ currentEmotion.riskDescription }}</div>
                        </div>
                    </div>
                </div>
             </div>
             <!-- 会话列表 -->
             <div class="session-history">
                <h4 class="section-title">会话列表</h4>
                <div class="session-list">
                    <div v-for="session in sessionList" :key="session.id" @click="handleSessionClick(session)" class="session-item">
                        <div class="session-info">
                            <div class="session-title">
                                <span>{{ session.sessionTitle }}</span>
                                <div class="session-meta">
                                    <span class="session-time">{{ session.startedAt }}</span>
                                </div>
                                <div class="session-preview">
                                    {{ session.lastMessageContent }}
                                </div>
                                <div class="session-stats">
                                    <span>
                                        <el-icon>
                                            <ChatRound />
                                        </el-icon>
                                        {{ session.messageCount || 0 }}
                                    </span>
                                    <span>
                                        <el-icon>
                                            <Clock />
                                        </el-icon>
                                        {{ session.durationMinutes || 0 }} 分钟
                                    </span>
                                </div>
                            </div>
                            <div class="session-actions">
                                <el-button text type="danger" size="mini" @click="handleDeleteSession(session.id)">
                                    <el-icon>
                                        <DeleteFilled />
                                    </el-icon>
                                </el-button>
                            </div>
                        </div>
                    </div>
                </div>
             </div>
        </div>
        <div class="chat-main">
            <div class="chat-header">
                <div class="header-left">
                    <div class="chat-avatar">
                        <el-image :src="iconUrl1" style="width: 30px;height: 30px" />
                    </div>
                    <div class="chat-info">
                        <h2>宁渡 AI 助手</h2>
                        <p>您的贴心 AI 心理健康助手</p>
                    </div>
                </div>
                <el-button circle class="new-session-btn" @click="createNewFrontendSession" title="新建会话">
                    <el-icon>
                        <Plus />
                    </el-icon>
                </el-button>
            </div>
            <!-- 聊天消息区域 -->
            <div class="chat-messages">
                <!-- 欢迎用语 -->
                <div class="message-item ai-message" v-if="messages.length === 0">
                    <div class="message-avatar">
                        <el-image :src="iconUrl" style="width: 18px;height: 18px" />
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <p>您好！我是小暖，您的 AI 心理健康助手。很高兴陪伴您，为您提供温暖的心理支持。请告诉我，今天您感觉怎么样？有什么想要分享的吗？</p>
                        </div>
                        <div class="message-time">刚刚</div>
                    </div>
                </div>
                <!-- 消息列表 -->
                <div v-for="msg in messages" :key="msg.id" class="message-item" :class="msg.senderType === 1 ?  'user-message' : 'ai-message'">
                    <div class="message-avatar">
                        <el-image v-if="msg.senderType === 1" style="width: 18px; height:18px" :src="iconUrl2"></el-image>
                        <el-image v-if="msg.senderType === 2" style="width: 18px; height:18px" :src="iconUrl"></el-image>
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <!-- AI正在思考中 -->
                            <div v-if="msg.senderType === 2 && isAiTyping && !msg.content" class="typing-indicator">
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                            </div>
                            <!-- AI错误提示 -->
                            <div v-else-if="msg.isError" class="error-message">
                                <p>{{ msg.content }}</p>
                            </div>
                            <!-- AI正常返回消息 -->
                             <MarkdownRenderer v-else-if="msg.senderType === 2 && !msg.isError" :content="msg.content" :is-ai-message="true" />
                             <p v-else-if="msg.content" v-html="formatMessageContent(msg.content)"></p>
                        </div>
                        <div class="message-time">{{ msg.senderType === 2 && isAiTyping ? '正在输入中...' : msg.createdAt }}</div>
                    </div>
                </div>
            </div>
            <!-- 消息输入区域 -->
            <div class="chat-input">
                <div class="input-container">
                    <el-input
                        v-model="userMessage"
                        placeholder="此刻你在想些什么？慢慢说，我在听……"
                        type="textarea"
                        :rows="3"
                        :disabled="isAiTyping"
                        @keydown="handleKeyDown"
                        class="message-input"
                        clearable />
                        <div class="input-footer">
                            <span>按 Enter 发送，Shift + Enter 换行</span>
                            <span>{{ userMessage.length }}/500</span>
                        </div>
                </div>
                <div class="action-buttons">
                    <!-- 停止按钮：仅在AI回复中显示，点击立即中断当前回复 -->
                    <el-button v-if="isAiTyping" circle class="stop-btn" title="停止回复" @click="stopAIResponse">
                        <el-icon>
                            <VideoPause />
                        </el-icon>
                    </el-button>
                    <el-button :disabled="!userMessage.trim() || userMessage.length > 500" type="primary" class="send-btn" @click="sendMessage">
                        <el-icon>
                            <Promotion />
                        </el-icon>
                    </el-button>
                </div>
            </div>
        </div>

        <!-- 心理测评弹窗：AI 推送量表后自动弹出，题目由服务端下发（AI 不参与题目内容） -->
        <el-dialog v-model="assessmentVisible" :title="assessment.scaleName || '心理自评'"
                   width="640px" :close-on-click-modal="false" class="assessment-dialog">
            <template v-if="!assessmentResult">
                <el-alert type="info" :closable="false" show-icon class="assessment-notice">
                    <template #title>{{ assessment.description }}</template>
                </el-alert>

                <div class="question-list">
                    <div v-for="(q, idx) in assessment.questions" :key="q.orderNo" class="question-item">
                        <div class="question-title">
                            <span class="question-no">{{ idx + 1 }}</span>
                            <span>{{ q.content }}</span>
                        </div>
                        <el-radio-group v-model="answers[idx]" class="option-group">
                            <el-radio v-for="(label, si) in assessment.options" :key="si" :value="si" border size="small">
                                {{ label }}
                            </el-radio>
                        </el-radio-group>
                    </div>
                </div>
            </template>

            <!-- 结果页：分数由服务端计算，不是模型算的 -->
            <template v-else>
                <div class="assessment-result">
                    <div class="result-score">
                        <span class="score-num">{{ assessmentResult.totalScore }}</span>
                        <span class="score-label">总分</span>
                    </div>
                    <el-tag :type="severityTagType(assessmentResult.severity)" size="large" effect="dark">
                        {{ assessmentResult.severity }}
                    </el-tag>
                    <p class="result-hint">
                        这个分数用于帮助你了解最近两周的状态，<strong>不是医学诊断</strong>。
                        如果困扰已经影响到日常生活，建议联系学校心理咨询中心与老师聊一聊。
                    </p>
                    <el-alert v-if="assessmentResult.riskFlag" type="error" :closable="false" show-icon>
                        <template #title>
                            你在自评中提到了伤害自己的念头。这很重要，请一定告诉身边信任的人，
                            或联系学校心理咨询中心 / 心理援助热线，你不需要独自面对。
                        </template>
                    </el-alert>
                </div>
            </template>

            <template #footer>
                <template v-if="!assessmentResult">
                    <el-button @click="assessmentVisible = false">稍后再做</el-button>
                    <el-button type="primary" :loading="submitting" @click="submitAssessmentAnswers">
                        提交（{{ answeredCount }}/{{ assessment.questions.length }}）
                    </el-button>
                </template>
                <el-button v-else type="primary" @click="assessmentVisible = false">完成</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { startSession, getSessionList, deleteSession, getSessionDetail, getSessionEmotion,
         getPendingAssessment, submitAssessment } from '@/api/frontend'
import { ElMessage } from 'element-plus'
import { ChatRound, DeleteFilled, VideoPause } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'

const iconUrl = new URL('@/assets/images/robot-fill.png', import.meta.url).href
const iconUrl1 = new URL('@/assets/images/like.png', import.meta.url).href
const iconUrl2 = new URL('@/assets/images/users.png', import.meta.url).href

// 新建会话
const createNewFrontendSession = () => {
    // 创建一个新的会话对象
    const newSession = {
        sessionId: `temp_${Date.now()}`,
        status: 'TEMP',
        sessionTitle: '新对话'
    }
    currentSession.value = newSession
    // 新会话没有任何分析结果，情绪面板必须重置，
    // 否则会残留上一个会话的情绪数据，误导用户
    resetEmotion()
}

// 定义一个当前会话对象
const currentSession = ref(null)
const sessionList = ref([])

// 定义对话消息
const messages = ref([])
// 定义用户输入消息
const userMessage = ref('')
// 定义AI助手是否正在输入
const isAiTyping = ref(false)
// 当前流式请求的控制器，供"停止回复"按钮中止请求使用
const streamCtrl = ref(null)

// 情绪花园的默认值（未分析 / 中性状态），与后端无分析结果时的返回保持一致
const DEFAULT_EMOTION = {
    primaryEmotion: '中性',
    emotionScore: 50,
    isNegative: false,
    riskLevel: 0,
    suggestion: '情绪状态平稳',
    improvementSuggestions: [],
    riskDescription: ''
}

// 情绪花园
const currentEmotion = ref({ ...DEFAULT_EMOTION })

// 重置情绪面板（新建会话时调用，避免残留上一次会话的情绪数据）
const resetEmotion = () => {
    currentEmotion.value = { ...DEFAULT_EMOTION }
}

const loadSessionEmotion = (sessionId) => {
   // 确保sessionID格式正确
    const id = sessionId.toString().startsWith('session_') ? sessionId : `session_${sessionId}`

    getSessionEmotion(id).then(res => {
        console.log(res)
        currentEmotion.value = res
    })
}

const getIntensityClass = (score) => {
    if (score >= 61) {
        return 3
    }
    if (score >= 31) {
        return 2
    }
    return 1
}

const getRiskText = (level) => {
    switch (level) {
        case 0:
            return '正常'
        case 1:
            return '关注'
        case 2:
            return '预警'
        case 3:
            return '危机'
        default:
            return '正常'
    }
}

// 定义处理键盘事件：Enter发送，Shift+Enter换行
const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        sendMessage()
    }
}

// 用户发送消息
const sendMessage = () => {
    if (!userMessage.value.trim()) return

    if (isAiTyping.value) {
        ElMessage.error('AI助手正在输入中，请稍后')
        return
    }

    const message = userMessage.value.trim()
    userMessage.value = ''

    // 如果没有会话或者是临时会话，就需要创建一个新的会话
    // （currentSession 可能为 null：会话列表接口失败时的兜底）
    if (!currentSession.value || currentSession.value.status === 'TEMP') {
       startNewSession(message)
    } else {
        // 继续现有会话
        messages.value.push({
            id: Date.now(),
            senderType: 1,
            content: message,
            createAt: new Date().toISOString()
        })
        startAIResponse(currentSession.value.sessionId, message)
    }
}

const startNewSession = (message) => {
    // 构建会话参数
    const sessionParams = {
        initialMessage: message
    }
    if (!currentSession.value || currentSession.value.sessionTitle === '新对话') {
        sessionParams.sessionTitle = `宁渡AI助手 - ${new Date().toLocaleString()}`
    } else {
        // 如果历史会话记录
        sessionParams.sessionTitle = currentSession.value.sessionTitle
    }
    // 调用后端接口创建新会话
    startSession(sessionParams).then(res => {
        console.log(res)
       // 将后端返回的数据转为前端会话格式
       const sessionData = {
            sessionId: res.sessionId,
            status: res.status,
            sessionTitle: sessionParams.sessionTitle
       }
       // 如果当前是临时会话，更新数据
       if (currentSession.value && currentSession.value.status === 'TEMP') {
            // 更新为正式会话
            Object.assign(currentSession.value, sessionData)
       } else {
            // 否则，创建一个新的会话
            currentSession.value = sessionData
       }
       // 更新会话列表
       getSessionPage()

       // 添加初始用户消息
       messages.value.push({
        id: Date.now(),
        senderType: 1,
        content: message,
        createAt: new Date().toISOString()
       })

       // 开始流式对话
       startAIResponse(currentSession.value.sessionId, message)
    })
}

const startAIResponse = (sessionId, userMessage) => {
    // 防止重复发送
    if (isAiTyping.value) {
        ElMessage.error('AI助手正在输入中，请稍后')
        return
    }


    isAiTyping.value = true

    const aiMessage = {
        id: `ai_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        senderType: 2,
        content: '',
        createAt: new Date().toISOString()
    }
    messages.value.push(aiMessage)

    // 调用流式接口
    const ctrl = new AbortController() // 用来中止fetch请求
    streamCtrl.value = ctrl // 交给"停止回复"按钮控制
    fetchEventSource('/api/psychological-chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Token': localStorage.getItem('token'),
            'Accept': 'text/event-stream'
        },
        body: JSON.stringify({
            sessionId,
            userMessage
        }),
        signal: ctrl.signal,
        onopen: (response) => {
            console.log(response)
            if (response.headers.get('Content-Type') !== 'text/event-stream') {
                ElMessage.error('服务器返回非流式数据')
            }
        },
        onmessage: (event) => {
            const raw = event.data.trim()
            if (!raw) return
            const eventName = event.event
            // 当前会话的AI消息
            const aiMessage = messages.value[messages.value.length - 1]

            if (eventName === 'done') {
                isAiTyping.value = false
                streamCtrl.value = null
                ctrl.abort()
                // 进行情绪分析
                loadSessionEmotion(currentSession.value.sessionId)
                // 检查 Agent 是否在本轮推送了测评量表
                checkPendingAssessment()
                return
            }
            const payload = JSON.parse(raw)
            const ok = String(payload.code) === '200'
            if (ok && payload.data && payload.data.content) {
                aiMessage.content += payload.data.content
            } else if (!ok) {
                // 错误回复的显示
                handleError(payload.message || 'AI回复失败')
            }
        },
        onerror: (err) => {
            handleError(err || 'AI回复失败')
            throw err
        },
        onclose: () => {
            // 开始情绪分析
            loadSessionEmotion(currentSession.value.sessionId)
        }
    })

}

// ===== 心理测评 =====
const assessmentVisible = ref(false)
const assessment = ref({ questions: [], options: [] })
const answers = ref([])
const assessmentResult = ref(null)
const submitting = ref(false)

const answeredCount = computed(() => answers.value.filter(a => a !== null && a !== undefined).length)

const severityTagType = (severity) => {
  if (severity === '重度' || severity === '中重度') return 'danger'
  if (severity === '中度') return 'warning'
  if (severity === '轻度') return 'info'
  return 'success'
}

/**
 * 检查是否有 AI 推送的待作答测评。
 * 在每轮 AI 回复结束后调用——Agent 可能通过 triggerAssessment 工具推送了量表。
 */
const checkPendingAssessment = () => {
  getPendingAssessment().then(res => {
    if (!res || !res.questions || res.questions.length === 0) return
    assessment.value = res
    // 未作答的位置保持 null，便于区分「选了第一项(0分)」与「还没选」
    answers.value = new Array(res.questions.length).fill(null)
    assessmentResult.value = null
    assessmentVisible.value = true
  }).catch(() => {
    // 测评属于附加能力，查询失败不打断对话
  })
}

const submitAssessmentAnswers = () => {
  if (answeredCount.value < assessment.value.questions.length) {
    ElMessage.warning('还有题目没有作答')
    return
  }
  submitting.value = true
  submitAssessment(assessment.value.recordId, answers.value)
    .then(res => {
      // 分数与严重程度均由服务端计算返回，前端只负责展示
      assessmentResult.value = res
    })
    .finally(() => {
      submitting.value = false
    })
}

// 停止AI回复：中止当前流式请求，保留已经生成的内容
const stopAIResponse = () => {
    if (!isAiTyping.value) return
    // abort 会让 fetchEventSource 静默结束（不触发 onerror/onclose），
    // 所以这里需要手动收尾状态
    streamCtrl.value?.abort()
    streamCtrl.value = null
    isAiTyping.value = false

    // 一个字都还没生成就点了停止，直接移除空气泡，避免留下空白消息
    const last = messages.value[messages.value.length - 1]
    if (last && last.senderType === 2 && !last.content) {
        messages.value.pop()
    }
    // 情绪分析是独立于回复生成的旁路任务，停止回复不影响它，这里照常刷新
    if (currentSession.value?.sessionId) {
        loadSessionEmotion(currentSession.value.sessionId)
    }
    ElMessage.info('已停止回复')
}

// 错误处理函数
const handleError = (error) => {
    // 当前会话的AI消息
    const aiMessage = messages.value[messages.value.length - 1]
    if (aiMessage) {
        aiMessage.content = 'AI回复失败，请重试'
    }
    isAiTyping.value = false
    streamCtrl.value = null
    ElMessage.error('AI回复失败，请重试')
}

const getSessionPage = () => {
    return getSessionList({
        pageNum: 1,
        pageSize: 10
    }).then(res => {
        console.log(res)
        sessionList.value = res.records
    })
}

// 获取会话数据
const handleSessionClick = (session) => {
    console.log(session, 'session')
    // 点击会话时，获取会话详情
    getSessionDetail(session.id).then(res => {
        console.log(res)
        messages.value = res
    })
    loadSessionEmotion(session.id)
    // 更新当前会话对象数据
    const sessionData = {
        sessionId: "session_" + session.id,
        status: 'ACTIVE',
        sessionTitle: session.sessionTitle
    }
    currentSession.value = sessionData
}

const handleDeleteSession = (sessionId) => {
    deleteSession(sessionId).then(res => {
        ElMessage.success('删除成功')
        getSessionPage()
    })
}

// 简单的换行逻辑
const formatMessageContent = (content) => {
    return content.replace(/\n/g, '<br>')
}

onMounted(() => {
    // 初始化时获取会话列表：有历史会话则自动打开最近一个（恢复聊天记录），否则新建会话
    getSessionPage()
        .then(() => {
            if (sessionList.value && sessionList.value.length > 0) {
                handleSessionClick(sessionList.value[0])
            } else {
                createNewFrontendSession()
            }
        })
        .catch(() => {
            // 列表加载失败（如登录过期、网络异常）时降级为空白新会话，
            // 否则 currentSession 会一直是 null，导致发送消息时报错点不动
            createNewFrontendSession()
        })
})

// 离开页面时中断仍在进行的流式请求，避免连接与 token 白白消耗
onUnmounted(() => {
    streamCtrl.value?.abort()
    streamCtrl.value = null
})
</script>
<style scoped lang="scss">
.consultation-container {
    margin: 0 auto;
    width: 1200px;
    display: flex;
    gap: 20px;
    padding: 24px 20px 40px;

    /* ===================== 左侧边栏 ===================== */
    .sidebar {
        width: 320px;
        flex-shrink: 0;

        /* AI 助手信息卡 */
        .ai-assistant-info {
            margin-bottom: 18px;
            background: var(--mh-surface);
            border-radius: var(--mh-radius-lg);
            padding: 20px 16px;
            box-shadow: var(--mh-shadow);
            border: 1px solid var(--mh-border-light);
            text-align: center;

            .breathing-circle {
                width: 62px;
                height: 62px;
                background: var(--mh-gradient);
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                margin: 0 auto 14px;
                /* 缓慢呼吸动效：呼应「深呼吸」这一最基础的放松练习 */
                animation: breathing 4.5s ease-in-out infinite;
                box-shadow: 0 8px 24px rgba(92, 141, 184, 0.28);
                position: relative;
            }

            .assistant-name {
                font-size: 16px;
                font-weight: 700;
                color: var(--mh-text);
                letter-spacing: 0.5px;
                margin: 0 0 10px;
            }

            .online-status {
                display: flex;
                align-items: center;
                justify-content: center;
                color: var(--mh-success);
                font-size: 12px;
                font-weight: 600;

                .status-dot {
                    width: 7px;
                    height: 7px;
                    background: var(--mh-success);
                    border-radius: 50%;
                    margin-right: 8px;
                    animation: pulse 2.4s infinite;
                    box-shadow: 0 0 0 0 rgba(106, 168, 143, 0.5);
                }
            }
        }

        /* 情绪花园 —— 本页最具辨识度的模块 */
        .emotion-garden {
            background: linear-gradient(160deg, #f2f7fb 0%, #edf3f9 55%, #f4f0f8 100%);
            border-radius: var(--mh-radius-lg);
            padding: 18px 16px;
            margin-bottom: 18px;
            box-shadow: var(--mh-shadow);
            border: 1px solid rgba(197, 219, 236, 0.6);
            position: relative;
            overflow: hidden;
            min-height: 300px;

            .garden-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 18px;

                .garden-title {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    font-size: 15px;
                    font-weight: 700;
                    color: var(--mh-text);
                    letter-spacing: 0.5px;

                    &::before {
                        content: '';
                        width: 3px;
                        height: 14px;
                        border-radius: 2px;
                        background: var(--mh-gradient);
                    }
                }
            }

            /* 分值圆环：按情绪水平切换配色 */
            .emotion-info {
                margin: 0 auto 18px;
                width: 92px;
                height: 92px;
                border-radius: 50%;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                box-shadow: 0 8px 22px rgba(92, 141, 184, 0.16);
                border: 3px solid rgba(255, 255, 255, 0.85);
                color: #fff;
                transition: background 0.6s ease;

                /* level-3 情绪平稳积极：青绿 */
                &.level-3 {
                    background: linear-gradient(135deg, #7fb8a8 0%, #6aa88f 100%);
                }

                /* level-2 中间状态：雾蓝（主色） */
                &.level-2 {
                    background: var(--mh-gradient);
                }

                /* level-1 情绪低落：偏冷紫，传递「需要被关照」的信号 */
                &.level-1 {
                    background: linear-gradient(135deg, #a99bc4 0%, #8e86b0 100%);
                }

                .emotion-name {
                    font-size: 15px;
                    font-weight: 600;
                    line-height: 1;
                    margin-bottom: 3px;
                }

                .emotion-score {
                    font-size: 15px;
                    font-weight: 700;
                    opacity: 0.92;
                }
            }

            .warm-tips {
                text-align: center;

                .emotion-status-text {
                    margin-bottom: 12px;

                    .status-label {
                        font-size: 13px;
                        color: var(--mh-text-secondary);
                        margin-right: 8px;
                    }

                    .status-emotion {
                        font-size: 14px;
                        font-weight: 600;
                        padding: 3px 12px;
                        border-radius: var(--mh-radius-full);
                        display: inline-block;
                        color: var(--mh-primary);
                        background: rgba(255, 255, 255, 0.85);
                        border: 1px solid var(--mh-primary-lighter);
                    }
                }

                .emotion-intensity {
                    margin-bottom: 14px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;

                    .intensity-dots {
                        display: flex;
                        gap: 5px;

                        .dot {
                            width: 7px;
                            height: 7px;
                            border-radius: 50%;
                            background: #d5e0ea;
                            transition: all 0.35s ease;

                            &.active {
                                background: var(--mh-gradient);
                                transform: scale(1.25);
                                box-shadow: 0 2px 8px rgba(92, 141, 184, 0.4);
                            }
                        }
                    }

                    .intensity-text {
                        font-size: 12px;
                        color: var(--mh-text-secondary);
                        font-weight: 500;
                    }
                }

                .warm-suggestion {
                    background: rgba(255, 255, 255, 0.92);
                    border-radius: var(--mh-radius);
                    padding: 12px;
                    margin-bottom: 12px;
                    display: flex;
                    align-items: flex-start;
                    gap: 10px;
                    border: 1px solid rgba(255, 255, 255, 0.9);
                    box-shadow: var(--mh-shadow-sm);

                    .suggestion-icon {
                        font-size: 18px;
                        flex-shrink: 0;
                        margin-top: 1px;
                    }

                    .suggestion-content {
                        text-align: left;
                        flex: 1;

                        .suggestion-title {
                            font-size: 13px;
                            font-weight: 600;
                            color: var(--mh-text);
                            margin-bottom: 5px;
                        }

                        .suggestion-text {
                            font-size: 12.5px;
                            color: var(--mh-text-secondary);
                            line-height: 1.6;
                        }
                    }
                }

                .healing-actions {
                    margin-bottom: 12px;

                    .actions-title {
                        font-size: 13px;
                        font-weight: 600;
                        color: var(--mh-text);
                        margin-bottom: 10px;
                    }

                    .actions-list {
                        display: flex;
                        flex-direction: column;
                        gap: 8px;

                        .action-item {
                            background: rgba(255, 255, 255, 0.8);
                            border-radius: var(--mh-radius-sm);
                            padding: 10px 12px;
                            display: flex;
                            align-items: center;
                            gap: 9px;
                            border: 1px solid rgba(255, 255, 255, 0.9);
                            box-shadow: var(--mh-shadow-sm);
                            text-align: left;

                            .action-icon {
                                font-size: 13px;
                                flex-shrink: 0;
                            }

                            .action-text {
                                font-size: 12.5px;
                                color: var(--mh-text-secondary);
                                line-height: 1.5;
                                flex: 1;
                            }
                        }
                    }
                }

                /* 风险提示：语义上必须醒目，因此保留暖色，与整体冷色形成对比 */
                .risk-notice {
                    background: linear-gradient(135deg, #fdf3e7, #fbe8d3);
                    border-radius: var(--mh-radius);
                    padding: 14px;
                    display: flex;
                    align-items: flex-start;
                    gap: 11px;
                    border: 1px solid rgba(217, 160, 91, 0.35);
                    box-shadow: 0 6px 18px rgba(217, 160, 91, 0.15);

                    .notice-icon {
                        font-size: 18px;
                        flex-shrink: 0;
                        margin-top: 1px;
                    }

                    .notice-content {
                        flex: 1;

                        .notice-title {
                            font-size: 13px;
                            font-weight: 700;
                            color: #b07d3a;
                            margin-bottom: 5px;
                        }

                        .notice-text {
                            font-size: 12.5px;
                            color: #9a6d33;
                            line-height: 1.6;
                        }
                    }
                }
            }
        }

        /* 会话列表 */
        .session-history {
            background: var(--mh-surface);
            border-radius: var(--mh-radius-lg);
            padding: 18px 16px;
            box-shadow: var(--mh-shadow);
            border: 1px solid var(--mh-border-light);
            margin-bottom: 18px;
            min-height: 200px;
            display: flex;
            flex-direction: column;

            .section-title {
                font-size: 15px;
                font-weight: 700;
                color: var(--mh-text);
                margin: 0 0 14px;
                display: flex;
                align-items: center;
                justify-content: space-between;
                letter-spacing: 0.5px;
            }

            .session-list {
                overflow-y: auto;
                max-height: 260px;
                scrollbar-width: thin;
                scrollbar-color: var(--mh-primary-lighter) transparent;

                .session-item {
                    position: relative;
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    padding: 12px;
                    margin-bottom: 6px;
                    border-radius: var(--mh-radius);
                    cursor: pointer;
                    transition: all 0.25s ease;
                    border: 1px solid transparent;

                    &:hover {
                        background: var(--mh-primary-bg);
                        border-color: var(--mh-border);
                    }

                    &.active {
                        background: var(--mh-primary-bg);
                        border-color: var(--mh-primary-lighter);
                    }

                    .session-info {
                        flex: 1;
                        min-width: 0;

                        .session-title {
                            font-weight: 500;
                            font-size: 13.5px;
                            color: var(--mh-text);
                            margin-bottom: 4px;
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;

                            .session-meta {
                                display: flex;
                                align-items: center;
                                gap: 8px;
                                margin-bottom: 6px;

                                .session-time {
                                    font-size: 12px;
                                    color: var(--mh-text-muted);
                                }
                            }

                            .session-preview {
                                width: 200px;
                                font-size: 12px;
                                color: var(--mh-text-secondary);
                                margin-bottom: 6px;
                                white-space: nowrap;
                                overflow: hidden;
                                text-overflow: ellipsis;
                            }

                            .session-stats {
                                display: flex;
                                align-items: center;
                                gap: 12px;

                                span {
                                    font-size: 12px;
                                    color: var(--mh-text-muted);
                                    display: flex;
                                    align-items: center;
                                    gap: 4px;
                                }
                            }
                        }

                        .session-actions {
                            position: absolute;
                            top: 10px;
                            right: 12px;
                            opacity: 0;
                            transition: opacity 0.25s ease;
                        }
                    }

                    /* 删除按钮仅在悬停时出现，避免列表视觉噪音 */
                    &:hover .session-actions {
                        opacity: 1;
                    }
                }

                .no-sessions-text {
                    text-align: center;
                    font-size: 14px;
                    color: var(--mh-text-muted);
                }
            }
        }
    }

    /* ===================== 右侧对话主区 ===================== */
    .chat-main {
        background: var(--mh-surface);
        border-radius: var(--mh-radius-lg);
        box-shadow: var(--mh-shadow);
        border: 1px solid var(--mh-border-light);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        flex: 1;
        min-width: 0;

        .chat-header {
            background: var(--mh-gradient);
            color: #fff;
            padding: 18px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-shrink: 0;

            .header-left {
                display: flex;
                align-items: center;

                .chat-avatar {
                    width: 46px;
                    height: 46px;
                    background: rgba(255, 255, 255, 0.22);
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin-right: 14px;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
                }

                .chat-info {
                    h2 {
                        font-size: 18px;
                        font-weight: 700;
                        margin-bottom: 3px;
                        letter-spacing: 0.5px;
                    }

                    p {
                        font-size: 13px;
                        opacity: 0.9;
                    }
                }
            }

            .new-session-btn {
                background: rgba(255, 255, 255, 0.2);
                border: none;
                color: #fff;
                transition: all 0.25s ease;

                &:hover {
                    background: rgba(255, 255, 255, 0.32);
                    transform: translateY(-1px);
                }
            }
        }

        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 18px;
            background: linear-gradient(180deg, #fbfdff 0%, #f7fafd 100%);
            min-height: 0;
            max-height: calc(100vh - 260px);
            scrollbar-width: thin;
            scrollbar-color: var(--mh-primary-lighter) transparent;

            .message-item {
                display: flex;
                align-items: flex-start;
                gap: 12px;

                .message-avatar {
                    width: 32px;
                    height: 32px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    flex-shrink: 0;
                }

                &.ai-message .message-avatar {
                    background: var(--mh-gradient);
                    box-shadow: 0 4px 12px rgba(92, 141, 184, 0.25);
                }

                &.user-message .message-avatar {
                    background: linear-gradient(135deg, #8fa8bd, #6b7c8f);
                    box-shadow: 0 4px 12px rgba(107, 124, 143, 0.22);
                }

                .message-content {
                    max-width: 72%;
                    min-width: 0;

                    .message-bubble {
                        background: var(--mh-surface);
                        border-radius: var(--mh-radius);
                        padding: 12px 16px;
                        position: relative;
                        animation: fadeInUp 0.35s ease-out;
                        border: 1px solid var(--mh-border);
                        box-shadow: var(--mh-shadow-sm);
                        font-size: 14px;
                        line-height: 1.75;
                        color: var(--mh-text);
                        word-break: break-word;

                        .typing-indicator {
                            display: flex;
                            gap: 5px;
                            padding: 6px 0;

                            .typing-dot {
                                width: 7px;
                                height: 7px;
                                background: var(--mh-primary-light);
                                border-radius: 50%;
                                animation: typing 1.4s ease-in-out infinite;

                                &:nth-child(2) {
                                    animation-delay: 0.2s;
                                }
                                &:nth-child(3) {
                                    animation-delay: 0.4s;
                                }
                            }
                        }

                        .error-message {
                            background: linear-gradient(135deg, #fdf5f5, #f9e8e8);
                            border: 1px solid rgba(201, 123, 123, 0.4);
                            border-radius: var(--mh-radius-sm);
                            padding: 10px 14px;
                            color: #a05c5c;
                            font-weight: 500;
                            display: flex;
                            align-items: center;
                            gap: 8px;
                        }
                    }

                    .message-time {
                        font-size: 12px;
                        color: var(--mh-text-muted);
                        margin-top: 5px;
                    }
                }

                /* 用户气泡用浅蓝底与 AI 的纯白区分，一眼可辨但不过分跳脱 */
                &.user-message .message-bubble {
                    background: var(--mh-primary-bg);
                    border-color: var(--mh-primary-lighter);
                }
            }
        }

        .chat-input {
            border-top: 1px solid var(--mh-border-light);
            padding: 18px 24px 20px;
            display: flex;
            gap: 12px;
            align-items: flex-end;
            background: var(--mh-surface);
            flex-shrink: 0;

            .input-container {
                flex: 1;
                min-width: 0;
            }

            .input-footer {
                display: flex;
                justify-content: space-between;
                align-items: center;
                font-size: 12px;
                color: var(--mh-text-muted);
                font-weight: 500;
                margin-top: 6px;
            }

            .action-buttons {
                display: flex;
                gap: 10px;
                align-items: center;
            }

            .send-btn {
                height: 58px;
                width: 58px;
                border-radius: var(--mh-radius);
                background: var(--mh-gradient) !important;
                border: none !important;
                box-shadow: 0 6px 18px rgba(92, 141, 184, 0.28);
                transition: all 0.25s ease;

                &:hover:not(.is-disabled) {
                    transform: translateY(-2px);
                    box-shadow: 0 8px 22px rgba(92, 141, 184, 0.36);
                }
            }

            /* 停止按钮：与发送按钮同尺寸，用暖色语义提示「中断」 */
            .stop-btn {
                height: 58px;
                width: 58px;
                border-radius: var(--mh-radius);
                background: linear-gradient(135deg, #d98b8b 0%, #c97b7b 100%) !important;
                border: none !important;
                color: #fff !important;
                box-shadow: 0 6px 18px rgba(201, 123, 123, 0.26);
                transition: all 0.25s ease;

                &:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 8px 22px rgba(201, 123, 123, 0.34);
                }
            }
        }
    }
}

/* ===== 心理测评弹窗 ===== */
.assessment-notice {
    margin-bottom: 18px;
    :deep(.el-alert__title) {
        font-size: 13px;
        line-height: 1.6;
    }
}

.question-list {
    max-height: 56vh;
    overflow-y: auto;
    padding-right: 6px;

    .question-item {
        padding: 14px 0;
        border-bottom: 1px solid var(--mh-border-light);

        &:last-child {
            border-bottom: none;
        }

        .question-title {
            display: flex;
            gap: 10px;
            font-size: 14px;
            line-height: 1.6;
            color: var(--mh-text);
            margin-bottom: 10px;

            .question-no {
                flex-shrink: 0;
                width: 22px;
                height: 22px;
                border-radius: 50%;
                background: var(--mh-primary-bg);
                color: var(--mh-primary);
                font-size: 12px;
                font-weight: 600;
                display: flex;
                align-items: center;
                justify-content: center;
            }
        }

        .option-group {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            padding-left: 32px;

            :deep(.el-radio) {
                margin-right: 0;
            }
        }
    }
}

.assessment-result {
    text-align: center;
    padding: 10px 0 4px;

    .result-score {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin-bottom: 14px;

        .score-num {
            font-size: 44px;
            font-weight: 700;
            color: var(--mh-primary);
            line-height: 1.1;
        }

        .score-label {
            font-size: 13px;
            color: var(--mh-text-muted);
        }
    }

    .result-hint {
        margin: 18px auto 12px;
        max-width: 460px;
        font-size: 13px;
        line-height: 1.8;
        color: var(--mh-text-secondary);
        text-align: left;
    }
}

/* ===== 动画 ===== */
@keyframes breathing {
    0%, 100% {
        transform: scale(1);
    }
    50% {
        transform: scale(1.07);
    }
}

@keyframes pulse {
    0% {
        box-shadow: 0 0 0 0 rgba(106, 168, 143, 0.5);
    }
    70% {
        box-shadow: 0 0 0 8px rgba(106, 168, 143, 0);
    }
    100% {
        box-shadow: 0 0 0 0 rgba(106, 168, 143, 0);
    }
}

@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(8px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

/* 尊重「减少动态效果」偏好——焦虑用户对持续动效更敏感 */
@media (prefers-reduced-motion: reduce) {
    .breathing-circle,
    .status-dot,
    .typing-dot {
        animation: none !important;
    }
}
</style>
