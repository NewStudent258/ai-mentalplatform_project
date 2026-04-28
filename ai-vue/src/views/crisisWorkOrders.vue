<template>
  <div>
    <PageHead title="危机工单" />

    <!-- 说明闭环流程，让接手的人一眼知道该做什么 -->
    <el-alert type="warning" :closable="false" show-icon class="flow-notice">
      <template #title>处理流程</template>
      <div class="notice-body">
        <p>AI 在对话中识别到风险等级 ≥ 2 时会自动建单，无需人工发起。</p>
        <p>· <strong>待认领</strong> → 点击「认领」接手，工单转入你的名下</p>
        <p>· <strong>处理中</strong> → 线下联系学生、评估情况后填写处置结果闭环</p>
        <p>· 处置结果必填——只标记「已处理」不写内容，事后无法追溯，等于没有闭环</p>
      </div>
    </el-alert>

    <!-- 状态筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="statusFilter" @change="loadList">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="0">待认领</el-radio-button>
        <el-radio-button :value="1">处理中</el-radio-button>
        <el-radio-button :value="2">已闭环</el-radio-button>
      </el-radio-group>
      <span class="count-hint">共 {{ orders.length }} 条</span>
    </div>

    <div v-if="orders.length === 0" class="empty">
      <el-empty description="暂无工单" />
    </div>

    <div v-else class="order-list">
      <el-card v-for="item in orders" :key="item.id" class="order-card" shadow="hover"
               :class="'risk-' + item.riskLevel">
        <div class="card-header">
          <div class="title-row">
            <!-- AI 识别高危的工单按风险等级标色；Agent 主动转介的没有风险等级，用来源标签区分 -->
            <el-tag v-if="item.riskLevel" :type="item.riskLevel >= 3 ? 'danger' : 'warning'" effect="dark" size="small">
              {{ item.riskLevel >= 3 ? '危机' : '预警' }}
            </el-tag>
            <el-tag v-else type="primary" effect="dark" size="small">{{ item.sourceDesc }}</el-tag>

            <el-tag :type="statusTagType(item.status)" size="small">{{ item.statusDesc }}</el-tag>
            <!-- 紧急程度：辅导员据此排优先级 -->
            <el-tag v-if="item.urgency === 1" type="danger" size="small" effect="plain">高优先</el-tag>
            <span class="order-id">工单 #{{ item.id }}</span>
          </div>
          <div class="meta-row">
            <span>学生 ID：{{ item.userId }}</span>
            <span v-if="item.sessionId">会话 ID：{{ item.sessionId }}</span>
            <span>创建时间：{{ dayjs(item.eventCreatedAt || item.createdAt).format('YYYY-MM-DD HH:mm') }}</span>
          </div>
        </div>

        <div class="card-body">
          <!-- AI 识别场景：展示触发时的情绪与原文 -->
          <template v-if="item.riskLevel">
            <div class="field">
              <span class="label">触发时情绪</span>
              <span class="value">{{ item.primaryEmotion || '—' }}（{{ item.emotionScore }} 分）</span>
            </div>
            <!-- 触发原文是辅导员判断介入方式的关键依据，完整展示 -->
            <div class="field">
              <span class="label">触发原文</span>
              <div class="trigger-text">{{ item.triggerMessage || '—' }}</div>
            </div>
          </template>
          <!-- 主动转介场景：没有触发原文，展示 Agent 判断的转介原因 -->
          <template v-else>
            <div class="field">
              <span class="label">转介原因</span>
              <div class="trigger-text escalate">{{ item.escalateReason || '—' }}</div>
            </div>
          </template>

          <div v-if="item.handlerName" class="field">
            <span class="label">处理人</span>
            <span class="value">{{ item.handlerName }}</span>
          </div>
          <div v-if="item.handleResult" class="field">
            <span class="label">处置结果</span>
            <div class="result-text">{{ item.handleResult }}</div>
          </div>
          <div v-if="item.closedAt" class="field">
            <span class="label">闭环时间</span>
            <span class="value">{{ dayjs(item.closedAt).format('YYYY-MM-DD HH:mm') }}</span>
          </div>
        </div>

        <div class="card-footer">
          <el-button v-if="item.status === 0" type="primary" @click="handleClaim(item)">
            认领工单
          </el-button>
          <el-button v-else-if="item.status === 1" type="success" @click="openCloseDialog(item)">
            填写处置结果并闭环
          </el-button>
          <span v-else class="closed-hint">该工单已处置完成</span>
        </div>
      </el-card>
    </div>

    <!-- 闭环处置 -->
    <el-dialog v-model="closeVisible" title="处置闭环" width="560px">
      <el-alert type="info" :closable="false" class="dialog-notice">
        请如实填写处置过程与结果。该记录会作为危机干预的正式留痕，供后续回溯。
      </el-alert>
      <el-input v-model="handleResult" type="textarea" :rows="5"
                placeholder="例如：已电话联系学生本人，情绪已平复，约定明日下午面谈；已同步辅导员共同关注。"
                maxlength="1000" show-word-limit />
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmClose">确认闭环</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import PageHead from '@/components/PageHead.vue'
import { getCrisisWorkOrders, claimWorkOrder, closeWorkOrder } from '@/api/admin'

