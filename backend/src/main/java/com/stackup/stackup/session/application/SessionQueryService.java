package com.stackup.stackup.session.application;

import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionQueryService {
    private final InterviewSessionRepository sessionRepo;
    private final InterviewMessageRepository messageRepo;
}
