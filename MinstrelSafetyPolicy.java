/** Standalone, conservative strategy gates for integration into the Minstrel Forge pilot.
 * This is NOT a replacement for the v0.7 pilot and does not claim Forge integration.
 */
public final class MinstrelSafetyPolicy {
    private MinstrelSafetyPolicy() {}
    public record PactState(int nextUpkeepGuaranteedGeneric, int nextUpkeepGuaranteedGreen,
                            boolean winsBeforeNextUpkeep, boolean paymentPrevented) {}
    /** Guaranteed mana excludes summoning-sick creatures, temporary mana, tapped sources,
     * lands to be sacrificed, and unconfirmed future draws/land drops. */
    public static boolean mayCastSummonersPact(PactState s) {
        return s.winsBeforeNextUpkeep || s.paymentPrevented ||
            (s.nextUpkeepGuaranteedGreen >= 2 &&
             s.nextUpkeepGuaranteedGeneric + s.nextUpkeepGuaranteedGreen >= 4);
    }
    /** Counts represent independently verified, reusable mana available at the next upkeep.
     * Generic excludes blue to avoid double counting. Unknown sources must be omitted. */
    public record NegationState(int guaranteedNonBlue, int guaranteedBlue,
                                boolean winsBeforeNextUpkeep, boolean paymentPrevented) {}
    public static boolean mayCastPactOfNegation(NegationState s) {
        return s.winsBeforeNextUpkeep || s.paymentPrevented ||
            (s.guaranteedBlue >= 2 &&
             s.guaranteedNonBlue + s.guaranteedBlue >= 5);
    }
    public record ReclamationState(int landsInGraveyard, int usefulLandfallTriggers,
                                   boolean winningLine, boolean graveyardThreatened) {}
    public static boolean mayCastSplendidReclamation(ReclamationState s) {
        return s.winningLine || s.graveyardThreatened && s.landsInGraveyard >= 2 ||
               s.landsInGraveyard >= 4 && s.usefulLandfallTriggers >= 2;
    }
    public record ScapeshiftState(int landsSacrificed, int landsFound,
                                  int usefulLandfallTriggers, boolean winningLine,
                                  boolean protectionOrRecursionAvailable) {}
    public static boolean mayCastScapeshift(ScapeshiftState s) {
        return s.landsSacrificed > 0 && s.landsFound > 0 &&
               (s.winningLine || s.usefulLandfallTriggers >= 4 && s.protectionOrRecursionAvailable);
    }
}
