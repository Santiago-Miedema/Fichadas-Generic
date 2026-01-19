package org.example;

import java.time.LocalDateTime;

/** Par entrada/salida ya emparejado. */
public record SessionPair(LocalDateTime in, LocalDateTime out) {}
