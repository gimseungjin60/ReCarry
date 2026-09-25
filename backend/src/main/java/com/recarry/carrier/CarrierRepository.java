package com.recarry.carrier;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CarrierRepository extends JpaRepository<Carrier, Long> {

	@Query("select c from Carrier c join fetch c.model order by c.model.size, c.featured desc, c.code")
	List<Carrier> findAllWithModel();

	@Query("select c from Carrier c join fetch c.model where c.code = :code")
	Optional<Carrier> findByCode(@Param("code") String code);

	/** 예약 직전에 캐리어 행을 잠근다 — 같은 캐리어에 대한 예약 생성이 한 줄로 선다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Carrier c where c.id = :id")
	Optional<Carrier> lockById(@Param("id") Long id);

	/**
	 * 기간 [start-1, end] 동안 비어 있는 AVAILABLE 캐리어 id.
	 * 점유 판정은 DB 배타 제약(bookings_no_overlap)과 같은 daterange 식을 쓴다.
	 */
	@Query(value = """
		SELECT c.id FROM carriers c
		JOIN carrier_models m ON m.id = c.model_id
		WHERE m.size = :size AND c.status = 'AVAILABLE'
		  AND NOT EXISTS (
		    SELECT 1 FROM bookings b
		    WHERE b.carrier_id = c.id
		      AND b.status IN ('REQUESTED', 'CONFIRMED', 'IN_USE')
		      AND b.occupied && daterange(CAST(:start AS date) - 1, CAST(:end AS date), '[]'))
		ORDER BY c.featured DESC, c.id
		""", nativeQuery = true)
	List<Long> findFreeIds(@Param("size") String size, @Param("start") LocalDate start, @Param("end") LocalDate end);

	/** 이 캐리어가 기간 안에 이미 점유돼 있는가 (행 잠금 뒤 다시 확인할 때 쓴다) */
	@Query(value = """
		SELECT EXISTS (
		  SELECT 1 FROM bookings b
		  WHERE b.carrier_id = :carrierId
		    AND b.status IN ('REQUESTED', 'CONFIRMED', 'IN_USE')
		    AND b.occupied && daterange(CAST(:start AS date) - 1, CAST(:end AS date), '[]'))
		""", nativeQuery = true)
	boolean isOccupied(@Param("carrierId") Long carrierId, @Param("start") LocalDate start, @Param("end") LocalDate end);

	/** 오늘 점유 중인 캐리어와 그 예약 상태 — 표시 상태(RESERVED/RENTED) 계산용 */
	@Query(value = """
		SELECT b.carrier_id AS carrierId, b.status AS status FROM bookings b
		WHERE b.status IN ('REQUESTED', 'CONFIRMED', 'IN_USE')
		  AND b.occupied @> CAST(:day AS date)
		""", nativeQuery = true)
	List<OccupiedToday> findOccupiedOn(@Param("day") LocalDate day);

	interface OccupiedToday {
		Long getCarrierId();
		String getStatus();
	}
}
