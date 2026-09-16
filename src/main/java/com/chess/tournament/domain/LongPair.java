package com.chess.tournament.domain;

import java.util.Objects;

/**
 * Unordered pair of tournament-player ids used for rematch detection.
 */
public record LongPair(long first, long second) {

    public LongPair {
        if (first > second) {
            long tmp = first;
            first = second;
            second = tmp;
        }
    }

    public static LongPair of(long a, long b) {
        return new LongPair(a, b);
    }

    public boolean contains(long id) {
        return id == first || id == second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LongPair other)) {
            return false;
        }
        return first == other.first && second == other.second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}
