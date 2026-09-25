package com.recarry.auth;

import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청에서 JWT 를 찾는다: 세션 쿠키 → (없으면) Authorization: Bearer 헤더(API 도구용).
 * 공개 API 에서는 토큰을 보지 않는다 — 만료된 쿠키가 남아 있어도 카탈로그 조회가 401 이 되지 않게.
 */
public class CookieTokenResolver implements BearerTokenResolver {

	private final SessionCookies cookies;
	private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

	public CookieTokenResolver(SessionCookies cookies) {
		this.cookies = cookies;
	}

	@Override
	public String resolve(HttpServletRequest req) {
		if (isPublic(req)) return null;
		return cookies.read(req).orElseGet(() -> header.resolve(req));
	}

	static boolean isPublic(HttpServletRequest req) {
		String path = req.getRequestURI();
		String method = req.getMethod();
		if (HttpMethod.GET.matches(method) && (path.equals("/api/carriers") || path.startsWith("/api/carriers/"))) return true;
		if (HttpMethod.GET.matches(method) && (path.equals("/api/auth/session") || path.equals("/api/health"))) return true;
		return HttpMethod.POST.matches(method)
			&& (path.equals("/api/auth/login") || path.equals("/api/auth/signup") || path.equals("/api/auth/logout"));
	}
}
