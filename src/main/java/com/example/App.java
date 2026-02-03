package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class App {

    public static void main(String[] args) {
        List<Integer> ids = new ArrayList<>();

        if (args.length > 0) {
            for (final String s : args) {
                ids.add(Integer.parseInt(s));
            }
        } else {
            ids = Arrays.asList(5, 12, 3, 9, 7, 1, 10);
        }

        RingNetwork ring = new RingNetwork(ids);
        ring.run();

        System.out.println("leaderId=" + ring.getLeaderId());
        System.out.println("rounds=" + ring.getRounds());
        System.out.println("messages=" + ring.getTotalMessagesSent());
    }
}
