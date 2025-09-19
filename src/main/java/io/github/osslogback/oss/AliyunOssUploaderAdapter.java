package io.github.osslogback.oss;
import io.github.ossappender.oss.AliyunOssUploader;
import io.github.osslogback.core.AsyncBatchSender;
import io.github.ossappender.core.UploadHooks;

/**
 * 适配器：将通用库的 AliyunOssUploader 适配为 AsyncBatchSender.Uploader。
 */
public final class AliyunOssUploaderAdapter implements AsyncBatchSender.Uploader, AutoCloseable {
    private final AliyunOssUploader delegate;
    /**
     * 构造适配器。
     * @param endpoint OSS Endpoint
     * @param accessKeyId AK
     * @param accessKeySecret SK
     * @param bucket 目标桶
     */
    public AliyunOssUploaderAdapter(String endpoint, String accessKeyId, String accessKeySecret, String bucket) {
        this.delegate = new AliyunOssUploader(endpoint, accessKeyId, accessKeySecret, bucket, "logs", 5, 500, 10000, UploadHooks.noop());
    }
    /**
     * 委托上传实现。
     */
    @Override
    public void upload(String objectKey, byte[] contentBytes, String contentType, String contentEncoding) throws Exception {
        delegate.upload(objectKey, contentBytes, contentType, contentEncoding);
    }
    /**
     * 关闭底层客户端。
     */
    @Override
    public void close() {
        delegate.close();
    }
}
