package com.recarry.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.common.ApiException;
import com.recarry.common.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 로그인 (REST API, 인가 코드 방식). SDK 없이 서버가 카카오와 직접 통신한다.
 * start → 카카오 동의 화면 → callback: 연결된 계정이면 로그인, 아니면 가입 대기 쿠키를 두고 /signup?via=kakao 로 보낸다.
 * 개인정보는 URL 에 싣지 않는다 — 가입 대기 정보는 서명한 httpOnly 쿠키(15분)에만 있다.
 * KAKAO_CLIENT_ID · KAKAO_CLIENT_SECRET 이 없으면 꺼진다 (/login?error=kakao_off).
 */
@RestController
@RequestMapping("/api/auth/kakao")
public class KakaoAuth {

	private static final Logger log = LoggerFactory.getLogger(KakaoAuth.class);
	static final String STATE = "recarry_kakao_state";
	static final String PENDING = "recarry_kakao_pending";
	private static final String PATH = "/api/auth/kakao";
	private static final Duration PENDING_TTL = Duration.ofMinutes(15);
	private static final SecureRandom RANDOM = new SecureRandom();

	/** 카카오에서 받은 회원 정보 */
	public record Profile(long id, String email, boolean emailVerified, String name, String phone) {}

	/** 가입 대기 — 쿠키에 서명해서 둔다 */
	public record Pending(long kakaoId, String email, boolean emailVerified, String name, String phone, String returnTo, long exp) {}

	/** 가입 화면이 미리 채울 정보 (카카오 회원번호는 보내지 않는다) */
	public record PendingView(String email, boolean emailVerified, String name, String phone, String returnTo) {}

	private final AuthService auth;
	private final SessionCookies sessions;
	private final AppProperties props;
	private final SecretKey key;
	private final Clock clock;
	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public KakaoAuth(AuthService auth, SessionCookies sessions, AppProperties props, SecretKey jwtKey, Clock clock) {
		this.auth = auth;
		this.sessions = sessions;
		this.props = props;
		this.key = jwtKey;
		this.clock = clock;
	}

	@GetMapping("/start")
	public void start(@RequestParam(required = false) String returnTo, HttpServletResponse res) {
		AppProperties.Kakao k = props.kakao();
		if (!k.configured()) {
			redirect(res, "/login?error=kakao_off");
			return;
		}
		byte[] b = new byte[24];
		RANDOM.nextBytes(b);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
		cookie(res, STATE, state + "." + b64(safe(returnTo)), Duration.ofMinutes(10));
		redirect(res, k.authBase() + "/oauth/authorize?response_type=code&client_id=" + enc(k.clientId())
			+ "&redirect_uri=" + enc(redirectUri()) + "&state=" + state);
	}

