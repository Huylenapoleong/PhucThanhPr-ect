package vn.phucthanh.audio.shared.storage;

public record StoredObject(
        String provider,
        String bucket,
        String objectKey,
        String etag,
        long sizeBytes
) {
}
