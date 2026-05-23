package com.stackup.stackup.common.messaging.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    boolean existsById(String messageId);
}
