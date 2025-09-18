# logback-oss-appender

高性能 Logback Appender SDK，将应用日志实时上传到阿里云 OSS。

特性：
- 不落盘：日志直接入内存队列并异步上传
- 无需改造：仅配置 logback.xml 即可接入
- 异步高吞吐：批处理、压缩、连接复用
- 不丢日志：可配置为生产侧阻塞等待，确保写入
- 可配置：队列大小、批量条数/字节、刷新间隔、重试与退避、gzip 等

## 快速开始

1) 引入依赖（Maven）
```xml
<dependency>
  <groupId>io.github.oss-logback</groupId>
  <artifactId>logback-oss-appender</artifactId>
  <version>0.1.0</version>
  </dependency>
```

2) 配置方式A（低侵入，推荐）：在现有 `logback.xml` 中新增 appender
参考 `src/main/resources/logback-oss-example.xml`：
```xml
<configuration>
  <appender name="OSS" class="io.github.osslogback.logback.OssAsyncAppender">
    <endpoint>${OSS_ENDPOINT}</endpoint>
    <accessKeyId>${OSS_AK}</accessKeyId>
    <accessKeySecret>${OSS_SK}</accessKeySecret>
    <bucket>${OSS_BUCKET}</bucket>
    <objectKeyPrefix>logs/${HOSTNAME}/</objectKeyPrefix>
    <appName>${APP_NAME:-demo}</appName>
    <gzip>true</gzip>
    <dropWhenQueueFull>false</dropWhenQueueFull>
    <maxQueueSize>200000</maxQueueSize>
    <maxBatchCount>5000</maxBatchCount>
    <maxBatchBytes>4194304</maxBatchBytes>
    <flushIntervalMillis>2000</flushIntervalMillis>
    <offerTimeoutMillis>500</offerTimeoutMillis>
    <maxRetries>5</maxRetries>
    <initialBackoffMillis>200</initialBackoffMillis>
    <backoffMultiplier>2.0</backoffMultiplier>
    <contentType>application/x-ndjson</contentType>
    <layout class="io.github.osslogback.logback.JsonLinesLayout"/>
  </appender>
  <root level="INFO"><appender-ref ref="OSS"/></root>
</configuration>
```

3) 运行时环境变量
- `OSS_ENDPOINT`，如 `https://oss-cn-hangzhou.aliyuncs.com`
- `OSS_AK`、`OSS_SK`
- `OSS_BUCKET`
- 可选：`APP_NAME`、`HOSTNAME`

## 设计说明

- `AsyncBatchSender`：内存队列 + 后台线程批处理，按条数/字节/时间窗口触发上传，支持 gzip、重试与指数退避。
- `AliyunOssUploader`：封装 OSS 客户端，设置 `Content-Type` 与 `Content-Encoding`。
- `OssAsyncAppender`：Logback 接入点，参数化配置，使用 `Layout` 将事件编码为 JSON Lines。

## 许可证

Apache-2.0

