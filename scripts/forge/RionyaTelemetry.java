package forge.game;

import com.google.common.eventbus.Subscribe;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.event.*;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.game.zone.ZoneView;


/** Structured, opt-in gameplay telemetry for the Rionya deck audit. */
public final class RionyaTelemetry {
    private final Game game;
    public RionyaTelemetry(final Game game) { this.game = game; }

    private static String enc(Object value) {
        if (value == null) return "";
        return String.valueOf(value).replace("%", "%25").replace("|", "%7C")
                .replace("\n", " ").replace("\r", " ").replace("=", "%3D");
    }
    private int turn() { return game.getPhaseHandler() == null ? 0 : game.getPhaseHandler().getTurn(); }
    private String phase() {
        return game.getPhaseHandler() == null || game.getPhaseHandler().getPhase() == null
                ? "" : game.getPhaseHandler().getPhase().name();
    }
    private void emit(String type, String fields) {
        System.out.println("RIONYA_EVENT|type=" + type + "|turn=" + turn()
                + "|phase=" + enc(phase()) + (fields.isEmpty() ? "" : "|" + fields));
    }
    private static String player(PlayerView p) {
        return p == null ? "" : "player_id=" + p.getId() + "|player=" + enc(p.getName());
    }
    private static String card(CardView c) {
        return c == null ? "" : "card_id=" + c.getId() + "|card=" + enc(c.getName());
    }
    private static String zone(ZoneView z) { return z == null ? "" : z.zoneType().name(); }

    private Player primary() {
        for (Player p : game.getPlayers()) {
            for (Card c : p.getCommanders()) {
                if ("Rionya, Fire Dancer".equals(c.getName())) return p;
            }
        }
        return null;
    }
    private void snapshot(String boundary) {
        Player p = primary();
        if (p == null) return;
        emit("snapshot", "boundary=" + enc(boundary) + "|player_id=" + p.getId()
                + "|player=" + enc(p.getName()) + "|life=" + p.getLife()
                + "|hand_size=" + p.getCardsIn(ZoneType.Hand).size()
                + "|battlefield_size=" + p.getCardsIn(ZoneType.Battlefield).size()
                + "|creatures=" + p.getCreaturesInPlay().size()
                + "|lands=" + p.getLandsInPlay().size());
        for (Card c : p.getCardsIn(ZoneType.Hand)) {
            emit("snapshot_card", "boundary=" + enc(boundary) + "|player_id=" + p.getId()
                    + "|zone=Hand|card_id=" + c.getId() + "|card=" + enc(c.getName()));
        }
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            emit("snapshot_card", "boundary=" + enc(boundary) + "|player_id=" + p.getId()
                    + "|zone=Battlefield|card_id=" + c.getId() + "|card=" + enc(c.getName()));
        }
    }

    @Subscribe public void zone(GameEventCardChangeZone e) {
        CardView c=e.card(); PlayerView owner=c == null ? null : c.getOwner();
        emit("zone", player(owner)+"|"+card(c)+"|from="+enc(zone(e.from()))+"|to="+enc(zone(e.to())));
    }
    @Subscribe public void mulligan(GameEventMulligan e) {
        emit("mulligan", player(e.player()));
    }
    @Subscribe public void turnBegin(GameEventTurnBegan e) {
        emit("turn_begin", player(e.turnOwner())+"|event_turn="+e.turnNumber()); snapshot("turn_begin");
    }
    @Subscribe public void turnEnd(GameEventTurnEnded e) { snapshot("turn_end"); emit("turn_end", ""); }
    @Subscribe public void life(GameEventPlayerLivesChanged e) {
        emit("life", player(e.player())+"|old="+e.oldLives()+"|new="+e.newLives());
    }
    @Subscribe public void playerDamage(GameEventPlayerDamaged e) {
        emit("player_damage", player(e.target())+"|"+card(e.source())+"|amount="+e.amount()
                +"|combat="+e.combat()+"|infect="+e.infect());
    }
    @Subscribe public void cardDamage(GameEventCardDamaged e) {
        emit("card_damage", card(e.card())+"|source="+enc(e.source()==null?"":e.source().getName())
                +"|amount="+e.amount()+"|damage_type="+enc(e.type()));
    }
    @Subscribe public void counters(GameEventCardCounters e) {
        emit("card_counters", card(e.card())+"|counter="+enc(e.type())+"|old="+e.oldValue()+"|new="+e.newValue());
    }
    @Subscribe public void mana(GameEventManaPool e) {
        emit("mana_pool", player(e.player())+"|mode="+enc(e.mode())+"|color="+e.manaColor());
    }
    @Subscribe public void land(GameEventLandPlayed e) {
        emit("land", player(e.player())+"|"+card(e.land()));
    }
    @Subscribe public void cast(GameEventSpellAbilityCast e) {
        PlayerView p=e.si()==null ? null : e.si().getActivatingPlayer();
        String host=e.sa()==null || e.sa().getHostCard()==null ? "" : e.sa().getHostCard().getName();
        emit("spell", player(p)+"|card="+enc(host)+"|targets="+enc(e.targetDescription()));
    }
}
