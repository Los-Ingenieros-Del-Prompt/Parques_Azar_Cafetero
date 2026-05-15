package com.aguardientes.azarcafetero.parques_service.domain.service;

import com.aguardientes.azarcafetero.parques_service.domain.model.Game;
import com.aguardientes.azarcafetero.parques_service.domain.model.Piece;
import com.aguardientes.azarcafetero.parques_service.domain.model.Player;

import java.util.*;

/**
 * Servicio de decisión del bot de Parqués.
 *
 * ┌─────────┬──────────────────────────────────────────────────────────────┐
 * │ EASY    │ Selección aleatoria entre movimientos válidos.               │
 * ├─────────┼──────────────────────────────────────────────────────────────┤
 * │ MEDIUM  │ Búsqueda greedy con función heurística (profundidad 1).      │
 * │         │ Evalúa cada movimiento posible con un score y elige el mejor.│
 * ├─────────┼──────────────────────────────────────────────────────────────┤
 * │ HARD    │ Greedy avanzado con análisis de amenazas (threat modeling).  │
 * │         │ Calcula el riesgo de cada casilla destino según la distancia │
 * │         │ de fichas enemigas, y pondera captura vs avance estratégico. │
 * └─────────┴──────────────────────────────────────────────────────────────┘
 */
public class ParquesBotDecisionService {

    // ─── Identificadores especiales para decisiones no-movimiento ─────────────
    public static final String EXIT_JAIL_ID = "__EXIT_JAIL__";
    public static final String PASS_ID      = "__PASS__";

    // ─── Casillas seguras (mismo conjunto que Game.java) ──────────────────────
    private static final Set<Integer> SAFE_SQUARES =
            Set.of(4, 11, 16, 21, 28, 33, 38, 45, 50, 55, 62, 67);

    // ─── Pesos heurísticos ────────────────────────────────────────────────────
    private static final double CAPTURE_BONUS    = 20.0;
    private static final double SAFE_BONUS       = 8.0;
    private static final double LADDER_BONUS     = 12.0;
    private static final double VICTORY_BONUS    = 30.0;
    private static final double EXIT_JAIL_BONUS  = 10.0;
    private static final double ADVANCE_WEIGHT   = 0.4;

    private final Random random;

    public ParquesBotDecisionService() { this.random = new Random(); }
    public ParquesBotDecisionService(Random random) { this.random = random; }

    /**
     * Representa una acción posible del bot.
     *   isExitJail() → salir de la cárcel con un par
     *   isPass()     → no hay movimientos válidos, pasar turno
     *   de lo contrario → mover la ficha pieceId con los dados diceSelection
     */
    public record BotDecision(String pieceId, int diceSelection) {
        public boolean isExitJail() { return EXIT_JAIL_ID.equals(pieceId); }
        public boolean isPass()     { return PASS_ID.equals(pieceId); }
    }

    public BotDecision decide(Game game, String botId, ParquesBotDifficulty difficulty) {
        List<BotDecision> options = getValidDecisions(game, botId);
        if (options.isEmpty()) return new BotDecision(PASS_ID, -1);

        return switch (difficulty) {
            case EASY   -> options.get(random.nextInt(options.size()));
            case MEDIUM -> greedyDecision(options, game, botId, false);
            case HARD   -> greedyDecision(options, game, botId, true);
        };
    }

