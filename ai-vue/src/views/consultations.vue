<template>
    <div>
        <PageHead title="咨询记录" />
        <el-table :data="tableData" style="width: 100%">
            <!-- 辅导员最需要先知道「这是谁的会话」，因此学生列放在最前 -->
            <el-table-column label="学生" width="140">
                <template #default="scope">
                    <div class="student-cell">
                        <span class="student-name">{{ scope.row.userName || '未知学生' }}</span>
                        <span class="student-id">ID: {{ scope.row.userId }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="会话内容">
                <template #default="scope">
                    <div class="session-title">{{ scope.row.sessionTitle }}</div>
                    <div class="session-preview">{{ scope.row.lastMessageContent }}</div>
                </template>
            </el-table-column>
            <el-table-column label="消息数" width="90">
                <template #default="scope">{{ scope.row.messageCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="开始时间" width="170">
                <template #default="scope">
                    {{ scope.row.startedAt ? dayjs(scope.row.startedAt).format('YYYY-MM-DD HH:mm') : '—' }}
                </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
                <template #default="scope">
                    <el-button type="primary" text @click="viewSessionDetail(scope.row)">查看对话</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination
         style="margin-top: 25px"
         :page-size="pagination.size"
         layout="prev, pager, next"
         :total="pagination.total"
         @change="handleChange"
         />
         <el-dialog
            v-model="showDetailDialog"
            title="咨询会话详情"
            width="70%"
            :close-on-click-modal="false"
        >
            <div class="session-detail">
                <div class="detail-header">
                    <div class="detail-row">
                        <div class="detail-label">学生：</div>
                        <div class="detail-value">{{ sessionDetail.userName || '未知' }}（ID: {{ sessionDetail.userId }}）</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">开始时间：</div>
                        <div class="detail-value">{{ sessionDetail.startedAt ? dayjs(sessionDetail.startedAt).format('YYYY-MM-DD HH:mm') : '—' }}</div>
                    </div>
                    <div class="detail-row">
                        <div class="detail-label">消息数：</div>
                        <div class="detail-value">{{ sessionDetail.messageCount }}</div>
                    </div>
                </div>

                <!-- 两个标签页：对话内容 + Agent 执行轨迹 -->
                <el-tabs v-model="activeTab" class="detail-tabs">
                    <el-tab-pane label="对话记录" name="messages">
                        <div class="messages-list" v-loading="loadingMessages">
                            <div v-for="message in sessionMessages" :key="message.id" class="message-item" :class="message.senderType === 1 ? 'user-message' : 'ai-message'">
                                <div class="message-header">
                                   <span class="sender">{{ message.senderType === 1 ? '学生' : 'AI助手' }}</span>
                                   <span class="time">{{ message.createdAt ? dayjs(message.createdAt).format('MM-DD HH:mm') : '' }}</span>
                                </div>
                                <div class="message-content">{{ message.content }}</div>
                            </div>
                        </div>
                    </el-tab-pane>

                    <el-tab-pane name="traces">
                        <template #label>
                            <span>Agent 执行轨迹</span>
                        </template>
                        <el-alert type="info" :closable="false" show-icon class="trace-notice">
                          <template #title>
                            AI 生成回复时实际执行了哪些步骤。若某次回复不理想，可从这里判断是没检索、检索不准，还是判断有误。
                          </template>
                        </el-alert>

                        <div v-if="traces.length === 0" class="empty-trace">
                          <el-empty description="该会话暂无执行轨迹" :image-size="80" />
                        </div>
                        <div v-else class="trace-list">
                            <div v-for="(step, idx) in traces" :key="step.id" class="trace-step"
                                 :class="'step-' + step.stepType.toLowerCase()">
                                <div class="step-marker">
                                    <span class="step-index">{{ idx + 1 }}</span>
                                </div>
                                <div class="step-body">
                                    <div class="step-title">
                                        <el-tag :type="stepTagType(step.stepType)" size="small" effect="plain">
                                            {{ stepTypeText(step.stepType) }}
                                        </el-tag>
                                        <span v-if="step.toolName" class="step-tool">{{ step.toolName }}</span>
                                        <span v-if="step.durationMs" class="step-duration">{{ step.durationMs }} ms</span>
                                    </div>
                                    <div v-if="step.toolInput" class="step-field">
                                        <span class="field-label">入参</span>
                                        <span class="field-value">{{ step.toolInput }}</span>
                                    </div>
                                    <div v-if="step.resultSummary" class="step-field">
                                        <span class="field-label">
                                            {{ step.stepType === 'TOOL_CALL' ? '结果' : '内容' }}
                                        </span>
                                        <span class="field-value">{{ step.resultSummary }}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </el-tab-pane>
                </el-tabs>
            </div>
            <template #footer>
                <el-button @click="showDetailDialog = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup>
import { onMounted, ref, reactive } from 'vue'
import dayjs from 'dayjs'
import PageHead from '@/components/PageHead.vue'
// 使用辅导员专用接口：数据范围为「全部学生」，且后端会校验辅导员身份
import { getCounselorSessions, getCounselorSessionMessages, getSessionTraces } from '@/api/admin'

const tableData = ref([])

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

// 会话详情
const sessionDetail = ref({})
const sessionMessages = ref([])
const loadingMessages = ref(false)

// 详情弹窗的标签页：默认看对话，需要时切到执行轨迹
const activeTab = ref('messages')

// Agent 执行轨迹
const traces = ref([])

const stepTypeText = (type) => ({
    TURN_START: '学生提问',
    TOOL_CALL: '调用工具',
    GENERATION: '生成回复',
    SAFETY_BLOCK: '安全拦截'
}[type] || type)

const stepTagType = (type) => ({
    TURN_START: 'info',
    TOOL_CALL: 'primary',
    GENERATION: 'success',
    SAFETY_BLOCK: 'danger'
}[type] || 'info')

const viewSessionDetail = (row) => {
    loadingMessages.value = true
    showDetailDialog.value = true
    activeTab.value = 'messages'
    traces.value = []

    getCounselorSessionMessages(row.id).then(res => {
        sessionMessages.value = res
        sessionDetail.value = row
    }).finally(() => {
        // 放在 finally：接口失败时也要关掉 loading，否则弹窗会一直转圈
        loadingMessages.value = false
    })

    // 轨迹与消息并行加载：两者互不依赖，串行会白白多等一个往返
    getSessionTraces(row.id)
        .then(res => {
            traces.value = res || []
        })
        .catch(() => {
            // 轨迹属于附加信息，取不到不影响查看对话，因此只置空不报错
            traces.value = []
        })
}

const handleChange = (page) => {
    pagination.currentPage = page
    handleSearch()
}

const handleSearch = () => {
    getCounselorSessions({
        pageNum: pagination.currentPage,
        pageSize: pagination.size
    }).then(res => {
        tableData.value = res.records
        pagination.total = res.total
    })
}

// 详情
const showDetailDialog = ref(false)

onMounted(() => {
    handleSearch()
})
</script>

<style lang="scss" scoped>
.student-cell {
    display: flex;
    flex-direction: column;
    line-height: 1.4;

    .student-name {
        font-size: 14px;
        font-weight: 600;
        color: var(--mh-text);
    }

    .student-id {
        font-size: 11px;
        color: var(--mh-text-muted);
    }
  }
/* ===== Agent 执行轨迹时间线 ===== */
.trace-notice {
  margin-bottom: 16px;
  :deep(.el-alert__title) {
    font-size: 13px;
    line-height: 1.6;
  }
}

.empty-trace {
  padding: 20px 0;
}

.trace-list {
  display: flex;
  flex-direction: column;
}

.trace-step {
  display: flex;
  gap: 14px;
  position: relative;
  padding-bottom: 18px;

  /* 用一条竖线把步骤串起来，直观体现「多步执行」的顺序关系 */
  &::before {
    content: '';
    position: absolute;
    left: 13px;
    top: 28px;
    bottom: 0;
    width: 1px;
    background: var(--mh-border);
  }

  &:last-child::before {
    display: none;
  }

  .step-marker {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    color: #fff;
    background: var(--mh-text-muted);
    z-index: 1;
  }

  /* 不同步骤类型用不同颜色标识，扫一眼就能看出执行路径 */
  &.step-turn_start .step-marker { background: #8fa8bd; }
  &.step-tool_call .step-marker { background: var(--mh-primary); }
  &.step-generation .step-marker { background: var(--mh-success); }
  &.step-safety_block .step-marker { background: var(--mh-danger); }

  .step-body {
    flex: 1;
    min-width: 0;
    padding-top: 2px;

    .step-title {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 8px;

      .step-tool {
        font-size: 13px;
        font-weight: 600;
        color: var(--mh-primary);
        font-family: 'Monaco', 'Menlo', monospace;
      }

      .step-duration {
        font-size: 12px;
        color: var(--mh-text-muted);
      }
    }

    .step-field {
      display: flex;
      gap: 10px;
      margin-bottom: 6px;
      font-size: 13px;
      line-height: 1.6;

      .field-label {
        flex-shrink: 0;
        width: 36px;
        color: var(--mh-text-muted);
      }

      .field-value {
        flex: 1;
        min-width: 0;
        color: var(--mh-text);
        word-break: break-word;
        background: var(--mh-surface-alt);
        border-radius: 6px;
        padding: 6px 10px;
      }
    }
  }
}

.detail-tabs {
  :deep(.el-tabs__content) {
    max-height: 62vh;
    overflow-y: auto;
    padding-right: 4px;
  }
}

.session-title {
    font-weight: 500;
    color: #333;
    margin-bottom: 4px;
  }
  .session-preview {
    font-size: 13px;
    color: #666;
    margin-bottom: 4px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .session-detail {
    max-height: 70vh;
    overflow-y: auto;
    .detail-header {
      margin-bottom: 20px;
      padding: 16px;
      background: #f8f9fa;
      border-radius: 8px;
      border: 1px solid #e9ecef;
    }

    .detail-row {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
      :last-child {
        margin-bottom: 0;
      }
      .detail-label {
        font-weight: 500;
        color: #495057;
        min-width: 80px;
        margin-right: 8px;
      }

      .detail-value {
        color: #333;
      }
    }
  }
  .messages-container {
    margin-top: 20px;
    .messages-header {
      margin-bottom: 16px;
      h4 {
        margin: 0;
        color: #333;
        font-size: 16px;
        font-weight: 500;
      }
    }
    .messages-list {
      max-height: 400px;
      overflow-y: auto;
      border: 1px solid #e9ecef;
      border-radius: 8px;
      padding: 16px;
      background: #fff;
      .message-item {
        margin-bottom: 12px;
        padding: 12px;
        border-radius: 8px;
        background: #f8f9fa;
        border: 1px solid #e9ecef;
        :last-child {
          margin-bottom: 0;
        }
        &.user-message {
          background: #e8f4fd;
        }

        &.ai-message {
          background: #f0f9f0;
        }
      }
      .message-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
        .sender {
          font-weight: 500;
          color: #333;
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .time {
          font-size: 12px;
          color: #999;
        }

        .message-content {
          color: #333;
          line-height: 1.6;
          white-space: pre-wrap;
          margin-top: 8px;
          font-size: 14px;
        }
      }
    }
  }
</style>