	@GetMapping("/callback")
	public void callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
			@RequestParam(required = false) String error, HttpServletRequest req, HttpServletResponse res) {
		String[] saved = read(req, STATE).map(v -> v.split("\\.", 2)).orElse(new String[0]);
		cookie(res, STATE, "", Duration.ZERO);
		// 우리가 시작한 로그인인지 (state) — 다른 사람의 인가 코드로 로그인시키는 공격을 막는다
		if (error != null || code == null || state == null || saved.length != 2 || !constantEquals(saved[0], state)) {
			redirect(res, "/login?error=kakao");
			return;
		}
		String returnTo = safe(unb64(saved[1]));
		Profile p;
		try {
			p = fetchProfile(code);
		} catch (Exception e) {
			log.warn("kakao login failed: {}", e.toString());
			redirect(res, "/login?error=kakao");
			return;
		}
		AuthService.Session s;
		try {
			s = auth.kakaoLogin(p);
		} catch (ApiException e) {
			redirect(res, "/login?error=kakao_admin");
			return;
		}
		if (s != null) {
			sessions.issue(res, s.issued().token(), s.issued().expiresAt());
			redirect(res, returnTo);
			return;
		}
		Pending pending = new Pending(p.id(), p.email(), p.emailVerified(), p.name(), p.phone(), returnTo,
			clock.instant().plus(PENDING_TTL).getEpochSecond());
		String payload = b64(json.writeValueAsString(pending));
		cookie(res, PENDING, payload + "." + Hmac.sign(key, "kakao-pending:" + payload), PENDING_TTL);
		redirect(res, "/signup?via=kakao");
	}

	@GetMapping("/pending")
	public PendingView pending(HttpServletRequest req) {
		Pending p = pendingOf(req).orElseThrow(() -> ApiException.notFound("NO_PENDING", "카카오 가입 정보가 없습니다. 다시 시도해주세요."));
		return new PendingView(p.email(), p.emailVerified(), p.name(), p.phone(), p.returnTo());
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthDtos.AuthResponse signup(@Valid @RequestBody AuthDtos.KakaoSignupRequest body, HttpServletRequest req,
			HttpServletResponse res) {
		Pending p = pendingOf(req).orElseThrow(() -> ApiException.badRequest("NO_PENDING", "카카오 가입 시간이 지났습니다. 다시 시도해주세요."));
		AuthService.Session s = auth.kakaoSignup(body, p);
		cookie(res, PENDING, "", Duration.ZERO);
		sessions.issue(res, s.issued().token(), s.issued().expiresAt());
		return new AuthDtos.AuthResponse(s.issued().expiresAt(), s.user());
	}

	// ------------------------------------------------------------------ 카카오 API

	private Profile fetchProfile(String code) throws Exception {
		AppProperties.Kakao k = props.kakao();
		String form = "grant_type=authorization_code&client_id=" + enc(k.clientId()) + "&redirect_uri=" + enc(redirectUri())
			+ "&code=" + enc(code) + "&client_secret=" + enc(k.clientSecret());
		JsonNode token = call(HttpRequest.newBuilder(URI.create(k.authBase() + "/oauth/token"))
			.header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
			.POST(HttpRequest.BodyPublishers.ofString(form)));
		JsonNode me = call(HttpRequest.newBuilder(URI.create(k.apiBase() + "/v2/user/me"))
			.header("Authorization", "Bearer " + token.path("access_token").asString()).GET());
		JsonNode a = me.path("kakao_account");
		String email = a.path("email").asString(null);
		boolean verified = email != null && a.path("is_email_verified").asBoolean(false) && a.path("is_email_valid").asBoolean(false);
		String name = a.path("name").asString(null);
		if (name == null || name.isBlank()) name = a.path("profile").path("nickname").asString(null);
		long id = me.path("id").asLong(0);
		if (id == 0) throw new IllegalStateException("no kakao id");
		return new Profile(id, email, verified, name, phone(a.path("phone_number").asString(null)));
	}

	private JsonNode call(HttpRequest.Builder b) throws Exception {
		HttpResponse<String> r = http.send(b.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) throw new IllegalStateException("kakao " + r.statusCode());
		return json.readTree(r.body());
	}

	/** "+82 10-1234-5678" → "010-1234-5678" (형식을 모르면 그대로) */
	public static String phone(String raw) {
		if (raw == null) return null;
		String d = raw.replaceAll("[^0-9]", "");
		if (d.startsWith("82")) d = "0" + d.substring(2);
		if (d.length() == 11) return d.substring(0, 3) + "-" + d.substring(3, 7) + "-" + d.substring(7);
		if (d.length() == 10) return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
		return raw;
	}

	// ------------------------------------------------------------------ 쿠키 · 이동

	private Optional<Pending> pendingOf(HttpServletRequest req) {
		String[] v = read(req, PENDING).map(x -> x.split("\\.", 2)).orElse(new String[0]);
		if (v.length != 2 || !Hmac.matches(key, "kakao-pending:" + v[0], v[1])) return Optional.empty();
		try {
			Pending p = json.readValue(unb64(v[0]), Pending.class);
			return clock.instant().getEpochSecond() > p.exp() ? Optional.empty() : Optional.of(p);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private String redirectUri() {
		return props.appUrl() + "/api/auth/kakao/callback";
	}

	/** 카카오에서 돌아오는 요청(다른 사이트에서 온 이동)에도 붙도록 Lax */
	private void cookie(HttpServletResponse res, String name, String value, Duration maxAge) {
		res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true).secure(props.cookie().secure())
			.sameSite("Lax").path(PATH).maxAge(maxAge).build().toString());
	}

	private static Optional<String> read(HttpServletRequest req, String name) {
		Cookie[] cs = req.getCookies();
		return cs == null ? Optional.empty()
			: Arrays.stream(cs).filter(c -> name.equals(c.getName()) && !c.getValue().isBlank()).map(Cookie::getValue).findFirst();
	}

	/** 상대 경로 Location — Vercel rewrite 뒤에서도 사이트 주소 그대로 이동한다 (sendRedirect 는 백엔드 호스트로 바꾼다) */
	private static void redirect(HttpServletResponse res, String location) {
		res.setStatus(HttpServletResponse.SC_FOUND);
		res.setHeader(HttpHeaders.LOCATION, location);
	}

	/** 우리 사이트 안의 경로만 (열린 리디렉트 방지) */
	static String safe(String returnTo) {
		// 제어 문자(탭 · 줄바꿈)는 브라우저가 지워 "/	/evil" → "//evil" 이 될 수 있어 막는다
		return returnTo != null && returnTo.startsWith("/") && !returnTo.startsWith("//") && !returnTo.contains("\\")
			&& returnTo.length() <= 200 && returnTo.chars().allMatch(c -> c > 0x20 && c != 0x7f) ? returnTo : "/";
	}

	private static boolean constantEquals(String a, String b) {
		return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String b64(String s) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	private static String unb64(String s) {
		try {
			return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return "";
		}
	}
}
