package com.stackup.stackup.common.storage;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link ObjectPurgeEvent} 를 받아 스토리지 객체를 실제로 지운다.
 *
 * <p>AFTER_COMMIT 인 이유: 삭제 트랜잭션이 롤백됐는데 객체를 먼저 지우면 DB 는 살아 있고
 * 내용물만 사라진 상태가 된다 — 되돌릴 수 없다.
 *
 * <p>실패해도 사용자의 삭제 요청을 실패시키지 않는다. 이 시점엔 이미 커밋돼 행이 soft delete
 * 상태라 어차피 도달 불가이고, 여기서 예외를 던져봐야 삭제를 되돌릴 수도 없다. 대신 키를
 * ERROR 로 남겨 수동 회수가 가능하게 한다 — 조용히 삼키면 파기됐다고 착각하게 된다.
 */
@Component
@RequiredArgsConstructor
public class ObjectPurgeListener {

    private static final Logger log = LoggerFactory.getLogger(ObjectPurgeListener.class);

    private final ObjectStorageClient storage;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ObjectPurgeEvent event) {
        for (String key : event.keys()) {
            try {
                storage.delete(key);
                log.info("storage object purged. key={}", key);
            } catch (Exception e) {
                // 키를 남긴다 — 이 로그가 없으면 어떤 객체가 남았는지 알 방법이 없다.
                log.error("storage object purge failed — 수동 회수 필요. key={}", key, e);
            }
        }
    }
}