    /**
     * Genera todas las acciones válidas para el bot en el estado actual.
     * Incluye: salir de la cárcel, mover ficha con dado 1, dado 2, o suma.
     */
    public List<BotDecision> getValidDecisions(Game game, String botId) {
        Player bot = findBot(game, botId);
        if (bot == null) return List.of();

        List<BotDecision> decisions = new ArrayList<>();
        int die1     = game.getDie1();
        int die2     = game.getDie2();
        boolean d1   = !game.isDie1Used();
        boolean d2   = !game.isDie2Used();

        // Opción: salir de la cárcel con par.
        // Solo si AMBOS dados están disponibles: exitJail consume die1 y die2.
        // Si uno ya fue usado (bot movió ficha activa primero), no se puede exitJail.
        if (game.isJailExitAvailable() && d1 && d2) {
            decisions.add(new BotDecision(EXIT_JAIL_ID, 0));
        }

        for (Piece piece : bot.getPieces()) {
            if (piece.isInJail() || piece.isAtVictory()) continue;

            if (d1 && piece.canMove(die1))          decisions.add(new BotDecision(piece.getId(), 1));
            if (d2 && piece.canMove(die2))          decisions.add(new BotDecision(piece.getId(), 2));
            if (d1 && d2 && piece.canMove(die1+die2)) decisions.add(new BotDecision(piece.getId(), 3));
        }

        return decisions;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEDIUM / HARD — Greedy con función heurística
    //
    // Para cada acción posible calcula h(acción, estado) y elige la máxima.
    //
    // MEDIUM: solo considera avance, captura y casillas seguras.
    // HARD:   añade threat modeling (riesgo de ser comido en destino)
    //         y ajuste estratégico según posición relativa en el juego.
    // ═══════════════════════════════════════════════════════════════════════════

    private BotDecision greedyDecision(List<BotDecision> options, Game game,
                                       String botId, boolean isHard) {
        return options.stream()
                .max(Comparator.comparingDouble(d -> evaluate(d, game, botId, isHard)))
                .orElse(options.get(0));
    }

    /**
     * Función heurística h(decisión, estado).
     *
     * Factores:
     *   + Avance de posición relativa
     *   + Captura de ficha enemiga en destino
     *   + Aterrizar en casilla segura
     *   + Entrar a la escalera (home stretch)
     *   + Completar ficha (victoria parcial)
     *   + Sacar ficha de la cárcel
     *   - [HARD] Riesgo de ser comido en la casilla destino
     *   ± [HARD] Ajuste según si el bot va ganando o perdiendo
     */
    private double evaluate(BotDecision decision, Game game, String botId, boolean isHard) {
        Player bot = findBot(game, botId);
        if (bot == null) return 0;

        if (decision.isExitJail()) {
            long inJail  = bot.getPieces().stream().filter(Piece::isInJail).count();
            long active  = bot.getPieces().stream().filter(p -> !p.isInJail() && !p.isAtVictory()).count();

            double score = EXIT_JAIL_BONUS + inJail * 2.5;

            if (isHard) {
                // Si ya hay fichas activas avanzadas, salir de la cárcel es menos urgente
                if (active >= 2) score *= 0.7;
                // Si no hay activas, es crítico salir
                if (active == 0) score *= 1.5;
            }
            return score;
        }

        Piece piece  = bot.findPiece(decision.pieceId());
        int steps    = getSteps(decision.diceSelection(), game);
        int currRel  = piece.getRelativePosition();
        int targetRel = currRel + steps;
        int targetAbs = piece.getAbsolutePositionAfterMove(steps);

        double score = 0;

        // ── Avance ────────────────────────────────────────────────────────────
        score += steps * ADVANCE_WEIGHT;

        // ── Captura ───────────────────────────────────────────────────────────
        if (targetAbs >= 0 && hasOpponentAt(game, botId, targetAbs)) {
            score += CAPTURE_BONUS;
            if (isHard && !isBotWinning(game, botId)) score += 8.0; // más agresivo si va perdiendo
        }

        // ── Casilla segura ────────────────────────────────────────────────────
        if (targetAbs >= 0 && SAFE_SQUARES.contains(targetAbs)) score += SAFE_BONUS;

        // ── Escalera (home stretch) ───────────────────────────────────────────
        if (targetRel >= 63 && currRel < 63) score += LADDER_BONUS;

        // ── Cercanía a la victoria ────────────────────────────────────────────
        if (targetRel >= 65) score += (targetRel - 64) * 3.0;

        // ── Victoria de ficha ─────────────────────────────────────────────────
        if (targetRel == 70) score += VICTORY_BONUS;

        // ── [HARD] Threat modeling: riesgo en la casilla destino ──────────────
        // Solo aplica en el recorrido común: en la escalera/home stretch no hay capturas.
        if (isHard && targetAbs >= 0 && targetAbs < 68 && !SAFE_SQUARES.contains(targetAbs)) {
            double threat = calculateThreat(game, botId, targetAbs);
            score -= threat;

            // Compensación: si ya estamos en posición peligrosa, moverse es mejor que quedarse
            int currAbs = piece.getAbsolutePosition();
            if (currAbs >= 0 && currAbs < 68 && !SAFE_SQUARES.contains(currAbs)) {
                double currentThreat = calculateThreat(game, botId, currAbs);
                score += currentThreat * 0.5; // "salir del peligro" vale la pena
            }
        }

        // ── [HARD] Ajuste estratégico ─────────────────────────────────────────
        if (isHard) {
            if (isBotWinning(game, botId)) {
                // Va ganando: prioriza avanzar fichas cercanas a la victoria
                if (targetRel >= 50) score += 5.0;
            } else {
                // Va perdiendo: prioriza capturas y sacar fichas de la cárcel
                if (hasOpponentAt(game, botId, targetAbs)) score += 5.0;
            }
        }

        return score;
    }

    // ─── Threat modeling ──────────────────────────────────────────────────────

    /**
     * Calcula el riesgo de la casilla absolutePos.
     *
     * Para cada ficha enemiga, estima la probabilidad de que llegue
     * a esa casilla en los próximos turnos (distancia 2–12).
     * A menor distancia → mayor amenaza.
     */
    private double calculateThreat(Game game, String botId, int absolutePos) {
        if (SAFE_SQUARES.contains(absolutePos)) return 0;

        double threat = 0;
        for (Player opponent : game.getPlayers()) {
            if (opponent.getId().equals(botId)) continue;
            for (Piece op : opponent.getPieces()) {
                if (op.isInJail() || op.isAtVictory() || op.isOnLadder()) continue;
                int dist = circularDistance(op.getAbsolutePosition(), absolutePos);
                if (dist >= 2 && dist <= 12) {
                    // Fichas más cerca = más peligrosas (peso inverso a la distancia)
                    threat += (13.0 - dist) / 12.0 * 6.0;
                }
            }
        }
        return Math.min(threat, 18.0); // cap para no penalizar infinitamente
    }

    /**
     * Distancia circular en el tablero común (68 casillas).
     * Mide cuántos pasos necesita una ficha en 'from' para llegar a 'to'.
     */
    private int circularDistance(int from, int to) {
        if (from < 0 || to < 0) return 100;
        int diff = to - from;
        return diff >= 0 ? diff : diff + 68;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasOpponentAt(Game game, String botId, int absolutePos) {
        if (absolutePos < 0) return false;
        for (Player opponent : game.getPlayers()) {
            if (opponent.getId().equals(botId)) continue;
            for (Piece op : opponent.getPieces()) {
                if (!op.isInJail() && !op.isAtVictory() && !op.isOnLadder()
                        && op.getAbsolutePosition() == absolutePos) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * El bot va ganando si la suma de posiciones relativas de sus fichas
     * supera a la de todos los oponentes.
     */
    private boolean isBotWinning(Game game, String botId) {
        Player bot = findBot(game, botId);
        if (bot == null) return false;

        int botProgress = bot.getPieces().stream()
                .mapToInt(p -> Math.max(0, p.getRelativePosition()))
                .sum();

        return game.getPlayers().stream()
                .filter(p -> !p.getId().equals(botId))
                .allMatch(opp -> {
                    int oppProgress = opp.getPieces().stream()
                            .mapToInt(p -> Math.max(0, p.getRelativePosition()))
                            .sum();
                    return botProgress > oppProgress;
                });
    }

    private int getSteps(int diceSelection, Game game) {
        return switch (diceSelection) {
            case 1 -> game.getDie1();
            case 2 -> game.getDie2();
            case 3 -> game.getDie1() + game.getDie2();
            default -> 0;
        };
    }

    private Player findBot(Game game, String botId) {
        return game.getPlayers().stream()
                .filter(p -> p.getId().equals(botId))
                .findFirst()
                .orElse(null);
    }

    // ─── Utilidades estáticas para identificar bots ───────────────────────────

    public static boolean isBot(String playerId) {
        return playerId != null && playerId.startsWith("BOT_");
    }

    public static String generateBotId(ParquesBotDifficulty difficulty) {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "BOT_" + difficulty.name() + "_" + uid;
    }

    public static ParquesBotDifficulty difficultyFromId(String botId) {
        try {
            return ParquesBotDifficulty.valueOf(botId.split("_")[1]);
        } catch (Exception e) {
            return ParquesBotDifficulty.MEDIUM;
        }
    }

    public static String botName(ParquesBotDifficulty difficulty) {
        return switch (difficulty) {
            case EASY   -> "Bot Fácil";
            case MEDIUM -> "Bot Medio";
            case HARD   -> "Bot Difícil";
        };
    }
}