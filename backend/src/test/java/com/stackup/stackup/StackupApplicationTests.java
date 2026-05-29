package com.stackup.stackup;

import com.stackup.stackup.auth.domain.OAuthStateRepository;
import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.github.domain.GithubRepositoryRepository;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageVoiceAnalysisRepository;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.user.domain.UserRepository;
import com.stackup.stackup.user.domain.consent.UserConsentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class StackupApplicationTests {

	@MockitoBean
	private OAuthStateRepository oauthStateRepository;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private ResumeRepository resumeRepository;

	@MockitoBean
	private GithubRepositoryRepository githubRepositoryRepository;

	@MockitoBean
	private AnalyzedDocumentRepository analyzedDocumentRepository;

	@MockitoBean
	private ProcessedMessageRepository processedMessageRepository;

	@MockitoBean
	private DocumentEmbeddingRepository documentEmbeddingRepository;

	@MockitoBean
	private UserConsentRepository userConsentRepository;

	@MockitoBean
	private InterviewSessionRepository interviewSessionRepository;

	@MockitoBean
	private InterviewMessageRepository interviewMessageRepository;

	@MockitoBean
	private SessionContextRepository sessionContextRepository;

	@MockitoBean
	private SessionFeedbackRepository sessionFeedbackRepository;

	@MockitoBean
	private MessageVoiceAnalysisRepository messageVoiceAnalysisRepository;

	@Test
	void contextLoads() {
	}

}
