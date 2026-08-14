package com.stackup.stackup.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.stackup.stackup.auth.domain.OAuthStateRepository;
import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.coverletter.domain.CoverLetterRepository;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.github.domain.GithubRepositoryRepository;
import com.stackup.stackup.log.ai.domain.AiRequestLogRepository;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageVoiceAnalysisRepository;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import com.stackup.stackup.user.domain.UserRepository;
import com.stackup.stackup.user.domain.consent.UserConsentRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;

/**
 * 로그인 시작 엔드포인트가 인증 없이 닿는지 검증한다.
 *
 * <p>SecurityConfig 의 permit 목록이 경로를 하나씩 나열하는 방식이라, provider 를 추가하면서
 * 여기에 넣는 걸 잊기 쉽다. 실제로 Google 로그인을 붙일 때 이 누락으로 "로그인하려면 로그인이
 * 필요한" 상태가 배포까지 나갔다. 컨트롤러 단위 테스트는 필터 체인을 타지 않아 잡지 못한다.
 */
@SpringBootTest
class AuthEndpointsPermitAllTest {

  @Autowired private WebApplicationContext webApplicationContext;
  // spring-security-test 없이 필터 체인을 직접 붙인다.
  @Autowired private FilterChainProxy springSecurityFilterChain;

  @MockitoBean private OAuthStateRepository oauthStateRepository;
  @MockitoBean private RefreshTokenRepository refreshTokenRepository;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private ResumeRepository resumeRepository;
  @MockitoBean private CoverLetterRepository coverLetterRepository;
  @MockitoBean private GithubRepositoryRepository githubRepositoryRepository;
  @MockitoBean private AnalyzedDocumentRepository analyzedDocumentRepository;
  @MockitoBean private ProcessedMessageRepository processedMessageRepository;
  @MockitoBean private DocumentEmbeddingRepository documentEmbeddingRepository;
  @MockitoBean private UserConsentRepository userConsentRepository;
  @MockitoBean private InterviewSessionRepository interviewSessionRepository;
  @MockitoBean private InterviewMessageRepository interviewMessageRepository;
  @MockitoBean private SessionContextRepository sessionContextRepository;
  @MockitoBean private SessionFeedbackRepository sessionFeedbackRepository;
  @MockitoBean private SessionQuestionPoolRepository sessionQuestionPoolRepository;
  @MockitoBean private MessageVoiceAnalysisRepository messageVoiceAnalysisRepository;
  @MockitoBean private AiRequestLogRepository aiRequestLogRepository;

  @Test
  void loginStartEndpoints_areReachableWithoutAuthentication() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .addFilters(springSecurityFilterChain)
        .build();

    for (String path : new String[] {"/api/auth/github", "/api/auth/google"}) {
      MvcResult result = mockMvc.perform(post(path)).andReturn();
      String body = result.getResponse().getContentAsString();

      // 필터가 막으면 AUTH_INVALID_TOKEN 이 돌아온다. 컨트롤러까지 닿기만 하면
      // (설정 미비로 실패하더라도) 다른 코드가 나온다.
      assertThat(body)
          .as("%s 는 인증 없이 컨트롤러까지 닿아야 한다", path)
          .doesNotContain("AUTH_INVALID_TOKEN");
    }
  }
}
