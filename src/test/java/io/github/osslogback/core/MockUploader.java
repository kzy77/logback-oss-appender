package io.github.osslogback.core;
import io.github.osslogback.core.AsyncBatchSender.Uploader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * 测试用上传器：记录所有上传调用，不进行网络请求。
 */
public final class MockUploader implements Uploader {
    public static final class UploadRecord {
        public final String key;
        public final byte[] content;
        public final String contentType;
        public final String contentEncoding;
        public UploadRecord(String key, byte[] content, String contentType, String contentEncoding) {
            this.key = key;
            this.content = content;
            this.contentType = contentType;
            this.contentEncoding = contentEncoding;
        }
    }
    private final List<UploadRecord> records = Collections.synchronizedList(new ArrayList<>());
    /**
     * 记录一次上传调用。
     */
    @Override
    public void upload(String objectKey, byte[] contentBytes, String contentType, String contentEncoding) {
        records.add(new UploadRecord(objectKey, contentBytes, contentType, contentEncoding));
    }
    /**
     * 获取已记录的上传调用列表。
     */
    public List<UploadRecord> getRecords() {
        return records;
    }
}
