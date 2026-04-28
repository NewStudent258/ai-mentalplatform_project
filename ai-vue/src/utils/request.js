import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const service = axios.create({
  baseURL: '/api', // 请求的前缀
  timeout: 5000, // 请求的超时时间
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 在发送请求之前做些什么
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['token'] = token
    }
    return config
  },
  (error) => {
    // 对请求错误做些什么
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    // 对响应数据做点什么
    const { data, config } = response
    // 处理业务状态码
    if (data.code === '200') {
        return data.data
    } else {
        if (data.code === '-1') {
          if (!config.url?.includes('/login')) {
            ElMessage.error(data.msg || '登录过期，请重新登录')

            // 清除登录信息
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            window.location.href = '/auth/login'
          } else {
            ElMessage.error(data.msg || '登录过期，请重新登录')
            return Promise.reject('网络请求失败....')
          }
        }
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    // 后端在认证失败时会返回带业务码的 JSON（A0230/A0231/A0301 等）。
    // 关键点：不能只看 HTTP 状态码就清登录态——框架层的错误（如接口不存在、
    // 服务端异常）同样可能返回 401/403，但它们与登录状态无关，
    // 一旦误清 token，用户会被莫名其妙地登出。
    const businessCode = error.response?.data?.code
    const isAuthFailure = status === 401
      || (status === 403 && typeof businessCode === 'string' && businessCode.startsWith('A0'))

    if (isAuthFailure) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      // 避免在登录页重复跳转
      if (!window.location.pathname.includes('/auth/login')) {
        window.location.href = '/auth/login'
      }
    } else if (status === 403) {
      // 权限不足等非登录态问题：只提示，不动登录态
      ElMessage.error(error.response?.data?.msg || '没有访问该资源的权限')
    }
    return Promise.reject(error)
  }
)

export default service