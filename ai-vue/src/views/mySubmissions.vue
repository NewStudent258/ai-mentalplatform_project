<template>
  <div class="submission-container">
    <div class="header-section">
      <div class="header-content">
        <h1>我的投稿</h1>
        <p class="subtitle">分享你的心理调适经验，帮助更多同学</p>
      </div>
      <el-button type="primary" size="large" @click="openDialog">
        <el-icon><EditPen /></el-icon>
        写投稿
      </el-button>
    </div>

    <!-- 投稿须知：把审核规则和 AI 引用规则讲清楚，避免用户误解 -->
    <el-alert type="info" :closable="false" show-icon class="notice">
      <template #title>
        投稿须知
      </template>
      <div class="notice-body">
        <p>1. 投稿提交后进入<strong>管理员审核</strong>，通过后才会在知识库中展示。</p>
        <p>2. 通过后内容的展示与「是否可被 AI 引用」是两件事：个人经验分享可以展示，
           但<strong>不一定会被 AI 引用</strong>，是否引用由审核人决定。</p>
        <p>3. 请勿包含具体药物建议、诊断结论；如涉及严重心理困扰，请引导读者寻求专业帮助。</p>
      </div>
    </el-alert>

    <div v-if="submissions.length === 0" class="empty">
      <el-empty description="还没有投稿，点击右上角写下第一篇吧" />
    </div>

    <div v-else class="submission-list">
      <div v-for="item in submissions" :key="item.id" class="submission-item">
        <div class="item-main">
          <div class="item-title">
            <h3>{{ item.title }}</h3>
            <el-tag :type="statusTagType(item.status)" size="small">
              {{ statusText(item.status) }}
            </el-tag>
            <!-- 只有已通过的投稿才有引用授权状态可展示 -->
            <el-tag v-if="item.status === 1" :type="item.citable === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ item.citable === 1 ? '可被 AI 引用' : '仅展示，不被 AI 引用' }}
            </el-tag>
          </div>
          <p class="item-summary">{{ item.summary || '（未填写摘要）' }}</p>
          <div class="item-meta">
            <span>{{ item.categoryName || '未分类' }}</span>
            <span>提交于 {{ dayjs(item.createdAt).format('YYYY-MM-DD HH:mm') }}</span>
          </div>
          <!-- 驳回原因必须让作者看到，否则用户不知道该怎么改 -->
          <el-alert v-if="item.status === 4 && item.rejectReason" type="error" :closable="false" class="reject">
            <template #title>驳回原因：{{ item.rejectReason }}</template>
          </el-alert>
        </div>
        <div class="item-actions">
          <el-button v-if="item.status !== 1" text type="danger" @click="handleDelete(item)">撤回</el-button>
          <span v-else class="published-hint">已发布，如需下架请联系管理员</span>
        </div>
      </div>
    </div>

    <!-- 投稿表单 -->
    <el-dialog v-model="dialogVisible" title="写投稿" width="720px" @closed="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="用一句话概括你想分享的内容" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="2"
                    placeholder="一两句话介绍这篇文章，会显示在列表页" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，例如：焦虑,放松,呼吸" maxlength="200" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10"
                    placeholder="支持 HTML 富文本，例如 <p>段落</p>、<h2>小标题</h2>" maxlength="50000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { submitArticle, getMySubmissions, deleteMySubmission } from '@/api/frontend'
import { categoryTree } from '@/api/admin'

const submissions = ref([])
const categories = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  title: '',
  categoryId: null,
  summary: '',
  tags: '',
  content: ''
})

const rules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请填写正文', trigger: 'blur' }]
}

const STATUS_TEXT = {
  0: '草稿',
  1: '已发布',
  2: '已下线',
  3: '待审核',
  4: '已驳回'
}

const statusText = (status) => STATUS_TEXT[status] ?? '未知'

const statusTagType = (status) => {
  switch (status) {
    case 1: return 'success'
    case 3: return 'warning'
    case 4: return 'danger'
    default: return 'info'
  }
}

const loadSubmissions = () => {
  getMySubmissions().then(res => {
    submissions.value = res || []
  })
}

const openDialog = () => {
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  Object.assign(form, { title: '', categoryId: null, summary: '', tags: '', content: '' })
}

const handleSubmit = () => {
  formRef.value.validate(valid => {
    if (!valid) return
    submitting.value = true
    submitArticle({ ...form })
      .then(() => {
        ElMessage.success('投稿已提交，等待管理员审核')
        dialogVisible.value = false
        loadSubmissions()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

const handleDelete = (item) => {
  ElMessageBox.confirm(`确认撤回投稿「${item.title}」吗？`, '确认', {
    confirmButtonText: '确认撤回',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    deleteMySubmission(item.id).then(() => {
      ElMessage.success('已撤回')
      loadSubmissions()
    })
  })
}

onMounted(async () => {
  loadSubmissions()
  try {
    const tree = await categoryTree()
    categories.value = (tree || []).map(item => ({
      label: item.categoryName,
      value: item.id
    }))
  } catch (e) {
    // 分类加载失败不阻塞页面，用户仍可看到自己的投稿列表
  }
})
</script>

<style scoped lang="scss">
.submission-container {
  margin: 0 auto;
  width: 1200px;
  padding: 20px;

  .header-section {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20px;
    .header-content {
      h1 {
        font-size: 24px;
        margin: 0 0 4px;
        background: var(--mh-gradient);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
      }
      .subtitle {
        margin: 0;
        font-size: 13px;
        color: #999;
      }
    }
  }

  .notice {
    margin-bottom: 20px;
    .notice-body p {
      margin: 4px 0;
      font-size: 13px;
      line-height: 1.6;
    }
  }

  .submission-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .submission-item {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    background: #fff;
    border-radius: 14px;
    padding: 18px 20px;
    border: 1px solid rgba(251, 146, 60, 0.12);
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);

    .item-main {
      flex: 1;
      min-width: 0;
    }

    .item-title {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      h3 {
        margin: 0;
        font-size: 16px;
        color: #333;
      }
    }

    .item-summary {
      margin: 8px 0;
      font-size: 13px;
      color: #666;
      line-height: 1.5;
    }

    .item-meta {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: #999;
    }

    .reject {
      margin-top: 10px;
    }

    .item-actions {
      flex-shrink: 0;
      .published-hint {
        font-size: 12px;
        color: #bbb;
      }
    }
  }
}
</style>
