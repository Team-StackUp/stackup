package com.stackup.stackup.resume.domain;

public enum ResumeFileType {
    PDF,
    // 웹 이력서(포트폴리오·블로그·노션 URL). S3 오브젝트 없이 source_url 만 갖는다.
    WEB
}
