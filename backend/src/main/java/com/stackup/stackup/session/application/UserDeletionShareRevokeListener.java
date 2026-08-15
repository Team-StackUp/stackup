package com.stackup.stackup.session.application;

import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.user.application.event.UserDeletedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * user 도메인 UserDeletedEvent 수신 → 해당 사용자의 모든 피드백 공유를 해제한다.
 *
 * 회원 탈퇴는 User row 만 soft delete 한다(세션·피드백은 남는다 — 데이터 보존 정책은
 * 별도 결정 사항). 문제는 공유 링크 조회(PublicFeedbackController.getByToken)가
 * feedback.deleted / session.deleted 만 보고 User.deleted 는 보지 않는다는 것 —
 * 탈퇴해도 이미 발급된 공유 링크는 계속 살아 있었다. "탈퇴했는데 공유했던 면접
 * 피드백을 아무나 볼 수 있다"는 최소한 막아야 하는 결함이라, 탈퇴 시점에 명시적으로
 * 끊는다(UserDeletionRevokeListener 의 refresh token revoke 와 같은 패턴).
 */
@Component
@RequiredArgsConstructor
public class UserDeletionShareRevokeListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionShareRevokeListener.class);

    private final SessionFeedbackRepository feedbackRepository;

    @EventListener
    @Transactional
    public void on(UserDeletedEvent event) {
        List<SessionFeedback> shared = feedbackRepository.findSharedByOwner(event.userId());
        shared.forEach(SessionFeedback::disableShare);
        if (!shared.isEmpty()) {
            log.info("feedback shares revoked on user delete. userId={}, count={}",
                event.userId(), shared.size());
        }
    }
}
