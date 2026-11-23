package com.supportpilot.repository;

import com.supportpilot.entity.SlaHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SlaHolidayRepository extends JpaRepository<SlaHoliday, Long> {

    List<SlaHoliday> findByCalendarIdOrderByHolidayDateAsc(Long calendarId);

    boolean existsByCalendarIdAndHolidayDate(Long calendarId, LocalDate holidayDate);

    long deleteByCalendarId(Long calendarId);

    @Modifying
    @Query("delete from SlaHoliday h where h.calendarId = :calendarId and h.holidayDate = :date")
    int deleteOne(@Param("calendarId") Long calendarId, @Param("date") LocalDate date);
}
