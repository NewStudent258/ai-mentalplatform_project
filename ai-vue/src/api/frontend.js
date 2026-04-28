import service from '@/utils/request'

export const register = (data) => {
    return service.post('/user/add', data)
}

export const startSession = (data) => {
    return service.post('/psychological-chat/session/start', data)
}

export const getSessionList = (params) => {
    return service.get('/psychological-chat/sessions', { params })
}

export const deleteSession = (sessionId) => {
    return service.delete(`/psychological-chat/sessions/${sessionId}`)
}

export const getSessionDetail = (sessionId) => {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export const getSessionEmotion = (sessionId) => {
    return service.get(`/psychological-chat/session/${sessionId}/emotion`)
}

export const addEmotionDiary = (data) => {
    return service.post('/emotion-diary', data)
}

export const getMyDiaryList = () => {
    return service.get('/emotion-diary/my')
}

export const getKnowledgeList = (params) => {
    return service.get('/knowledge/article/page', { params })
}

export const getKnowledgeDetail = (articleId) => {
    return service.get(`/knowledge/article/${articleId}`)
}

// ===== 用户投稿 =====
export const submitArticle = (data) => {
    return service.post('/knowledge/submission', data)
}

export const getMySubmissions = () => {
    return service.get('/knowledge/submission/mine')
}

export const deleteMySubmission = (id) => {
    return service.delete(`/knowledge/submission/${id}`)
}

// ===== 心理测评 =====
/** 查询待作答的测评（AI 推送后会在这里出现） */
export const getPendingAssessment = () => {
    return service.get('/assessment/pending')
}

/** 提交作答（计分与风险判定均在服务端完成） */
export const submitAssessment = (recordId, answers) => {
    return service.post(`/assessment/${recordId}/submit`, { answers })
}

/** 本人的测评历史 */
export const getMyAssessments = () => {
    return service.get('/assessment/my')
}