const orders = ref([])
// null 表示不筛选状态
const statusFilter = ref(null)

const closeVisible = ref(false)
const handleResult = ref('')
const closingId = ref(null)
const submitting = ref(false)

const statusTagType = (status) => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'primary'
    case 2: return 'success'
    default: return 'info'
  }
}

const loadList = () => {
  getCrisisWorkOrders(statusFilter.value).then(res => {
    orders.value = res || []
  })
}

const handleClaim = (item) => {
  claimWorkOrder(item.id).then(() => {
    ElMessage.success('已认领，请尽快联系学生')
    loadList()
  })
}

const openCloseDialog = (item) => {
  closingId.value = item.id
  handleResult.value = ''
  closeVisible.value = true
}

const confirmClose = () => {
  if (!handleResult.value.trim()) {
    ElMessage.warning('请填写处置结果')
    return
  }
  submitting.value = true
  closeWorkOrder(closingId.value, { handleResult: handleResult.value.trim() })
    .then(() => {
      ElMessage.success('工单已闭环')
      closeVisible.value = false
      loadList()
    })
    .finally(() => {
      submitting.value = false
    })
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.flow-notice {
  margin-bottom: 18px;
  .notice-body p {
    margin: 4px 0;
    font-size: 13px;
    line-height: 1.7;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  .count-hint {
    font-size: 13px;
    color: var(--mh-text-muted);
  }
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order-card {
  border-left: 4px solid var(--mh-border);

  /* 风险等级用左侧色条区分：危机红、预警橙，扫一眼就能排优先级 */
  &.risk-3 {
    border-left-color: var(--mh-danger);
  }
  &.risk-2 {
    border-left-color: var(--mh-warning);
  }

  .card-header {
    border-bottom: 1px solid var(--mh-border-light);
    padding-bottom: 12px;

    .title-row {
      display: flex;
      align-items: center;
      gap: 10px;

      .order-id {
        font-size: 13px;
        color: var(--mh-text-muted);
      }
    }

    .meta-row {
      margin-top: 8px;
      display: flex;
      gap: 20px;
      font-size: 12px;
      color: var(--mh-text-secondary);
      flex-wrap: wrap;
    }
  }

  .card-body {
    padding: 14px 0;

    .field {
      margin-bottom: 12px;

      .label {
        display: inline-block;
        width: 76px;
        font-size: 13px;
        color: var(--mh-text-secondary);
        vertical-align: top;
      }

      .value {
        font-size: 13px;
        color: var(--mh-text);
      }

      .trigger-text {
        display: inline-block;
        max-width: calc(100% - 90px);
        padding: 10px 14px;
        border-radius: var(--mh-radius-sm);
        background: #fdf5f5;
        border-left: 3px solid var(--mh-danger);
        font-size: 13px;
        line-height: 1.7;
        color: var(--mh-text);
        word-break: break-word;

        /* 主动转介不是「危机触发」，用中性蓝而非警示红，避免误导紧迫程度 */
        &.escalate {
          background: var(--mh-primary-bg);
          border-left-color: var(--mh-primary-light);
        }
      }

      .result-text {
        display: inline-block;
        max-width: calc(100% - 90px);
        padding: 10px 14px;
        border-radius: var(--mh-radius-sm);
        background: var(--mh-primary-bg);
        font-size: 13px;
        line-height: 1.7;
        color: var(--mh-text);
        word-break: break-word;
      }
    }
  }

  .card-footer {
    border-top: 1px solid var(--mh-border-light);
    padding-top: 14px;

    .closed-hint {
      font-size: 13px;
      color: var(--mh-text-muted);
    }
  }
}

.dialog-notice {
  margin-bottom: 14px;
}
</style>
