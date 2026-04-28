-- 心理健康咨询助手 数据库初始化脚本
-- 使用方法: mysql -uroot -p < schema.sql

CREATE DATABASE IF NOT EXISTS `mental_health_assistant`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `mental_health_assistant`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`   VARCHAR(50)  NOT NULL COMMENT '用户名',
    `email`      VARCHAR(100) NOT NULL COMMENT '邮箱',
    `phone`      VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `password`   VARCHAR(255) NOT NULL COMMENT '密码',
    `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`     VARCHAR(255) DEFAULT NULL COMMENT '头像路径',
    `gender`     INT          DEFAULT NULL COMMENT '性别',
    `birthday`   DATE         DEFAULT NULL COMMENT '生日',
    `user_type`  INT          DEFAULT 1 COMMENT '用户类型 1:普通用户 2:管理员',
    `status`     INT          DEFAULT 1 COMMENT '状态 0:禁用 1:正常',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 咨询会话表
CREATE TABLE IF NOT EXISTS `consultation_session` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`               BIGINT       NOT NULL COMMENT '用户ID',
    `session_title`         VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
    `started_at`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `last_emotion_analysis` TEXT         DEFAULT NULL COMMENT '最后一次情绪分析结果(JSON格式)',
    `last_emotion_updated_at` DATETIME   DEFAULT NULL COMMENT '最后一次情绪分析更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '咨询会话表';

-- 咨询消息表
CREATE TABLE IF NOT EXISTS `consultation_message` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id`  BIGINT      NOT NULL COMMENT '会话ID',
    `sender_type` INT         NOT NULL COMMENT '发送者类型 1:用户 2:AI助手',
    `message_type` INT        NOT NULL DEFAULT 1 COMMENT '消息类型 1:文本',
    `content`     TEXT        NOT NULL COMMENT '消息内容',
    `emotion_tag` VARCHAR(50) DEFAULT NULL COMMENT '情绪标签',
    `ai_model`    VARCHAR(50) DEFAULT NULL COMMENT '使用的AI模型',
    `created_at`  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '咨询消息表';

-- 情绪日记表
CREATE TABLE IF NOT EXISTS `emotion_diary` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`          BIGINT       NOT NULL COMMENT '用户ID',
    `diary_date`       DATE         NOT NULL COMMENT '日记日期',
    `mood_score`       INT          NOT NULL COMMENT '情绪评分1-10',
    `dominant_emotion` VARCHAR(50)  DEFAULT NULL COMMENT '主要情绪',
    `emotion_triggers` VARCHAR(1000) DEFAULT NULL COMMENT '情绪触发因素',
    `diary_content`    VARCHAR(2000) DEFAULT NULL COMMENT '日记内容',
    `sleep_quality`    INT          DEFAULT NULL COMMENT '睡眠质量1-5',
    `stress_level`     INT          DEFAULT NULL COMMENT '压力水平1-5',
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '情绪日记表';

