package com.warbox.account;

import java.time.LocalDate;
import java.time.Period;

/** Minimum age rule for creating an account. */
public final class AgePolicy {

    public static final int MINIMUM_AGE = 18;

    private AgePolicy() {}

    /** Age in completed years on {@code today}; someone turning 18 today is an adult. */
    public static boolean isAdult(LocalDate birthDate, LocalDate today) {
        return !birthDate.isAfter(today) && Period.between(birthDate, today).getYears() >= MINIMUM_AGE;
    }
}
