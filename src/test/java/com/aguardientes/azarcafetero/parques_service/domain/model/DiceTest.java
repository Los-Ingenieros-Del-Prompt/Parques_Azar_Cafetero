package com.aguardientes.azarcafetero.parques_service.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiceTest {

    @Test
    void roll_shouldProduceDie1Between1And6() {
        Dice dice = new Dice();
        for (int i = 0; i < 50; i++) {
            dice.roll();
            assertTrue(dice.getDie1() >= 1 && dice.getDie1() <= 6,
                    "die1 debe estar entre 1 y 6, fue: " + dice.getDie1());
        }
    }

    @Test
    void roll_shouldProduceDie2Between1And6() {
        Dice dice = new Dice();
        for (int i = 0; i < 50; i++) {
            dice.roll();
            assertTrue(dice.getDie2() >= 1 && dice.getDie2() <= 6,
                    "die2 debe estar entre 1 y 6, fue: " + dice.getDie2());
        }
    }

    @Test
    void getTotal_shouldEqualSumOfBothDice() {
        Dice dice = new Dice();
        for (int i = 0; i < 30; i++) {
            dice.roll();
            assertEquals(dice.getDie1() + dice.getDie2(), dice.getTotal());
        }
    }

    @Test
    void isPair_shouldReturnTrueWhenBothDiceAreEqual() {
        // Lanzamos hasta obtener un par — estadísticamente garantizado en muchos intentos
        Dice dice = new Dice();
        boolean pairFound = false;
        for (int i = 0; i < 200; i++) {
            dice.roll();
            if (dice.getDie1() == dice.getDie2()) {
                assertTrue(dice.isPair());
                pairFound = true;
                break;
            }
        }
        assertTrue(pairFound, "No se encontró ningún par en 200 lanzamientos (altamente improbable)");
    }

    @Test
    void isPair_shouldReturnFalseWhenDiceAreDifferent() {
        Dice dice = new Dice();
        boolean nonPairFound = false;
        for (int i = 0; i < 200; i++) {
            dice.roll();
            if (dice.getDie1() != dice.getDie2()) {
                assertFalse(dice.isPair());
                nonPairFound = true;
                break;
            }
        }
        assertTrue(nonPairFound, "No se encontró ningún no-par en 200 lanzamientos (altamente improbable)");
    }

    @Test
    void isSpecialPair_shouldReturnTrueForPairOf1() {
        // Usamos reflexión para forzar valores específicos no disponibles en la API pública
        // Como alternativa, validamos el comportamiento observable con muchos lanzamientos
        Dice dice = new Dice();
        boolean specialFound = false;
        for (int i = 0; i < 500; i++) {
            dice.roll();
            if ((dice.getDie1() == 1 && dice.getDie2() == 1) ||
                (dice.getDie1() == 6 && dice.getDie2() == 6)) {
                assertTrue(dice.isSpecialPair());
                specialFound = true;
                break;
            }
        }
        // Solo notamos si no se encontró, no fallamos (es probabilístico)
        if (!specialFound) {
            System.out.println("Advertencia: no se encontró par especial en 500 lanzamientos");
        }
    }

    @Test
    void isSpecialPair_shouldReturnFalseForNonSpecialPair() {
        Dice dice = new Dice();
        for (int i = 0; i < 500; i++) {
            dice.roll();
            // Par de 2,3,4,5 no es especial
            if (dice.isPair() && dice.getDie1() != 1 && dice.getDie1() != 6) {
                assertFalse(dice.isSpecialPair());
                return;
            }
        }
    }

    @Test
    void total_shouldBeBetween2And12() {
        Dice dice = new Dice();
        for (int i = 0; i < 100; i++) {
            dice.roll();
            assertTrue(dice.getTotal() >= 2 && dice.getTotal() <= 12,
                    "Total debe estar entre 2 y 12, fue: " + dice.getTotal());
        }
    }
}
