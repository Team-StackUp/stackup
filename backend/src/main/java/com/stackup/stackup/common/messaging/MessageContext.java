package com.stackup.stackup.common.messaging;

public record MessageContext(
    Long userId,
    Long sessionId,
    Long documentId,
    Long repositoryId
) {
    public static MessageContext empty() {
        return new MessageContext(null, null, null, null);
    }

    public static MessageContext ofUser(Long userId) {
        return new MessageContext(userId, null, null, null);
    }

    public MessageContext withSession(Long newSessionId) {
        return new MessageContext(userId, newSessionId, documentId, repositoryId);
    }

    public MessageContext withDocument(Long newDocumentId) {
        return new MessageContext(userId, sessionId, newDocumentId, repositoryId);
    }

    public MessageContext withRepository(Long newRepositoryId) {
        return new MessageContext(userId, sessionId, documentId, newRepositoryId);
    }
}
