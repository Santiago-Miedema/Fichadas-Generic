package org.example;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailySessionRow(
        LocalDate day,
        LocalDateTime in,
        LocalDateTime out,
        EstadoDia estado
) {}
