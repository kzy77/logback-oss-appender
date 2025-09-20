package io.github.osslogback.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.status.ErrorStatus;
import io.github.osslogback.adapter.S3LogbackAdapter;

import java.util.Objects;

/**
 * S3兼容对象存储 Logback Appender：
 * - 支持AWS S3、阿里云OSS、腾讯云COS、MinIO、Cloudflare R2等所有S3兼容存储
 * - 基于AWS SDK v2构建，提供统一的对象存储接口
 * - 继承 UnsynchronizedAppenderBase 避免线程同步开销
 * - 依赖 Encoder 将 ILoggingEvent 序列化为字符串
 * - 核心逻辑委托给 S3LogbackAdapter（复用log-java-producer的高性能组件）
 */
public final class OssAsyncAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    // Logback必需
    private Encoder<ILoggingEvent> encoder;

    // S3兼容存储配置 - 必需参数
    private String endpoint;
    private String region;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket;

    // 应用行为配置 - 可选参数，提供最优默认值
    private String keyPrefix = "logs/";
    private int maxQueueSize = 200_000;
    private int maxBatchCount = 5_000;
    private int maxBatchBytes = 4 * 1024 * 1024;
    private long flushIntervalMs = 2000L;
    private boolean dropWhenQueueFull = false;
    private boolean multiProducer = false;
    private int maxRetries = 5;
    private long baseBackoffMs = 200L;
    private long maxBackoffMs = 10000L;

    private S3LogbackAdapter adapter;

    @Override
    public void start() {
        if (encoder == null) {
            addStatus(new ErrorStatus("No encoder set for the appender named \"" + name + "\".", this));
            return;
        }
        try {
            // 验证必需参数
            Objects.requireNonNull(accessKeyId, "accessKeyId must be set");
            Objects.requireNonNull(accessKeySecret, "accessKeySecret must be set");
            Objects.requireNonNull(bucket, "bucket must be set");

            // 构建S3LogbackAdapter配置
            S3LogbackAdapter.Config config = new S3LogbackAdapter.Config();
            config.endpoint = this.endpoint;
            config.region = this.region;
            config.accessKeyId = this.accessKeyId;
            config.accessKeySecret = this.accessKeySecret;
            config.bucket = this.bucket;
            config.keyPrefix = this.keyPrefix;
            config.maxQueueSize = this.maxQueueSize;
            config.maxBatchCount = this.maxBatchCount;
            config.maxBatchBytes = this.maxBatchBytes;
            config.flushIntervalMs = this.flushIntervalMs;
            config.dropWhenQueueFull = this.dropWhenQueueFull;
            config.multiProducer = this.multiProducer;
            config.maxRetries = this.maxRetries;
            config.baseBackoffMs = this.baseBackoffMs;
            config.maxBackoffMs = this.maxBackoffMs;

            this.adapter = new S3LogbackAdapter(config);
            super.start();
        } catch (Exception e) {
            addError("Failed to start S3CompatibleAppender", e);
        }
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted() || adapter == null) {
            return;
        }
        try {
            // logback encoder 不是线程安全的
            byte[] encoded = encoder.encode(eventObject);
            adapter.offer(new String(encoded, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            addError("Failed to encode and send log event", e);
        }
    }

    @Override
    public void stop() {
        if (adapter != null) {
            try {
                adapter.close();
            } catch (Exception e) {
                addError("Failed to gracefully close adapter", e);
            }
        }
        super.stop();
    }

    // region setters for logback config
    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    public void setRegion(String region) {
        this.region = region;
    }
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }
    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }
    public void setMaxBatchCount(int maxBatchCount) {
        this.maxBatchCount = maxBatchCount;
    }
    public void setMaxBatchBytes(int maxBatchBytes) {
        this.maxBatchBytes = maxBatchBytes;
    }
    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }
    public void setDropWhenQueueFull(boolean dropWhenQueueFull) {
        this.dropWhenQueueFull = dropWhenQueueFull;
    }
    public void setMultiProducer(boolean multiProducer) {
        this.multiProducer = multiProducer;
    }
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    public void setBaseBackoffMs(long baseBackoffMs) {
        this.baseBackoffMs = baseBackoffMs;
    }
    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }
    // endregion
}
