package com.chess.tournament.domain;

import java.time.Instant;
import java.util.Objects;

public final class Player {

    private Long id;
    private String name;
    private Integer age;
    private String country;
    private int globalRating;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public Player() {
        this.globalRating = 1500;
        this.active = true;
    }

    public Player(Long id, String name, Integer age, String country, int globalRating,
                  boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.country = country;
        this.globalRating = globalRating;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getGlobalRating() {
        return globalRating;
    }

    public void setGlobalRating(int globalRating) {
        this.globalRating = globalRating;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Player player)) {
            return false;
        }
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
