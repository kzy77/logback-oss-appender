package io.github.osslogback.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * 异步批处理发送器：
 * - 接收编码后的日志字符串，放入内存队列（不落盘）
 * - 后台线程根据条数/字节数/时间窗口进行聚合，调用 Uploader 上传
 * - 支持无损模式（生产侧阻塞等待队列可用）与高吞吐模式（队列满时丢弃并计数）
 */
public final class AsyncBatchSender implements AutoCloseable {

    /**
     * 上传器接口，便于替换不同云厂商实现。
     */
    public interface Uploader {
        /**
         * 执行上传。
         * @param objectKey 目标对象Key（例如 oss 路径）
         * @param contentBytes 内容字节
         * @param contentType 媒体类型
         * @param contentEncoding 内容编码（例如 gzip），可为 null
         * @throws Exception 上传失败抛出异常
         */
        void upload(String objectKey, byte[] contentBytes, String contentType, String contentEncoding) throws Exception;
    }

    public static final class Config {
        /** 队列最大长度（条） */
        public int maxQueueSize = 100_000;
        /** 单批最大条数 */
        public int maxBatchCount = 5_000;
        /** 单批最大字节（压缩前） */
        public int maxBatchBytes = 4 * 1024 * 1024;
        /** 强制刷新间隔（毫秒） */
        public long flushIntervalMillis = 2000L;
        /** 生产侧阻塞等待（无损），超时时间（毫秒），<=0 表示一直等待 */
        public long offerTimeoutMillis = 500L;
        /** 队列满是否丢弃（false 表示阻塞直到可用，推荐保证不丢日志） */
        public boolean dropWhenQueueFull = false;
        /** 启用 gzip 压缩 */
        public boolean gzip = true;
        /** 对象Key前缀 */
        public String objectKeyPrefix = "logs/";
        /** 内容类型 */
        public String contentType = "application/x-ndjson";
        /** 失败重试次数 */
        public int maxRetries = 5;
        /** 初始退避毫秒 */
        public long initialBackoffMillis = 200L;
        /** 退避倍率 */
        public double backoffMultiplier = 2.0;
        /** 应用标识（生成object key使用） */
        public String appName = "app";
    }

    private final Config config;
    private final BlockingQueue<String> queue;
    private final Uploader uploader;
    private final Thread workerThread;
    private volatile boolean running = true;

    // metrics
    private volatile long droppedCount = 0;
    private volatile long sentBatches = 0;
    private volatile long sentRecords = 0;
    private volatile long lastErrorTime = 0;
    private volatile String lastErrorMessage = null;

