package io.github.osslogback.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.status.ErrorStatus;
import io.github.osslogback.core.AsyncBatchSender;
import io.github.osslogback.oss.AliyunOssUploaderAdapter;

import java.util.Objects;

/**
 * Logback Appender 主实现。
 * - 继承 UnsynchronizedAppenderBase 避免线程同步开销
 * - 依赖 Encoder 将 ILoggingEvent 序列化为字符串
 * - 核心逻辑委托给 AsyncBatchSender
 */
public final class OssAsyncAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    // required by logback
    private Encoder<ILoggingEvent> encoder;
    // OSS client config
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket;
    // appender behavior
    private String appName = "default-app";
    private String objectKeyPrefix = "logs/";
    private int maxQueueSize = 200_000;
    private int maxBatchCount = 5_000;
    private int maxBatchBytes = 4 * 1024 * 1024;
    private long flushIntervalMillis = 2000L;
    private long offerTimeoutMillis = 500L;
    private boolean dropWhenQueueFull = false;
    private boolean gzip = true;
    private String contentType = "application/x-ndjson";
    private int maxRetries = 5;
    private long initialBackoffMillis = 200L;
    private double backoffMultiplier = 2.0;

    private AsyncBatchSender sender;

    @Override
    public void start() {
        if (encoder == null) {
            addStatus(new ErrorStatus("No encoder set for the appender named \"" + name + "\".", this));
            return;
        }
        try {
            Objects.requireNonNull(endpoint, "endpoint must be set");
            Objects.requireNonNull(accessKeyId, "accessKeyId must be set");
            Objects.requireNonNull(accessKeySecret, "accessKeySecret must be set");
            Objects.requireNonNull(bucket, "bucket must be set");

            AsyncBatchSender.Config config = new AsyncBatchSender.Config();
            config.appName = this.appName;
            config.objectKeyPrefix = this.objectKeyPrefix;
            config.maxQueueSize = this.maxQueueSize;
            config.maxBatchCount = this.maxBatchCount;
            config.maxBatchBytes = this.maxBatchBytes;
            config.flushIntervalMillis = this.flushIntervalMillis;
            config.offerTimeoutMillis = this.offerTimeoutMillis;
            config.dropWhenQueueFull = this.dropWhenQueueFull;
            config.gzip = this.gzip;
            config.contentType = this.contentType;
            config.maxRetries = this.maxRetries;
            config.initialBackoffMillis = this.initialBackoffMillis;
            config.backoffMultiplier = this.backoffMultiplier;

            AliyunOssUploaderAdapter uploader = new AliyunOssUploaderAdapter(
                    endpoint, accessKeyId, accessKeySecret, bucket
            );
            this.sender = new AsyncBatchSender(config, uploader);
            super.start();
        } catch (Exception e) {
            addError("Failed to start OssAsyncAppender", e);
        }
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted() || sender == null) {
            return;
        }
        try {
            // logback encoder is not thread-safe
            byte[] encoded = encoder.encode(eventObject);
            sender.offer(new String(encoded, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            addError("Failed to encode and send log event", e);
        }
    }

    @Override
    public void stop() {
        if (sender != null) {
            try {
                sender.close();
            } catch (Exception e) {
                addError("Failed to gracefully close sender", e);
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
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }
    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
    public void setAppName(String appName) {
        this.appName = appName;
    }
    public void setObjectKeyPrefix(String objectKeyPrefix) {
        this.objectKeyPrefix = objectKeyPrefix;
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
    public void setFlushIntervalMillis(long flushIntervalMillis) {
        this.flushIntervalMillis = flushIntervalMillis;
    }
    public void setOfferTimeoutMillis(long offerTimeoutMillis) {
        this.offerTimeoutMillis = offerTimeoutMillis;
    }
    public void setDropWhenQueueFull(boolean dropWhenQueueFull) {
        this.dropWhenQueueFull = dropWhenQueueFull;
    }
    public void setGzip(boolean gzip) {
        this.gzip = gzip;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    public void setInitialBackoffMillis(long initialBackoffMillis) {
        this.initialBackoffMillis = initialBackoffMillis;
    }
    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }
    // endregion
}
