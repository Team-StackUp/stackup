package com.stackup.stackup.session;

import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.session.application.SessionCallbackService;
import com.stackup.stackup.session.application.SessionQueryService;
import com.stackup.stackup.session.application.SessionService;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.MessageSubmitCommand;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SessionGoldenPathIT {

    static final PostgreSQLContainer<?> pg;
    static final RabbitMQContainer mq;

    static {
        pg = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
        mq = new RabbitMQContainer("rabbitmq:3-management");
        pg.start();
        mq.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.rabbitmq.host", mq::getHost);
        r.add("spring.rabbitmq.port", mq::getAmqpPort);
    }

    @Autowired SessionService sessionService;
    @Autowired SessionCallbackService callbackService;
    @Autowired SessionQueryService queryService;
    @Autowired InterviewMessageRepository messageRepo;

    @Autowired UserRepository userRepo;
    @Autowired ResumeRepository resumeRepo;
    @Autowired AnalyzedDocumentRepository documentRepo;

    @Test
    void create_then_first_then_answer_then_followup_then_complete() {
        // fixture: user + analyzed document
        Long userId = givenUser();
        Long docId  = givenAnalyzedDocumentForUser(userId);

        // 1. 세션 생성 → status=READY, max_questions=2
        SessionResult created = sessionService.create(new SessionCreateCommand(
            userId, "IT 테스트 세션", null,
            SessionMode.ONLINE, InterviewType.TECHNICAL,
            JobCategory.BACKEND,
            2,   // maxQuestions = 2 (FOLLOWUP 한 번 후 자동 완료)
            60,
            List.of(docId)));
        assertThat(created.status()).isEqualTo(SessionStatus.READY);
        Long sessionId = created.id();

        // 2. AI 가 FIRST callback 발행한 것을 시뮬레이트
        callbackService.apply(new QuestionsCallbackEnvelope(
            "mid-first", "callback.questions", "v1", "trace-1",
            Instant.now(), "ai-server",
            new QuestionsCallbackPayload(
                sessionId, "FIRST", "PROJECT_DEEP_DIVE",
                "자신의 프로젝트에서 가장 도전적이었던 기술적 문제는 무엇이었나요?",
                null, null, null),
            new MessageContext(userId, sessionId, null, null)));

        // 3. 세션 IN_PROGRESS + 첫 질문 INSERT 검증
        SessionResult afterFirst = queryService.get(sessionId, userId);
        assertThat(afterFirst.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        List<MessageResult> ms1 = queryService.getMessages(sessionId, userId);
        assertThat(ms1).hasSize(1);
        assertThat(ms1.get(0).role()).isEqualTo(MessageRole.INTERVIEWER);

        // 4. 답변 제출
        MessageResult answer1 = sessionService.submitAnswer(new MessageSubmitCommand(
            sessionId, userId, "저는 분산 시스템에서 데이터 일관성 문제를 해결했습니다.", "idem-1"));
        assertThat(answer1.role()).isEqualTo(MessageRole.INTERVIEWEE);

        // 5. 멱등 — 같은 키로 재전송 시 같은 메시지 반환
        MessageResult answer1Dup = sessionService.submitAnswer(new MessageSubmitCommand(
            sessionId, userId, "내 답변 1 (재전송)", "idem-1"));
        assertThat(answer1Dup.id()).isEqualTo(answer1.id());

        // 6. AI 가 FOLLOWUP callback 발행 → max=2 도달 → 자동 COMPLETED
        callbackService.apply(new QuestionsCallbackEnvelope(
            "mid-fu", "callback.questions", "v1", "trace-2",
            Instant.now(), "ai-server",
            new QuestionsCallbackPayload(
                sessionId, "FOLLOWUP", "CS_FUNDAMENTAL",
                "그 문제를 해결하기 위해 어떤 알고리즘을 사용했나요?",
                answer1.id(), null, null),
            new MessageContext(userId, sessionId, null, null)));

        // 7. 세션 COMPLETED + endedAt 설정 검증
        SessionResult complete = queryService.get(sessionId, userId);
        assertThat(complete.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(complete.endedAt()).isNotNull();

        // 8. 메시지 3개: Q1(first), A1(answer), Q2(followup)
        List<MessageResult> ms2 = queryService.getMessages(sessionId, userId);
        assertThat(ms2).hasSize(3);
        assertThat(ms2.get(0).role()).isEqualTo(MessageRole.INTERVIEWER);   // Q1
        assertThat(ms2.get(1).role()).isEqualTo(MessageRole.INTERVIEWEE);   // A1
        assertThat(ms2.get(2).role()).isEqualTo(MessageRole.INTERVIEWER);   // Q2 (followup)
    }

    private Long givenUser() {
        User user = User.createGithubUser(
            System.nanoTime(),       // unique githubId per test run
            "it-user",
            "it-user@example.com",
            null,
            "encrypted-token-stub"
        );
        return userRepo.save(user).getId();
    }

    private Long givenAnalyzedDocumentForUser(Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        Resume resume = resumeRepo.save(
            Resume.create(user, "cv.pdf", "resumes/raw/cv.pdf", ResumeFileType.PDF, 1024L));
        AnalyzedDocument doc = documentRepo.save(AnalyzedDocument.forResume(resume));
        return doc.getId();
    }
}
