package vn.phucthanh.audio.shared.event;

public interface OutboxPublisher {

    void publish(String aggregateType, Object aggregateId, String eventType, Object payload);
}
