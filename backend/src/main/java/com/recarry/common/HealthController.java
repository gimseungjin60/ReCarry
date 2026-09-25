package com.recarry.common;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 호스팅의 health check 용. 인증 없이 열려 있고, DB 에 "select 1" 만 보낸다.
 * 내부 정보(버전 · 오류 원문 · 접속 정보)는 응답에 넣지 않는다.
 * ponytail: Actuator 대신 엔드포인트 하나. 지표·readiness 분리가 필요해지면 spring-boot-starter-actuator 로 옮긴다.
 */
@RestController
public class HealthController {

	private final JdbcTemplate jdbc;

	public HealthController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/api/health")
	public ResponseEntity<Map<String, String>> health() {
		try {
			jdbc.queryForObject("select 1", Integer.class);
			return ResponseEntity.ok(Map.of("status", "UP"));
		} catch (RuntimeException e) {
			return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
		}
	}
}
