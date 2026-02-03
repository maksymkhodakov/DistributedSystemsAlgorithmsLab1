package com.example;

public enum Direction {
    LEFT,
    RIGHT;

    public Direction opposite() {
        return this == LEFT ? RIGHT : LEFT;
    }
}
