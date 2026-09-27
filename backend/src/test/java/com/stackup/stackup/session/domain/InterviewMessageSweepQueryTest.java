package com.stackup.stackup.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.support.PostgresRepositoryTest;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 두 스위퍼의 조회 쿼리를 실제 PostgreSQL 에서 검증한다.
 *
 * <p>이 쿼리들은 <b>멈춘 면접을 푸는 유일한 장치</b>인데 지금까지 아무 테스트도 없었다.
 * 기본 테스트 프로파일은 리포지토리를 목으로 대체하므로 JPQL 이 검증되지 않는다
 * (backend/CLAUDE.md §15.1). 너무 넓게 잡으면 <b>정상 진행 중인 질문·답변을 실패로
 * 되돌리고</b>, 너무 좁게 잡으면 조용히 0건이 되어 스위퍼가 있으나 마나가 된다 —
 * 둘 다 배포 전에는 아무 신호도 주지 않는다.
 *
 * <p>`createdAt` 은 감사 필드라 테스트에서 못 바꾼다. 대신 <b>cutoff 를 움직여</b>
 * `createdAt < :before` 를 양방향으로 확인한다.
 */
@PostgresRepositoryTest
class InterviewMessageSweepQueryTest {

    private static final Instant FUTURE = Instant.now().plusSeconds(600);
    private static final Instant PAST = Instant.now().minusSeconds(600);

    @Autowired UserRepository userRepository;
    @Autowired InterviewSessionRepository sessionRepository;
    @Autowired InterviewMessageRepository messageRepository;

    @Test
    void 멈춘_꼬리질문만_고른다() {
        User user = userRepository.save(User.createGithubUser(98101L, "sweep-followup", null, null, "t"));
        InterviewSession live = startedSession(user);
        InterviewMessage question = messageRepository.save(InterviewMessage.interviewer(live, 1, "질문?"));

        InterviewMessage stuck = messageRepository.save(
            InterviewMessage.followupPlaceholder(live, 2, question));

        // 대조군 1: 콜백이 도착해 확정된 꼬리질문.
        InterviewMessage done = InterviewMessage.followupPlaceholder(live, 3, question);
        done.completeFollowup("진짜 꼬리질문?", false);
        messageRepository.save(done);

        // 대조군 2: 이미 실패로 확정된 꼬리질문(두 번 처리하면 안 된다).
        InterviewMessage alreadyFailed = InterviewMessage.followupPlaceholder(live, 4, question);
        alreadyFailed.failFollowup();
        messageRepository.save(alreadyFailed);

        // 대조군 3: 전사 대기 중인 음성 답변 — 다른 스위퍼의 몫이다.
        messageRepository.save(InterviewMessage.voiceInterviewee(live, 5, question, "idem-a"));

        List<InterviewMessage> found = messageRepository.findStaleFollowupPlaceholders(
            InterviewMessage.FOLLOWUP_GENERATING_TEXT, FUTURE);

        assertThat(found).extracting(InterviewMessage::getId).containsExactly(stuck.getId());
    }

    @Test
    void 멈춘_음성답변만_고른다() {
        User user = userRepository.save(User.createGithubUser(98102L, "sweep-voice", null, null, "t"));
        InterviewSession live = startedSession(user);
        InterviewMessage question = messageRepository.save(InterviewMessage.interviewer(live, 1, "질문?"));

        InterviewMessage stuck = messageRepository.save(
            InterviewMessage.voiceInterviewee(live, 2, question, "idem-b"));

        // 대조군: 전사가 도착한 답변, 그리고 꼬리질문 placeholder(다른 스위퍼의 몫).
        InterviewMessage done = InterviewMessage.voiceInterviewee(live, 3, question, "idem-c");
        done.completeWithTranscript("전사된 답변입니다");
        messageRepository.save(done);
        messageRepository.save(InterviewMessage.followupPlaceholder(live, 4, question));

        List<InterviewMessage> found = messageRepository.findStaleTranscribing(
            InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT, FUTURE);

        assertThat(found).extracting(InterviewMessage::getId).containsExactly(stuck.getId());
    }

    // cutoff 가 실제로 걸리는지. 이게 안 걸리면 방금 만든 placeholder 를 즉시 실패시킨다.
    @Test
    void 임계시각_이전에_만들어진_것만_고른다() {
        User user = userRepository.save(User.createGithubUser(98103L, "sweep-cutoff", null, null, "t"));
        InterviewSession live = startedSession(user);
        InterviewMessage question = messageRepository.save(InterviewMessage.interviewer(live, 1, "질문?"));
        messageRepository.save(InterviewMessage.followupPlaceholder(live, 2, question));
        messageRepository.save(InterviewMessage.voiceInterviewee(live, 3, question, "idem-d"));

        assertThat(messageRepository.findStaleFollowupPlaceholders(
            InterviewMessage.FOLLOWUP_GENERATING_TEXT, PAST)).isEmpty();
        assertThat(messageRepository.findStaleTranscribing(
            InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT, PAST)).isEmpty();
    }

    // 끝난 세션·삭제된 세션에는 손대지 않는다 — 스위퍼가 종료된 면접을 사후 변조하면 안 된다.
    @Test
    void 종료되거나_삭제된_세션은_제외한다() {
        User user = userRepository.save(User.createGithubUser(98104L, "sweep-terminal", null, null, "t"));

        InterviewSession ended = startedSession(user);
        InterviewMessage endedQ = messageRepository.save(InterviewMessage.interviewer(ended, 1, "질문?"));
        messageRepository.save(InterviewMessage.followupPlaceholder(ended, 2, endedQ));
        messageRepository.save(InterviewMessage.voiceInterviewee(ended, 3, endedQ, "idem-e"));
        ended.end();
        sessionRepository.save(ended);

        InterviewSession removed = startedSession(user);
        InterviewMessage removedQ = messageRepository.save(InterviewMessage.interviewer(removed, 1, "질문?"));
        messageRepository.save(InterviewMessage.followupPlaceholder(removed, 2, removedQ));
        messageRepository.save(InterviewMessage.voiceInterviewee(removed, 3, removedQ, "idem-f"));
        removed.markDeleted();
        sessionRepository.save(removed);

        assertThat(messageRepository.findStaleFollowupPlaceholders(
            InterviewMessage.FOLLOWUP_GENERATING_TEXT, FUTURE)).isEmpty();
        assertThat(messageRepository.findStaleTranscribing(
            InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT, FUTURE)).isEmpty();
    }

    private InterviewSession startedSession(User user) {
        InterviewSession s = InterviewSession.create(
            user, "면접", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null);
        s.start();
        return sessionRepository.save(s);
    }
}
