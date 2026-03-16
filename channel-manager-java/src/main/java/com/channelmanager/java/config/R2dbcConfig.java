package com.channelmanager.java.config; // 설정 패키지

import org.springframework.context.annotation.Configuration; // Spring 설정 클래스 어노테이션
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing; // R2DBC 감사(Auditing) 활성화

// R2DBC 설정 클래스
// @EnableR2dbcAuditing을 선언하여 @CreatedDate, @LastModifiedDate가 자동으로 동작하게 한다
// 이 설정이 없으면 엔티티의 createdAt, updatedAt 필드에 값이 채워지지 않는다
// JPA에서의 @EnableJpaAuditing과 동일한 역할을 R2DBC 환경에서 수행한다
// Kotlin 모듈의 R2dbcConfig와 동일한 역할을 한다
@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {
    // 설정만 활성화하면 되므로 클래스 본문은 비어있다
    // Kotlin에서는 class R2dbcConfig로 한 줄이지만, Java에서는 중괄호가 필요하다
}
