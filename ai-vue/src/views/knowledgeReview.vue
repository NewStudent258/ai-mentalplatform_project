<template>
  <div>
    <PageHead title="投稿审核" />

    <el-alert type="warning" :closable="false" show-icon class="review-notice">
      <template #title>审核要点</template>
      <div class="notice-body">
        <p>· <strong>是否发布</strong>：决定内容能否被读者看到。</p>
        <p>· <strong>是否可被 AI 引用</strong>：决定内容能否作为 AI 回答用户时的专业依据，二者相互独立。</p>
        <p>· 个人经验分享建议「仅展示」——可以给读者启发，但不适合被 AI 作为专业建议转述给他人。</p>
        <p>· 含有具体药物用法、诊断结论、或可能误导的内容，请直接驳回并说明原因。</p>
      </div>
    </el-alert>

    <div v-if="pendingList.length === 0" class="empty">
      <el-empty description="暂无待审核投稿" />
    </div>

    <div v-else class="review-list">
      <el-card v-for="item in pendingList" :key="item.id" class="review-card" shadow="hover">
        <div class="card-header">
          <div class="title-row">
            <h3>{{ item.title }}</h3>
            <el-tag type="primary" effect="plain" size="small">{{ item.categoryName || '未分类' }}</el-tag>
            <el-tag type="info" effect="plain" size="small">用户投稿</el-tag>
          </div>
          <div class="meta-row">
            <span>投稿人：{{ item.authorName }}</span>
            <span>提交时间：{{ dayjs(item.createdAt).format('YYYY-MM-DD HH:mm') }}</span>
            <span v-if="item.tags">标签：{{ item.tags }}</span>
          </div>
        </div>

        <div class="card-body">
          <p class="summary"><strong>摘要：</strong>{{ item.summary || '（未填写）' }}</p>
          <el-collapse>
            <el-collapse-item title="查看正文全文">
              <div class="content-preview" v-html="item.content"></div>
            </el-collapse-item>
          </el-collapse>
        </div>

        <div class="card-footer">
          <div class="citable-toggle">
            <span class="toggle-label">允许被 AI 引用</span>
            <el-switch v-model="citableMap[item.id]" />
            <span class="toggle-hint">
              {{ citableMap[item.id] ? '审核通过后该文章会进入 AI 知识库' : '仅展示给读者，AI 不会引用' }}
            </span>
          </div>
          <div class="actions">
            <el-button type="danger" plain @click="handleReject(item)">驳回</el-button>
            <el-button type="success" @click="handleApprove(item)">通过</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 驳回原因 -->
    <el-dialog v-model="rejectVisible" title="驳回投稿" width="520px">
      <p class="reject-tip">请填写驳回原因，作者会看到这段说明：</p>
      <el-input v-model="rejectReason" type="textarea" :rows="4"
                placeholder="例如：包含具体药物剂量建议，不适合作为心理科普内容；建议改为分享个人就医经历。"
                maxlength="500" show-word-limit />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import PageHead from '@/components/PageHead.vue'
import { pendingReviewList, reviewArticle } from '@/api/admin'

const pendingList = ref([])
// 每篇投稿的「是否可被AI引用」开关，默认关闭：
// 授权 AI 引用必须是审核人显式做出的决定，不能靠默认值放开
const citableMap = reactive({})

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectingId = ref(null)
const submitting = ref(false)

const loadPending = () => {
  pendingReviewList().then(res => {
    pendingList.value = res || []
    // 新加载的投稿默认不可被 AI 引用
    pendingList.value.forEach(item => {
      if (citableMap[item.id] === undefined) {
        citableMap[item.id] = false
      }
    })
  })
}

const handleApprove = (item) => {
  submitting.value = true
  reviewArticle(item.id, { approved: true, citable: citableMap[item.id] })
    .then(() => {
      ElMessage.success(citableMap[item.id] ? '已通过，并加入 AI 知识库' : '已通过，仅展示不被 AI 引用')
      loadPending()
    })
    .finally(() => {
      submitting.value = false
    })
}

const handleReject = (item) => {
  rejectingId.value = item.id
  rejectReason.value = ''
  rejectVisible.value = true
}

const confirmReject = () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  reviewArticle(rejectingId.value, { approved: false, rejectReason: rejectReason.value.trim() })
    .then(() => {
      ElMessage.success('已驳回')
      rejectVisible.value = false
      loadPending()
    })
    .finally(() => {
      submitting.value = false
    })
}

onMounted(loadPending)
</script>

<style scoped lang="scss">
.review-notice {
  margin-bottom: 20px;
  .notice-body p {
    margin: 4px 0;
    font-size: 13px;
    line-height: 1.6;
  }
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-card {
  .card-header {
    border-bottom: 1px solid #f0f0f0;
    padding-bottom: 12px;
    .title-row {
      display: flex;
      align-items: center;
      gap: 8px;
      h3 {
        margin: 0;
        font-size: 16px;
      }
    }
    .meta-row {
      margin-top: 8px;
      display: flex;
      gap: 18px;
      font-size: 12px;
      color: #888;
    }
  }

  .card-body {
    padding: 12px 0;
    .summary {
      font-size: 13px;
      color: #555;
      margin: 0 0 8px;
    }
    .content-preview {
      max-height: 320px;
      overflow-y: auto;
      font-size: 13px;
      line-height: 1.7;
      color: #444;
      padding: 12px;
      background: #fafafa;
      border-radius: 8px;
    }
  }

  .card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-top: 1px solid #f0f0f0;
    padding-top: 14px;

    .citable-toggle {
      display: flex;
      align-items: center;
      gap: 10px;
      .toggle-label {
        font-size: 13px;
        font-weight: 600;
        color: #333;
      }
      .toggle-hint {
        font-size: 12px;
        color: #999;
      }
    }
  }
}

.reject-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #666;
}
</style>
