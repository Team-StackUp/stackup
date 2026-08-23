package com.stackup.stackup.common.storage;

import java.util.List;

/**
 * 스토리지 객체 즉시 파기 요청.
 *
 * <p>자료를 지우면 DB 행은 soft delete 로 남지만(세션 컨텍스트가 FK 로 참조한다) 내용물은
 * 남길 이유가 없다. 이력서 원본 PDF 에는 이름·연락처·주소가 들어 있고
 * (`docs/security.md §5.2`), 분석 마크다운은 그걸 재구성한 문서다.
 *
 * <p>발행은 도메인 트랜잭션 안에서, 실제 삭제는 {@link ObjectPurgeListener} 가 커밋 이후에
 * 한다 — 롤백된 삭제로 객체를 날리면 복구할 방법이 없다.
 */
public record ObjectPurgeEvent(List<String> keys) {

    public ObjectPurgeEvent {
        keys = keys == null ? List.of() : keys.stream().filter(k -> k != null && !k.isBlank()).toList();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }
}
