package com.example;

/**
 * Вузол у кільці.
 * active=true  -> ще претендує на лідера
 * active=false -> вибув (побачив кандидата з більшим id)
 * HS логіка:
 * - у фазі k кандидат шле OUT вліво і вправо з hopLimit=2^k
 * - чекає 2 REPLY (зліва і справа)
 * - якщо отримав обидва, переходить до наступної фази k+1
 * - якщо його OUT повернувся до нього самого => він лідер
 */
public final class Node {

    public final int id;

    public boolean active = true;  // ще в грі?
    public int phase = 0;          // поточна фаза k

    // прапори "отримав відповідь" для поточної фази
    public boolean gotLeftReply = false;
    public boolean gotRightReply = false;

    // для симуляції: щоб не слати OUT для однієї і тієї ж фази багато разів
    public int startedPhase = -1;

    public Node(int id) {
        this.id = id;
    }

    /** Чи готовий кандидат стартувати OUT для своєї поточної фази? */
    public boolean needsToStartCurrentPhase() {
        return active && startedPhase != phase;
    }

    /** Скидання reply-прапорів при переході у наступну фазу */
    public void resetRepliesForNewPhase() {
        gotLeftReply = false;
        gotRightReply = false;
    }
}

