package com.stackup.stackup.common.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackup.stackup.auth.domain.OAuthStateRepository;
import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
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
import com.stackup.stackup.user.domain.UserRepository;
import com.stackup.stackup.user.domain.consent.UserConsentRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * OpenAPI 스펙을 정적 파일(backend/openapi.json)로 산출하는 테스트.
 *
 * <p>프론트엔드는 이 커밋된 파일에서 openapi-typescript 로 타입을 생성하므로, 로컬 백엔드 기동 없이도 타입 안정성을 얻는다. 백엔드
 * 계약을 바꾸면 이 테스트를 다시 실행해 openapi.json 을 갱신·커밋한다. (CI 에서 "커밋된 스펙이 최신인지" 검증하는 가드로 확장
 * 가능 — 생성 결과와 커밋본을 비교.)
 *
 * <p>test 프로파일은 application-test.yml 의 더미 secret 으로 전체 컨텍스트를 로드하고, 모든 repository 는
 * {@link StackupApplicationTests} 와 동일하게 mock 처리해 실 DB 없이 springdoc 의 매핑·스키마만으로 스펙을
 * 렌더한다.
 */
@SpringBootTest
class OpenApiSpecExportTest {

  private static final Path OUTPUT = Path.of("openapi.json");

  @Autowired private WebApplicationContext webApplicationContext;

  @MockitoBean private OAuthStateRepository oauthStateRepository;
  @MockitoBean private RefreshTokenRepository refreshTokenRepository;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private ResumeRepository resumeRepository;
  @MockitoBean private GithubRepositoryRepository githubRepositoryRepository;
  @MockitoBean private AnalyzedDocumentRepository analyzedDocumentRepository;
  @MockitoBean private ProcessedMessageRepository processedMessageRepository;
  @MockitoBean private DocumentEmbeddingRepository documentEmbeddingRepository;
  @MockitoBean private UserConsentRepository userConsentRepository;
  @MockitoBean private InterviewSessionRepository interviewSessionRepository;
  @MockitoBean private InterviewMessageRepository interviewMessageRepository;
  @MockitoBean private SessionContextRepository sessionContextRepository;
  @MockitoBean private SessionFeedbackRepository sessionFeedbackRepository;
  @MockitoBean private MessageVoiceAnalysisRepository messageVoiceAnalysisRepository;
  @MockitoBean private AiRequestLogRepository aiRequestLogRepository;

  @Test
  void exportOpenApiSpec() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    MvcResult result =
        mockMvc.perform(get("/api/v3/api-docs")).andExpect(status().isOk()).andReturn();

    byte[] raw = result.getResponse().getContentAsByteArray();
    ObjectMapper objectMapper = new ObjectMapper();
    Object tree = objectMapper.readValue(raw, Object.class);
    String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);

    Files.writeString(OUTPUT, pretty + System.lineSeparator(), StandardCharsets.UTF_8);
  }
}
