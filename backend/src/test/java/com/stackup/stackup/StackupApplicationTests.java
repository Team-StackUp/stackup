package com.stackup.stackup;

import com.stackup.stackup.auth.domain.OAuthStateRepository;
import com.stackup.stackup.auth.domain.RefreshTokenRepository;
import com.stackup.stackup.user.domain.UserRepository;
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

	@Test
	void contextLoads() {
	}

}
