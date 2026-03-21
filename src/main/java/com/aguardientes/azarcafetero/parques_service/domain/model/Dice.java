package com.aguardientes.azarcafetero.parques_service.domain.model;

import java.util.Random;

public class Dice {

    private static final Random RANDOM = new Random();
    private int die1;
    private int die2;

    public void roll() {
        this.die1 = RANDOM.nextInt(6) + 1;
        this.die2 = RANDOM.nextInt(6) + 1;
    }

    public int getDie1() { return die1; }
    public int getDie2() { return die2; }
    public int getTotal() { return die1 + die2; }
    public boolean isPair() { return die1 == die2; }
    public boolean isSpecialPair() { return isPair() && (die1 == 1 || die1 == 6); }
}
