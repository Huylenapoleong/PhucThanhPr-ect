package vn.phucthanh.audio.shared.storage;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStoragePort {

    StoredObject upload(
            String bucket,
            String objectKey,
            String contentType,
            long contentLength,
            InputStream content
    );

    void delete(String bucket, String objectKey);

    String createSignedUrl(String bucket, String objectKey, Duration validity);
}
