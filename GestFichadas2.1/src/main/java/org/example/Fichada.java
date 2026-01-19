package org.example;

import java.time.LocalDateTime;

/** Log crudo mínimo. */
public record Fichada(long id, LocalDateTime dateTime, Long userId) {}
