package com.recarry.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recarry.auth.AuthDtos.AuthResponse;
import com.recarry.auth.AuthDtos.LoginRequest;
import com.recarry.auth.AuthDtos.SessionResponse;
import com.recarry.auth.AuthDtos.SignupRequest;
import com.recarry.common.AppProperties;
import com.recarry.user.UserRepository;
import com.recarry.user.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 로그인하면 JWT 를 httpOnly 쿠키로 내려준다 (본문에는 토큰이 없다).
 * 로그아웃은 쿠키를 지운다. 서버에 세션이 없으므로, 이미 복사된 토큰은 만료(JWT_TTL_MINUTES)까지 유효하다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService auth;
	private final SessionCookies cookies;
	private final JwtDecoder decoder;
	private final UserRepository users;
	private final AppProperties props;
	private final PasswordResetService resets;

	public AuthController(AuthService auth, SessionCookies cookies, JwtDecoder decoder, UserRepository users,
			AppProperties props, PasswordResetService resets) {
		this.props = props;
		this.resets = resets;
		this.auth = auth;
		this.cookies = cookies;
		this.decoder = decoder;
		this.users = users;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse signup(@Valid @RequestBody SignupRequest req, HttpServletResponse res) {
		return start(auth.signup(req), res);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
		return start(auth.login(req), res);
	}

	/** 비밀번호 재설정 코드 요청 — 가입 여부와 관계없이 204 */
	@PostMapping("/password-reset/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void requestReset(@Valid @RequestBody AuthDtos.ResetRequest req, HttpServletRequest http) {
		resets.request(req.email());
	}

	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirmReset(@Valid @RequestBody AuthDtos.ResetConfirm req) {
		resets.confirm(req.email(), req.code(), req.newPassword());
	}

	@PostMapping("/find-email")
	public AuthDtos.FindEmailResponse findEmail(@Valid @RequestBody AuthDtos.FindEmailRequest req, HttpServletRequest http) {
		return new AuthDtos.FindEmailResponse(resets.findEmails(req.name(), req.phone()));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletResponse res) {
		cookies.clear(res);
	}

	/**
	 * 지금 로그인돼 있는가. 비로그인·만료·위조 쿠키는 401 대신 user=null 로 답한다
	 * (페이지를 열 때마다 콘솔에 401 이 찍히지 않게). 나쁜 쿠키는 이참에 지운다.
	 */
	@GetMapping("/session")
	@Transactional(readOnly = true)
	public SessionResponse session(HttpServletRequest req, HttpServletResponse res) {
		String token = cookies.read(req).orElse(null);
		if (token == null) return new SessionResponse(null);
		try {
			Long id = Long.valueOf(decoder.decode(token).getSubject());
			UserResponse user = users.findById(id).map(u -> UserResponse.of(u, props.legal())).orElse(null);
			if (user == null) cookies.clear(res);
			return new SessionResponse(user);
		} catch (JwtException | NumberFormatException e) {
			cookies.clear(res);
			return new SessionResponse(null);
		}
	}

	private AuthResponse start(AuthService.Session s, HttpServletResponse res) {
		cookies.issue(res, s.issued().token(), s.issued().expiresAt());
		return new AuthResponse(s.issued().expiresAt(), s.user());
	}
}
