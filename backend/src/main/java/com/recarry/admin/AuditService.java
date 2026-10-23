package com.recarry.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.recarry.user.User;
import com.recarry.user.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

/** 관리자 변경 기록. 기록은 변경과 같은 트랜잭션에 들어간다 (변경이 롤백되면 기록도 남지 않는다). */
@Service
public class AuditService {

	public record AuditResponse(Instant at, String admin, String action, String target, String detail, String ip) {}

	/** 기록 종류 — 변경 / 고객정보 조회 */
	public enum Kind { CHANGE, VIEW }

	private final AuditLogRepository logs;
	private final UserRepository users;

	AuditService(AuditLogRepository logs, UserRepository users) {
		this.logs = logs;
		this.users = users;
	}

	@Transactional
	public void record(Long adminId, String action, String target, String detail) {
		logs.save(new AuditLog(adminId, action, target, detail, clientIp()));
	}

	/** 고객 개인정보를 본 기록 (접속기록 보관 의무). action 은 _VIEW 로 끝난다 */
	@Transactional
	public void recordView(Long adminId, String what, String target) {
		record(adminId, what + "_VIEW", target, "");
	}

	/** 접속지는 참고용 — Vercel 을 거치면 정확하지 않을 수 있다 (보안 판단에는 쓰지 않는다) */
	private static String clientIp() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return "";
		HttpServletRequest req = attrs.getRequest();
		String xff = req.getHeader("X-Forwarded-For");
		return xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : req.getRemoteAddr();
	}

	/** 최근 기록부터 limit 개 */
	@Transactional(readOnly = true)
	public List<AuditResponse> recent(int limit, Kind kind) {
		PageRequest req = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
		List<AuditLog> page = (kind == Kind.VIEW ? logs.findByActionEndingWith("_VIEW", req) : logs.findByActionNotLike("%VIEW", req))
			.getContent();
		List<Long> adminIds = page.stream().map(AuditLog::getAdminId).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> emails = users.findAllById(adminIds).stream()
			.collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a, HashMap::new));
		return page.stream()
			.map(l -> new AuditResponse(l.getCreatedAt(), emails.getOrDefault(l.getAdminId(), "—"), l.getAction(),
				l.getTarget(), l.getDetail(), l.getIp()))
			.toList();
	}
}
