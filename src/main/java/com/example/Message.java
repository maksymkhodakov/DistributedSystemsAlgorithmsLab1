package com.example;

import lombok.RequiredArgsConstructor;

/**
 * Повідомлення HS алгоритму.
 * OUT  (isReply=false): "Я кандидат originId, фаза phase, пройду не більше ttl кроків"
 * REPLY(isReply=true):  "Твій OUT дійшов до межі (ttl==0) і повертається назад"
 * У симуляції кожна передача повідомлення по ребру = +1 до лічильника messages.
 */
@RequiredArgsConstructor
public final class Message {

    public final int originId;      // id кандидата (того, хто запустив хвилю)
    public final int phase;         // фаза k
    public final int ttl;           // залишок "хопів" (тільки для OUT)
    public final Direction dir;     // напрям руху цього повідомлення
    public final boolean isReply;   // false=OUT, true=REPLY

    /** Для OUT: зменшуємо ttl при пересиланні далі */
    public Message decTtl() {
        return new Message(originId, phase, ttl - 1, dir, isReply);
    }

    @Override
    public String toString() {
        return "Message{" +
                "originId=" + originId +
                ", phase=" + phase +
                ", ttl=" + ttl +
                ", dir=" + dir +
                ", isReply=" + isReply +
                '}';
    }
}