    /**
     * 构造一个异步批处理发送器。
     * @param config 配置
     * @param uploader 上传实现
     */
    public AsyncBatchSender(Config config, Uploader uploader) {
        this.config = Objects.requireNonNull(config, "config");
        this.uploader = Objects.requireNonNull(uploader, "uploader");
        this.queue = new ArrayBlockingQueue<>(Math.max(1024, config.maxQueueSize));
        this.workerThread = new Thread(this::runWorker, "oss-logback-sender");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /**
     * 提交一条日志（已编码字符串，末尾不必包含换行）。
     * @param line 日志行
     */
    public void offer(String line) {
        if (!running) {
            return;
        }
        boolean offered;
        try {
            if (config.dropWhenQueueFull) {
                offered = queue.offer(line);
                if (!offered) {
                    droppedCount++;
                }
            } else {
                long timeout = config.offerTimeoutMillis;
                if (timeout <= 0) {
                    queue.put(line);
                    offered = true;
                } else {
                    offered = queue.offer(line, timeout, TimeUnit.MILLISECONDS);
                    if (!offered) {
                        // 仍未成功则阻塞等待，确保不丢
                        queue.put(line);
                        offered = true;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            droppedCount++;
        }
    }

    private void runWorker() {
        List<String> batch = new ArrayList<>(config.maxBatchCount);
        long lastFlush = System.currentTimeMillis();
        while (running || !queue.isEmpty()) {
            try {
                String first = queue.poll(200, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();
                if (first != null) {
                    batch.add(first);
                }
                // drain more with limits
                while (batch.size() < config.maxBatchCount && !queue.isEmpty()) {
                    String v = queue.peek();
                    if (v == null) {
                        break;
                    }
                    int predictedBytes = predictedBatchBytes(batch, v);
                    if (predictedBytes > config.maxBatchBytes) {
                        break;
                    }
                    batch.add(queue.poll());
                }

                boolean timeExceeded = (now - lastFlush) >= config.flushIntervalMillis;
                boolean sizeExceeded = batch.size() >= config.maxBatchCount;
                boolean bytesExceeded = batchBytes(batch) >= config.maxBatchBytes;

                if (!batch.isEmpty() && (timeExceeded || sizeExceeded || bytesExceeded)) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlush = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                lastErrorTime = System.currentTimeMillis();
                lastErrorMessage = e.getMessage();
            }
        }

        // final drain
        if (!batch.isEmpty()) {
            try {
                flushBatch(batch);
            } catch (Exception ignore) {
                // 最后阶段忽略
            }
        }
    }

    private int predictedBatchBytes(List<String> current, String next) {
        return batchBytes(current) + (next == null ? 0 : next.getBytes(StandardCharsets.UTF_8).length) + 1;
    }

    private int batchBytes(List<String> batch) {
        int total = 0;
        for (String s : batch) {
            total += s.getBytes(StandardCharsets.UTF_8).length + 1; // +\n
        }
        return total;
    }

    private void flushBatch(List<String> batch) throws Exception {
        if (batch.isEmpty()) {
            return;
        }
        byte[] raw = joinWithNewlines(batch);
        byte[] toSend;
        String contentEncoding = null;
        if (config.gzip) {
            toSend = gzip(raw);
            contentEncoding = "gzip";
        } else {
            toSend = raw;
        }
        String key = buildObjectKey();
        retryUpload(key, toSend, config.contentType, contentEncoding);
        sentBatches++;
        sentRecords += batch.size();
    }

    private String buildObjectKey() {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(java.time.ZoneOffset.UTC);
        String date = fmt.format(Instant.now());
        String uuid = UUID.randomUUID().toString();
        return config.objectKeyPrefix + config.appName + "/" + date + "/" + uuid + ".jsonl" + (config.gzip ? ".gz" : "");
    }

    private void retryUpload(String key, byte[] content, String contentType, String contentEncoding) throws Exception {
        int attempts = 0;
        long backoff = config.initialBackoffMillis;
        while (true) {
            try {
                uploader.upload(key, content, contentType, contentEncoding);
                return;
            } catch (Exception ex) {
                attempts++;
                if (attempts > config.maxRetries) {
                    lastErrorTime = System.currentTimeMillis();
                    lastErrorMessage = ex.getMessage();
                    throw ex;
                }
                Thread.sleep(Math.max(50, backoff));
                backoff = (long) Math.min(30_000L, backoff * config.backoffMultiplier);
            }
        }
    }

    private byte[] joinWithNewlines(List<String> batch) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(batchBytes(batch));
        try {
            for (int i = 0; i < batch.size(); i++) {
                baos.write(batch.get(i).getBytes(StandardCharsets.UTF_8));
                baos.write('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    private byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(256, data.length / 2));
            GZIPOutputStream gzip = new GZIPOutputStream(baos, true);
            gzip.write(data);
            gzip.finish();
            gzip.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 停止发送器，等待队列发送完成。
     */
    @Override
    public void close() {
        running = false;
        try {
            workerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 获取累计丢弃条数（仅在允许丢弃时可能增加） */
    public long getDroppedCount() {
        return droppedCount;
    }
    /** 获取累计发送批次数 */
    public long getSentBatches() {
        return sentBatches;
    }
    /** 获取累计发送记录数 */
    public long getSentRecords() {
        return sentRecords;
    }
    /** 最近错误时间戳（毫秒） */
    public long getLastErrorTime() {
        return lastErrorTime;
    }
    /** 最近错误信息 */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
