package com.recarry;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 HTTP → Spring Security → Service → PostgreSQL 전체 경로를 검증한다.
 * 테스트끼리 간섭하지 않도록 각 테스트가 서로 다른 날짜 구간(window)을 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestDatabaseConfig.class, ApiIntegrationTest.MailCapture.class })
class ApiIntegrationTest {

	static final String ADMIN_EMAIL = "admin-" + UUID.randomUUID() + "@test.recarry";
	static final String ADMIN_PASSWORD = "Adm1n" + UUID.randomUUID();
	static final AtomicInteger WINDOW = new AtomicInteger();
	/** 테스트마다 새로 만드는 서명 키 (만료 토큰을 직접 만들 때도 쓴다) */
	static final String JWT_SECRET;
	static {
		byte[] key = new byte[48];
		new SecureRandom().nextBytes(key);
		JWT_SECRET = Base64.getEncoder().encodeToString(key);
	}

	/** 비밀 값은 테스트마다 새로 만든다 (저장소에 남지 않는다). */
	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("recarry.jwt.secret", () -> JWT_SECRET);
		r.add("recarry.admin.email", () -> ADMIN_EMAIL);
		r.add("recarry.admin.password", () -> ADMIN_PASSWORD);
		// 만료 정책 시행일 이전으로 되돌린 created_at 도 대상이 되게 한다
		r.add("recarry.booking.request-expiry-from", () -> "2000-01-01");
		// 메일은 실제로 보내지 않고 CapturingMailer 가 받는다
		r.add("recarry.mail.log-only", () -> "true");
		r.add("recarry.mail.require-verified-email", () -> "true");
		// 카카오 로그인은 가짜 카카오 서버(FAKE_KAKAO)로
		r.add("recarry.kakao.client-id", () -> "test-client");
		r.add("recarry.kakao.client-secret", () -> "test-secret");
		r.add("recarry.kakao.auth-base", () -> "http://localhost:" + FAKE_KAKAO.getAddress().getPort());
		r.add("recarry.kakao.api-base", () -> "http://localhost:" + FAKE_KAKAO.getAddress().getPort());
	}

	/** 인가 코드 → 카카오 회원 정보(JSON). 토큰은 코드를 그대로 돌려주고, /v2/user/me 는 그 토큰의 회원 정보를 준다 */
	static final java.util.Map<String, String> KAKAO_USERS = new java.util.concurrent.ConcurrentHashMap<>();
	static final com.sun.net.httpserver.HttpServer FAKE_KAKAO;
	static {
		try {
			FAKE_KAKAO = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		FAKE_KAKAO.createContext("/oauth/token", ex -> {
			String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			var code = java.util.regex.Pattern.compile("code=([^&]+)").matcher(form);
			boolean ok = form.contains("client_secret=test-secret") && code.find() && KAKAO_USERS.containsKey(code.group(1));
			reply(ex, ok ? 200 : 400, ok ? "{\"access_token\":\"" + code.group(1) + "\"}" : "{}");
		});
		FAKE_KAKAO.createContext("/v2/user/me", ex -> {
			String auth = ex.getRequestHeaders().getFirst("Authorization");
			String user = auth == null ? null : KAKAO_USERS.get(auth.replace("Bearer ", ""));
			reply(ex, user == null ? 401 : 200, user == null ? "{}" : user);
		});
		FAKE_KAKAO.start();
	}

	static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(status, b.length);
		ex.getResponseBody().write(b);
		ex.close();
	}

	/** 보낸 메일을 기억하는 Mailer — 비밀번호 재설정 코드를 테스트가 읽는다 */
	@org.springframework.boot.test.context.TestConfiguration
	static class MailCapture {
		@org.springframework.context.annotation.Bean
		@org.springframework.context.annotation.Primary
		CapturingMailer capturingMailer(com.recarry.common.AppProperties props) {
			return new CapturingMailer(props);
		}
	}

	static class CapturingMailer extends com.recarry.common.Mailer {
		final java.util.Map<String, String> lastBody = new java.util.concurrent.ConcurrentHashMap<>();

		CapturingMailer(com.recarry.common.AppProperties props) {
			super(props);
		}

		@Override
		public boolean send(String to, String subject, String text) {
			lastBody.put(to, text);
			return true;
		}
	}

	@Autowired
	CapturingMailer mails;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	final HttpClient http = HttpClient.newHttpClient();
	final ObjectMapper json = new ObjectMapper();

	// ------------------------------------------------------------------ helpers

	static final String COOKIE = "recarry_session";

	record Res(int status, JsonNode body, List<String> setCookies) {
		String code() { return body.path("code").asString(); }

		/** Set-Cookie 에서 세션 토큰 값 */
		String session() {
			return setCookies.stream().filter(c -> c.startsWith(COOKIE + "=")).findFirst()
				.map(c -> c.substring(COOKIE.length() + 1, c.indexOf(';'))).orElse(null);
		}
	}

	/** 브라우저처럼: 세션은 쿠키로, 상태 변경 요청에는 프론트 전용 헤더를 붙인다 */
	Res call(String method, String path, String session, Object body) throws Exception {
		return send(method, path, session, body, true);
	}

	Res send(String method, String path, String session, Object body, boolean clientHeader) throws Exception {
		if (path.equals("/api/auth/signup") && body instanceof java.util.Map<?, ?> m && m.get("email") instanceof String email
				&& !m.containsKey("emailCode")) {
			String[] code = emailCode(email);
			if (code != null) {
				java.util.Map<Object, Object> withCode = new java.util.HashMap<>(m);
				withCode.put("emailChallenge", code[0]);
				withCode.put("emailCode", code[1]);
				body = withCode;
			}
		}
		return rawSend(method, path, session, body, clientHeader);
	}

	/** 가입 인증번호를 받아 메일에서 읽는다 → {challenge, code}. 받을 수 없는 이메일(형식 오류 · 이미 가입)이면 null */
	String[] emailCode(String email) throws Exception {
		String key = email.trim().toLowerCase();
		mails.lastBody.remove(key);
		Res r = rawSend("POST", "/api/auth/signup/email-code", null, java.util.Map.of("email", email), true);
		if (r.status() != 200) return null;
		for (int i = 0; i < 50 && !mails.lastBody.getOrDefault(key, "").contains("인증번호"); i++) Thread.sleep(100);
		var m = java.util.regex.Pattern.compile("인증번호는 ([0-9]{6})").matcher(mails.lastBody.get(key));
		assertThat(m.find()).isTrue();
		return new String[] { r.body().get("challenge").asString(), m.group(1) };
	}

	Res rawSend(String method, String path, String session, Object body, boolean clientHeader) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("Content-Type", "application/json");
		if (session != null) b.header("Cookie", COOKIE + "=" + session);
		if (clientHeader) b.header("X-Recarry-Client", "web");
		String payload = body == null ? "" : (body instanceof String s ? s : json.writeValueAsString(body));
		b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
		HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return new Res(r.statusCode(), r.body().isEmpty() ? json.nullNode() : json.readTree(r.body()),
			r.headers().allValues("set-cookie"));
	}

	String signup() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@test.recarry";
		Res r = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", email, "password", "passw0rd!", "name", "테스터", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		assertThat(r.status()).isEqualTo(201);
		verified(email);
		return r.session();
	}

	/** 메일 인증을 마친 것으로 — 예약 테스트용 (인증 흐름 자체는 email_verification_gates_booking 이 본다) */
	void verified(String email) {
		jdbc.update("UPDATE users SET email_verified_at = now() WHERE email = ?", email.toLowerCase());
	}

	/** 관리자가 결제 예정 금액 그대로 입금 확인 → 예약 확정 */
	Res confirmPaid(String admin, String number) throws Exception {
		int due = call("GET", "/api/admin/bookings/" + number, admin, null).body().get("amountDue").asInt();
		return call("POST", "/api/admin/bookings/" + number + "/payment", admin, java.util.Map.of("amount", due, "payerName", "입금자"));
	}

	String adminToken() throws Exception {
		Res r = call("POST", "/api/auth/login", null, java.util.Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
		assertThat(r.status()).isEqualTo(200);
		return r.session();
	}

	/** 테스트마다 겹치지 않는 [start, end] — 점유 기간(start-1 ~ end)까지 떨어지도록 10일 간격 */
	LocalDate[] window(int nights) {
		LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5 + 10L * WINDOW.getAndIncrement());
		return new LocalDate[] { start, start.plusDays(nights) };
	}

	java.util.Map<String, Object> booking(String size, String code, LocalDate[] w, Integer expected) {
		java.util.Map<String, Object> m = new java.util.HashMap<>(java.util.Map.of(
			"size", size, "startDate", w[0].toString(), "endDate", w[1].toString(),
			"recipientName", "받는분", "phone", "010-2222-3333", "address", "서울시 테스트로 1",
			"addressDetail", "101호", "requestMessage", "문 앞에 두세요"));
		if (code != null) m.put("carrierCode", code);
		if (expected != null) m.put("expectedTotal", expected);
		return m;
	}

	// ------------------------------------------------------------------ auth

	@Test
	void signup_login_me_and_password_is_not_exposed() throws Exception {
		String email = "Mixed.Case-" + UUID.randomUUID() + "@Test.Recarry";
		Res s = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", email, "password", "passw0rd!", "name", "김테스트", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		assertThat(s.status()).isEqualTo(201);
		assertThat(s.body().get("user").get("email").asString()).isEqualTo(email.toLowerCase());
		assertThat(s.body().get("user").has("passwordHash")).isFalse();

		// 대소문자만 다른 중복 가입은 거절
		Res dup = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", email.toUpperCase(), "password", "passw0rd!", "name", "x", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		assertThat(dup.status()).isEqualTo(409);
		assertThat(dup.code()).isEqualTo("EMAIL_TAKEN");

		Res login = call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "passw0rd!"));
		assertThat(login.status()).isEqualTo(200);
		Res me = call("GET", "/api/users/me", login.session(), null);
		assertThat(me.status()).isEqualTo(200);
		assertThat(me.body().get("name").asString()).isEqualTo("김테스트");
		assertThat(me.body().get("role").asString()).isEqualTo("USER");

		Res wrong = call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "wrong-pass1"));
		assertThat(wrong.status()).isEqualTo(401);
		Res unknown = call("POST", "/api/auth/login", null, java.util.Map.of("email", "nobody@test.recarry", "password", "passw0rd!"));
		assertThat(unknown.status()).isEqualTo(401);
		assertThat(unknown.code()).isEqualTo(wrong.code()); // 가입 여부를 구분할 수 없다
	}

	@Test
	void signup_validation() throws Exception {
		Res r = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", "not-an-email", "password", "short", "name", "", "phone", "abc"));
		assertThat(r.status()).isEqualTo(400);
		assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
		assertThat(r.body().get("fields").has("email")).isTrue();
		assertThat(r.body().get("fields").has("password")).isTrue();
		assertThat(r.body().get("fields").has("phone")).isTrue();

		Res malformed = call("POST", "/api/auth/signup", null, "{not json");
		assertThat(malformed.status()).isEqualTo(400);
	}

	@Test
	void signup_requires_both_consents_and_records_them() throws Exception {
		String email = "consent-" + UUID.randomUUID() + "@test.recarry";
		// 동의 필드를 보내지 않거나 false 면 가입되지 않는다
		Res none = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", email, "password", "passw0rd!", "name", "동의", "phone", "010-1234-5678"));
		assertThat(none.status()).isEqualTo(400);
		assertThat(none.body().get("fields").has("agreeTerms")).isTrue();
		assertThat(none.body().get("fields").has("agreePrivacy")).isTrue();
		Res half = call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "동의", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", false));
		assertThat(half.status()).isEqualTo(400);
		assertThat(half.body().get("fields").has("agreePrivacy")).isTrue();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();

		Res ok = call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "동의", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		assertThat(ok.status()).isEqualTo(201);
		var row = jdbc.queryForMap("SELECT terms_version, privacy_version, agreed_at FROM users WHERE email = ?", email);
		assertThat(row.get("terms_version")).isEqualTo("2026-10-25");
		assertThat(row.get("privacy_version")).isEqualTo("2026-10-25");
		assertThat(row.get("agreed_at")).isNotNull();
	}

	@Test
	void unauthenticated_and_bad_tokens_are_rejected() throws Exception {
		assertThat(call("GET", "/api/bookings", null, null).status()).isEqualTo(401);
		assertThat(call("POST", "/api/bookings", null, booking("24", null, window(3), null)).status()).isEqualTo(401);
		assertThat(call("GET", "/api/users/me", "not.a.jwt", null).status()).isEqualTo(401);
		// 다른 키로 서명한 토큰
		String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZXMiOlsiQURNSU4iXSwiaXNzIjoicmVjYXJyeSJ9."
			+ "c2lnbmF0dXJlLXRoYXQtZG9lcy1ub3QtbWF0Y2g";
		assertThat(call("GET", "/api/admin/carriers", forged, null).status()).isEqualTo(401);
	}

	// ------------------------------------------------------------------ carriers

	@Test
	void health_is_public_and_checks_db() throws Exception {
		Res h = call("GET", "/api/health", null, null);
		assertThat(h.status()).isEqualTo(200);
		assertThat(h.body().get("status").asString()).isEqualTo("UP");
		// 만료·위조 쿠키가 있어도 health 는 열린다 (호스팅 health check 는 쿠키를 보내지 않지만 안전하게)
		assertThat(call("GET", "/api/health", "forged.token.value", null).status()).isEqualTo(200);
	}

	@Test
	void public_catalog() throws Exception {
		Res all = call("GET", "/api/carriers", null, null);
		assertThat(all.status()).isEqualTo(200);
		assertThat(all.body().size()).isEqualTo(3);
		JsonNode m24 = all.body().get(1);
		assertThat(m24.get("size").asString()).isEqualTo("24");
		assertThat(m24.get("featured").get("code").asString()).isEqualTo("RC-24-0187");
		assertThat(m24.get("featured").get("events").size()).isEqualTo(5);
		assertThat(m24.get("headline").size()).isEqualTo(2);

		Res only = call("GET", "/api/carriers?size=28", null, null);
		assertThat(only.body().size()).isEqualTo(1);
		assertThat(only.body().get(0).get("featured").get("grade").asString()).isEqualTo("B");

		assertThat(call("GET", "/api/carriers/RC-20-0412", null, null).status()).isEqualTo(200);
		Res missing = call("GET", "/api/carriers/RC-99-0000", null, null);
		assertThat(missing.status()).isEqualTo(404);
		assertThat(missing.code()).isEqualTo("CARRIER_NOT_FOUND");
	}

	// ------------------------------------------------------------------ bookings

	@Test
	void create_booking_recalculates_price_and_is_readable_by_owner_only() throws Exception {
		String alice = signup();
		String bob = signup();
		LocalDate[] w = window(4); // 4박 → 24" 25,900 + 2×5,000

		Res created = call("POST", "/api/bookings", alice, booking("24", "RC-24-0187", w, 35900));
		assertThat(created.status()).isEqualTo(201);
		JsonNode b = created.body();
		assertThat(b.get("status").asString()).isEqualTo("REQUESTED");
		assertThat(b.get("totalPrice").asInt()).isEqualTo(35900);
		assertThat(b.get("basePrice").asInt()).isEqualTo(25900);
		assertThat(b.get("extraPrice").asInt()).isEqualTo(10000);
		assertThat(b.get("carrierCode").asString()).isEqualTo("RC-24-0187");
		assertThat(b.get("deliveryDate").asString()).isEqualTo(w[0].minusDays(1).toString());
		// 보증금 = 대여 요금 30%, 100원 미만 버림 (35,900 → 10,770 → 10,700). 대여 요금 합계와 별도
		assertThat(b.get("deposit").asInt()).isEqualTo(10700);
		assertThat(b.get("expiresAt").isNull()).isFalse();
		assertThat(b.get("customer").isNull()).isTrue();
		String number = b.get("bookingNumber").asString();
		assertThat(number).matches("RB-\\d{6}-[A-Z2-9]{6}");

		Res mine = call("GET", "/api/bookings", alice, null);
		assertThat(mine.body().size()).isEqualTo(1);
		assertThat(call("GET", "/api/bookings/" + number, alice, null).status()).isEqualTo(200);

		// 다른 사용자는 존재 여부조차 알 수 없다
		Res other = call("GET", "/api/bookings/" + number, bob, null);
		assertThat(other.status()).isEqualTo(404);
		assertThat(call("POST", "/api/bookings/" + number + "/cancel", bob, null).status()).isEqualTo(404);
		assertThat(call("GET", "/api/bookings", bob, null).body().size()).isZero();
	}

	@Test
	void price_is_never_taken_from_the_client() throws Exception {
		String user = signup();
		LocalDate[] w = window(3);
		Res tampered = call("POST", "/api/bookings", user, booking("20", null, w, 100));
		assertThat(tampered.status()).isEqualTo(409);
		assertThat(tampered.code()).isEqualTo("PRICE_MISMATCH");
		assertThat(call("GET", "/api/bookings", user, null).body().size()).isZero();

		// totalPrice 같은 임의 필드를 보내도 무시되고 서버 금액이 저장된다
		java.util.Map<String, Object> body = booking("20", null, w, null);
		body.put("totalPrice", 1);
		Res ok = call("POST", "/api/bookings", user, body);
		assertThat(ok.status()).isEqualTo(201);
		assertThat(ok.body().get("totalPrice").asInt()).isEqualTo(19900 + 4000);
	}

	@Test
	void same_carrier_cannot_be_double_booked() throws Exception {
		String a = signup();
		String b = signup();
		LocalDate[] w = window(3);

		// 24" 는 두 대 — 같은 캐리어를 원해도 두 번째는 다른 캐리어로 배정되고, 세 번째는 자리가 없다
		Res first = call("POST", "/api/bookings", a, booking("24", "RC-24-0187", w, null));
		Res second = call("POST", "/api/bookings", b, booking("24", "RC-24-0187", w, null));
		assertThat(first.status()).isEqualTo(201);
		assertThat(second.status()).isEqualTo(201);
		assertThat(second.body().get("carrierCode").asString()).isNotEqualTo("RC-24-0187");
		Res third = call("POST", "/api/bookings", b, booking("24", null, w, null));
		assertThat(third.status()).isEqualTo(409);
		assertThat(third.code()).isEqualTo("CARRIER_UNAVAILABLE");

		// 점유 기간은 출발 전날부터라, 끝나는 날에 바로 이어 시작하는 예약도 겹친다
		LocalDate[] touching = { w[1], w[1].plusDays(2) };
		assertThat(call("POST", "/api/bookings", b, booking("24", null, touching, null)).status()).isEqualTo(409);

		Res avail = call("GET", "/api/carriers/availability?start=" + w[0] + "&end=" + w[1], null, null);
		assertThat(avail.body().get(1).get("available").asInt()).isZero();
	}

	@Test
	void concurrent_requests_for_the_last_carrier_only_one_wins() throws Exception {
		LocalDate[] w = window(2); // 28" 는 한 대뿐
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < 6; i++) tokens.add(signup());

		ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
		List<Future<Res>> results = new ArrayList<>();
		for (String t : tokens) {
			Callable<Res> task = () -> call("POST", "/api/bookings", t, booking("28", null, w, null));
			results.add(pool.submit(task));
		}
		int created = 0, conflicts = 0;
		for (Future<Res> f : results) {
			int s = f.get().status();
			if (s == 201) created++;
			else if (s == 409) conflicts++;
		}
		pool.shutdown();
		assertThat(created).isEqualTo(1);
		assertThat(conflicts).isEqualTo(tokens.size() - 1);
	}

	@Test
	void invalid_carrier_dates_and_inputs() throws Exception {
		String user = signup();
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

		Res unknownCode = call("POST", "/api/bookings", user, booking("24", "RC-24-9999", window(3), null));
		assertThat(unknownCode.status()).isEqualTo(404);
		Res unknownSize = call("POST", "/api/bookings", user, booking("99", null, window(3), null));
		assertThat(unknownSize.status()).isEqualTo(404);
		Res wrongSize = call("POST", "/api/bookings", user, booking("20", "RC-24-0187", window(3), null));
		assertThat(wrongSize.code()).isEqualTo("CARRIER_SIZE_MISMATCH");

		Res past = call("POST", "/api/bookings", user, booking("24", null, new LocalDate[] { today, today.plusDays(2) }, null));
		assertThat(past.status()).isEqualTo(400);
		assertThat(past.code()).isEqualTo("INVALID_DATES");
		LocalDate[] w = window(3);
		Res reversed = call("POST", "/api/bookings", user, booking("24", null, new LocalDate[] { w[1], w[0] }, null));
		assertThat(reversed.code()).isEqualTo("INVALID_DATES");
		Res sameDay = call("POST", "/api/bookings", user, booking("24", null, new LocalDate[] { w[0], w[0] }, null));
		assertThat(sameDay.code()).isEqualTo("INVALID_DATES");
		Res tooLong = call("POST", "/api/bookings", user, booking("24", null, new LocalDate[] { w[0], w[0].plusDays(31) }, null));
		assertThat(tooLong.code()).isEqualTo("INVALID_DATES");
		Res badAvail = call("GET", "/api/carriers/availability?start=" + w[1] + "&end=" + w[0], null, null);
		assertThat(badAvail.status()).isEqualTo(400);

		java.util.Map<String, Object> missing = booking("24", null, window(3), null);
		missing.remove("address");
		missing.put("phone", "not-a-phone");
		Res invalid = call("POST", "/api/bookings", user, missing);
		assertThat(invalid.status()).isEqualTo(400);
		assertThat(invalid.body().get("fields").has("address")).isTrue();
		assertThat(invalid.body().get("fields").has("phone")).isTrue();

		assertThat(call("GET", "/api/bookings", user, null).body().size()).isZero();
	}

	@Test
	void user_can_cancel_only_requested_booking_and_it_frees_the_carrier() throws Exception {
		String user = signup();
		LocalDate[] w = window(3);
		String number = call("POST", "/api/bookings", user, booking("28", null, w, null)).body().get("bookingNumber").asString();
		String path = "/api/carriers/availability?start=" + w[0] + "&end=" + w[1];
		assertThat(call("GET", path, null, null).body().get(2).get("available").asInt()).isZero();

		Res cancelled = call("POST", "/api/bookings/" + number + "/cancel", user, null);
		assertThat(cancelled.status()).isEqualTo(200);
		assertThat(cancelled.body().get("status").asString()).isEqualTo("CANCELLED");
		assertThat(call("GET", path, null, null).body().get(2).get("available").asInt()).isEqualTo(1);

		Res again = call("POST", "/api/bookings/" + number + "/cancel", user, null);
		assertThat(again.status()).isEqualTo(409);
	}

	// ------------------------------------------------------------------ admin

	@Test
	void user_cannot_reach_admin_api() throws Exception {
		String user = signup();
		assertThat(call("GET", "/api/admin/carriers", user, null).status()).isEqualTo(403);
		assertThat(call("GET", "/api/admin/bookings", user, null).status()).isEqualTo(403);
		assertThat(call("PATCH", "/api/admin/carriers/1/status", user, java.util.Map.of("status", "REPAIR")).status()).isEqualTo(403);
	}

	@Test
	void admin_manages_bookings_and_carriers() throws Exception {
		String admin = adminToken();
		String user = signup();
		LocalDate[] w = window(3);
		String number = call("POST", "/api/bookings", user, booking("20", "RC-20-0412", w, null)).body().get("bookingNumber").asString();

		Res detail = call("GET", "/api/admin/bookings/" + number, admin, null);
		assertThat(detail.status()).isEqualTo(200);
		assertThat(detail.body().get("customer").get("email").asString()).startsWith("user-");
		assertThat(call("GET", "/api/admin/bookings?status=REQUESTED", admin, null).body().size()).isPositive();

		// 전이 규칙: REQUESTED → IN_USE 는 불가, CONFIRMED → IN_USE → RETURNED 는 가능
		String statusPath = "/api/admin/bookings/" + number + "/status";
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "IN_USE")).code()).isEqualTo("INVALID_STATUS_TRANSITION");
		// 확정은 상태 변경이 아니라 입금 확인으로만
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "CONFIRMED")).code()).isEqualTo("USE_PAYMENT_CONFIRM");
		assertThat(confirmPaid(admin, number).status()).isEqualTo(200);
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "IN_USE")).status()).isEqualTo(200);
		// 배송이 시작된(이용 중) 예약은 사용자가 직접 취소할 수 없다
		assertThat(call("POST", "/api/bookings/" + number + "/cancel", user, null).code()).isEqualTo("NOT_CANCELLABLE");
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "RETURNED")).status()).isEqualTo(200);
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "CANCELLED")).status()).isEqualTo(409);
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "NOPE")).status()).isEqualTo(400);

		// 캐리어 운영 상태: REPAIR 로 바꾸면 예약 가능 대수에서 빠진다. RESERVED 는 직접 지정할 수 없다
		Res carriers = call("GET", "/api/admin/carriers", admin, null);
		assertThat(carriers.body().size()).isGreaterThanOrEqualTo(6);
		long id = -1;
		for (JsonNode c : carriers.body()) if (c.get("code").asString().equals("RC-20-S02")) id = c.get("id").asLong();
		LocalDate[] later = window(3);
		String avail = "/api/carriers/availability?start=" + later[0] + "&end=" + later[1];
		int before = call("GET", avail, null, null).body().get(0).get("available").asInt();
		Res repair = call("PATCH", "/api/admin/carriers/" + id + "/status", admin, java.util.Map.of("status", "REPAIR"));
		assertThat(repair.body().get("status").asString()).isEqualTo("REPAIR");
		assertThat(call("GET", avail, null, null).body().get(0).get("available").asInt()).isEqualTo(before - 1);
		assertThat(call("PATCH", "/api/admin/carriers/" + id + "/status", admin, java.util.Map.of("status", "RESERVED")).status()).isEqualTo(400);
		assertThat(call("PATCH", "/api/admin/carriers/999999/status", admin, java.util.Map.of("status", "REPAIR")).status()).isEqualTo(404);
		call("PATCH", "/api/admin/carriers/" + id + "/status", admin, java.util.Map.of("status", "AVAILABLE"));
	}

	@Test
	void admin_registers_edits_and_records_carrier_history() throws Exception {
		String admin = adminToken();
		String code = "RC-20-T" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		java.util.Map<String, Object> create = new java.util.HashMap<>(java.util.Map.of(
			"size", "20", "code", code, "grade", "A", "collectedFrom", "테스트 회수처", "repairSummary", "바퀴 ×1"));

		// 사용자는 관리 API 를 쓸 수 없다
		assertThat(call("POST", "/api/admin/carriers", signup(), create).status()).isEqualTo(403);

		// 등록: 세척·검수 전(INSPECTION)으로 시작하고, 검수 결과는 비어 있다
		Res created = call("POST", "/api/admin/carriers", admin, create);
		assertThat(created.status()).isEqualTo(201);
		long id = created.body().get("id").asLong();
		assertThat(created.body().get("status").asString()).isEqualTo("INSPECTION");
		assertThat(created.body().get("inspection").size()).isZero();
		assertThat(created.body().get("featured").asBoolean()).isFalse();
		assertThat(call("POST", "/api/admin/carriers", admin, create).code()).isEqualTo("CARRIER_CODE_TAKEN");

		java.util.Map<String, Object> bad = new java.util.HashMap<>(create);
		bad.put("code", "rc bad");
		bad.put("grade", "C");
		Res invalid = call("POST", "/api/admin/carriers", admin, bad);
		assertThat(invalid.status()).isEqualTo(400);
		assertThat(invalid.body().get("fields").has("code")).isTrue();
		assertThat(invalid.body().get("fields").has("grade")).isTrue();
		java.util.Map<String, Object> noSize = new java.util.HashMap<>(create);
		noSize.put("size", "99");
		noSize.put("code", code + "X");
		assertThat(call("POST", "/api/admin/carriers", admin, noSize).code()).isEqualTo("MODEL_NOT_FOUND");

		// 수정 + 대표 지정: 같은 사이즈의 기존 대표는 내려간다. 원래 대표로 되돌린다
		String path = "/api/admin/carriers/" + id;
		Res edited = call("PATCH", path, admin,
			java.util.Map.of("grade", "B", "collectedFrom", "수정 회수처", "repairSummary", "", "featured", true));
		assertThat(edited.status()).isEqualTo(200);
		assertThat(edited.body().get("grade").asString()).isEqualTo("B");
		assertThat(edited.body().get("code").asString()).isEqualTo(code);
		assertThat(call("GET", "/api/carriers?size=20", null, null).body().get(0).get("featured").get("code").asString())
			.isEqualTo(code);
		long originalId = call("GET", "/api/carriers/RC-20-0412", null, null).body().get("id").asLong();
		assertThat(call("PATCH", "/api/admin/carriers/" + originalId, admin, java.util.Map.of("grade", "A",
			"collectedFrom", "공항 인근", "repairSummary", "지퍼 슬라이더 교체", "featured", true)).status()).isEqualTo(200);
		assertThat(call("GET", "/api/carriers/" + code, null, null).body().get("featured").asBoolean()).isFalse();

		// 검수: 모든 항목을 한 번씩, 오늘(서울) 이후 날짜는 안 된다
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		List<java.util.Map<String, Object>> checks = new ArrayList<>();
		for (String item : List.of("CLEANING", "EXTERIOR", "WHEELS", "HANDLE", "ZIPPER")) {
			boolean zipper = item.equals("ZIPPER");
			checks.add(java.util.Map.of("item", item, "passed", !zipper, "note", zipper ? "슬라이더 재교체 필요" : ""));
		}
		String insp = path + "/inspection";
		assertThat(call("POST", insp, admin, java.util.Map.of("inspectedAt", today.toString(), "checks", checks.subList(0, 4))).code())
			.isEqualTo("INVALID_INSPECTION");
		List<java.util.Map<String, Object>> dup = new ArrayList<>(checks.subList(0, 4));
		dup.add(checks.get(0));
		assertThat(call("POST", insp, admin, java.util.Map.of("inspectedAt", today.toString(), "checks", dup)).code())
			.isEqualTo("INVALID_INSPECTION");
		assertThat(call("POST", insp, admin, java.util.Map.of("inspectedAt", today.plusDays(1).toString(), "checks", checks)).code())
			.isEqualTo("INVALID_DATE");
		// 재점검(불통과)에는 메모가 있어야 한다
		List<java.util.Map<String, Object>> noNote = new ArrayList<>(checks.subList(0, 4));
		noNote.add(java.util.Map.of("item", "ZIPPER", "passed", false));
		assertThat(call("POST", insp, admin, java.util.Map.of("inspectedAt", today.toString(), "checks", noNote)).code()).isEqualTo("INVALID_INSPECTION");
		Res inspected = call("POST", insp, admin, java.util.Map.of("inspectedAt", today.toString(), "checks", checks));
		assertThat(inspected.status()).isEqualTo(200);
		assertThat(inspected.body().get("inspectedAt").asString()).isEqualTo(today.toString());
		JsonNode result = inspected.body().get("inspection");
		assertThat(result.size()).isEqualTo(5);
		assertThat(result.get(0).get("item").asString()).isEqualTo("CLEANING");
		assertThat(result.get(4).get("passed").asBoolean()).isFalse();
		assertThat(result.get(4).get("note").asString()).isEqualTo("슬라이더 재교체 필요");
		// 다시 기록하면 이전 결과를 대체한다
		Res again = call("POST", insp, admin, java.util.Map.of("inspectedAt", today.toString(), "checks",
			checks.stream().map(c -> java.util.Map.<String, Object>of("item", c.get("item"), "passed", true)).toList()));
		assertThat(again.body().get("inspection").size()).isEqualTo(5);
		assertThat(again.body().get("inspection").get(4).get("passed").asBoolean()).isTrue();
		// 검수 기록은 운영 상태를 바꾸지 않는다. 공개 상세에도 같은 결과가 보인다
		Res pub = call("GET", "/api/carriers/" + code, null, null);
		assertThat(pub.body().get("status").asString()).isEqualTo("INSPECTION");
		assertThat(pub.body().get("inspection").size()).isEqualTo(5);

		// Story 기록: 날짜순으로 쌓이고, 삭제는 그 캐리어의 기록만
		Res e1 = call("POST", path + "/events", admin,
			java.util.Map.of("date", today.toString(), "type", "REPAIR", "title", "지퍼 교체", "detail", ""));
		assertThat(e1.status()).isEqualTo(201);
		Res e2 = call("POST", path + "/events", admin,
			java.util.Map.of("date", today.minusDays(3).toString(), "type", "COLLECTED", "title", "회수"));
		JsonNode events = e2.body().get("events");
		assertThat(events.size()).isEqualTo(2);
		assertThat(events.get(0).get("type").asString()).isEqualTo("COLLECTED");
		assertThat(call("POST", path + "/events", admin,
			java.util.Map.of("date", today.toString(), "type", "NOPE", "title", "x")).status()).isEqualTo(400);
		long eventId = events.get(1).get("id").asLong();
		assertThat(call("DELETE", "/api/admin/carriers/" + originalId + "/events/" + eventId, admin, null).status()).isEqualTo(404);
		Res removed = call("DELETE", path + "/events/" + eventId, admin, null);
		assertThat(removed.status()).isEqualTo(200);
		assertThat(removed.body().get("events").size()).isEqualTo(1);

		// 다른 테스트의 재고 계산에 끼지 않도록 운영 중지로 둔다
		assertThat(call("PATCH", path + "/status", admin, java.util.Map.of("status", "UNAVAILABLE")).status()).isEqualTo(200);
	}

	@Test
	void admin_edits_model_price_and_server_uses_it() throws Exception {
		String admin = adminToken();
		JsonNode m = call("GET", "/api/carriers?size=28", null, null).body().get(0);
		List<String> headline = new ArrayList<>();
		for (JsonNode h : m.get("headline")) headline.add(h.asString());
		java.util.Map<String, Object> body = new java.util.HashMap<>(java.util.Map.of(
			"name", m.get("name").asString(), "inch", m.get("inch").asString(), "capacity", m.get("capacity").asString(),
			"usage", m.get("usage").asString(), "dims", m.get("dims").asString(), "weight", m.get("weight").asString(),
			"price", m.get("price").asInt(), "extraNightPrice", m.get("extraNightPrice").asInt(),
			"headline", String.join("\n", headline), "description", m.get("description").asString()));
		assertThat(call("PATCH", "/api/admin/carrier-models/28", signup(), body).status()).isEqualTo(403);
		try {
			java.util.Map<String, Object> changed = new java.util.HashMap<>(body);
			changed.put("price", 30000);
			assertThat(call("PATCH", "/api/admin/carrier-models/28", admin, changed).status()).isEqualTo(204);
			LocalDate[] w = window(2);
			Res avail = call("GET", "/api/carriers/availability?start=" + w[0] + "&end=" + w[1], null, null);
			assertThat(avail.body().get(2).get("totalPrice").asInt()).isEqualTo(30000);

			changed.put("price", -1);
			assertThat(call("PATCH", "/api/admin/carrier-models/28", admin, changed).status()).isEqualTo(400);
			// 0원(가격을 지운 실수)과 비현실적으로 큰 값은 거절 — 보증금 계산 오버플로 방지
			changed.put("price", 0);
			assertThat(call("PATCH", "/api/admin/carrier-models/28", admin, changed).status()).isEqualTo(400);
			changed.put("price", 2_000_000_000);
			assertThat(call("PATCH", "/api/admin/carrier-models/28", admin, changed).status()).isEqualTo(400);
			assertThat(call("PATCH", "/api/admin/carrier-models/99", admin, body).status()).isEqualTo(404);
		} finally {
			assertThat(call("PATCH", "/api/admin/carrier-models/28", admin, body).status()).isEqualTo(204);
		}
		JsonNode restored = call("GET", "/api/carriers?size=28", null, null).body().get(0);
		assertThat(restored.get("headline").size()).isEqualTo(2);
		assertThat(restored.get("price").asInt()).isEqualTo(m.get("price").asInt());
	}

	@Test
	void sample_featured_carriers_carry_inspection_results() throws Exception {
		JsonNode c = call("GET", "/api/carriers/RC-24-0187", null, null).body();
		assertThat(c.get("inspection").size()).isEqualTo(5);
		// 대표가 아닌 sample 캐리어는 검수 결과 기록이 없다 — 화면은 "통과"로 꾸미지 않는다
		assertThat(call("GET", "/api/carriers/RC-24-S01", null, null).body().get("inspection").size()).isZero();
	}

	// ------------------------------------------------------------------ 예약 요청 만료 · 보증금

	@Autowired
	com.recarry.booking.BookingExpiry expiry;

	@Test
	void unconfirmed_requests_expire_after_three_days_and_free_the_carrier() throws Exception {
		String user = signup();
		LocalDate[] w = window(3);
		Res avail = call("GET", "/api/carriers/availability?start=" + w[0] + "&end=" + w[1], null, null);
		assertThat(avail.body().get(2).get("deposit").asInt()).isEqualTo(37900 * 30 / 100 / 100 * 100);
		String old = call("POST", "/api/bookings", user, booking("28", null, w, null)).body().get("bookingNumber").asString();
		String fresh = call("POST", "/api/bookings", user, booking("20", null, w, null)).body().get("bookingNumber").asString();
		jdbc.update("UPDATE bookings SET created_at = now() - interval '3 days 1 minute' WHERE booking_number = ?", old);

		expiry.expire();
		JsonNode expired = call("GET", "/api/bookings/" + old, user, null).body();
		assertThat(expired.get("status").asString()).isEqualTo("CANCELLED");
		assertThat(expired.get("cancelReason").asString()).isEqualTo("EXPIRED");
		assertThat(expired.get("expiresAt").isNull()).isTrue();
		assertThat(call("GET", "/api/bookings/" + fresh, user, null).body().get("status").asString()).isEqualTo("REQUESTED");
		// 만료된 요청이 잡고 있던 28" 가 다시 예약 가능
		assertThat(call("GET", "/api/carriers/availability?start=" + w[0] + "&end=" + w[1], null, null)
			.body().get(2).get("available").asInt()).isEqualTo(1);
	}

	// ------------------------------------------------------------------ 내 계정

	@Test
	void profile_update_and_password_change_revokes_other_sessions() throws Exception {
		String email = "acct-" + UUID.randomUUID() + "@test.recarry";
		String first = call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "계정", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).session();
		String second = call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "passw0rd!")).session();

		Res updated = call("PATCH", "/api/users/me", first, java.util.Map.of("name", "새이름", "phone", "010-9999-8888"));
		assertThat(updated.status()).isEqualTo(200);
		assertThat(updated.body().get("name").asString()).isEqualTo("새이름");
		assertThat(call("PATCH", "/api/users/me", first, java.util.Map.of("name", "", "phone", "x")).status()).isEqualTo(400);

		Res wrong = call("POST", "/api/users/me/password", first,
			java.util.Map.of("currentPassword", "nope-pass1", "newPassword", "newpassw0rd"));
		assertThat(wrong.status()).isEqualTo(400);
		assertThat(wrong.code()).isEqualTo("WRONG_PASSWORD");
		Thread.sleep(1100); // 토큰 iat 는 초 단위 — 변경 시각과 다른 초에 발급된 토큰임을 보장
		Res changed = call("POST", "/api/users/me/password", first,
			java.util.Map.of("currentPassword", "passw0rd!", "newPassword", "newpassw0rd"));
		assertThat(changed.status()).isEqualTo(204);
		String renewed = changed.session();
		assertThat(renewed).isNotNull();
		// 다른 기기(second)와 변경 전 토큰(first)은 끊기고, 새 쿠키는 쓸 수 있다
		assertThat(call("GET", "/api/users/me", second, null).status()).isEqualTo(401);
		assertThat(call("GET", "/api/users/me", first, null).status()).isEqualTo(401);
		assertThat(call("GET", "/api/users/me", renewed, null).status()).isEqualTo(200);
		assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "passw0rd!")).status()).isEqualTo(401);
		assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "newpassw0rd")).status()).isEqualTo(200);
	}

	@Test
	void withdrawal_is_blocked_by_active_booking_then_anonymizes_account() throws Exception {
		String admin = adminToken();
		String email = "bye-" + UUID.randomUUID() + "@test.recarry";
		java.util.Map<String, Object> signupBody = java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "탈퇴", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true);
		String user = call("POST", "/api/auth/signup", null, signupBody).session();
		verified(email);
		String confirmed = call("POST", "/api/bookings", user, booking("20", null, window(3), null)).body().get("bookingNumber").asString();
		String requested = call("POST", "/api/bookings", user, booking("20", null, window(3), null)).body().get("bookingNumber").asString();
		String rejected = call("POST", "/api/bookings", user, booking("20", null, window(3), null)).body().get("bookingNumber").asString();
		confirmPaid(admin, confirmed);
		call("PATCH", "/api/admin/bookings/" + rejected + "/status", admin, java.util.Map.of("status", "CANCELLED"));
		// 관리자 계정은 탈퇴할 수 없다
		assertThat(call("POST", "/api/users/me/withdraw", admin, java.util.Map.of("password", ADMIN_PASSWORD)).code())
			.isEqualTo("ADMIN_CANNOT_WITHDRAW");

		assertThat(call("POST", "/api/users/me/withdraw", user, java.util.Map.of("password", "wrong-pass1")).code()).isEqualTo("WRONG_PASSWORD");
		assertThat(call("POST", "/api/users/me/withdraw", user, java.util.Map.of("password", "passw0rd!")).code()).isEqualTo("HAS_ACTIVE_BOOKING");

		call("PATCH", "/api/admin/bookings/" + confirmed + "/status", admin, java.util.Map.of("status", "CANCELLED"));
		Res bye = call("POST", "/api/users/me/withdraw", user, java.util.Map.of("password", "passw0rd!"));
		assertThat(bye.status()).isEqualTo(204);
		assertThat(bye.setCookies()).anyMatch(c -> c.contains("Max-Age=0"));
		assertThat(call("GET", "/api/users/me", user, null).status()).isEqualTo(401);
		assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "passw0rd!")).status()).isEqualTo(401);

		// 남은 예약 요청은 고객 취소로 닫히고 배송 정보가 지워진다. 운영자가 취소한(확정됐던) 예약은 기록을 남긴다
		var erased = jdbc.queryForMap("SELECT status, cancel_reason, phone, address FROM bookings WHERE booking_number = ?", requested);
		assertThat(erased.get("status")).isEqualTo("CANCELLED");
		assertThat(erased.get("cancel_reason")).isEqualTo("USER");
		assertThat(erased.get("address")).isEqualTo("");
		// 운영자가 거절한 예약 요청(결제 전)도 지운다
		var rej = jdbc.queryForMap("SELECT cancel_reason, address FROM bookings WHERE booking_number = ?", rejected);
		assertThat(rej.get("cancel_reason")).isEqualTo("REJECTED");
		assertThat(rej.get("address")).isEqualTo("");
		assertThat(jdbc.queryForObject("SELECT address FROM bookings WHERE booking_number = ?", String.class, confirmed)).isNotEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();
		// 같은 이메일로 다시 가입할 수 있다
		assertThat(call("POST", "/api/auth/signup", null, signupBody).status()).isEqualTo(201);
		// 탈퇴 계정의 비식별 이메일 도메인으로는 가입할 수 없다 (탈퇴를 막는 선점 방지)
		java.util.Map<String, Object> squat = new java.util.HashMap<>(signupBody);
		squat.put("email", "deleted-1@deleted.recarry");
		assertThat(call("POST", "/api/auth/signup", null, squat).code()).isEqualTo("EMAIL_NOT_ALLOWED");
	}

	@Test
	void members_on_an_old_document_version_must_consent_again_before_booking() throws Exception {
		String email = "reconsent-" + UUID.randomUUID() + "@test.recarry";
		String token = call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "재동의", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).session();
		assertThat(call("GET", "/api/users/me", token, null).body().get("consentRequired").asBoolean()).isFalse();
		verified(email);

		// 이전 문서(draft)에 동의한 회원
		jdbc.update("UPDATE users SET terms_version = 'draft', privacy_version = 'draft' WHERE email = ?", email);
		assertThat(call("GET", "/api/auth/session", token, null).body().get("user").get("consentRequired").asBoolean()).isTrue();
		Res blocked = call("POST", "/api/bookings", token, booking("24", null, window(3), null));
		assertThat(blocked.status()).isEqualTo(403);
		assertThat(blocked.code()).isEqualTo("CONSENT_REQUIRED");

		assertThat(call("POST", "/api/users/me/consent", token, java.util.Map.of("agreeTerms", true, "agreePrivacy", false)).status())
			.isEqualTo(400);
		Res agreed = call("POST", "/api/users/me/consent", token, java.util.Map.of("agreeTerms", true, "agreePrivacy", true));
		assertThat(agreed.status()).isEqualTo(200);
		assertThat(agreed.body().get("consentRequired").asBoolean()).isFalse();
		assertThat(jdbc.queryForObject("SELECT terms_version FROM users WHERE email = ?", String.class, email)).isEqualTo("2026-10-25");
		assertThat(call("POST", "/api/bookings", token, booking("24", null, window(3), null)).status()).isEqualTo(201);
	}

	// ------------------------------------------------------------------ 비밀번호 찾기

	/** 메일은 커밋 뒤 백그라운드로 가므로 잠시 기다린다 */
	String codeIn(String email) throws InterruptedException {
		for (int i = 0; i < 50 && !mails.lastBody.containsKey(email); i++) Thread.sleep(100);
		var m = java.util.regex.Pattern.compile("([0-9]{6})").matcher(mails.lastBody.get(email));
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	@Test
	void password_reset_by_email_code() throws Exception {
		String email = "reset-" + UUID.randomUUID() + "@test.recarry";
		String token = call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "재설정", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).session();

		// 없는 이메일도 같은 응답 (가입 여부 비노출) · 메일은 가지 않는다
		String nobody = "nobody-" + UUID.randomUUID() + "@test.recarry";
		assertThat(call("POST", "/api/auth/password-reset/request", null, java.util.Map.of("email", nobody)).status()).isEqualTo(204);
		assertThat(mails.lastBody).doesNotContainKey(nobody);

		assertThat(call("POST", "/api/auth/password-reset/request", null, java.util.Map.of("email", email.toUpperCase())).status()).isEqualTo(204);
		String first = codeIn(email);
		mails.lastBody.remove(email);
		// 다시 요청하면 이전 코드는 쓸 수 없다
		call("POST", "/api/auth/password-reset/request", null, java.util.Map.of("email", email));
		String code = codeIn(email);
		java.util.Map<String, Object> confirm = new java.util.HashMap<>(java.util.Map.of("email", email, "code", first, "newPassword", "resetpass1"));
		if (!first.equals(code)) {
			assertThat(call("POST", "/api/auth/password-reset/confirm", null, confirm).code()).isEqualTo("INVALID_CODE");
		}
		confirm.put("code", code.equals("000000") ? "111111" : "000000");
		assertThat(call("POST", "/api/auth/password-reset/confirm", null, confirm).code()).isEqualTo("INVALID_CODE");
		confirm.put("code", "12ab");
		assertThat(call("POST", "/api/auth/password-reset/confirm", null, confirm).code()).isEqualTo("VALIDATION_FAILED");

		Thread.sleep(1100); // 토큰 iat(초 단위)가 재설정 시각보다 앞서게
		confirm.put("code", code);
		assertThat(call("POST", "/api/auth/password-reset/confirm", null, confirm).status()).isEqualTo(204);
		// 재설정하면 기존 로그인은 끊기고, 새 비밀번호로 로그인한다. 같은 코드는 다시 쓸 수 없다
		assertThat(call("GET", "/api/users/me", token, null).status()).isEqualTo(401);
		assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "passw0rd!")).status()).isEqualTo(401);
		assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "resetpass1")).status()).isEqualTo(200);
		assertThat(call("POST", "/api/auth/password-reset/confirm", null, confirm).code()).isEqualTo("INVALID_CODE");
	}

	@Test
	void reset_code_locks_after_five_wrong_attempts() throws Exception {
		String email = "lock-" + UUID.randomUUID() + "@test.recarry";
		call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "잠금", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		call("POST", "/api/auth/password-reset/request", null, java.util.Map.of("email", email));
		String code = codeIn(email);
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 5; i++) {
			call("POST", "/api/auth/password-reset/confirm", null, java.util.Map.of("email", email, "code", wrong, "newPassword", "resetpass1"));
		}
		// 5번 틀린 뒤에는 맞는 코드도 거절 — 새 코드를 받아야 한다
		assertThat(call("POST", "/api/auth/password-reset/confirm", null,
			java.util.Map.of("email", email, "code", code, "newPassword", "resetpass1")).code()).isEqualTo("INVALID_CODE");
	}

	@Test
	void find_email_by_name_and_phone_shows_masked_address() throws Exception {
		String local = "findme" + UUID.randomUUID().toString().substring(0, 8);
		String phone = "010-" + (1000 + new SecureRandom().nextInt(9000)) + "-" + (1000 + new SecureRandom().nextInt(9000));
		call("POST", "/api/auth/signup", null, java.util.Map.of("email", local + "@test.recarry", "password", "passw0rd!",
			"name", "찾기테스트", "phone", phone, "agreeTerms", true, "agreePrivacy", true));
		// 하이픈 없이 입력해도 찾는다. 이메일은 앞 2글자만 보인다
		Res found = call("POST", "/api/auth/find-email", null, java.util.Map.of("name", "찾기테스트", "phone", phone.replace("-", "")));
		assertThat(found.status()).isEqualTo(200);
		assertThat(found.body().get("emails").get(0).asString()).isEqualTo("fi***@test.recarry");
		Res none = call("POST", "/api/auth/find-email", null, java.util.Map.of("name", "다른이름", "phone", phone));
		assertThat(none.body().get("emails").size()).isZero();
		// 숫자가 없는 번호는 형식 오류 — 휴대폰 번호가 비어 있는 계정(관리자 bootstrap)을 찾지 못하게
		assertThat(call("POST", "/api/auth/find-email", null, java.util.Map.of("name", "관리자", "phone", "---------")).code())
			.isEqualTo("VALIDATION_FAILED");
	}

	// ------------------------------------------------------------------ 무통장 입금 · 환불 · 보증금 · 이메일 인증

	@Test
	void bank_transfer_confirm_then_customer_cancel_refunds_by_policy() throws Exception {
		String admin = adminToken();
		String user = signup();
		// 배송 예정일이 3일보다 먼 예약 → 입금 후 취소해도 전액 환불
		LocalDate[] w = window(3);
		JsonNode b = call("POST", "/api/bookings", user, booking("20", null, w, null)).body();
		String number = b.get("bookingNumber").asString();
		int due = b.get("amountDue").asInt();
		assertThat(due).isEqualTo(b.get("totalPrice").asInt() + b.get("deposit").asInt());
		// 입금 대기 화면: 계좌(임시) · 금액 · 기한
		JsonNode pay = b.get("payment");
		assertThat(pay.get("sample").asBoolean()).isTrue();
		assertThat(pay.get("amount").asInt()).isEqualTo(due);
		assertThat(pay.get("deadline").isNull()).isFalse();

		assertThat(call("POST", "/api/admin/bookings/" + number + "/payment", admin,
			java.util.Map.of("amount", due - 1, "payerName", "테스터")).code()).isEqualTo("AMOUNT_MISMATCH");
		assertThat(call("POST", "/api/admin/bookings/" + number + "/payment", user,
			java.util.Map.of("amount", due, "payerName", "테스터")).status()).isEqualTo(403);
		JsonNode paid = confirmPaid(admin, number).body();
		assertThat(paid.get("status").asString()).isEqualTo("CONFIRMED");
		assertThat(paid.get("paidAmount").asInt()).isEqualTo(due);
		assertThat(paid.get("payment").isNull()).isTrue();
		assertThat(confirmPaid(admin, number).code()).isEqualTo("NOT_AWAITING_PAYMENT");

		// 고객 화면: 지금 취소하면 전액
		JsonNode mine = call("GET", "/api/bookings/" + number, user, null).body();
		assertThat(mine.get("cancellable").asBoolean()).isTrue();
		assertThat(mine.get("cancelRefund").asInt()).isEqualTo(due);
		JsonNode cancelled = call("POST", "/api/bookings/" + number + "/cancel", user, null).body();
		assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
		assertThat(cancelled.get("refundAmount").asInt()).isEqualTo(due);

		// 운영자: 환불 송금 완료 (두 번은 안 된다)
		Res done = call("POST", "/api/admin/bookings/" + number + "/refund-done", admin, null);
		assertThat(done.status()).isEqualTo(200);
		assertThat(done.body().get("refundedAt").isNull()).isFalse();
		assertThat(call("POST", "/api/admin/bookings/" + number + "/refund-done", admin, null).code()).isEqualTo("ALREADY_REFUNDED");
	}

	@Test
	void refund_rules_follow_the_rental_policy() throws Exception {
		String admin = adminToken();
		String user = signup();
		// 배송 예정일이 이틀 뒤(출발 3일 뒤)인 예약을 만들려면 날짜를 직접 고른다
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		LocalDate[] near = { today.plusDays(3), today.plusDays(5) };   // 배송 예정일 = 오늘 + 2일
		JsonNode b = call("POST", "/api/bookings", user, booking("28", null, near, null)).body();
		if (b.get("bookingNumber") == null) return;   // 28" 한 대가 다른 테스트에 잡혀 있으면 건너뛴다
		String number = b.get("bookingNumber").asString();
		confirmPaid(admin, number);
		// 입금 후 24시간 안이면 전액
		assertThat(call("GET", "/api/bookings/" + number, user, null).body().get("cancelRefund").asInt())
			.isEqualTo(b.get("amountDue").asInt());
		// 24시간이 지났다면(입금 시각을 이틀 전으로) — 배송 2일 전이므로 보증금은 공제
		jdbc.update("UPDATE bookings SET paid_at = now() - interval '2 days' WHERE booking_number = ?", number);
		JsonNode later = call("GET", "/api/bookings/" + number, user, null).body();
		assertThat(later.get("cancelRefund").asInt()).isEqualTo(b.get("totalPrice").asInt());
		// 운영자 취소는 환불액을 정할 수 있다 (입금액을 넘을 수는 없다)
		String path = "/api/admin/bookings/" + number + "/status";
		assertThat(call("PATCH", path, admin, java.util.Map.of("status", "CANCELLED", "refundAmount", 999_999_999)).code()).isEqualTo("INVALID_REFUND");
		JsonNode c = call("PATCH", path, admin, java.util.Map.of("status", "CANCELLED", "refundAmount", 1000)).body();
		assertThat(c.get("refundAmount").asInt()).isEqualTo(1000);
		assertThat(c.get("cancelReason").asString()).isEqualTo("ADMIN");
	}

	@Test
	void deposit_is_settled_after_return() throws Exception {
		String admin = adminToken();
		String user = signup();
		String number = call("POST", "/api/bookings", user, booking("24", null, window(3), null)).body().get("bookingNumber").asString();
		String path = "/api/admin/bookings/" + number + "/status";
		String dep = "/api/admin/bookings/" + number + "/deposit";
		assertThat(call("POST", dep, admin, java.util.Map.of("deduction", 0)).code()).isEqualTo("NOT_RETURNED");
		int deposit = confirmPaid(admin, number).body().get("deposit").asInt();
		call("PATCH", path, admin, java.util.Map.of("status", "IN_USE"));
		call("PATCH", path, admin, java.util.Map.of("status", "RETURNED"));
		assertThat(call("POST", dep, admin, java.util.Map.of("deduction", deposit + 1, "note", "x")).code()).isEqualTo("INVALID_DEDUCTION");
		assertThat(call("POST", dep, admin, java.util.Map.of("deduction", 1000)).code()).isEqualTo("INVALID_DEDUCTION");   // 사유 없음
		JsonNode s = call("POST", dep, admin, java.util.Map.of("deduction", 1000, "note", "바퀴 교체")).body();
		assertThat(s.get("depositDeduction").asInt()).isEqualTo(1000);
		assertThat(s.get("depositSettledAt").isNull()).isFalse();
		assertThat(call("POST", dep, admin, java.util.Map.of("deduction", 0)).code()).isEqualTo("ALREADY_SETTLED");
		// 고객도 정산 결과를 본다
		assertThat(call("GET", "/api/bookings/" + number, user, null).body().get("depositNote").asString()).isEqualTo("바퀴 교체");
	}

	@Test
	void email_verification_gates_booking() throws Exception {
		String email = "verify-" + UUID.randomUUID() + "@test.recarry";
		String token = call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "인증", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).session();
		// 인증번호 도입 전에 가입해 인증하지 않은 회원
		jdbc.update("UPDATE users SET email_verified_at = null WHERE email = ?", email);
		assertThat(call("GET", "/api/users/me", token, null).body().get("emailVerified").asBoolean()).isFalse();
		Res blocked = call("POST", "/api/bookings", token, booking("20", null, window(3), null));
		assertThat(blocked.status()).isEqualTo(403);
		assertThat(blocked.code()).isEqualTo("EMAIL_NOT_VERIFIED");

		// My Page 에서 인증 메일을 다시 받는다 — 링크의 토큰으로 인증
		mails.lastBody.remove(email);
		assertThat(call("POST", "/api/users/me/verify-email", token, null).status()).isEqualTo(204);
		for (int i = 0; i < 50 && !mails.lastBody.getOrDefault(email, "").contains("token="); i++) Thread.sleep(100);
		var m = java.util.regex.Pattern.compile("token=([A-Za-z0-9._-]+)").matcher(mails.lastBody.get(email));
		assertThat(m.find()).isTrue();
		String link = m.group(1);
		assertThat(call("POST", "/api/auth/verify-email", null, java.util.Map.of("token", link + "x")).code()).isEqualTo("INVALID_TOKEN");
		assertThat(call("POST", "/api/auth/verify-email", null, java.util.Map.of("token", "1.1.abc")).code()).isIn("INVALID_TOKEN", "TOKEN_EXPIRED");
		assertThat(call("POST", "/api/auth/verify-email", null, java.util.Map.of("token", link)).status()).isEqualTo(204);
		assertThat(call("GET", "/api/users/me", token, null).body().get("emailVerified").asBoolean()).isTrue();
		assertThat(call("POST", "/api/bookings", token, booking("20", null, window(3), null)).status()).isEqualTo(201);
		// 예약 접수 메일에는 입금 안내가 들어 있다
		for (int i = 0; i < 50 && !mails.lastBody.getOrDefault(email, "").contains("입금 계좌"); i++) Thread.sleep(100);
		assertThat(mails.lastBody.get(email)).contains("입금 계좌").contains("임시");
	}

	// ------------------------------------------------------------------ 가입 인증번호 · 카카오 로그인

	@Test
	void signup_requires_email_code() throws Exception {
		JsonNode opts = call("GET", "/api/auth/options", null, null).body();
		assertThat(opts.get("signupEmailCode").asBoolean()).isTrue();
		assertThat(opts.get("kakao").asBoolean()).isTrue();

		String email = "code-" + UUID.randomUUID() + "@test.recarry";
		java.util.Map<String, Object> body = new java.util.HashMap<>(java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "인증번호", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		body.put("emailCode", "");   // 자동으로 붙이지 않게
		Res none = call("POST", "/api/auth/signup", null, body);
		assertThat(none.status()).isEqualTo(400);
		assertThat(none.body().get("fields").has("emailCode")).isTrue();

		String[] code = emailCode(email);
		body.put("emailChallenge", code[0]);
		body.put("emailCode", code[1].equals("000000") ? "000001" : "000000");
		Res wrong = call("POST", "/api/auth/signup", null, body);
		assertThat(wrong.code()).isEqualTo("INVALID_CODE");
		assertThat(wrong.body().get("fields").get("emailCode").asString()).isNotBlank();
		// 다른 이메일의 challenge 로는 통과하지 않는다
		body.put("emailCode", code[1]);
		body.put("email", "other-" + email);
		assertThat(call("POST", "/api/auth/signup", null, body).code()).isEqualTo("INVALID_CODE");
		body.put("email", email);
		Res ok = call("POST", "/api/auth/signup", null, body);
		assertThat(ok.status()).isEqualTo(201);
		JsonNode u = ok.body().get("user");
		assertThat(u.get("emailVerified").asBoolean()).isTrue();
		assertThat(u.get("hasPassword").asBoolean()).isTrue();
		assertThat(u.get("kakao").asBoolean()).isFalse();
		assertThat(call("POST", "/api/bookings", ok.session(), booking("20", null, window(3), null)).status()).isEqualTo(201);
		// 이미 가입한 이메일은 인증번호를 보내지 않는다
		assertThat(rawSend("POST", "/api/auth/signup/email-code", null, java.util.Map.of("email", email), true).code()).isEqualTo("EMAIL_TAKEN");
	}

	/** 카카오 로그인 한 바퀴: start → (카카오) → callback. 결과 Location 과 쿠키들을 돌려준다 */
	record Kakao(String location, java.util.Map<String, String> cookies) {}

	Kakao kakao(String code, String returnTo, boolean tamperState) throws Exception {
		HttpResponse<String> start = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
			+ "/api/auth/kakao/start?returnTo=" + java.net.URLEncoder.encode(returnTo, StandardCharsets.UTF_8))).build(),
			HttpResponse.BodyHandlers.ofString());
		assertThat(start.statusCode()).isEqualTo(302);
		String loc = start.headers().firstValue("location").orElseThrow();
		assertThat(loc).contains("/oauth/authorize").contains("client_id=test-client").contains("redirect_uri=");
		var st = java.util.regex.Pattern.compile("state=([^&]+)").matcher(loc);
		assertThat(st.find()).isTrue();
		String state = tamperState ? st.group(1) + "x" : st.group(1);
		String stateCookie = cookies(start).get("recarry_kakao_state");
		assertThat(start.headers().allValues("set-cookie").get(0)).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/auth/kakao");
		HttpResponse<String> cb = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
			+ "/api/auth/kakao/callback?code=" + code + "&state=" + state)).header("Cookie", "recarry_kakao_state=" + stateCookie).build(),
			HttpResponse.BodyHandlers.ofString());
		assertThat(cb.statusCode()).isEqualTo(302);
		return new Kakao(cb.headers().firstValue("location").orElseThrow(), cookies(cb));
	}

	static java.util.Map<String, String> cookies(HttpResponse<?> r) {
		java.util.Map<String, String> m = new java.util.HashMap<>();
		for (String c : r.headers().allValues("set-cookie")) {
			String nv = c.substring(0, c.indexOf(';'));
			m.put(nv.substring(0, nv.indexOf('=')), nv.substring(nv.indexOf('=') + 1));
		}
		return m;
	}

	static String kakaoUser(long id, String email, boolean verified, String name, String phone) {
		return "{\"id\":" + id + ",\"kakao_account\":{\"email\":\"" + email + "\",\"is_email_verified\":" + verified
			+ ",\"is_email_valid\":true,\"name\":\"" + name + "\",\"phone_number\":\"" + phone + "\"}}";
	}

	Res withCookie(String method, String path, String cookie, Object body) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("Content-Type", "application/json").header("X-Recarry-Client", "web").header("Cookie", cookie);
		b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
		HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return new Res(r.statusCode(), r.body().isEmpty() ? json.nullNode() : json.readTree(r.body()), r.headers().allValues("set-cookie"));
	}

	@Test
	void kakao_new_user_signs_up_then_logs_in() throws Exception {
		long id = 9_000_000_000L + WINDOW.incrementAndGet();
		String email = "kakao-" + UUID.randomUUID() + "@test.recarry";
		KAKAO_USERS.put("code-new-" + id, kakaoUser(id, email, true, "카카오", "+82 10-9876-5432"));

		// 처음: 가입 대기 쿠키를 두고 가입 화면으로 (개인정보는 URL 에 없다)
		Kakao first = kakao("code-new-" + id, "/booking", false);
		assertThat(first.location()).isEqualTo("/signup?via=kakao");
		String pending = first.cookies().get("recarry_kakao_pending");
		assertThat(pending).isNotBlank();
		assertThat(first.cookies()).doesNotContainKey(COOKIE);
		JsonNode view = withCookie("GET", "/api/auth/kakao/pending", "recarry_kakao_pending=" + pending, null).body();
		assertThat(view.get("email").asString()).isEqualTo(email);
		assertThat(view.get("phone").asString()).isEqualTo("010-9876-5432");
		assertThat(view.get("returnTo").asString()).isEqualTo("/booking");
		assertThat(view.has("kakaoId")).isFalse();
		// 위조한 쿠키는 통하지 않는다
		assertThat(withCookie("GET", "/api/auth/kakao/pending", "recarry_kakao_pending=" + pending + "x", null).status()).isEqualTo(404);
		assertThat(call("POST", "/api/auth/kakao/signup", null, java.util.Map.of("email", email, "name", "카카오",
			"phone", "010-9876-5432", "agreeTerms", true, "agreePrivacy", true)).code()).isEqualTo("NO_PENDING");

		// 카카오가 확인한 이메일 그대로 가입 → 인증번호 없이 인증된 계정
		Res s = withCookie("POST", "/api/auth/kakao/signup", "recarry_kakao_pending=" + pending, java.util.Map.of("email", email,
			"name", "카카오", "phone", "010-9876-5432", "agreeTerms", true, "agreePrivacy", true));
		assertThat(s.status()).isEqualTo(201);
		JsonNode u = s.body().get("user");
		assertThat(u.get("kakao").asBoolean()).isTrue();
		assertThat(u.get("hasPassword").asBoolean()).isFalse();
		assertThat(u.get("emailVerified").asBoolean()).isTrue();
		assertThat(u.get("consentRequired").asBoolean()).isFalse();
		String session = s.session();
		assertThat(call("POST", "/api/bookings", session, booking("20", null, window(3), null)).status()).isEqualTo(201);

		// 다시 카카오로 → 바로 로그인하고 원래 가던 곳으로
		Kakao again = kakao("code-new-" + id, "/mypage", false);
		assertThat(again.location()).isEqualTo("/mypage");
		assertThat(again.cookies().get(COOKIE)).isNotBlank();
		assertThat(call("GET", "/api/users/me", again.cookies().get(COOKIE), null).body().get("email").asString()).isEqualTo(email);

		// 비밀번호가 없는 계정: 비밀번호 변경은 안내, 탈퇴는 비밀번호 없이
		assertThat(call("POST", "/api/users/me/password", session, java.util.Map.of("currentPassword", "x", "newPassword", "passw0rd!2")).code())
			.isEqualTo("NO_PASSWORD");
		String number = call("GET", "/api/bookings", session, null).body().get(0).get("bookingNumber").asString();
		call("POST", "/api/bookings/" + number + "/cancel", session, null);
		assertThat(call("POST", "/api/users/me/withdraw", session, java.util.Map.of()).status()).isEqualTo(204);
		// 탈퇴하면 같은 카카오 계정으로 새로 가입할 수 있다
		assertThat(kakao("code-new-" + id, "/", false).location()).isEqualTo("/signup?via=kakao");
	}

	@Test
	void kakao_links_existing_account_by_verified_email() throws Exception {
		// 인증번호로 가입한 기존 회원 — 같은 이메일의 카카오로 들어오면 연결, 비밀번호는 그대로
		String email = "link-" + UUID.randomUUID() + "@test.recarry";
		assertThat(call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "기존", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).status()).isEqualTo(201);
		long id = 9_100_000_000L + WINDOW.incrementAndGet();
		KAKAO_USERS.put("code-link-" + id, kakaoUser(id, email.toUpperCase(), true, "기존", "010-1234-5678"));
		Kakao k = kakao("code-link-" + id, "/booking", false);
		assertThat(k.location()).isEqualTo("/booking");
		JsonNode me = call("GET", "/api/users/me", k.cookies().get(COOKIE), null).body();
		assertThat(me.get("kakao").asBoolean()).isTrue();
		assertThat(me.get("hasPassword").asBoolean()).isTrue();
		assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "passw0rd!")).status()).isEqualTo(200);

		// 이메일 인증을 안 한 계정에 연결하면: 남이 먼저 만든 계정일 수 있으니 비밀번호를 무효로 하고 옛 로그인을 끊는다
		String squat = "squat-" + UUID.randomUUID() + "@test.recarry";
		String old = call("POST", "/api/auth/signup", null, java.util.Map.of("email", squat, "password", "passw0rd!",
			"name", "선점", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).session();
		jdbc.update("UPDATE users SET email_verified_at = null WHERE email = ?", squat);
		Thread.sleep(1100);   // 토큰 iat 는 초 단위
		long id2 = 9_200_000_000L + WINDOW.incrementAndGet();
		KAKAO_USERS.put("code-squat-" + id2, kakaoUser(id2, squat, true, "주인", "010-1234-5678"));
		Kakao owner = kakao("code-squat-" + id2, "/", false);
		assertThat(owner.location()).isEqualTo("/");
		assertThat(call("GET", "/api/users/me", old, null).status()).isEqualTo(401);
		assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", squat, "password", "passw0rd!")).status()).isEqualTo(401);
		assertThat(call("GET", "/api/users/me", owner.cookies().get(COOKIE), null).body().get("hasPassword").asBoolean()).isFalse();

		// 카카오가 이메일을 확인하지 않았으면 연결하지 않고 가입 화면으로 (이메일을 바꾸면 인증번호 필요)
		long id3 = 9_300_000_000L + WINDOW.incrementAndGet();
		KAKAO_USERS.put("code-unv-" + id3, kakaoUser(id3, email, false, "미인증", "010-1234-5678"));
		Kakao unv = kakao("code-unv-" + id3, "/", false);
		assertThat(unv.location()).isEqualTo("/signup?via=kakao");
		String pending = "recarry_kakao_pending=" + unv.cookies().get("recarry_kakao_pending");
		assertThat(withCookie("POST", "/api/auth/kakao/signup", pending, java.util.Map.of("email", email, "name", "미인증",
			"phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).code()).isEqualTo("EMAIL_TAKEN");
		String fresh = "fresh-" + UUID.randomUUID() + "@test.recarry";
		Res noCode = withCookie("POST", "/api/auth/kakao/signup", pending, java.util.Map.of("email", fresh, "name", "미인증",
			"phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		assertThat(noCode.body().get("fields").has("emailCode")).isTrue();
		String[] code = emailCode(fresh);
		Res withCode = withCookie("POST", "/api/auth/kakao/signup", pending, java.util.Map.of("email", fresh, "name", "미인증",
			"phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true, "emailChallenge", code[0], "emailCode", code[1]));
		assertThat(withCode.status()).isEqualTo(201);
		assertThat(withCode.body().get("user").get("emailVerified").asBoolean()).isTrue();
	}

	@Test
	void kakao_rejects_bad_state_and_never_links_admin() throws Exception {
		long id = 9_400_000_000L + WINDOW.incrementAndGet();
		KAKAO_USERS.put("code-state-" + id, kakaoUser(id, "state-" + id + "@test.recarry", true, "상태", "010-1234-5678"));
		Kakao bad = kakao("code-state-" + id, "/", true);
		assertThat(bad.location()).isEqualTo("/login?error=kakao");
		assertThat(bad.cookies()).doesNotContainKey(COOKIE);
		// 다른 사이트로 보내는 returnTo 는 / 로
		assertThat(kakao("code-state-" + id, "//evil.example", false).cookies()).containsKey("recarry_kakao_pending");
		assertThat(withCookie("GET", "/api/auth/kakao/pending", "recarry_kakao_pending="
			+ kakao("code-state-" + id, "https://evil.example", false).cookies().get("recarry_kakao_pending"), null)
			.body().get("returnTo").asString()).isEqualTo("/");
		// 카카오 오류 (모르는 코드)
		assertThat(kakao("unknown-code", "/", false).location()).isEqualTo("/login?error=kakao");

		long admin = 9_500_000_000L + WINDOW.incrementAndGet();
		KAKAO_USERS.put("code-admin-" + admin, kakaoUser(admin, ADMIN_EMAIL, true, "관리자", "010-1234-5678"));
		Kakao a = kakao("code-admin-" + admin, "/admin", false);
		assertThat(a.location()).isEqualTo("/login?error=kakao_admin");
		assertThat(a.cookies()).doesNotContainKey(COOKIE);
		assertThat(jdbc.queryForObject("SELECT kakao_id FROM users WHERE email = ?", Long.class, ADMIN_EMAIL)).isNull();
	}

	@Test
	void kakao_phone_numbers_are_normalized() {
		assertThat(com.recarry.auth.KakaoAuth.phone("+82 10-1234-5678")).isEqualTo("010-1234-5678");
		assertThat(com.recarry.auth.KakaoAuth.phone("+82 2-123-4567")).isEqualTo("+82 2-123-4567");
		assertThat(com.recarry.auth.KakaoAuth.phone(null)).isNull();
	}

	// ------------------------------------------------------------------ 관리자 2단계 인증 · 접속기록

	@Test
	void totp_matches_rfc6238_vectors() {
		String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";   // ASCII "12345678901234567890"
		assertThat(com.recarry.auth.Totp.code(secret, com.recarry.auth.Totp.step(59))).isEqualTo("287082");
		assertThat(com.recarry.auth.Totp.code(secret, com.recarry.auth.Totp.step(1111111109))).isEqualTo("081804");
	}

	@Test
	void admin_two_factor_login() throws Exception {
		String admin = adminToken();
		JsonNode setup = call("POST", "/api/admin/2fa/setup", admin, null).body();
		String secret = setup.get("secret").asString();
		assertThat(setup.get("uri").asString()).startsWith("otpauth://totp/");
		long now = com.recarry.auth.Totp.step(java.time.Instant.now().getEpochSecond());
		assertThat(call("POST", "/api/admin/2fa/enable", admin, java.util.Map.of("code", "000000")).code()).isIn("INVALID_OTP", null);
		String code = com.recarry.auth.Totp.code(secret, now);
		assertThat(call("POST", "/api/admin/2fa/enable", admin, java.util.Map.of("code", code)).status()).isEqualTo(204);
		try {
			// 비밀번호만으로는 로그인할 수 없다 (쿠키도 주지 않는다)
			Res noOtp = call("POST", "/api/auth/login", null, java.util.Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
			assertThat(noOtp.status()).isEqualTo(401);
			assertThat(noOtp.code()).isEqualTo("OTP_REQUIRED");
			assertThat(noOtp.session()).isNull();
			// 이미 쓴 코드(같은 시간 단계)는 다시 쓸 수 없다
			assertThat(call("POST", "/api/auth/login", null,
				java.util.Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD, "otp", code)).code()).isEqualTo("INVALID_OTP");
			String next = com.recarry.auth.Totp.code(secret, now + 1);   // 다음 30초 단계 (±1 허용)
			Res ok = call("POST", "/api/auth/login", null, java.util.Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD, "otp", next));
			assertThat(ok.status()).isEqualTo(200);
			admin = ok.session();
		} finally {
			// 다른 테스트가 비밀번호만으로 관리자 로그인하므로 끈다
			jdbc.update("UPDATE users SET totp_enabled = false, totp_secret = null, totp_last_step = null WHERE email = ?", ADMIN_EMAIL);
		}
		// 사용자는 2단계 인증 설정에 닿지 못한다
		assertThat(call("POST", "/api/admin/2fa/setup", signup(), null).status()).isEqualTo(403);
	}

	@Test
	void admin_viewing_customer_data_is_logged() throws Exception {
		String admin = adminToken();
		String user = signup();
		String number = call("POST", "/api/bookings", user, booking("20", null, window(3), null)).body().get("bookingNumber").asString();
		assertThat(call("GET", "/api/admin/bookings/" + number, admin, null).status()).isEqualTo(200);
		JsonNode views = call("GET", "/api/admin/audit-logs?kind=VIEW", admin, null).body();
		boolean found = false;
		for (JsonNode l : views) if (l.get("action").asString().equals("BOOKING_VIEW") && l.get("target").asString().equals(number)) found = true;
		assertThat(found).isTrue();
		// 변경 기록 목록에는 조회가 섞이지 않는다
		for (JsonNode l : call("GET", "/api/admin/audit-logs", admin, null).body()) assertThat(l.get("action").asString()).doesNotEndWith("_VIEW");
	}

	@Test
	void role_change_takes_effect_on_existing_tokens() throws Exception {
		String email = "role-" + UUID.randomUUID() + "@test.recarry";
		String token = call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!",
			"name", "권한", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true)).session();
		assertThat(call("GET", "/api/bookings", token, null).status()).isEqualTo(200);
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
		// 토큰에 적힌 권한이 지금 권한과 다르면 다시 로그인해야 한다
		assertThat(call("GET", "/api/bookings", token, null).status()).isEqualTo(401);
		jdbc.update("UPDATE users SET role = 'USER' WHERE email = ?", email);
	}

	@Test
	void admin_changes_are_recorded_in_audit_log() throws Exception {
		String admin = adminToken();
		String user = signup();
		String number = call("POST", "/api/bookings", user, booking("24", null, window(3), null)).body().get("bookingNumber").asString();
		call("PATCH", "/api/admin/bookings/" + number + "/status", admin, java.util.Map.of("status", "CANCELLED"));
		// 결제 전 요청을 운영자가 취소하면 "거절"(REJECTED)
		assertThat(call("GET", "/api/bookings/" + number, user, null).body().get("cancelReason").asString()).isEqualTo("REJECTED");

		Res logs = call("GET", "/api/admin/audit-logs", admin, null);
		assertThat(logs.status()).isEqualTo(200);
		boolean found = false;
		for (JsonNode l : logs.body()) {
			if (l.get("target").asString().equals(number)) {
				found = true;
				assertThat(l.get("action").asString()).isEqualTo("BOOKING_STATUS");
				assertThat(l.get("detail").asString()).isEqualTo("REQUESTED → CANCELLED");
				assertThat(l.get("admin").asString()).isEqualTo(ADMIN_EMAIL);
			}
		}
		assertThat(found).isTrue();
		assertThat(call("GET", "/api/admin/audit-logs", user, null).status()).isEqualTo(403);
	}

	// ------------------------------------------------------------------ cookie session · CSRF · rate limit

	@Test
	void session_cookie_is_httponly_and_token_is_not_in_body() throws Exception {
		String email = "cookie-" + UUID.randomUUID() + "@test.recarry";
		Res s = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", email, "password", "passw0rd!", "name", "쿠키", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		assertThat(s.body().has("token")).isFalse();
		String cookie = s.setCookies().stream().filter(c -> c.startsWith(COOKIE + "=")).findFirst().orElseThrow();
		assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api").contains("Secure");

		// 세션 확인: 쿠키가 있으면 사용자, 없으면 user=null (401 아님)
		assertThat(call("GET", "/api/auth/session", s.session(), null).body().get("user").get("email").asString()).isEqualTo(email);
		Res anonymous = call("GET", "/api/auth/session", null, null);
		assertThat(anonymous.status()).isEqualTo(200);
		assertThat(anonymous.body().get("user").isNull()).isTrue();

		// 로그아웃은 쿠키를 지운다 → 쿠키 없는 요청은 보호 API 에서 401
		Res out = call("POST", "/api/auth/logout", s.session(), null);
		assertThat(out.status()).isEqualTo(204);
		assertThat(out.setCookies()).anyMatch(c -> c.startsWith(COOKIE + "=;") && c.contains("Max-Age=0"));
		assertThat(call("GET", "/api/bookings", null, null).status()).isEqualTo(401);
	}

	@Test
	void expired_or_forged_cookie_is_rejected_but_public_api_still_works() throws Exception {
		String expired = expiredToken();
		assertThat(call("GET", "/api/users/me", expired, null).status()).isEqualTo(401);
		assertThat(call("GET", "/api/bookings", "forged.token.value", null).status()).isEqualTo(401);
		// 나쁜 쿠키가 남아 있어도 공개 카탈로그는 열린다
		assertThat(call("GET", "/api/carriers", expired, null).status()).isEqualTo(200);
		Res session = call("GET", "/api/auth/session", expired, null);
		assertThat(session.body().get("user").isNull()).isTrue();
		assertThat(session.setCookies()).anyMatch(c -> c.contains("Max-Age=0"));
	}

	@Test
	void state_changing_requests_need_the_client_header() throws Exception {
		String user = signup();
		// 다른 사이트의 form 전송처럼 헤더 없이 오면 쿠키가 있어도 거절
		assertThat(send("POST", "/api/bookings", user, booking("24", null, window(3), null), false).status()).isEqualTo(403);
		assertThat(send("POST", "/api/auth/login", null,
			java.util.Map.of("email", "x@test.recarry", "password", "passw0rd!"), false).status()).isEqualTo(403);
		// 조회(GET)는 헤더가 없어도 된다
		assertThat(send("GET", "/api/bookings", user, null, false).status()).isEqualTo(200);
	}

	@Test
	void repeated_login_failures_are_throttled() throws Exception {
		String email = "limit-" + UUID.randomUUID() + "@test.recarry";
		call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!", "name", "제한", "phone", "010-1234-5678", "agreeTerms", true, "agreePrivacy", true));
		for (int i = 0; i < 5; i++) {
			assertThat(call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "wrong-pass1")).status()).isEqualTo(401);
		}
		// 한도를 넘으면 맞는 비밀번호도 잠시 막힌다
		Res blocked = call("POST", "/api/auth/login", null, java.util.Map.of("email", email, "password", "passw0rd!"));
		assertThat(blocked.status()).isEqualTo(429);
		assertThat(blocked.code()).isEqualTo("TOO_MANY_ATTEMPTS");
	}

	@Test
	void unknown_booking_is_404() throws Exception {
		String user = signup();
		assertThat(call("GET", "/api/bookings/RB-000000-XXXXXX", user, null).status()).isEqualTo(404);
		assertThat(call("GET", "/api/admin/bookings/RB-000000-XXXXXX", adminToken(), null).status()).isEqualTo(404);
	}

	/** 테스트와 같은 키로 서명했지만 이미 만료된 토큰 */
	String expiredToken() throws Exception {
		var key = new javax.crypto.spec.SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
		var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("recarry").subject("1")
			.issuedAt(java.time.Instant.now().minusSeconds(7200)).expiresAt(java.time.Instant.now().minusSeconds(3600))
			.claim("roles", List.of("ADMIN")).build();
		var header = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
		return encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
