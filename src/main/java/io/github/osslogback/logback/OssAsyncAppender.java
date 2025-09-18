package io.github.osslogback.logback;

import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import io.github.osslogback.core.AsyncBatchSender;
import io.github.osslogback.oss.AliyunOssUploaderAdapter;

import java.util.Objects;

/**
 * Logback Appender：
 * - 无需改造应用，只需在 logback.xml 配置该 appender
 * - 事件使用 Layout 编码为字符串，实时入队，不落盘
 * - 后台异步批量上传到 OSS
 */
public final class OssAsyncAppender extends AppenderBase<ILoggingEvent> {
    // 可配置属性（通过logback.xml的<appender>子元素设置）
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket;
    private String objectKeyPrefix = "logs/";
    private String appName = "app";
    private boolean gzip = true;
    private boolean dropWhenQueueFull = false;
    private int maxQueueSize = 100_000;
    private int maxBatchCount = 5_000;
    private int maxBatchBytes = 4 * 1024 * 1024;
    private long flushIntervalMillis = 2000L;
    private long offerTimeoutMillis = 500L;
    private int maxRetries = 5;
    private long initialBackoffMillis = 200L;
    private double backoffMultiplier = 2.0;
    private String contentType = "application/x-ndjson";
    private Layout<ILoggingEvent> layout;

    private AsyncBatchSender sender;
    private AliyunOssUploaderAdapter uploader;
    private boolean registerShutdownHook = true;
    private Thread shutdownHook;

    /**
     * 启动：构造Uploader与Sender
     */
    @Override
    public void start() {
        if (isStarted()) return;
        try {
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(accessKeyId, "accessKeyId");
            Objects.requireNonNull(accessKeySecret, "accessKeySecret");
            Objects.requireNonNull(bucket, "bucket");
            if (layout == null) {
                addError("layout 未设置，请在 appender 中配置 <layout>...");
                return;
            }
            uploader = new AliyunOssUploaderAdapter(endpoint, accessKeyId, accessKeySecret, bucket);
            AsyncBatchSender.Config cfg = new AsyncBatchSender.Config();
            cfg.maxQueueSize = maxQueueSize;
            cfg.maxBatchCount = maxBatchCount;
            cfg.maxBatchBytes = maxBatchBytes;
            cfg.flushIntervalMillis = flushIntervalMillis;
            cfg.offerTimeoutMillis = offerTimeoutMillis;
            cfg.dropWhenQueueFull = dropWhenQueueFull;
            cfg.gzip = gzip;
            cfg.objectKeyPrefix = objectKeyPrefix;
            cfg.contentType = contentType;
            cfg.maxRetries = maxRetries;
            cfg.initialBackoffMillis = initialBackoffMillis;
            cfg.backoffMultiplier = backoffMultiplier;
            cfg.appName = appName;
            sender = new AsyncBatchSender(cfg, uploader);
            // 注册JVM关闭钩子，确保容器/进程退出前完成冲刷
            if (registerShutdownHook) {
                shutdownHook = new Thread(() -> {
                    try {
                        if (sender != null) sender.close();
                    } catch (Exception ignore) {
                    }
                    try {
                        if (uploader != null) uploader.close();
                    } catch (Exception ignore) {
                    }
                }, "oss-logback-shutdown-hook");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            }
            super.start();
        } catch (Exception e) {
            addError("OssAsyncAppender 启动失败", e);
        }
    }

    /**
     * 关闭：优雅停止后台线程并关闭OSS客户端
     */
    @Override
    public void stop() {
        if (!isStarted()) return;
        try {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignore) {
                    // JVM 已在关闭阶段，忽略
                } finally {
                    shutdownHook = null;
                }
            }
            if (sender != null) sender.close();
        } catch (Exception ignore) {
        } finally {
            if (uploader != null) uploader.close();
            super.stop();
        }
    }

    /**
     * 处理日志事件：编码并入队
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) return;
        try {
            String line = layout.doLayout(eventObject);
            if (line == null) return;
            line = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
            sender.offer(line);
        } catch (Exception e) {
            addError("入队失败", e);
        }
    }

    // region setters for logback config
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public void setObjectKeyPrefix(String objectKeyPrefix) { this.objectKeyPrefix = objectKeyPrefix; }
    public void setAppName(String appName) { this.appName = appName; }
    public void setGzip(boolean gzip) { this.gzip = gzip; }
    public void setDropWhenQueueFull(boolean dropWhenQueueFull) { this.dropWhenQueueFull = dropWhenQueueFull; }
    public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }
    public void setMaxBatchCount(int maxBatchCount) { this.maxBatchCount = maxBatchCount; }
    public void setMaxBatchBytes(int maxBatchBytes) { this.maxBatchBytes = maxBatchBytes; }
    public void setFlushIntervalMillis(long flushIntervalMillis) { this.flushIntervalMillis = flushIntervalMillis; }
    public void setOfferTimeoutMillis(long offerTimeoutMillis) { this.offerTimeoutMillis = offerTimeoutMillis; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public void setInitialBackoffMillis(long initialBackoffMillis) { this.initialBackoffMillis = initialBackoffMillis; }
    public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setLayout(Layout<ILoggingEvent> layout) { this.layout = layout; }
    public void setRegisterShutdownHook(boolean registerShutdownHook) { this.registerShutdownHook = registerShutdownHook; }
    // endregion
}


