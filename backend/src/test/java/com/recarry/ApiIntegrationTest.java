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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
@Import(TestDatabaseConfig.class)
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
	}

	@LocalServerPort
	int port;

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
			java.util.Map.of("email", email, "password", "passw0rd!", "name", "테스터", "phone", "010-1234-5678"));
		assertThat(r.status()).isEqualTo(201);
		return r.session();
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
			java.util.Map.of("email", email, "password", "passw0rd!", "name", "김테스트", "phone", "010-1234-5678"));
		assertThat(s.status()).isEqualTo(201);
		assertThat(s.body().get("user").get("email").asString()).isEqualTo(email.toLowerCase());
		assertThat(s.body().get("user").has("passwordHash")).isFalse();

		// 대소문자만 다른 중복 가입은 거절
		Res dup = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", email.toUpperCase(), "password", "passw0rd!", "name", "x", "phone", "010-1234-5678"));
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
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "CONFIRMED")).status()).isEqualTo(200);
		// 확정된 예약은 사용자가 직접 취소할 수 없다
		assertThat(call("POST", "/api/bookings/" + number + "/cancel", user, null).code()).isEqualTo("NOT_CANCELLABLE");
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "IN_USE")).status()).isEqualTo(200);
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "RETURNED")).status()).isEqualTo(200);
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "CANCELLED")).status()).isEqualTo(409);
		assertThat(call("PATCH", statusPath, admin, java.util.Map.of("status", "NOPE")).status()).isEqualTo(400);

		// 캐리어 운영 상태: REPAIR 로 바꾸면 예약 가능 대수에서 빠진다. RESERVED 는 직접 지정할 수 없다
		Res carriers = call("GET", "/api/admin/carriers", admin, null);
		assertThat(carriers.body().size()).isEqualTo(6);
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

	// ------------------------------------------------------------------ cookie session · CSRF · rate limit

	@Test
	void session_cookie_is_httponly_and_token_is_not_in_body() throws Exception {
		String email = "cookie-" + UUID.randomUUID() + "@test.recarry";
		Res s = call("POST", "/api/auth/signup", null,
			java.util.Map.of("email", email, "password", "passw0rd!", "name", "쿠키", "phone", "010-1234-5678"));
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
		call("POST", "/api/auth/signup", null, java.util.Map.of("email", email, "password", "passw0rd!", "name", "제한", "phone", "010-1234-5678"));
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
