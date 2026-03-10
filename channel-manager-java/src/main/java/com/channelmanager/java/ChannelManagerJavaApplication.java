package com.channelmanager.java; // Java 모듈의 루트 패키지

import org.springframework.boot.SpringApplication; // Spring Boot 애플리케이션을 시작하는 클래스
import org.springframework.boot.autoconfigure.SpringBootApplication; // Spring Boot 자동 설정 어노테이션

// @SpringBootApplication은 아래 3개 어노테이션의 조합이다:
// - @Configuration: 이 클래스가 Spring 설정 클래스임을 선언
// - @EnableAutoConfiguration: 클래스패스의 라이브러리를 기반으로 자동 설정 활성화
// - @ComponentScan: 이 패키지 하위의 @Component, @Service 등을 자동 스캔
@SpringBootApplication
public class ChannelManagerJavaApplication { // Spring Boot 진입점 클래스

  // 애플리케이션의 메인 메서드 - JVM이 이 메서드를 실행하여 Spring Boot를 시작한다
  public static void main(String[] args) {
    SpringApplication.run(ChannelManagerJavaApplication.class, args); // Spring Boot 애플리케이션 시작
  }
}
