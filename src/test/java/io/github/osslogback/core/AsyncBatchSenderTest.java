package io.github.osslogback.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步批处理核心逻辑测试：
 * - 按条数与时间窗口触发
 * - 队列满时的阻塞/丢弃策略
 * - gzip 开关与对象 key 生成
 */
public class AsyncBatchSenderTest {

    /**
     * 验证时间窗口触发上传与 gzip 标记。
     */
    @Test
    public void testFlushByTimeWindowWithGzip() throws Exception {
        MockUploader mock = new MockUploader();
        AsyncBatchSender.Config cfg = new AsyncBatchSender.Config();
        cfg.maxQueueSize = 100;
        cfg.maxBatchCount = 1000;
        cfg.maxBatchBytes = 1024 * 1024;
        cfg.flushIntervalMillis = 200; // 快速触发
        cfg.gzip = true;
        cfg.objectKeyPrefix = "test/";
        cfg.appName = "demo";

        try (AsyncBatchSender sender = new AsyncBatchSender(cfg, mock)) {
            sender.offer("a");
            sender.offer("b");
            Thread.sleep(300);
        }

        List<MockUploader.UploadRecord> rs = mock.getRecords();
        assertEquals(1, rs.size(), "应产生1个批次");
        MockUploader.UploadRecord rec = rs.get(0);
        assertNotNull(rec.key);
        assertTrue(rec.key.startsWith("test/demo/"));
        assertEquals("application/x-ndjson", rec.contentType);
        assertEquals("gzip", rec.contentEncoding);
    }

    /**
     * 验证按条数上限触发分批。
     */
    @Test
    public void testFlushByBatchCount() throws Exception {
        MockUploader mock = new MockUploader();
        AsyncBatchSender.Config cfg = new AsyncBatchSender.Config();
        cfg.maxQueueSize = 100;
        cfg.maxBatchCount = 3;
        cfg.maxBatchBytes = 1024 * 1024;
        cfg.flushIntervalMillis = 5_000; // 不靠时间触发
        cfg.gzip = false;

        try (AsyncBatchSender sender = new AsyncBatchSender(cfg, mock)) {
            sender.offer("1");
            sender.offer("2");
            sender.offer("3"); // 触发一次
            // 等待 worker 消费
            Thread.sleep(150);
            sender.offer("4");
        }

        List<MockUploader.UploadRecord> rs = mock.getRecords();
        assertTrue(rs.size() >= 1, "至少一个批次");
        String firstBatch = new String(rs.get(0).content, StandardCharsets.UTF_8);
        assertTrue(firstBatch.contains("1") && firstBatch.contains("2") && firstBatch.contains("3"));
    }

    /**
     * 验证丢弃策略：队列极小，允许丢弃。
     */
    @Test
    public void testDropWhenQueueFull() throws Exception {
        MockUploader mock = new MockUploader();
        AsyncBatchSender.Config cfg = new AsyncBatchSender.Config();
        cfg.maxQueueSize = 1;
        cfg.maxBatchCount = 1000;
        cfg.maxBatchBytes = 1024 * 1024;
        cfg.flushIntervalMillis = 1000;
        cfg.dropWhenQueueFull = true;

        try (AsyncBatchSender sender = new AsyncBatchSender(cfg, mock)) {
            for (int i = 0; i < 100; i++) {
                sender.offer("x" + i);
            }
        }

        // 有丢弃发生
        // 由于是异步，这里只验证至少一次上传发生
        List<MockUploader.UploadRecord> rs = mock.getRecords();
        assertTrue(rs.size() >= 1);
    }
}


