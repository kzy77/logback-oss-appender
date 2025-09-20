# Logback OSS Appender

高性能 Logback Appender SDK，将应用日志实时上传到阿里云 OSS。

## 特性

- **零配置开箱即用**：遵循最佳实践的默认配置，仅需4个必要参数
- **不落盘**：日志直接入内存队列并异步上传
- **无需改造**：仅配置 logback.xml 即可接入
- **异步高吞吐**：批处理、NDJSON格式、gzip压缩、连接复用
- **不丢日志**：可配置为生产侧阻塞等待，确保写入
- **可调优**：所有性能参数可按需调整

## 快速开始

### 1) 引入依赖（Maven）
```xml
<dependency>
  <groupId>io.github.oss-logback</groupId>
  <artifactId>logback-oss-appender</artifactId>
  <version>0.1.0</version>
</dependency>
```

### 2) 最简配置（推荐）
只需配置4个必要参数，其他采用最优默认值：

```xml
<configuration>
  <appender name="OSS" class="io.github.osslogback.logback.OssAsyncAppender">
    <endpoint>${OSS_ENDPOINT}</endpoint>
    <accessKeyId>${OSS_AK}</accessKeyId>
    <accessKeySecret>${OSS_SK}</accessKeySecret>
    <bucket>${OSS_BUCKET}</bucket>
    <layout class="io.github.osslogback.logback.JsonLinesLayout"/>
  </appender>
  <root level="INFO"><appender-ref ref="OSS"/></root>
</configuration>
```

### 3) 环境变量配置
```bash
export OSS_ENDPOINT="https://oss-cn-hangzhou.aliyuncs.com"
export OSS_AK="your-access-key-id"
export OSS_SK="your-access-key-secret"
export OSS_BUCKET="your-bucket-name"
```

### 4) 完整配置选项（可选）
如需自定义，可覆盖以下默认配置：

```xml
<configuration>
  <appender name="OSS" class="io.github.osslogback.logback.OssAsyncAppender">
    <!-- 必需配置 -->
    <endpoint>${OSS_ENDPOINT}</endpoint>
    <accessKeyId>${OSS_AK}</accessKeyId>
    <accessKeySecret>${OSS_SK}</accessKeySecret>
    <bucket>${OSS_BUCKET}</bucket>

    <!-- 可选配置（显示默认值） -->
    <appName>default-app</appName>                    <!-- 应用标识 -->
    <objectKeyPrefix>logs/</objectKeyPrefix>          <!-- OSS对象键前缀 -->
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
  <root level="INFO"><appender-ref ref="OSS"/></root>
</configuration>
```

## 配置说明

### 必需配置
| 参数 | 说明 | 示例 |
|------|------|------|
| `endpoint` | OSS服务端点 | `https://oss-cn-hangzhou.aliyuncs.com` |
| `accessKeyId` | 访问密钥ID | 环境变量 `${OSS_AK}` |
| `accessKeySecret` | 访问密钥Secret | 环境变量 `${OSS_SK}` |
| `bucket` | OSS bucket名称 | 环境变量 `${OSS_BUCKET}` |

### 性能调优参数
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxQueueSize` | 200000 | 内存队列容量，影响内存使用和丢日志风险 |
| `maxBatchCount` | 5000 | 单次上传最大日志条数 |
| `maxBatchBytes` | 4194304 | 单次上传最大字节数(4MB) |
| `flushIntervalMillis` | 2000 | 强制刷新间隔(毫秒) |
| `dropWhenQueueFull` | false | 队列满时丢弃vs阻塞，false保证不丢日志 |

### 输出格式参数
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `gzip` | true | 启用gzip压缩，显著减少存储空间 |
| `contentType` | application/x-ndjson | 输出格式，支持流式处理和分析 |
| `objectKeyPrefix` | logs/ | OSS对象键前缀 |
| `appName` | default-app | 应用标识，用于生成唯一的对象键 |

## 设计说明

- **NDJSON格式**：每行一条JSON记录，支持流式处理，容错性强
- **gzip压缩**：默认启用，典型压缩率70-80%，显著节省存储和传输成本
- **批处理聚合**：按条数/字节/时间窗口智能聚合，平衡延迟与吞吐
- **指数退避重试**：网络异常时自动重试，避免雪崩效应
- **零配置理念**：默认配置基于生产最佳实践，开箱即用

## 许可证

Apache-2.0