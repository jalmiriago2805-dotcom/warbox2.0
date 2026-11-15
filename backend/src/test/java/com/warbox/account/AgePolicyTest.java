package com.warbox.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AgePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Test
    void eighteenthBirthdayTodayIsAdult() {
        assertThat(AgePolicy.isAdult(LocalDate.of(2008, 11, 15), TODAY)).isTrue();
    }

    @Test
    void eighteenthBirthdayTomorrowIsNotAdult() {
        assertThat(AgePolicy.isAdult(LocalDate.of(2008, 11, 16), TODAY)).isFalse();
    }

    @Test
    void olderPersonIsAdult() {
        assertThat(AgePolicy.isAdult(LocalDate.of(1950, 1, 1), TODAY)).isTrue();
    }

    @Test
    void futureBirthDateIsNotAdult() {
        assertThat(AgePolicy.isAdult(TODAY.plusDays(1), TODAY)).isFalse();
    }

    @Test
    void leapDayBirthdayFollowsPeriodBetween() {
        LocalDate leapBirth = LocalDate.of(2008, 2, 29);
        // 2026 is not a leap year: on Feb 28 the 18th year is not complete yet; on Mar 1 it is.
        assertThat(AgePolicy.isAdult(leapBirth, LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(AgePolicy.isAdult(leapBirth, LocalDate.of(2026, 3, 1))).isTrue();
    }
}
