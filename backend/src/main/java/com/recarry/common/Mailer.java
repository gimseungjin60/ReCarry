package com.recarry.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * 거래 메일(비밀번호 재설정 코드) 발송 — Brevo HTTPS API.
 * Render 무료 인스턴스는 SMTP 포트(25·465·587)가 막혀 있어 SMTP 대신 HTTPS 로 보낸다.
 * API 키가 없으면: local 프로필(logOnly)은 서버 로그에 남기고, 그 밖에는 보내지 않고 경고만 남긴다.
 * ponytail: 업체 하나에 묶인 한 클래스 — 업체를 바꾸면 send() 본문만 바꾼다.
 */
@Component
public class Mailer {

	private static final Logger log = LoggerFactory.getLogger(Mailer.class);
	private static final URI API = URI.create("https://api.brevo.com/v3/smtp/email");

	private final AppProperties.Mail cfg;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final ObjectMapper json = new ObjectMapper();

	public Mailer(AppProperties props) {
		this.cfg = props.mail();
	}

	/** @return 보냈으면(또는 로컬 로그로 대신했으면) true */
	public boolean send(String to, String subject, String text) {
		if (!cfg.configured()) {
			if (cfg.logOnly()) {
				log.info("[local mail] to={} subject={}\n{}", to, subject, text);
				return true;
			}
			log.warn("mail not sent: MAIL_API_KEY / MAIL_FROM are not configured");
			return false;
		}
		try {
			String body = json.writeValueAsString(Map.of(
				"sender", Map.of("name", cfg.fromName(), "email", cfg.from()),
				"to", List.of(Map.of("email", to)),
				"subject", subject,
				"textContent", text));
			HttpRequest req = HttpRequest.newBuilder(API).timeout(Duration.ofSeconds(10))
				.header("api-key", cfg.apiKey())
				.header("content-type", "application/json")
				.header("accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() / 100 == 2) return true;
			// 응답 본문에는 받는 사람 주소가 들어갈 수 있어 상태 코드만 남긴다
			log.warn("mail API rejected the message: status={}", res.statusCode());
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			log.warn("mail API call failed: {}", e.getClass().getSimpleName());
			return false;
		}
	}
}
