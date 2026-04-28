import service from '@/utils/request'

export function login(data) {
    return service.post('/user/login', data)
}


export function categoryTree() {
    return service.get('/knowledge/category/tree')
}

export function articlePage(params) {
     return service.get('/knowledge/article/page', { params })
}

export function uploadFile(file, businessInfo) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('businessType', 'ARTICLE')
    formData.append('businessId', businessInfo.businessId)
    formData.append('businessField', 'cover')

    return service.post('/file/upload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}


export function createArticle(data) {
    return service.post('/knowledge/article', data)
}

export function getArticleDetail(id) {
    return service.get(`/knowledge/article/${id}`)
}


export function updateArticle(id, data) {
    return service.put(`/knowledge/article/${id}`, data)
}

export function changeArticleStatus(id, data) {
    return service.put(`/knowledge/article/${id}/status`, data)
}

export function deleteArticle(id) {
    return service.delete(`/knowledge/article/${id}`)
}

// ===== 投稿审核（管理员）=====
// 审核接口挂在独立前缀下：/api/knowledge/article/* 有一条公开的 GET 规则，
// 把审核接口放在那之下容易被意外放行，因此后端另起了 /knowledge/review
export function pendingReviewList() {
    return service.get('/knowledge/review/pending')
}

export function reviewArticle(id, data) {
    return service.post(`/knowledge/review/${id}`, data)
}

export function getConsultationPage(params) {
    return service.get('/psychological-chat/sessions', { params })
}

export function getSessionDetail(sessionId) {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export function getEmotionalPage(params) {
    return service.get('/emotion-diary/admin/page', { params })
}

// ===== 辅导员视角（roleType=3）=====
// 这些接口的数据范围是「全部学生」，与上面的学生接口刻意分开，
// 避免在同一接口里混用两种数据范围而在后续改动中误放开权限。

/** 全部学生的会话列表 */
export function getCounselorSessions(params) {
    return service.get('/psychological-chat/counselor/sessions', { params })
}

/** 查看指定会话的全部消息 */
export function getCounselorSessionMessages(sessionId) {
    return service.get(`/psychological-chat/counselor/sessions/${sessionId}/messages`)
}

/** 全部学生的情绪日记（默认按心情分升序，状态最差的排前面） */
export function getCounselorDiaries(params) {
    return service.get('/emotion-diary/counselor/page', { params })
}

/** 指定会话的 Agent 执行轨迹（思考与工具调用过程） */
export function getSessionTraces(sessionId) {
    return service.get(`/psychological-chat/counselor/sessions/${sessionId}/traces`)
}

// ===== 站内通知 =====
export function getMyNotifications(limit) {
    return service.get('/notification/my', { params: { limit } })
}

export function getUnreadCount() {
    return service.get('/notification/unread-count')
}

export function markNotificationRead(id) {
    return service.post(`/notification/${id}/read`)
}

export function markAllNotificationsRead() {
    return service.post('/notification/read-all')
}

export function getAnalyticsOverview() {
    return service.get(`/data-analytics/overview`)
}

export function logout() {
    return service.post('/user/logout')
}

// ===== 危机工单（仅辅导员角色可访问）=====
export function getCrisisWorkOrders(status) {
    // status 为 null/undefined 时不传参，返回全部工单
    const params = (status === null || status === undefined) ? {} : { status }
    return service.get('/crisis/work-orders', { params })
}

export function claimWorkOrder(id) {
    return service.post(`/crisis/work-orders/${id}/claim`)
}

export function closeWorkOrder(id, data) {
    return service.post(`/crisis/work-orders/${id}/close`, data)
}