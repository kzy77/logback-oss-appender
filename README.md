# Logback S3 Compatible Appender

高性能 Logback Appender，支持所有 AWS S3 兼容对象存储服务。

## 🌟 特性

- **🌐 通用兼容**：支持 AWS S3、阿里云OSS、腾讯云COS、MinIO、Cloudflare R2 等所有 S3 兼容存储
- **⚡ 零配置开箱即用**：基于 AWS SDK v2，自动检测云厂商并应用最优配置
- **🚀 极致性能**：异步批处理、NDJSON格式、gzip压缩、无锁队列
- **💾 不落盘**：日志直接入内存队列并异步上传
- **🔒 不丢日志**：可配置为生产侧阻塞等待，确保写入
- **🛠️ 可调优**：所有性能参数可按需调整

## 🚀 快速开始

### 1) 引入依赖（Maven）
```xml
<dependency>
  <groupId>io.github.oss-logback</groupId>
  <artifactId>logback-oss-appender</artifactId>
  <version>0.1.0</version>
</dependency>
```

### 2) 最简配置（推荐）

#### AWS S3
```xml
<configuration>
  <appender name="S3" class="io.github.osslogback.logback.OssAsyncAppender">
    <accessKeyId>${AWS_ACCESS_KEY_ID}</accessKeyId>
    <accessKeySecret>${AWS_SECRET_ACCESS_KEY}</accessKeySecret>
    <bucket>${S3_BUCKET}</bucket>
    <region>us-east-1</region>
    <layout class="io.github.osslogback.logback.JsonLinesLayout"/>
  </appender>
  <root level="INFO"><appender-ref ref="S3"/></root>
</configuration>
```

#### 阿里云 OSS
```xml
<configuration>
  <appender name="OSS" class="io.github.osslogback.logback.OssAsyncAppender">
    <endpoint>https://oss-cn-hangzhou.aliyuncs.com</endpoint>
    <accessKeyId>${OSS_ACCESS_KEY_ID}</accessKeyId>
    <accessKeySecret>${OSS_ACCESS_KEY_SECRET}</accessKeySecret>
    <bucket>${OSS_BUCKET}</bucket>
    <layout class="io.github.osslogback.logback.JsonLinesLayout"/>
  </appender>
  <root level="INFO"><appender-ref ref="OSS"/></root>
</configuration>
```

#### MinIO
```xml
<configuration>
  <appender name="MINIO" class="io.github.osslogback.logback.OssAsyncAppender">
    <endpoint>http://localhost:9000</endpoint>
    <accessKeyId>${MINIO_ACCESS_KEY}</accessKeyId>
    <accessKeySecret>${MINIO_SECRET_KEY}</accessKeySecret>
    <bucket>${MINIO_BUCKET}</bucket>
    <layout class="io.github.osslogback.logback.JsonLinesLayout"/>
  </appender>
  <root level="INFO"><appender-ref ref="MINIO"/></root>
</configuration>
```

### 3) 环境变量配置

#### AWS S3
```bash
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"
export S3_BUCKET="your-bucket-name"
```

#### 阿里云 OSS
```bash
export OSS_ACCESS_KEY_ID="your-access-key-id"
export OSS_ACCESS_KEY_SECRET="your-access-key-secret"
export OSS_BUCKET="your-bucket-name"
```

### 4) 完整配置选项（可选）

```xml
<configuration>
  <appender name="S3" class="io.github.osslogback.logback.OssAsyncAppender">
    <!-- 必需配置 -->
    <endpoint>https://s3.amazonaws.com</endpoint>          <!-- 仅非AWS S3需要 -->
    <region>us-east-1</region>                            <!-- AWS区域或等效区域 -->
    <accessKeyId>${ACCESS_KEY_ID}</accessKeyId>
    <accessKeySecret>${ACCESS_KEY_SECRET}</accessKeySecret>
    <bucket>${BUCKET}</bucket>

    <!-- 可选配置（显示默认值） -->
    <appName>default-app</appName>                    <!-- 应用标识 -->
    <objectKeyPrefix>logs/</objectKeyPrefix>          <!-- 对象键前缀 -->
    <maxQueueSize>200000</maxQueueSize>               <!-- 内存队列大小 -->
    <maxBatchCount>5000</maxBatchCount>               <!-- 单批最大条数 -->
    <maxBatchBytes>4194304</maxBatchBytes>            <!-- 单批最大字节(4MB) -->
    <flushIntervalMillis>2000</flushIntervalMillis>   <!-- 强制刷新间隔 -->
    <offerTimeoutMillis>500</offerTimeoutMillis>      <!-- 入队超时时间 -->
    <dropWhenQueueFull>false</dropWhenQueueFull>      <!-- 队列满时是否丢弃 -->
    <gzip>true</gzip>                                 <!-- 启用gzip压缩 -->
    <contentType>application/x-ndjson</contentType>   <!-- 内容类型 -->
    <maxRetries>5</maxRetries>                        <!-- 最大重试次数 -->
    <initialBackoffMillis>200</initialBackoffMillis> <!-- 初始退避时间 -->
    <backoffMultiplier>2.0</backoffMultiplier>        <!-- 退避倍增因子 -->

    <layout class="io.github.osslogback.logback.JsonLinesLayout"/>
  </appender>
  <root level="INFO"><appender-ref ref="S3"/></root>
</configuration>
```

