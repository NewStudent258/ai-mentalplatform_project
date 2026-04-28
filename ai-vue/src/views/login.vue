<template>
    <div class="container">
        <div class="title">
            <div class="back-home">
                <el-icon><Back /></el-icon>
                <span>返回首页</span>
            </div>
            <div class="title-text">
                <h2>登录您的账户</h2>
                <p>请输入您的登录信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form
                ref="ruleFormRef"
                :model="formData"
                :rules="rules"
                label-position="top"
            >
                <el-form-item label="用户名或邮箱" prop="username">
                    <el-input v-model="formData.username" size="large" placeholder="请输入用户名" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password />
                </el-form-item>
                <el-button class="btn" size="large" type="primary" @click="submitForm(ruleFormRef)">登录</el-button>
            </el-form>
            <div class="footer">
                <p>还没有账户？<router-link to="/auth/register">去注册</router-link></p>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { login } from '@/api/admin'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const ruleFormRef = ref()

const formData = reactive({
    username: '',
    password: ''
})
const rules = reactive({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
    ]
})

// 登录
const router = useRouter()
const submitForm = async (formEl) => {
    if (!formEl) return
    // 先校验表单，校验不通过则不提交
    const valid = await formEl.validate().catch(() => false)
    if (!valid) return
    try {
        const res = await login(formData)
        // 成功时响应拦截器返回 { token, userInfo }
        if (res?.token) {
            // 登录成功，保存token和用户信息
            localStorage.setItem('token', res.token)
            localStorage.setItem('userInfo', JSON.stringify(res.userInfo))
            // 根据用户角色决定跳转的路径。
            // 管理员与辅导员进入各自的默认工作台（平台数据 / 危机工单），学生进入前台。
            // 注意辅导员不能落到 /back/dashboard——那是管理员专属页面，后端会拒绝。
            const role = Number(res.userInfo.userType)
            if (role === 3) {
                router.push('/back/crisis')
            } else if (role === 2) {
                router.push('/back/dashboard')
            } else {
                router.push('/')
            }
        } else if (res?.data?.msg) {
            // 后端业务错误（用户不存在/密码错误等）
            ElMessage.error(res.data.msg)
        }
    } catch (e) {
        ElMessage.error(e?.response?.data?.msg || '登录失败，请检查输入')
    }
}

</script>
<style scoped lang="scss">
    .container {
        width: 384px;
        .title {
            .back-home {
                margin-bottom: 60px;
            }
            .title-text {
                text-align: center;
                h2 {
                    font-size: 36px;
                    margin-bottom: 10px;
                }
                p {
                    font-size: 18px;
                    color: #6b7280;
                }
            }
        }
        .form-container {
            margin-top: 30px;
            .btn {
                margin-top: 40px;
                width: 100%;
            }
            .footer {
                padding: 30px;
                text-align: center;
            }
        }
    }
</style>