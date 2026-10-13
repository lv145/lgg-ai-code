package com.lgg.lggaicode.core;

public record GenerationEvent(
        long id,
        String event,
        String data
) {
}