## 📋 配置说明

### 必需配置
| 参数 | 说明 | AWS S3 | 阿里云OSS | MinIO |
|------|------|--------|-----------|--------|
| `accessKeyId` | 访问密钥ID | ✅ | ✅ | ✅ |
| `accessKeySecret` | 访问密钥Secret | ✅ | ✅ | ✅ |
| `bucket` | 存储桶名称 | ✅ | ✅ | ✅ |
| `endpoint` | 服务端点 | 可选* | ✅必需 | ✅必需 |
| `region` | 区域标识 | ✅推荐 | 自动检测 | 自动设置 |

*AWS S3 可省略 endpoint，将使用默认端点

### 🎯 性能调优参数
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxQueueSize` | 200000 | 内存队列容量，影响内存使用和丢日志风险 |
| `maxBatchCount` | 5000 | 单次上传最大日志条数 |
| `maxBatchBytes` | 4194304 | 单次上传最大字节数(4MB) |
| `flushIntervalMillis` | 2000 | 强制刷新间隔(毫秒) |
| `dropWhenQueueFull` | false | 队列满时丢弃vs阻塞，false保证不丢日志 |

### 📦 输出格式参数
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `gzip` | true | 启用gzip压缩，显著减少存储空间 |
| `contentType` | application/x-ndjson | 输出格式，支持流式处理和分析 |
| `objectKeyPrefix` | logs/ | 对象键前缀 |
| `appName` | default-app | 应用标识，用于生成唯一的对象键 |

## 🏗️ 架构设计

### S3 兼容性
- **标准协议**：基于 AWS SDK v2，遵循 S3 API 标准
- **自动适配**：根据 endpoint 自动检测云厂商特性
- **统一接口**：一套代码支持所有 S3 兼容存储

### 高性能架构
- **NDJSON格式**：每行一条JSON记录，支持流式处理，容错性强
- **gzip压缩**：默认启用，典型压缩率70-80%，显著节省存储成本
- **批处理聚合**：按条数/字节/时间窗口智能聚合，平衡延迟与吞吐
- **指数退避重试**：网络异常时自动重试，避免雪崩效应
- **异步队列**：基于高性能队列，无锁设计，超低延迟

### 云厂商支持

| 存储服务 | 自动检测 | 特殊配置 | 测试状态 |
|----------|----------|----------|----------|
| **AWS S3** | ✅ | 无 | ✅ |
| **阿里云 OSS** | ✅ | 无 | ✅ |
| **腾讯云 COS** | ✅ | 无 | 🧪 |
| **MinIO** | ✅ | 路径风格 | ✅ |
| **Cloudflare R2** | ✅ | 无 | 🧪 |
| **其他 S3 兼容** | ✅ | 通用模式 | 🧪 |

## 🔧 最佳实践

### 生产环境推荐配置
```xml
<!-- 高吞吐量场景 -->
<maxQueueSize>500000</maxQueueSize>
<maxBatchCount>10000</maxBatchCount>
<flushIntervalMillis>5000</flushIntervalMillis>
<dropWhenQueueFull>false</dropWhenQueueFull>

<!-- 低延迟场景 -->
<maxQueueSize>50000</maxQueueSize>
<maxBatchCount>1000</maxBatchCount>
<flushIntervalMillis>500</flushIntervalMillis>
```

### 安全建议
- 使用环境变量或密钥管理服务存储凭证
- 定期轮换访问密钥
- 为应用分配最小权限的 IAM 策略

### 监控建议
- 监控队列积压情况
- 设置上传失败告警
- 定期检查存储成本

## 📄 许可证

Apache-2.0