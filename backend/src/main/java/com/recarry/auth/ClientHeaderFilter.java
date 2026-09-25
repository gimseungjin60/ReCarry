package com.recarry.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.recarry.common.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CSRF 방어. 쿠키는 브라우저가 자동으로 붙이므로, 상태를 바꾸는 /api 요청은
 * 우리 프론트만 붙이는 헤더(X-Recarry-Client)가 있어야 통과한다.
 * 다른 사이트의 form / img 요청은 이 헤더를 붙일 수 없고, fetch 로 붙이려 해도 CORS preflight 에서 막힌다.
 */
public class ClientHeaderFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Recarry-Client";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest req) {
		String m = req.getMethod();
		return !req.getRequestURI().startsWith("/api/") || m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		if (req.getHeader(HEADER) == null) {
			res.setStatus(HttpServletResponse.SC_FORBIDDEN);
			res.setContentType(MediaType.APPLICATION_JSON_VALUE);
			res.setCharacterEncoding(StandardCharsets.UTF_8.name());
			res.getWriter().write(ErrorResponse.of("FORBIDDEN", "허용되지 않은 요청입니다.").toJson());
			return;
		}
		chain.doFilter(req, res);
	}
}
