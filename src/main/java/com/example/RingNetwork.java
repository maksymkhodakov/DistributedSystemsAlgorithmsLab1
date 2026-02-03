package com.example;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Симуляція кільця + HS алгоритму.
 * моделюємо синхронні раунди:
 * - inbox[i]  : повідомлення, які вузол i отримує в цьому раунді
 * - outbox[i] : повідомлення, які будуть доставлені вузлу i в наступному раунді
 * Підрахунок messages:
 * - кожен виклик send(...) = 1 повідомлення (одна передача по ребру)
 */
public final class RingNetwork {

    private final List<Node> nodes;
    private final int n;

    private List<List<Message>> inbox;
    private List<List<Message>> outbox;

    @Getter
    private long totalMessagesSent = 0;
    @Getter
    private int rounds = 0;

    private Integer leaderId = null;

    public RingNetwork(List<Integer> ids) {
        validateUnique(ids);

        this.nodes = new ArrayList<>();
        for (final int id : ids) {
            nodes.add(new Node(id));
        }

        this.n = nodes.size();
        this.inbox = newEmptyBoxes();
        this.outbox = newEmptyBoxes();
    }

    private void validateUnique(List<Integer> ids) {
        Set<Integer> set = new HashSet<>(ids);
        if (set.size() != ids.size()) {
            throw new IllegalArgumentException("All node IDs must be unique for HS algorithm.");
        }
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Ring must contain at least 2 nodes.");
        }
    }

    private List<List<Message>> newEmptyBoxes() {
        List<List<Message>> boxes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) boxes.add(new ArrayList<>());
        return boxes;
    }

    private int leftOf(int i)  { return (i - 1 + n) % n; }
    private int rightOf(int i) { return (i + 1) % n; }

    /**
     * Надіслати повідомлення від вузла fromIdx у напрямку dir до сусіда.
     * Доставка буде в наступному раунді (тобто в outbox).
     */
    private void send(int fromIdx, Direction dir, Message msg) {
        int toIdx = dir == Direction.LEFT ? leftOf(fromIdx) : rightOf(fromIdx);
        outbox.get(toIdx).add(msg);
        totalMessagesSent++;
    }

    /** Старт поточної фази кандидата: OUT в обидва боки з hopLimit = 2^phase */
    private void startPhase(int idx) {
        final Node node = nodes.get(idx);
        if (!node.active) return;

        int hopLimit = 1 << node.phase; // 2^phase

        // OUT вліво
        send(idx, Direction.LEFT, new Message(node.id, node.phase, hopLimit, Direction.LEFT, false));

        // OUT вправо
        send(idx, Direction.RIGHT, new Message(node.id, node.phase, hopLimit, Direction.RIGHT, false));

        node.startedPhase = node.phase;
    }

    /**
     * Обробка одного повідомлення у вузлі idx.
     * Якщо знаходиться лідер — встановлюємо leaderId.
     */
    private void handleMessage(int idx, Message msg) {
        Node me = nodes.get(idx);

        if (!msg.isReply) {
            handlingOutMessage(idx, msg, me);

        } else {
            handlingReplyMessage(idx, msg, me);
        }
    }

    private void handlingReplyMessage(int idx, Message msg, Node me) {
        // ========= REPLY повідомлення =========
        if (msg.originId == me.id) {
            // REPLY повернувся до кандидата (до origin)
            // Приймаємо його тільки якщо фаза збігається (захист від "старих" reply)
            if (msg.phase != me.phase) {
                return;
            }

            // Напрямок REPLY показує, з якого боку він прийшов до origin:
            // якщо dir=LEFT -> reply прийшов зліва (бо рухався LEFT до origin)
            if (msg.dir == Direction.LEFT) {
                me.gotLeftReply = true;
            }
            else {
                me.gotRightReply = true;
            }

            // Якщо кандидат отримав відповіді з обох боків — фаза успішна:
            // у радіусі 2^k немає більшого id, отже можна подвоїти радіус (k++)
            if (me.gotLeftReply && me.gotRightReply) {
                me.phase++;
                me.resetRepliesForNewPhase();
                // Нову фазу стартуємо наприкінці раунду (в run()), щоб зберегти синхронність.
            }
        } else {
            // Це REPLY не для мене — просто пересилаємо далі в його напрямку
            send(idx, msg.dir, msg);
        }
    }

    private void handlingOutMessage(int idx, Message msg, Node me) {
        // ========= OUT повідомлення =========
        if (msg.originId == me.id) {
            // Моє власне OUT повернулося до мене (обійшло кільце) => я лідер
            leaderId = me.id;
            return;
        }

        if (msg.originId < me.id) {
            // Кандидат слабший за мене => "вбиваємо" його повідомлення (далі не пересилаємо)
            // Це ключова ідея HS: менші id відсіюються рано.
            return;
        }

        // msg.originId > me.id: я зустрів сильнішого кандидата
        // Отже я гарантовано не лідер => вибуваю (стаю неактивним)
        me.active = false;

        if (msg.ttl == 0) {
            // Дійшли до межі hopLimit => перетворюємо OUT на REPLY і шлемо назад
            Direction back = msg.dir.opposite();
            Message reply = new Message(msg.originId, msg.phase, 0, back, true);
            send(idx, back, reply);
        } else {
            // TTL ще є => пересилаємо далі в тому ж напрямку, зменшивши ttl
            send(idx, msg.dir, msg.decTtl());
        }
    }

    /**
     * Запуск симуляції HS до знаходження лідера.
     * rounds = кількість синхронних раундів обробки (inbox -> outbox -> inbox ...)
     */
    public void run() {
        // На початку всі активні вузли стартують фазу 0
        startPhasesForNeccessaryNodes();

        // Переходимо до 1-го раунду доставки
        inbox = outbox;
        outbox = newEmptyBoxes();
        rounds = 0;

        // Основний цикл раундів до пошуки лідера
        while (leaderId == null) {
            rounds++;

            // Обробляємо всі повідомлення, що прийшли в цьому раунді
            for (int i = 0; i < n && leaderId == null; i++) {
                for (final Message msg : inbox.get(i)) {
                    if (leaderId != null) {
                        break;
                    }
                    handleMessage(i, msg);
                }
            }

            if (leaderId != null) {
                break;
            }

            // Наприкінці раунду: кандидати, які перейшли в нову фазу, мають стартувати її
            startPhasesForNeccessaryNodes();

            // Доставка повідомлень на наступний раунд
            inbox = outbox;
            outbox = newEmptyBoxes();

            // якщо повідомлень нема, а лідера нема — щось не так
            safetyCheck();
        }
    }

    private void startPhasesForNeccessaryNodes() {
        for (int i = 0; i < n; i++) {
            if (nodes.get(i).needsToStartCurrentPhase()) {
                startPhase(i);
            }
        }
    }

    private void safetyCheck() {
        boolean anyInTransit = false;
        for (List<Message> box : inbox) {
            if (!box.isEmpty()) {
                anyInTransit = true; break;
            }
        }
        if (!anyInTransit) {
            throw new IllegalStateException("No messages in transit but leader not elected. Check correctness/IDs.");
        }
    }

    public int getLeaderId() {
        if (leaderId == null) {
            throw new IllegalStateException("Not finished yet.");
        }
        return leaderId;
    }
}