-- 知识文章分类表
CREATE TABLE IF NOT EXISTS `knowledge_category` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id`     BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID，0为顶级',
    `sort_order`    INT         NOT NULL DEFAULT 0 COMMENT '排序号',
    `status`        INT         NOT NULL DEFAULT 1 COMMENT '状态 0:禁用 1:启用',
    `created_at`    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识文章分类表';

-- 知识文章表
-- 状态(status)取值：0 草稿(管理员未发布) / 1 已发布 / 2 已下线 / 3 待审核(用户投稿) / 4 已驳回
-- 注意：用户投稿先进 3，管理员审核通过后才可能到 1
CREATE TABLE IF NOT EXISTS `knowledge_article` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    `title`           VARCHAR(200) NOT NULL COMMENT '文章标题',
    `content`         MEDIUMTEXT   COMMENT '富文本内容',
    `summary`         VARCHAR(1000) DEFAULT NULL COMMENT '文章摘要',
    `cover_image`     VARCHAR(255) DEFAULT NULL COMMENT '封面图片路径',
    `category_id`     BIGINT       NOT NULL COMMENT '分类ID',
    `author_name`     VARCHAR(50)  DEFAULT NULL COMMENT '作者名称',
    `author_id`       BIGINT       DEFAULT NULL COMMENT '投稿用户ID，NULL表示管理员/系统创建',
    `author_type`     INT          NOT NULL DEFAULT 1 COMMENT '作者类型 1:系统/管理员 2:用户投稿',
    `tags`            VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
    `read_count`      INT          NOT NULL DEFAULT 0 COMMENT '阅读量',
    `status`          INT          NOT NULL DEFAULT 0 COMMENT '状态 0:草稿 1:已发布 2:已下线 3:待审核 4:已驳回',
    `citable`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可被AI引用 0:否 1:是。与status解耦：可展示不等于可被AI当作专业依据引用',
    `reviewed_by`     BIGINT       DEFAULT NULL COMMENT '审核人ID',
    `reviewed_at`     DATETIME     DEFAULT NULL COMMENT '审核时间',
    `reject_reason`   VARCHAR(500) DEFAULT NULL COMMENT '驳回原因，仅status=4时有值',
    `published_at`    DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_citable` (`status`, `citable`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识文章表（含用户投稿与审核状态，citable决定能否被AI引用）';

-- ============================================================
-- 增量迁移：为已存在的旧库补齐投稿审核相关字段
--
-- 为什么不用「先 DROP PROCEDURE + DELIMITER $$ + CREATE PROCEDURE」这套常见写法：
--   DELIMITER 是 mysql 命令行客户端的指令，不是 SQL 语法。
--   用 JDBC / Spring 的 ScriptUtils 执行时会在 DELIMITER 处直接语法报错，
--   导致整个脚本中断（种子数据都插不进去）。这里改用 MySQL 原生动态 SQL，
--   纯 SQL 语句、无客户端依赖，JDBC 与 mysql CLI 都能正确执行。
--
-- 效果：无论跑在新库还是旧库、跑多少次都不报错，可安全重复执行。
-- 若后续还要加字段，照抄下面六段中任意一段、改掉列名与定义即可。
-- ============================================================

-- author_id
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_article' AND COLUMN_NAME = 'author_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_article` ADD COLUMN `author_id` BIGINT DEFAULT NULL COMMENT ''投稿用户ID，NULL表示管理员/系统创建''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- author_type
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_article' AND COLUMN_NAME = 'author_type');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_article` ADD COLUMN `author_type` INT NOT NULL DEFAULT 1 COMMENT ''作者类型 1:系统/管理员 2:用户投稿''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- citable
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_article' AND COLUMN_NAME = 'citable');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_article` ADD COLUMN `citable` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否可被AI引用 0:否 1:是''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- reviewed_by
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_article' AND COLUMN_NAME = 'reviewed_by');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_article` ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL COMMENT ''审核人ID''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- reviewed_at
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_article' AND COLUMN_NAME = 'reviewed_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_article` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT ''审核时间''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- reject_reason
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_article' AND COLUMN_NAME = 'reject_reason');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_article` ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT ''驳回原因，仅status=4时有值''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 危机干预闭环
--
-- 为什么拆成两张表，而不是合成一张：
--   crisis_event 记录「发生了什么」——AI 的识别结果，一旦写入不再修改，
--                属于事实留痕，用于事后回溯与统计；
--   crisis_work_order 记录「谁在处理」——认领、处置、闭环，状态会不断变化。
-- 两者生命周期不同：事件是不可变的历史，工单是可变的流程。
-- 合成一张表会导致「修改工单状态时连带改写事件记录」，破坏留痕的可信度。
-- ============================================================

-- 危机事件（不可变的事实记录）
CREATE TABLE IF NOT EXISTS `crisis_event` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    `user_id`         BIGINT       NOT NULL COMMENT '学生用户ID',
    `session_id`      BIGINT       NOT NULL COMMENT '触发会话ID',
    `risk_level`      INT          NOT NULL COMMENT '风险等级 2:预警 3:危机',
    `primary_emotion` VARCHAR(50)  DEFAULT NULL COMMENT '触发时的主要情绪',
    `emotion_score`   INT          DEFAULT NULL COMMENT '触发时的情绪分值',
    `trigger_message` TEXT         DEFAULT NULL COMMENT '触发的用户消息（留痕）',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '识别时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '危机事件表（AI识别的留痕，不可变）';

-- 关怀工单（可变的处理流程）
-- 承载两类来源：AI 识别高危自动建单、Agent 判断需要人工介入时主动转介。
-- 两者共用一条队列，用 source 区分；辅导员需要的是一个收件箱，不是两个。
CREATE TABLE IF NOT EXISTS `crisis_work_order` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `event_id`        BIGINT        DEFAULT NULL COMMENT '关联的危机事件ID，Agent主动转介时为空',
    `user_id`         BIGINT        NOT NULL COMMENT '学生用户ID（冗余，便于按学生查询）',
    `source`          VARCHAR(20)   NOT NULL DEFAULT 'AUTO_DETECTED' COMMENT '来源 AUTO_DETECTED:AI识别高危 AGENT_ESCALATED:Agent主动转介 STUDENT_REQUESTED:学生主动求助',
    `urgency`         INT           NOT NULL DEFAULT 2 COMMENT '紧急程度 1:高 2:中 3:低',
    `escalate_reason` VARCHAR(500)  DEFAULT NULL COMMENT '转介原因（非AI识别场景）',
    `status`          INT           NOT NULL DEFAULT 0 COMMENT '状态 0:待认领 1:处理中 2:已闭环',
    `handler_id`      BIGINT        DEFAULT NULL COMMENT '处理人（辅导员）用户ID',
    `handler_name`    VARCHAR(50)   DEFAULT NULL COMMENT '处理人姓名（冗余，便于展示）',
    `claimed_at`      DATETIME      DEFAULT NULL COMMENT '认领时间',
    `closed_at`       DATETIME      DEFAULT NULL COMMENT '闭环时间',
    `handle_result`   VARCHAR(1000) DEFAULT NULL COMMENT '处置结果（闭环时必填）',
    `created_at`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '建单时间',
    `updated_at`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 一个事件最多对应一张工单，从数据库层面兜住重复建单。
    -- 注意：MySQL 的唯一索引不把多个 NULL 视为冲突，因此主动转介（event_id 为空）可有多条
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_status` (`status`),
    KEY `idx_urgency` (`urgency`),
    KEY `idx_handler_id` (`handler_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关怀工单表（处理流程，状态可变）';

-- 增量迁移：为已存在的旧库补齐关怀工单新增字段
-- source
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crisis_work_order' AND COLUMN_NAME = 'source');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `crisis_work_order` ADD COLUMN `source` VARCHAR(20) NOT NULL DEFAULT ''AUTO_DETECTED'' COMMENT ''来源''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- urgency
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crisis_work_order' AND COLUMN_NAME = 'urgency');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `crisis_work_order` ADD COLUMN `urgency` INT NOT NULL DEFAULT 2 COMMENT ''紧急程度 1:高 2:中 3:低''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- escalate_reason
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crisis_work_order' AND COLUMN_NAME = 'escalate_reason');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `crisis_work_order` ADD COLUMN `escalate_reason` VARCHAR(500) DEFAULT NULL COMMENT ''转介原因''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- escalated（超时升级标记）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crisis_work_order' AND COLUMN_NAME = 'escalated');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `crisis_work_order` ADD COLUMN `escalated` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否已因超时被升级''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- escalated_at
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crisis_work_order' AND COLUMN_NAME = 'escalated_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `crisis_work_order` ADD COLUMN `escalated_at` DATETIME DEFAULT NULL COMMENT ''升级时间''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- event_id 改可空（旧库中该列是 NOT NULL）
SET @ddl := IF((SELECT IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crisis_work_order' AND COLUMN_NAME = 'event_id') = 'NO',
    'ALTER TABLE `crisis_work_order` MODIFY COLUMN `event_id` BIGINT DEFAULT NULL COMMENT ''关联的危机事件ID，Agent主动转介时为空''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Agent 执行轨迹
-- 记录 Agent 每次生成回复时实际执行的步骤（工具调用、内容生成、安全拦截）。
-- 存在的意义：Agent 与普通问答机器人的区别在于「多步执行」，如果这些步骤不可见，
-- 系统就是黑盒——回复不对时无法判断是没检索、检索错了、还是判断失误。
CREATE TABLE IF NOT EXISTS `agent_trace` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '轨迹ID',
    `turn_id`        VARCHAR(64)  NOT NULL COMMENT '轮次标识（一次用户发言对应一个轮次）',
    `session_id`     BIGINT       NOT NULL COMMENT '会话ID',
    `user_id`        BIGINT       DEFAULT NULL COMMENT '用户ID',
    `step_index`     INT          NOT NULL COMMENT '步骤序号，轮次内从0递增',
    `step_type`      VARCHAR(20)  NOT NULL COMMENT '步骤类型 TURN_START/TOOL_CALL/GENERATION/SAFETY_BLOCK',
    `tool_name`      VARCHAR(64)  DEFAULT NULL COMMENT '工具名（仅TOOL_CALL）',
    `tool_input`     TEXT         DEFAULT NULL COMMENT '工具入参',
    `result_summary` VARCHAR(500) DEFAULT NULL COMMENT '结果摘要（截断保存）',
    `duration_ms`    BIGINT       DEFAULT NULL COMMENT '耗时(毫秒)',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_turn_id` (`turn_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent执行轨迹表（记录思考与工具调用过程）';

-- ============================================================
-- 心理测评（受控工具）
--
-- 设计原则：题目预置、服务端计分、风险由代码判定。
-- AI 只负责一件事——判断「什么时候建议用户做测评」。
-- 原因：PHQ-9 / GAD-7 是经临床验证的标准化量表，题目措辞与顺序影响信效度；
--      若由模型转述题目、计算分数，结果将失去参考价值。
-- ============================================================

-- 量表定义
CREATE TABLE IF NOT EXISTS `assessment_scale` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '量表ID',
    `code`        VARCHAR(20)  NOT NULL COMMENT '量表编码 PHQ9/GAD7',
    `name`        VARCHAR(100) NOT NULL COMMENT '量表名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '量表说明',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '心理量表定义表';

-- 量表题目（预置，AI 不可生成）
CREATE TABLE IF NOT EXISTS `assessment_question` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `scale_code` VARCHAR(20)  NOT NULL COMMENT '所属量表编码',
    `order_no`   INT          NOT NULL COMMENT '题号，从1开始',
    `content`    VARCHAR(300) NOT NULL COMMENT '题目内容',
    `risk_item`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否风险题项（得分>0时触发危机流程）',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_scale_code` (`scale_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '心理量表题目表（预置，AI不可生成）';

-- 测评记录
CREATE TABLE IF NOT EXISTS `assessment_record` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`      BIGINT       NOT NULL COMMENT '学生用户ID',
    `session_id`   BIGINT       DEFAULT NULL COMMENT '触发测评的会话ID',
    `scale_code`   VARCHAR(20)  NOT NULL COMMENT '量表编码',
    `status`       INT          NOT NULL DEFAULT 0 COMMENT '状态 0:待作答 1:已完成 2:已放弃',
    `answers`      VARCHAR(500) DEFAULT NULL COMMENT '作答明细，逗号分隔',
    `total_score`  INT          DEFAULT NULL COMMENT '总分（服务端计算）',
    `severity`     VARCHAR(20)  DEFAULT NULL COMMENT '严重程度',
    `risk_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否命中风险题项',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    `completed_at` DATETIME     DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_scale_code` (`scale_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '心理测评记录表';

-- 种子数据：量表定义
INSERT IGNORE INTO `assessment_scale` (`code`, `name`, `description`) VALUES
('PHQ9', '情绪状态自评（PHQ-9）', '用于了解最近两周的情绪状态。请根据过去两周的实际感受作答，答案没有对错。'),
('GAD7', '焦虑状态自评（GAD-7）', '用于了解最近两周的焦虑状态。请根据过去两周的实际感受作答，答案没有对错。');

-- 种子数据：PHQ-9 题目（第 9 题为风险题项：自伤念头）
INSERT IGNORE INTO `assessment_question` (`scale_code`, `order_no`, `content`, `risk_item`) VALUES
('PHQ9', 1, '做事时提不起劲或没有兴趣', 0),
('PHQ9', 2, '感到心情低落、沮丧或绝望', 0),
('PHQ9', 3, '入睡困难、睡不安稳或睡眠过多', 0),
('PHQ9', 4, '感觉疲倦或没有活力', 0),
('PHQ9', 5, '食欲不振或吃太多', 0),
('PHQ9', 6, '觉得自己很糟，或觉得自己很失败，或让自己和家人失望', 0),
('PHQ9', 7, '对事物专注有困难，例如阅读或看电视时', 0),
('PHQ9', 8, '动作或说话速度明显缓慢，或相反地烦躁、坐立不安', 0),
('PHQ9', 9, '有不如死掉、或用某种方式伤害自己的念头', 1);

-- 种子数据：GAD-7 题目
INSERT IGNORE INTO `assessment_question` (`scale_code`, `order_no`, `content`, `risk_item`) VALUES
('GAD7', 1, '感到紧张、焦虑或急切', 0),
('GAD7', 2, '不能够停止或控制担忧', 0),
('GAD7', 3, '对各种各样的事情担忧过多', 0),
('GAD7', 4, '很难放松下来', 0),
('GAD7', 5, '由于不安而无法静坐', 0),
('GAD7', 6, '变得容易烦恼或急躁', 0),
('GAD7', 7, '感到似乎将有可怕的事情发生而害怕', 0);

-- 站内通知
-- 存在的意义：危机工单创建后若只是躺在数据库里，没人去看就等于没建单。
-- 通知把「有事发生」主动推给该处理的人，闭环才真正成立。
CREATE TABLE IF NOT EXISTS `notification` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`    BIGINT        NOT NULL COMMENT '接收人用户ID',
    `type`       VARCHAR(30)   NOT NULL COMMENT '类型 CRISIS_ORDER/ORDER_ESCALATED/ORDER_CLAIMED',
    `title`      VARCHAR(200)  NOT NULL COMMENT '标题',
    `content`    VARCHAR(1000) DEFAULT NULL COMMENT '正文',
    `biz_id`     BIGINT        DEFAULT NULL COMMENT '关联业务ID（工单ID等），便于前端跳转',
    `is_read`    TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已读',
    `created_at` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知表';

-- 种子数据：分类
INSERT IGNORE INTO `knowledge_category` (`id`, `category_name`, `sort_order`) VALUES
(1, '情绪管理', 1), (2, '压力应对', 2), (3, '睡眠改善', 3), (4, '人际关系', 4), (5, '自我成长', 5);

-- 种子数据：示例文章
-- 这两篇是系统文章，author_type=1 且 citable=1（可被 AI 引用）。
-- 应用启动时会自动把它们向量化进 Redis，供 AI 检索引用。
-- 对比：用户投稿默认 author_type=2 且 citable=0——可展示，但不进入 AI 知识库。
INSERT IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `author_name`, `author_type`, `citable`, `tags`, `read_count`, `status`, `published_at`) VALUES
(1, '如何与焦虑情绪和平相处', '<h2>认识焦虑</h2><p>焦虑是我们面对压力时的一种正常情绪反应，它提醒我们关注重要的事情。试着把焦虑当作一位来提醒你的朋友，而不是敌人。</p><h2>三个实用方法</h2><p><strong>1. 4-7-8呼吸法</strong>：吸气4秒、屏息7秒、缓慢呼气8秒，重复4轮，能快速让身体放松下来。</p><p><strong>2. 把担心写下来</strong>：给焦虑一个具体的形状，你会发现它没有想象中那么庞大。</p><p><strong>3. 五分钟正念</strong>：找个安静的地方，把注意力放在呼吸上，允许思绪来去。</p><h2>什么时候需要寻求帮助</h2><p>如果焦虑持续超过两周，已经影响到睡眠和日常生活，请一定联系学校的心理咨询中心，寻求专业帮助并不可耻。</p>', '焦虑并不可怕，学会与它和平相处是成长的一部分。', 1, '系统管理员', 1, 1, '焦虑,情绪管理,放松', 128, 1, NOW()),
(2, '改善睡眠质量的五个小习惯', '<h2>睡眠的重要性</h2><p>良好的睡眠是心理健康的基石。长期睡眠不足会放大情绪波动，降低抗压能力。</p><h2>五个小习惯</h2><p>1. 固定作息时间，周末也不超过1小时的偏差；</p><p>2. 睡前1小时远离手机屏幕，蓝光会抑制褪黑素分泌；</p><p>3. 卧室保持凉爽、黑暗、安静；</p><p>4. 下午3点后不喝咖啡和浓茶；</p><p>5. 睡前一小时写下明天的待办清单，把烦恼交给纸笔。</p>', '睡个好觉，从这五个小习惯开始。', 3, '系统管理员', 1, 1, '睡眠,冥想,放松', 96, 1, NOW());
