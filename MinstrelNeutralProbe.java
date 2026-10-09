import forge.GuiDesktop;
import forge.LobbyPlayer;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilMana;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameLogEntry;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.game.phase.PhaseType;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.player.GamePlayerUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MinstrelNeutralProbe {
  private static final String MOWU_PILOT_VERSION="MINSTREL_LANDFALL_HEURISTIC_V0_2";
  private static final AtomicLong DECISION_PROGRESS = new AtomicLong();
  private static final java.util.concurrent.atomic.AtomicInteger ACTIVATION_COUNT = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger PREMIUM_COUNT = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger WHIFF_COUNT = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger PEAK_THOPTERS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger THOPTER_TOKENS_SEEN = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger PEAK_WOLVES = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger WOLVES_SEEN = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger FALDORN_EXILE_PLAYS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger FALDORN_ACTIVATIONS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger TUTOR_CASTS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger WORLDLY_TUTOR_CASTS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger ENLIGHTENED_TUTOR_CASTS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger TOTAL_P1_COUNTERS_ADDED = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger PEAK_P1_COUNTERS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger PEAK_COUNTERED_CREATURES = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger PEAK_CREATURE_POWER = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger FINAL_P1_COUNTERS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger FINAL_COUNTERED_CREATURES = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger AMBIGUOUS_COUNTER_ADDS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicInteger> COUNTER_SOURCE_HINTS = new java.util.concurrent.ConcurrentHashMap<>();
  // v6.5 durable draw/card-access telemetry. Each counted game writes JSONL incrementally.
  private static volatile Path CURRENT_DRAW_LEDGER = null;
  private static volatile int CURRENT_DRAW_GAME = 0;
  private static final java.util.concurrent.atomic.AtomicInteger DRAW_LEDGER_EVENTS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger DRAW_LEDGER_CARDS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger DRAW_LEDGER_TRUE_DRAWS = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.concurrent.atomic.AtomicInteger DRAW_LEDGER_TOP_ACCESS = new java.util.concurrent.atomic.AtomicInteger();
  private static final Object DRAW_LEDGER_LOCK = new Object();
  private static String jesc(String x){ if(x==null)return ""; return x.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r"); }
  private static void appendDrawLedger(int turn,String phase,String accessType,String source,String confidence,List<String> cards,int handBefore,int handAfter,int libBefore,int libAfter,String candidates){
    try{
      Path p=CURRENT_DRAW_LEDGER; if(p==null)return; int n=cards==null?0:cards.size();
      int ev=DRAW_LEDGER_EVENTS.incrementAndGet(); DRAW_LEDGER_CARDS.addAndGet(n);
      if(accessType.startsWith("DRAW")||"TURN_DRAW".equals(accessType)) DRAW_LEDGER_TRUE_DRAWS.addAndGet(n);
      if(accessType.startsWith("TOP_ACCESS")) DRAW_LEDGER_TOP_ACCESS.addAndGet(n);
      StringBuilder ca=new StringBuilder("["); if(cards!=null)for(int i=0;i<cards.size();i++){if(i>0)ca.append(',');ca.append('\"').append(jesc(cards.get(i))).append('\"');}ca.append(']');
      String line="{\"schema\":\"mowu-draw-ledger-v1\",\"game\":"+CURRENT_DRAW_GAME+",\"event\":"+ev+",\"turn\":"+turn+",\"phase\":\""+jesc(phase)+"\",\"access_type\":\""+jesc(accessType)+"\",\"source\":\""+jesc(source)+"\",\"confidence\":\""+jesc(confidence)+"\",\"source_candidates\":\""+jesc(candidates)+"\",\"cards_count\":"+n+",\"cards\":"+ca+",\"hand_before\":"+handBefore+",\"hand_after\":"+handAfter+",\"library_before\":"+libBefore+",\"library_after\":"+libAfter+"}"+System.lineSeparator();
      synchronized(DRAW_LEDGER_LOCK){Files.writeString(p,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND,StandardOpenOption.WRITE);}
      System.out.println("MOWU_DRAW_LEDGER game="+CURRENT_DRAW_GAME+" event="+ev+" access="+accessType+" source=["+source+"] confidence="+confidence+" cards="+n+" turn="+turn+" phase="+phase);
    }catch(Exception e){System.out.println("MOWU_DRAW_LEDGER_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());}
  }
  static final class SelectiveLineOutputStream extends OutputStream {
    private final OutputStream sink;
    private final boolean passAll;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream(512);
    SelectiveLineOutputStream(OutputStream sink, boolean passAll){this.sink=sink;this.passAll=passAll;}
    private boolean keep(String s){
      return passAll || s.startsWith("FARMER_") || s.startsWith("MOWU_") || s.startsWith("FALDORN_") || s.startsWith("RAPH_") || s.startsWith("AI_MULLIGAN") ||
             s.startsWith("GAMELOG ") || s.startsWith("Exception") || s.startsWith("Caused by:") ||
             s.startsWith("WARNING") || s.startsWith("ERROR") || s.startsWith("SEVERE");
    }
    private void emit() throws IOException {
      String s=line.toString(StandardCharsets.UTF_8.name()); line.reset();
      if(!keep(s)) return;
      synchronized(sink){ sink.write(s.getBytes(StandardCharsets.UTF_8)); sink.write('\n'); }
    }
    @Override public void write(int v)throws IOException{ if(v=='\n')emit(); else if(v!='\r')line.write(v); }
    @Override public void write(byte[] b,int o,int l)throws IOException{ for(int i=o;i<o+l;i++)write(b[i]); }
    @Override public void flush()throws IOException{ if(line.size()>0)emit(); synchronized(sink){sink.flush();} }
  }
  static void installLog(String f)throws Exception{
    if(f==null||f.isEmpty())return;
    Path p=Paths.get(f).toAbsolutePath(); if(p.getParent()!=null)Files.createDirectories(p.getParent());
    OutputStream sink=new BufferedOutputStream(Files.newOutputStream(p,StandardOpenOption.CREATE,StandardOpenOption.APPEND,StandardOpenOption.WRITE),65536);
    // Fast-safe logging: retain the complete Kinnan audit/strategy stream plus final game log,
    // while suppressing repetitive Forge stdout chatter. Stderr is always retained.
    System.setOut(new PrintStream(new SelectiveLineOutputStream(sink,false),true,StandardCharsets.UTF_8.name()));
    System.setErr(new PrintStream(new SelectiveLineOutputStream(sink,true),true,StandardCharsets.UTF_8.name()));
  }

  public static class AggroLobby extends LobbyPlayerAi {
    private final boolean audit;
    public AggroLobby(String name, boolean audit){super(name,Collections.emptySet());this.audit=audit;}
    @Override public Player createIngamePlayer(Game game,int id){
      Player p=new Player(getName(),game,id);
      if (audit) {
        // Galadriel keeps the custom strategic controller.
        p.setFirstController(new MinstrelController(game,p,this));
      } else {
        // Opponent speed-safety: use Forge's deterministic heuristic AI rather than
        // simulation search. Forge still owns legality, targets, combat, stack, RNG,
        // triggers, damage, and outcomes; this only removes the expensive look-ahead
        // search that can hang on complex multiplayer boards.
        FastOpponentController pc = new FastOpponentController(game,p,this);
        p.setFirstController(pc);
      }
      return p;
    }
  }

  public static class FastOpponentController extends PlayerControllerAi {
    protected final Player me;
    private String lastActionSig="";
    private int actionsThisPhase=0;
    private int budgetTurn=-1;
    private String budgetPhase="";
    private String lastStackSig="";

    FastOpponentController(Game game, Player player, LobbyPlayer lobby){
      super(game,player,lobby); this.me=player; getAi().setUseSimulation(null);
    }

    private void resetBudgetIfNeeded(){
      int t=me.getGame().getPhaseHandler().getTurn();
      String p=String.valueOf(me.getGame().getPhaseHandler().getPhase());
      if(t!=budgetTurn || !p.equals(budgetPhase)){
        budgetTurn=t; budgetPhase=p; actionsThisPhase=0; lastActionSig=""; lastStackSig="";
      }
    }

    private String actionSig(){
      StringBuilder sb=new StringBuilder();
      sb.append(me.getGame().getPhaseHandler().getTurn()).append(':').append(me.getGame().getPhaseHandler().getPhase()).append(':');
      for(Card c:me.getCardsIn(ZoneType.Hand)) sb.append('H').append(c.getId()).append(',');
      for(Card c:me.getCardsIn(ZoneType.Battlefield)) sb.append('B').append(c.getId()).append(c.isTapped()?'T':'U').append(',');
      sb.append("L").append(me.getLife());
      return sb.toString();
    }

    private SpellAbility chooseLand(){
      try {
        CardCollection lands=ComputerUtilAbility.getAvailableLandsToPlay(me.getGame(), me);
        if(lands==null || lands.isEmpty()) return null;
        // Prefer an untapped-looking land when Forge exposes multiple choices; otherwise first legal.
        Card best=null;
        for(Card c:lands){ if(best==null) best=c; }
        if(best==null) return null;
        for(SpellAbility sa:best.getAllPossibleAbilities(me,true)){
          if(sa.isLandAbility() && sa.canPlay()) return sa;
        }
      } catch(Exception ignored){}
      return null;
    }

    protected int quickScore(SpellAbility sa){
      if(sa==null || sa.getHostCard()==null) return Integer.MIN_VALUE;
      Card c=sa.getHostCard();
      int score=0;
      try { score += c.getCMC()*12; } catch(Exception ignored){}
      try { if(c.getType().isCreature()) score += 30; } catch(Exception ignored){}
      try { if(c.getType().isPlaneswalker()) score += 20; } catch(Exception ignored){}
      try { if(c.getType().isEnchantment() || c.getType().isArtifact()) score += 12; } catch(Exception ignored){}
      String n=c.getName().toLowerCase(Locale.ROOT);
      if(n.contains("sol ring")||n.contains("signet")||n.contains("talisman")) score+=45;
      if(n.contains("draw")||n.contains("ringleader")||n.contains("tutor")) score+=15;
      if(sa.isActivatedAbility()) score += 8;
      return score;
    }

    private boolean prepareQuickTargets(SpellAbility sa){
      try {
        if(sa==null || !sa.usesTargeting()) return true;
        if(sa.isTargetNumberValid()) return true;
        sa.resetTargets();
        boolean ok=chooseTargetsFor(sa);
        if(!ok || !sa.isTargetNumberValid()){ sa.resetTargets(); return false; }
        return true;
      } catch(Exception ignored){
        try { if(sa!=null) sa.resetTargets(); } catch(Exception ignored2){}
        return false;
      }
    }

    protected boolean isReactiveOnly(String n){return false;}
    protected boolean permitQuickAction(SpellAbility sa){return true;}
    protected SpellAbility chooseQuickAction(boolean stackNonEmpty){
      List<SpellAbility> candidates=new ArrayList<>();
      try {
        candidates.addAll(ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand), me));
        candidates.addAll(ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Command), me));
      } catch(Exception ignored){}
      // Keep important non-mana activated abilities (Krenko, sacrifice/value engines, etc.) alive.
      if(!stackNonEmpty){
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          try {
            for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
              if(sa.isActivatedAbility() && !sa.isManaAbility()) candidates.add(sa);
            }
          } catch(Exception ignored){}
        }
      }
      SpellAbility best=null; int bestScore=Integer.MIN_VALUE;
      for(SpellAbility sa:candidates){
        try {
          if(sa==null || sa.isLandAbility() || !sa.canPlay()) continue;
          // Never repeatedly activate the commander's combat pump during main phase.
          if(!stackNonEmpty && sa.isActivatedAbility() && sa.getHostCard()!=null && "The Wandering Minstrel".equals(sa.getHostCard().getName())) continue;
          if(!stackNonEmpty && isReactiveOnly(sa.getHostCard().getName())) continue;
          if(!permitQuickAction(sa)) continue;
          if(!ComputerUtilMana.canPayManaCost(sa,me,0,false)) continue;
          if(!prepareQuickTargets(sa)) continue;
          // Off-turn/stack windows naturally filter to instant-speed legal actions via canPlay().
          int sc=quickScore(sa);
          if(sc>bestScore){bestScore=sc;best=sa;}
        } catch(Exception ignored){}
      }
      return best;
    }

    @Override public void declareBlockers(Player defender, forge.game.combat.Combat combat){
      try{
        int attackers=combat.getAttackers().size(); int power=0;
        for(Card c:combat.getAttackers()) power += Math.max(0,c.getNetPower());
        // Forge's full blocker search becomes combinatorial on enormous token attacks.
        // Passing on blocks is always a legal combat decision; use it only at absurd board sizes
        // where blocker optimization cannot realistically change the outcome and would stall the JVM.
        if(attackers>=30 || (attackers>=15 && power>=300)){
          System.out.println("FAST_OPPONENT_BLOCK_CAP player="+me.getName()+" attackers="+attackers+" power="+power+" action=no_blocks");
          return;
        }
      }catch(Exception ignored){}
      super.declareBlockers(defender,combat);
    }

    @Override public List<SpellAbility> chooseSpellAbilityToPlay(){
      resetBudgetIfNeeded();
      boolean stackNonEmpty=!me.getGame().getStack().isEmpty();

      if(stackNonEmpty){
        SpellAbility top=me.getGame().getStack().peekAbility();
        String ss=budgetTurn+":"+budgetPhase+":"+me.getGame().getStack().size()+":"+(top==null?"null":top.getId());
        if(ss.equals(lastStackSig)) return null;
        lastStackSig=ss;
        SpellAbility r=chooseQuickAction(true);
        return r==null?null:Collections.singletonList(r);
      }

      if(!me.getGame().getPhaseHandler().isPlayerTurn(me)) return null;
      PhaseType ph=me.getGame().getPhaseHandler().getPhase();
      if(ph!=PhaseType.MAIN1 && ph!=PhaseType.MAIN2) return null;

      String sig=actionSig();
      if(sig.equals(lastActionSig)) return null;

      // Land drop is cheap and independent of Forge's expensive strategic search.
      SpellAbility land=chooseLand();
      if(land!=null){ lastActionSig=sig; return Collections.singletonList(land); }

      // Permit a bounded number of meaningful nonland actions per main phase.  This
      // keeps opponents functional while guaranteeing they cannot monopolize the JVM.
      if(actionsThisPhase>=3) return null;
      SpellAbility r=chooseQuickAction(false);
      if(r==null){ lastActionSig=sig; return null; }
      actionsThisPhase++;
      lastActionSig="";
      return Collections.singletonList(r);
    }
  }

  /** Pilot-specific prioritization; Forge still validates legal actions and targets. */
  public static class MinstrelController extends FastOpponentController {
    MinstrelController(Game game, Player player, LobbyPlayer lobby){ super(game,player,lobby); }
    @Override protected boolean isReactiveOnly(String n){
      return Arrays.asList("Counterspell","Swan Song","An Offer You Can't Refuse","Arcane Denial","Flusterstorm","Dovin's Veto","Fierce Guardianship","Pact of Negation","Heroic Intervention","Teferi's Protection","Veil of Summer","Tamiyo's Safekeeping","Boros Charm","Swords to Plowshares","Path to Exile","Nature's Claim","Beast Within","Assassin's Trophy").contains(n);
    }
    // Conservative safety gates: called BEFORE selecting a spell, not just as score penalties.
    // This pilot is a reconstruction from v0.3, not the missing v0.7 source.
    private int battlefieldLands(){
      int count=0;
      for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isLand()) count++;
      return count;
    }
    private int graveyardLands(){
      int count=0;
      for(Card c:me.getCardsIn(ZoneType.Graveyard)) if(c.isLand()) count++;
      return count;
    }
    private boolean safeMinstrelAction(SpellAbility sa){
      if(sa==null || sa.getHostCard()==null) return false;
      String n=sa.getHostCard().getName();
      if("Pact of Negation".equals(n)){
        System.out.println("MINSTREL_PACT_DECISION card=Pact_of_Negation allow=false reason=unverified_3UU next_upkeep_sources=unknown phase="+me.getGame().getPhaseHandler().getPhase());
        return false;
      }
      if("Summoner's Pact".equals(n)){
        // Fail closed: battlefield land count is NOT a guarantee of 2GG at next upkeep.
        // A future implementation must inspect colors, untap restrictions, and win lines.
        System.out.println("MINSTREL_PACT_DECISION card=Summoners_Pact allow=false reason=unverified_2GG next_upkeep_sources=unknown phase="+me.getGame().getPhaseHandler().getPhase());
        return false;
      }
      if("Splendid Reclamation".equals(n) &&
         !MinstrelSafetyPolicy.mayCastSplendidReclamation(
           new MinstrelSafetyPolicy.ReclamationState(graveyardLands(),graveyardLands(),false,false))){
        System.out.println("MINSTREL_SAFETY_BLOCK Splendid_Reclamation premature");
        return false;
      }
      if("Scapeshift".equals(n) &&
         !MinstrelSafetyPolicy.mayCastScapeshift(
           new MinstrelSafetyPolicy.ScapeshiftState(battlefieldLands(),battlefieldLands(),0,false,false))){
        System.out.println("MINSTREL_SAFETY_BLOCK Scapeshift unverified_line");
        return false;
      }
      return true;
    }
    @Override protected boolean permitQuickAction(SpellAbility sa){
      return safeMinstrelAction(sa);
    }
    @Override protected int quickScore(SpellAbility sa){
      int score=super.quickScore(sa);
      if(sa==null || sa.getHostCard()==null)return score;
      String name=sa.getHostCard().getName().toLowerCase(Locale.ROOT);
      if(name.equals("the wandering minstrel"))score+=190;
      if(name.equals("sol ring") || name.equals("arcane signet") || name.equals("birds of paradise") || name.equals("bloom tender"))score+=110;
      if(name.equals("exploration") || name.equals("dryad of the ilysian grove"))score+=145;
      if(name.equals("lotus cobra") || name.equals("tireless provisioner"))score+=115;
      if(name.equals("rhystic study") || name.equals("mystic remora") || name.equals("esper sentinel"))score+=100;
      if(name.equals("field of the dead") || name.equals("retreat to hagra") || name.equals("tunneling geopede") || name.equals("valakut exploration"))score+=105;
      if(name.equals("aftermath analyst") || name.equals("splendid reclamation"))score+=85;
      if(name.equals("scapeshift"))score+=65;
      if(name.equals("craterhoof behemoth") || name.equals("moraug, fury of akoum"))score+=50;
      // Avoid wasting protection/counterspells as proactive plays.
      if(name.equals("teferi's protection") || name.equals("heroic intervention") || name.equals("boros charm") || name.equals("tamiyo's safekeeping") || name.equals("veil of summer"))score-=200;
      return score;
    }
  }

  public static class FarmerController extends PlayerControllerAi {
    private final Player me;
    private forge.ai.AiController faldornPaymentBrains;
    private final boolean auditEnabled;
    private boolean extraOneLandFreeCredit=false;
    private Boolean pendingKeepDecision=null;
    private String lastHand="";
    private List<String> lastLibrary=null;
    private int snapshotNo=0;
    // v15: once we commit to an end-step Rhys double, preserve Rhys and mana sources through combat/opponent turns.
    private boolean rhysReserveArmed=false;
    private int rhysReserveStartedTurn=-1;
    private int kinnanActivationSeq=0;
    private int kinnanPremiumHits=0;
    private int kinnanWhiffs=0;
    private int peakThopters=0;
    private int totalThopterTokensSeen=0;
    private final Set<Card> seenThopterTokens=Collections.newSetFromMap(new IdentityHashMap<Card,Boolean>());
    private final Set<Card> seenWolfTokens=Collections.newSetFromMap(new IdentityHashMap<Card,Boolean>());
    private final Set<Integer> speakerEtbProcessed=new HashSet<>();
    private final Set<Integer> piaEtbProcessed=new HashSet<>();
    private final Map<Integer,String> piaNativeTargetById=new HashMap<>();
    private final Map<Integer,String> speakerNativeTargetById=new HashMap<>();
    private int lastAggravatedSelectionTurn=-999;
    private int lastAggravatedSelectionAttackCount=-1;
    private int battlefieldSnapshotNo=0;
    private int lastAuditTurn=-999;
    private String lastAuditPhase="";
    private String lastBattlefield="";
    // v15 draw diagnostics: lifecycle/availability markers for every draw engine.
    private final Set<String> lastActiveDrawEngineSet=new HashSet<>();
    private final Set<String> lastHandDrawEngineSet=new HashSet<>();
    private int drawEventSeq=0;
    private int lastHandSize=-1;
    private String pendingImmediateDrawSource="";
    private int pendingImmediateDrawTurn=-999;
    private final IdentityHashMap<Card,Integer> lastP1CountersByCard=new IdentityHashMap<>();
    private int counterEventSeq=0;
    private String lastNoActionSig="";
    private int lastFaldornActivationOpportunityTurn=-999;
    private String pendingTutorTarget="";
    private boolean pendingTutorUrgent=false;
    private String pendingTutorSource="";
    private int riteCommitTurn=-999;
    private int riteCommitRemaining=0;
    private boolean riteCommitted=false;
    // Raph & Mikey v0.2 pilot state. Track real combat participation so extra-combat spells
    // are sequenced after an actual attack rather than fired blindly in Main 1.
    private int lastRaphAttackTurn=-999;
    private int raphAttacksThisTurn=0;
    private String lastRaphTutorTarget="";
    private boolean raphCoverageAudited=false;
    // v0.3.9 integrated offensive tutor stages + commander payment telemetry.
    private int raphOffensiveTutorStage=0;
    private int pendingRaphCastNo=0, pendingRaphTreasuresBefore=0, pendingRaphManaBefore=0;
    private boolean pendingRaphPayment=false;
    private String actionSig(){
      StringBuilder sb=new StringBuilder();
      sb.append(me.getGame().getPhaseHandler().getTurn()).append(':').append(me.getGame().getPhaseHandler().getPhase()).append(':');
      for(Card c:me.getCardsIn(ZoneType.Hand)) sb.append('H').append(c.getId()).append(',');
      for(Card c:me.getCardsIn(ZoneType.Battlefield)) sb.append('B').append(c.getId()).append(c.isTapped()?'T':'U').append(',');
      sb.append("L").append(me.getLife());
      return sb.toString();
    }

    FarmerController(Game game,Player player,LobbyPlayer lobby,boolean audit){
      super(game,player,lobby);
      this.me=player;
      this.auditEnabled=audit;
      // v1.9: Forge's ComputerUtil creates a generic AiCostDecision for normal activations.
      // CostDiscard(type=Card) then calls player.getController().getAi().getCardsToDiscard(),
      // bypassing PlayerControllerAi chooseCardsForCost/getCostDecisionMaker hooks.  Supply a
      // narrowly customized AiController only through getAi() so actual Faldorn/Speaker cost
      // payment uses the exact tier-selected card. PlayerControllerAi's own internal `brains`
      // field remains untouched for all of its ordinary AI behavior.
      this.faldornPaymentBrains=new FaldornPaymentAiController(player,game,this);
      super.getAi().setUseSimulation(null);
      this.faldornPaymentBrains.setUseSimulation(null);
    }

    @Override public forge.ai.AiController getAi(){
      return faldornPaymentBrains!=null?faldornPaymentBrains:super.getAi();
    }

    private static final class FaldornPaymentAiController extends forge.ai.AiController {
      private final FarmerController owner;
      FaldornPaymentAiController(Player player,Game game,FarmerController owner){ super(player,game); this.owner=owner; }
      @Override public forge.game.card.CardCollection getCardsToDiscard(int amount,String[] valid,SpellAbility sa,forge.game.card.CardCollectionView already){
        try{
          if(amount==1 && sa!=null && sa.getHostCard()!=null &&
             ("Faldorn, Dread Wolf Herald".equals(sa.getHostCard().getName()) || "Formidable Speaker".equals(sa.getHostCard().getName()))){
            forge.game.card.CardCollection candidates=new forge.game.card.CardCollection(owner.me.getCardsIn(ZoneType.Hand));
            if(already!=null) candidates.removeAll(already);
            if(valid!=null && valid.length>0) candidates=forge.game.card.CardLists.getValidCards(candidates,valid,sa.getActivatingPlayer(),sa.getHostCard(),sa);
            Card pick=owner.neutralFaldornDiscardChoice(candidates);
            if(pick!=null){
              forge.game.card.CardCollection out=new forge.game.card.CardCollection(); out.add(pick);
              if(owner.auditEnabled) System.out.println("FALDORN_PAYMENT_LOCK source="+sa.getHostCard().getName()+" selected="+pick.getName()+" tier="+owner.neutralFaldornDiscardTier(pick,candidates)+" hand="+owner.me.getCardsIn(ZoneType.Hand).size());
              return out;
            }
            if(owner.auditEnabled) System.out.println("FALDORN_PAYMENT_LOCK source="+sa.getHostCard().getName()+" selected=NONE reason=no_strategic_discard");
            return new forge.game.card.CardCollection();
          }
        }catch(Exception e){ if(owner.auditEnabled) System.out.println("FALDORN_PAYMENT_LOCK_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage()); }
        return super.getCardsToDiscard(amount,valid,sa,already);
      }
    }

    private static boolean isLand(Card c){return c!=null && c.isLand();}
    private static int landCount(CardCollectionView cards){int n=0;for(Card c:cards)if(isLand(c))n++;return n;}
    private static String cardNames(CardCollectionView cards){StringBuilder sb=new StringBuilder();boolean first=true;for(Card c:cards){if(!first)sb.append(" | ");first=false;sb.append(c.getName());}return sb.toString();}
    private static List<String> namesList(CardCollectionView cards){
      List<String> out=new ArrayList<>(); for(Card c:cards) out.add(c.getName()); return out;
    }
    private int countThopters(){
      int n=0;
      for(Card c:me.getCardsIn(ZoneType.Battlefield)){
        try { if(c.isToken() && c.getType().isCreature()) n++; } catch(Exception ignored){}
      }
      return n;
    }
    private int countFoodTokens(){
      int n=0; for(Card c:me.getCardsIn(ZoneType.Battlefield)) try { if(c.isToken() && c.getName().toLowerCase().contains("food")) n++; } catch(Exception ignored){} return n;
    }
    private void updateThopterMetrics(){
      int current=0;
      for(Card c:me.getCardsIn(ZoneType.Battlefield)){
        try {
          if(c.isToken() && c.getType().isCreature()){
            current++;
            if(seenThopterTokens.add(c)){ totalThopterTokensSeen++; THOPTER_TOKENS_SEEN.incrementAndGet(); }
            String tn=c.getName(); boolean wolf="Wolf Token".equals(tn) || c.getType().hasSubtype("Wolf");
            if(wolf && seenWolfTokens.add(c)){ WOLVES_SEEN.incrementAndGet(); if(auditEnabled)System.out.println("FALDORN_WOLF_CREATED total="+WOLVES_SEEN.get()+" turn="+me.getGame().getPhaseHandler().getTurn()+" phase="+me.getGame().getPhaseHandler().getPhase()); }
          }
        } catch(Exception ignored){}
      }
      int wolvesNow=0; try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isToken()&&c.getType().isCreature() && ("Wolf Token".equals(c.getName())||c.getType().hasSubtype("Wolf"))) wolvesNow++;}catch(Exception ignored){}
      if(wolvesNow>PEAK_WOLVES.get()) PEAK_WOLVES.set(wolvesNow);
      if(current>peakThopters){peakThopters=current;PEAK_THOPTERS.set(Math.max(PEAK_THOPTERS.get(),peakThopters));System.out.println("FARMER_TOKEN_PEAK turn="+me.getGame().getPhaseHandler().getTurn()+" phase="+me.getGame().getPhaseHandler().getPhase()+" creature_tokens="+peakThopters+" wolves="+wolvesNow+" total_creature_tokens_seen="+totalThopterTokensSeen+" total_wolves_seen="+WOLVES_SEEN.get()+" food="+countFoodTokens());}
    }
    private static final Set<String> COUNTER_AMPLIFIERS = new HashSet<>(Arrays.asList("Hardened Scales","Branching Evolution","Primal Vigor","Ozolith, the Shattered Spire"));
    private Set<String> activeCounterCreatorSet(){ LinkedHashSet<String> out=new LinkedHashSet<>(); try{for(Card c:me.getCardsIn(ZoneType.Battlefield)){String n=c.getName();if(COUNTER_ENGINES.contains(n)&&!COUNTER_AMPLIFIERS.contains(n))out.add(n);}}catch(Exception ignored){} return out; }
    private Set<String> activeCounterAmplifierSet(){ LinkedHashSet<String> out=new LinkedHashSet<>(); try{for(Card c:me.getCardsIn(ZoneType.Battlefield))if(COUNTER_AMPLIFIERS.contains(c.getName()))out.add(c.getName());}catch(Exception ignored){} return out; }
    private int cardP1Counters(Card c){try{if(c!=null&&c.getType().isCreature())return Math.max(0,c.getPowerBonusFromCounters());}catch(Exception ignored){}return 0;}
    private void updateCounterMetrics(String reason){
      try{
        int total=0,carriers=0,maxPower=0,added=0; IdentityHashMap<Card,Integer> now=new IdentityHashMap<>();
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){ if(!c.getType().isCreature())continue; int pc=cardP1Counters(c);now.put(c,pc);total+=pc;if(pc>0)carriers++;maxPower=Math.max(maxPower,Math.max(0,c.getNetPower()));int before=lastP1CountersByCard.getOrDefault(c,0);if(pc>before)added+=pc-before; }
        if(added>0){ TOTAL_P1_COUNTERS_ADDED.addAndGet(added); Set<String> creators=activeCounterCreatorSet(),amps=activeCounterAmplifierSet();String hint;if(creators.size()==1){hint=creators.iterator().next();COUNTER_SOURCE_HINTS.computeIfAbsent(hint,k->new java.util.concurrent.atomic.AtomicInteger()).addAndGet(added);}else{hint=creators.isEmpty()?"UNATTRIBUTED":"AMBIGUOUS:"+String.join("|",creators);AMBIGUOUS_COUNTER_ADDS.addAndGet(added);} if(auditEnabled)System.out.println("FARMER_COUNTER_EVENT seq="+(++counterEventSeq)+" added="+added+" board_total="+total+" countered_creatures="+carriers+" source_hint=["+hint+"] amplifiers_active=["+String.join("|",amps)+"] reason="+reason); }
        PEAK_P1_COUNTERS.set(Math.max(PEAK_P1_COUNTERS.get(),total));PEAK_COUNTERED_CREATURES.set(Math.max(PEAK_COUNTERED_CREATURES.get(),carriers));PEAK_CREATURE_POWER.set(Math.max(PEAK_CREATURE_POWER.get(),maxPower));FINAL_P1_COUNTERS.set(total);FINAL_COUNTERED_CREATURES.set(carriers);lastP1CountersByCard.clear();lastP1CountersByCard.putAll(now);
      }catch(Exception e){if(auditEnabled)System.out.println("FARMER_COUNTER_AUDIT_ERROR "+e.getClass().getSimpleName());}
    }

    private String battlefieldState(){
      List<String> xs=new ArrayList<>();
      for(Card c:me.getCardsIn(ZoneType.Battlefield)){
        String x=c.getName();
        try { if(c.getType().isCreature()) x += "["+c.getNetPower()+"/"+c.getNetToughness()+"]"; } catch(Exception ignored){}
        try { if(c.isToken()) x += "{token}"; } catch(Exception ignored){}
        xs.add(x);
      }
      return String.join(" | ",xs);
    }
    private void auditState(String reason){
      if(!auditEnabled)return;
      try{
        if(raphDeck() && pendingRaphPayment){
          int now=0; try{now=me.getTotalCommanderCast();}catch(Exception ignored){}
          if(now>=pendingRaphCastNo){
            int after=raphTreasureCount(), spent=Math.max(0,pendingRaphTreasuresBefore-after);
            System.out.println("RAPH_CAST_PAYMENT cast_number="+pendingRaphCastNo+" tax="+(2*Math.max(0,pendingRaphCastNo-1))+" treasures="+spent+" treasures_before="+pendingRaphTreasuresBefore+" treasures_after="+after+" mana_est_before="+pendingRaphManaBefore+" rituals=UNATTRIBUTED");
            pendingRaphPayment=false;
          }
        }
        updateThopterMetrics();
        updateCounterMetrics(reason);
        int t=me.getGame().getPhaseHandler().getTurn();
        String ph=String.valueOf(me.getGame().getPhaseHandler().getPhase());
        boolean phaseChanged=(t!=lastAuditTurn || !ph.equals(lastAuditPhase));
        if(!phaseChanged) return;
        boolean ownTurn=me.getGame().getPhaseHandler().isPlayerTurn(me);
        if(!ownTurn && me.getGame().getStack().isEmpty()) { lastAuditTurn=t; lastAuditPhase=ph; return; }

        CardCollectionView h=me.getCardsIn(ZoneType.Hand);
        String hs=cardNames(h);
        String bf=battlefieldState();
        auditDrawEngineLifecycle(t,ph);
        if(mowuDeck()) {
          boolean starter=mowuStarterOnline();
          boolean amplifier=!activeCounterAmplifierSet().isEmpty();
          boolean payoff=mowuBattlefield()!=null || hasNamed("Kalonian Hydra",ZoneType.Battlefield) || hasNamed("Managorger Hydra",ZoneType.Battlefield);
          System.out.println("MOWU_ENGINE_STATE turn="+t+" phase="+ph+" starter_online="+starter+" amplifier_online="+amplifier+" payoff_online="+payoff+" board_p1_counters="+mowuBoardCounterTotal()+" mowu_p1_counters="+mowuOwnCounterTotal());
        }
        System.out.println("FARMER_STATE n="+(++battlefieldSnapshotNo)+" reason="+reason+" turn="+t+" phase="+ph+" life="+me.getLife()+" creature_tokens="+countThopters()+" peak_creature_tokens="+peakThopters+" token_total_seen="+totalThopterTokensSeen+" p1_counters_board="+FINAL_P1_COUNTERS.get()+" countered_creatures="+FINAL_COUNTERED_CREATURES.get()+" peak_p1_counters="+PEAK_P1_COUNTERS.get()+" peak_countered_creatures="+PEAK_COUNTERED_CREATURES.get()+" peak_creature_power="+PEAK_CREATURE_POWER.get()+" food="+countFoodTokens()+" active_draw_engines=["+activeDrawEngines()+"] draw_engines_in_hand=["+drawEnginesInHand()+"] battlefield=["+bf+"] hand=["+hs+"]");

        if(lastLibrary==null || true){
          CardCollectionView l=me.getCardsIn(ZoneType.Library);
          List<String> lib=namesList(l);
          if(lastLibrary==null){
            System.out.println("FARMER_AUDIT INITIAL reason="+reason+" turn="+t+" phase="+ph);
            System.out.println("FARMER_AUDIT HAND("+h.size()+"): "+hs);
            System.out.println("FARMER_AUDIT LIBRARY_INITIAL("+lib.size()+", top-to-bottom): "+String.join(" | ",lib));
          } else {
            boolean prefixRemoval=false; int removedCount=lastLibrary.size()-lib.size();
            if(removedCount>0){prefixRemoval=true;for(int i=0;i<lib.size();i++){if(!lastLibrary.get(i+removedCount).equals(lib.get(i))){prefixRemoval=false;break;}}}
            if(prefixRemoval){
              List<String> removed=new ArrayList<>(lastLibrary.subList(0,removedCount)); Set<String> drawCandidates=activeDrawEngineSet();
              int handBefore=lastHandSize<0?h.size():lastHandSize,handAfter=h.size(),handDelta=handAfter-handBefore; String source,access,confidence;
              boolean removedNowInExile=false;
              if(faldornDeck()){
                try{
                  Map<String,Integer> exCounts=new HashMap<>(); for(Card ec:me.getCardsIn(ZoneType.Exile)) exCounts.put(ec.getName(),exCounts.getOrDefault(ec.getName(),0)+1);
                  Map<String,Integer> need=new HashMap<>(); for(String rn:removed) need.put(rn,need.getOrDefault(rn,0)+1);
                  removedNowInExile=true; for(Map.Entry<String,Integer> e:need.entrySet()) if(exCounts.getOrDefault(e.getKey(),0)<e.getValue()){removedNowInExile=false;break;}
                }catch(Exception ignored){removedNowInExile=false;}
              }
              if(removedNowInExile){source="FALDORN_OR_IMPULSE_EXILE";access="EXILE_ACCESS";confidence="high";}
              else if(!pendingImmediateDrawSource.isEmpty()&&(t==pendingImmediateDrawTurn||t==pendingImmediateDrawTurn+1)){source=pendingImmediateDrawSource;access="DRAW_CONFIRMED";confidence="high";pendingImmediateDrawSource="";pendingImmediateDrawTurn=-999;}
              else if("DRAW".equals(ph)&&removedCount==1){source="TURN_DRAW";access="TURN_DRAW";confidence="high";}
              else if(drawCandidates.size()==1){source=drawCandidates.iterator().next();if("Augur of Autumn".equals(source)&&handDelta<=0){access="TOP_ACCESS_LIKELY";confidence="medium";}else{access=handDelta>0?"DRAW_LIKELY":"DRAW_OR_TOP_ACCESS";confidence=handDelta>0?"medium":"low";}}
              else{source=drawCandidates.isEmpty()?"UNATTRIBUTED":"AMBIGUOUS:"+String.join("|",drawCandidates);access=handDelta>0?"DRAW_LIKELY":"DRAW_OR_TOP_ACCESS";confidence=drawCandidates.isEmpty()?"low":"medium";}
              appendDrawLedger(t,ph,access,source,confidence,removed,handBefore,handAfter,lastLibrary.size(),lib.size(),String.join("|",drawCandidates));
              for(String card:removed)System.out.println("FARMER_DRAW_EVENT seq="+(++drawEventSeq)+" card="+card+" turn="+t+" phase="+ph+" kind="+access+" hand="+h.size()+" source_hint=["+source+"] active_draw_engines=["+activeDrawEngines()+"] draw_engines_in_hand=["+drawEnginesInHand()+"] library_now="+lib.size());
              System.out.println("FARMER_AUDIT DRAW_OR_TOP_REMOVE cards="+removedCount+" names=["+String.join(" | ",removed)+"] library_now="+lib.size());
            }
            if(!prefixRemoval&&!lib.equals(lastLibrary))System.out.println("FARMER_AUDIT LIBRARY_MUTATION reason="+reason+" size="+lib.size()+" top-to-bottom="+String.join(" | ",lib));
          }
          lastLibrary=lib;
        }
        lastHand=hs; lastHandSize=h.size(); lastBattlefield=bf; lastAuditTurn=t; lastAuditPhase=ph;
      }catch(Exception e){System.out.println("FARMER_AUDIT snapshot_error="+e.getClass().getSimpleName()+":"+e.getMessage());}
    }

    private static final Set<String> COMBO_CORE = new HashSet<>(Arrays.asList(
      "Basalt Monolith","Thrasios, Triton Hero"
    ));
    private static final Set<String> MANA_DORKS = new HashSet<>(Arrays.asList(
      "Llanowar Elves","Elvish Mystic","Fyndhorn Elves","Birds of Paradise","Delighted Halfling","Noble Hierarch","Arbor Elf",
      "Devoted Druid","Incubation Druid","Bloom Tender","Elvish Archdruid","Marwyn, the Nurturer","Rishkar, Peema Renegade"
    ));
    private static final Set<String> BIG_HITS = new HashSet<>(Arrays.asList(
      "Consecrated Sphinx","Hullbreaker Horror","Tidespout Tyrant","Jin-Gitaxias, Core Augur","Nezahal, Primal Tide",
      "Scourge of Fleets","Kederekt Leviathan","Pathrazer of Ulamog","It That Betrays","Artisan of Kozilek","Ulamog's Crusher"
    ));
    private static final Set<String> HAND_REPAIR = new HashSet<>(Arrays.asList(
      "Brainstorm","Dream Cache","Scroll Rack","Brainstone","Riverwise Augur","Cavalier of Gales","Jace, the Mind Sculptor"
    ));
    private static final Set<String> IMPACT_UTILITY = new HashSet<>(Arrays.asList(
      "Glen Elendra Archmage","Spellskite","Destiny Spinner","Tireless Provisioner","Cavalier of Gales","Riverwise Augur"
    ));
    // Strict deck-evaluation definition: only creatures that materially advance the
    // blue-lock / Eldrazi-sacrifice / immediate board-control plan are PREMIUM.
    // Everything else, including mana dorks and generic utility/value creatures, is a WHIFF.
    private static final Set<String> PREMIUM_FARMER_HITS = new HashSet<>(Arrays.asList(
      "Hullbreaker Horror","Tidespout Tyrant","Jin-Gitaxias, Core Augur",
      "Scourge of Fleets","Kederekt Leviathan",
      "Pathrazer of Ulamog","It That Betrays","Artisan of Kozilek","Ulamog's Crusher",
      "Terastodon","Titan of Industry","Kogla, the Titan Ape","Apex Altisaur","Thorn Mammoth"
    ));

    private boolean isKinnanActivationMove(ZoneType destination, List<ZoneType> origin, SpellAbility sa, Player decider){
      return decider==me && destination==ZoneType.Battlefield && origin!=null && origin.contains(ZoneType.Library)
        && sa!=null && sa.getHostCard()!=null && "Kinnan, Bonder Prodigy".equals(sa.getHostCard().getName()) && !sa.isSpell();
    }
    private String topFiveNow(){
      List<String> all=namesList(me.getCardsIn(ZoneType.Library));
      return String.join(" | ", all.subList(0, Math.min(5, all.size())));
    }
    private void logKinnanResolution(SpellAbility sa, CardCollection options, Card chosen, String top5){
      int id=++kinnanActivationSeq;
      String candidates=options==null?"":cardNames(options);
      String selected=chosen==null?"NONE":chosen.getName();
      boolean premium=chosen!=null && PREMIUM_FARMER_HITS.contains(chosen.getName());
      if(premium) kinnanPremiumHits++; else kinnanWhiffs++;
      ACTIVATION_COUNT.incrementAndGet();
      if(premium) PREMIUM_COUNT.incrementAndGet(); else WHIFF_COUNT.incrementAndGet();
      System.out.println("FARMER_ACTIVATION id="+id+" turn="+me.getGame().getPhaseHandler().getTurn()+
        " top5=["+top5+"] candidates=["+candidates+"] selected="+selected+
        " evaluation="+(premium?"PREMIUM":"WHIFF")+
        " premium_total="+kinnanPremiumHits+" whiff_total="+kinnanWhiffs);
    }

    private boolean hasNamed(String name, ZoneType... zones){
      for(ZoneType z: zones) for(Card c: me.getCardsIn(z)) if(name.equals(c.getName())) return true;
      return false;
    }
    private Card findNamed(CardCollection options, String name){
      if(options==null)return null; for(Card c:options) if(name.equals(c.getName())) return c; return null;
    }
    private boolean kinnanAvailable(){ return hasNamed("Kinnan, Bonder Prodigy", ZoneType.Battlefield, ZoneType.Command, ZoneType.Hand); }
    private boolean basaltAvailable(){ return hasNamed("Basalt Monolith", ZoneType.Battlefield, ZoneType.Hand); }
    private boolean thrasiosAvailable(){ return hasNamed("Thrasios, Triton Hero", ZoneType.Battlefield, ZoneType.Hand); }
    private boolean comboLiveOrNear(){ return kinnanAvailable() && basaltAvailable() && thrasiosAvailable(); }
    private boolean kinnanOnBattlefield(){ return hasNamed("Kinnan, Bonder Prodigy", ZoneType.Battlefield); }
    private boolean basaltOnBattlefield(){ return hasNamed("Basalt Monolith", ZoneType.Battlefield); }
    private boolean thrasiosOnBattlefield(){ return hasNamed("Thrasios, Triton Hero", ZoneType.Battlefield); }
    private int strandedBombCount(){ int n=0; for(Card c:me.getCardsIn(ZoneType.Hand)) if(BIG_HITS.contains(c.getName())) n++; return n; }
    private boolean repairNeeded(){ return strandedBombCount()>0; }

    private int impactScore(String n){
      if(n==null)return 0;
      if("Faldorn, Dread Wolf Herald".equals(n)) return 2300;
      if("Formidable Speaker".equals(n)) return 2250;
      if(FALDORN_DOUBLERS.contains(n)) return 2150;
      if("Shared Animosity".equals(n)) return 2050;
      if(FALDORN_EXILE_ENGINES.contains(n)) return 1900;
      if("Mowu, Loyal Companion".equals(n)) return 2200;
      if("Kalonian Hydra".equals(n)) return 2050;
      if("Bristly Bill, Spine Sower".equals(n)) return 1950;
      if("Saryth, the Viper's Fang".equals(n)) return 1850;
      if("Managorger Hydra".equals(n)||"Defiler of Vigor".equals(n)) return 1750;
      if("Toski, Bearer of Secrets".equals(n)||"Beast Whisperer".equals(n)) return 1650;
      if("Guardian Project".equals(n)||"Sylvan Library".equals(n)) return 1700;
      if("Forgotten Ancient".equals(n)) return 1725;
      if("Ouroboroid".equals(n)) return 2100;
      if("Tale of Katara and Toph".equals(n)) return 2025;
      if("Ivy Lane Denizen".equals(n)) return 1580;
      if("Champion of Lambholt".equals(n)) return 1500;
      if("Rosie Cotton of South Lane".equals(n)) return 1480;
      if("Scurry Oak".equals(n)||"Herd Baloth".equals(n)) return 1440;
      if("Ozolith, the Shattered Spire".equals(n)) return 1490;
      if("Innkeeper\'s Talent".equals(n)) return 1460;
      if("Academy Manufactor".equals(n)) return 1400;
      if("Peregrin Took".equals(n)) return 1380;
      if("Tireless Provisioner".equals(n)) return 1360;
      if("Mycoloth".equals(n)) return 1320;
      if("Scute Swarm".equals(n)) return 1300;
      if("Bennie Bracks, Zoologist".equals(n)||"Ohran Frostfang".equals(n)) return 1250;
      if(RAMP.contains(n)) return 500;
      return 800;
    }

    private static final Set<String> TOKEN_ENGINES = new HashSet<>(Arrays.asList(
      "Rhys the Redeemed","Felidar Retreat","March of the Multitudes","Secure the Wastes","Finale of Glory","Scute Swarm","Scurry Oak","Herd Baloth","Aura Mutation","Nissa, Voice of Zendikar","Avenger of Zendikar","Primal Vigor","Sundering Growth","Adeline, Resplendent Cathar","Hop to It","Emmara, Soul of the Accord","Queen Allenal of Ruadach","Esika's Chariot","Torens, Fist of the Angels","Elspeth, Storm Slayer","Tendershoot Dryad"
    ));
    private static final Set<String> COUNTER_ENGINES = new HashSet<>(Arrays.asList(
      "Rosie Cotton of South Lane","Good-Fortune Unicorn","Hardened Scales","Branching Evolution","Tribute to the World Tree","Ozolith, the Shattered Spire","Innkeeper's Talent","Tale of Katara and Toph","Ouroboroid","Cathars' Crusade","Primal Vigor","Nissa, Voice of Zendikar","Felidar Retreat","Torens, Fist of the Angels","Elspeth, Storm Slayer"
    ));
    private static final Set<String> FOOD_ENGINES = new HashSet<>(Arrays.asList(
      "Farmer Cotton","Peregrin Took","Samwise Gamgee","Tireless Provisioner","Gilded Goose","Sam, Loyal Attendant","Academy Manufactor","The Shire"
    ));
    private static final Set<String> DRAW_ENGINES = new HashSet<>(Arrays.asList(
      "Skullclamp","Caretaker's Talent","Bennie Bracks, Zoologist","Ohran Frostfang","Guardian Project","Welcoming Vampire","Toski, Bearer of Secrets","Bugenhagen, Wise Elder","Elder Gargaroth","Tribute to the World Tree",
      "Beast Whisperer","Augur of Autumn","Sylvan Library","Return of the Wildspeaker","Rishkar's Expertise"
    ));
    private static final Set<String> PAYOFFS = new HashSet<>(Arrays.asList(
      "Champion of Lambholt","Rosie Cotton of South Lane","Cathars' Crusade","Ozolith, the Shattered Spire","Innkeeper's Talent","Nissa, Voice of Zendikar","Hamza, Guardian of Arashin","Elder Gargaroth","Adeline, Resplendent Cathar","Queen Allenal of Ruadach","Torens, Fist of the Angels","Elspeth, Storm Slayer","Tendershoot Dryad"
    ));
    private static final Set<String> RAMP = new HashSet<>(Arrays.asList(
      "Birds of Paradise","Llanowar Elves","Elvish Mystic","Fyndhorn Elves","Avacyn's Pilgrim","Delighted Halfling","Noble Hierarch","Bloom Tender","Incubation Druid","Devoted Druid","Selvala, Heart of the Wilds","Bugenhagen, Wise Elder",
      "Talisman of Unity","Talisman of Impulse","Sol Ring","Arcane Signet","Emerald Medallion","Wild Growth","Utopia Sprawl","Nature's Lore","Three Visits","Rampant Growth"
    ));
    private static final Set<String> INTERACTION = new HashSet<>(Arrays.asList(
      "Path to Exile","Swords to Plowshares","Nature's Claim","Naturalize","Foundation Breaker","Song of the Dryads","Kenrith's Transformation","Sundering Growth","Aura Mutation","Beast Within",
      "Ram Through","Tail Swipe","Warg Tactics","Origin of Metalbending","Lightning Bolt","Flame Slash","Fiery Temper","Avacyn's Judgment","Vandalblast","Ancient Grudge","Pyroblast"
    ));
    private static final Set<String> PROTECTION = new HashSet<>(Arrays.asList(
      "Surge of Salvation","Snakeskin Veil","Tamiyo's Safekeeping","Tyvar's Stand","Gaea's Gift","Royal Treatment","Loran's Escape","Galadriel's Dismissal","Restoration Magic","Warg Tactics","Origin of Metalbending","Heroic Intervention","Veil of Summer","Deflecting Swat"
    ));
    private static final Set<String> TUTORS = new HashSet<>(Arrays.asList(
      "Worldly Tutor","Sylvan Tutor","Chord of Calling","Green Sun's Zenith","Enlightened Tutor","Eladamri's Call","Idyllic Tutor","Gamble","Imperial Recruiter"
    ));
    private static final Set<String> LIFEGAIN_PAYOFFS = new HashSet<>(Arrays.asList(
      "Lathiel, the Bounteous Dawn","Trelasarra, Moon Dancer","Cleric Class","Aerith Gainsborough"
    ));
    private static final Set<String> SINGLE_TARGET_REMOVAL = new HashSet<>(Arrays.asList(
      "Path to Exile","Swords to Plowshares","Nature's Claim","Sundering Growth","Aura Mutation","Beast Within","Shattering Spree"
    ));
    // v18.6: alternate-win permanents are not ordinary value pieces. If one can win the game
    // from the battlefield, treat it as a must-answer threat and spend legal interaction now.
    private static final Set<String> MUST_ANSWER_ALT_WIN = new HashSet<>(Arrays.asList(
      "Liliana's Contract","Revel in Riches","Mechanized Production","Simic Ascendancy",
      "Test of Endurance","Felidar Sovereign","Helix Pinnacle","Darksteel Reactor",
      "Mayael's Aria","Epic Struggle","Mirrodin Besieged","Triskaidekaphile"
    ));
    private static final Set<String> SINGLE_TARGET_PROTECTION = new HashSet<>(Arrays.asList(
      "Snakeskin Veil","Tamiyo's Safekeeping","Tyvar's Stand","Gaea's Gift","Royal Treatment","Loran's Escape","Restoration Magic","Warg Tactics","Origin of Metalbending"
    ));
    private static final Set<String> MOWU_FIGHT = new HashSet<>(Arrays.asList("Ram Through","Tail Swipe"));
    private static final Set<String> MOWU_TARGETED_SUPPORT = new HashSet<>(Arrays.asList("Hydra's Growth","Alpha Authority","Swiftfoot Boots","Tyrite Sanctum"));
    private static final Set<String> MOWU_COUNTER_STARTERS = new HashSet<>(Arrays.asList(
      "Retreat to Kazandu","Ride the Shoopuf","Origin of Metalbending","Bristly Bill, Spine Sower","Biophagus","Michelangelo, Weirdness to 11",
      "Rishkar, Peema Renegade","Ouroboroid","Oran-Rief Ooze","Verdurous Gearhulk","Defiler of Vigor","Managorger Hydra","Forgotten Ancient","Ivy Lane Denizen"
    ));
    private static final Set<String> EARLY_ENGINES = new HashSet<>(Arrays.asList(
      "Rosie Cotton of South Lane","Good-Fortune Unicorn","Hardened Scales","Ozolith, the Shattered Spire","Innkeeper's Talent","Peregrin Took","Samwise Gamgee","Tireless Provisioner",
      "Skullclamp","Tocasia's Welcome","Caretaker's Talent","Champion of Lambholt","Scurry Oak","Haliya, Ascendant Cadet","Welcoming Vampire","Beast Whisperer","Colossal Majesty","Bugenhagen, Wise Elder","Adeline, Resplendent Cathar","Hop to It","Emmara, Soul of the Accord","Queen Allenal of Ruadach","Esika's Chariot","Oketra's Monument","Torens, Fist of the Angels","Tendershoot Dryad"
    ));

    private boolean producerOnline(){ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(TOKEN_ENGINES.contains(c.getName()) || FOOD_ENGINES.contains(c.getName())) return true; return false; }
    private boolean lifegainPayoffOnline(){
      for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(LIFEGAIN_PAYOFFS.contains(c.getName())) return true;
      return false;
    }
    private Card bestSamwiseHistoricTarget(SpellAbility sa){
      try{
        for(Card c:me.getCardsIn(ZoneType.Graveyard)) if(sa.canTarget(c)) return c;
      }catch(Exception ignored){}
      return null;
    }
    private boolean prepareSamwiseIfUseful(SpellAbility sa){
      try{
        if(sa==null || sa.getHostCard()==null || !"Samwise Gamgee".equals(sa.getHostCard().getName()) || !sa.isActivatedAbility()) return true;
        if(countFoodTokens()<3) return false;
        if(!sa.usesTargeting()) return true;
        Card target=bestSamwiseHistoricTarget(sa);
        if(target==null){ if(auditEnabled) System.out.println("FARMER_SAMWISE_SKIP reason=no_legal_historic_target food="+countFoodTokens()); return false; }
        sa.resetTargets(); sa.getTargets().add(target);
        if(auditEnabled) System.out.println("FARMER_SAMWISE_TARGET target="+target.getName()+" food="+countFoodTokens());
        return sa.isTargetNumberValid();
      }catch(Exception e){ return false; }
    }

    private int permanentThreatScore(Card c){
      if(c==null) return 0;
      int s=0;
      try{ s += Math.max(0,c.getNetPower())*4 + Math.max(0,c.getNetToughness())*2; }catch(Exception ignored){}
      try{ s += Math.max(0,c.getCMC())*3; }catch(Exception ignored){}
      String n=c.getName();
      if(MUST_ANSWER_ALT_WIN.contains(n)) s+=10000;
      if("Krenko, Mob Boss".equals(n)) s+=180;
      if("Aesi, Tyrant of Gyre Strait".equals(n)) s+=110;
      if("Reaper King".equals(n)) s+=100;
      if("Shelob, Child of Ungoliant".equals(n)) s+=90;
      if("Cass, Hand of Vengeance".equals(n)) s+=70;
      if("Zedruu the Greathearted".equals(n)) s+=45;
      try{ if(c.getType().isPlaneswalker()) s+=45; }catch(Exception ignored){}
      try{ if(c.getType().isEnchantment() || c.getType().isArtifact()) s+=12; }catch(Exception ignored){}
      return s;
    }

    private int playerThreatScore(Player p){
      if(p==null || p.hasLost()) return Integer.MIN_VALUE;
      int s=0, creatures=0, totalPower=0;
      for(Card c:p.getCardsIn(ZoneType.Battlefield)){
        s += permanentThreatScore(c);
        try{ if(c.getType().isCreature()){ creatures++; totalPower += Math.max(0,c.getNetPower()); } }catch(Exception ignored){}
      }
      s += creatures*5 + totalPower*2;
      String pn=p.getName().toLowerCase(Locale.ROOT);
      if(pn.contains("krenko")) s+=140;
      if(pn.contains("aesi")) s+=70;
      if(p.getLife()<=10) s-=25; // dying players are lower survival priority unless lethal is immediate
      return s;
    }

    private Player topThreat(){
      Player best=null; int score=Integer.MIN_VALUE;
      for(Player p:me.getOpponents()){ int s=playerThreatScore(p); if(s>score){score=s;best=p;} }
      return best;
    }

    private int topThreatScore(){ Player p=topThreat(); return p==null?0:playerThreatScore(p); }

    private boolean survivalMode(){
      int threat=topThreatScore();
      int oppCreatures=0, oppPower=0;
      Player p=topThreat();
      if(p!=null){ for(Card c:p.getCardsIn(ZoneType.Battlefield)) try{ if(c.getType().isCreature()){oppCreatures++;oppPower+=Math.max(0,c.getNetPower());} }catch(Exception ignored){} }
      return me.getLife()<=22 || threat>=170 || oppCreatures>=7 || oppPower>=24;
    }

    private boolean stackThreatensUs(){
      try{
        if(me.getGame().getStack().isEmpty()) return false;
        SpellAbility top=me.getGame().getStack().peekAbility();
        if(top==null) return false;
        Player ap=top.getActivatingPlayer();
        if(ap==me) return false;
        for(forge.game.GameObject o:top.getTargets()){
          if(o==me) return true;
          if(o instanceof Card && ((Card)o).getController()==me) return true;
        }
        String n=top.getHostCard()==null?"":top.getHostCard().getName().toLowerCase(Locale.ROOT);
        return n.contains("wrath") || n.contains("massacre") || n.contains("blasphemous act") || n.contains("farewell") || n.contains("damnation") || n.contains("supreme verdict") || n.contains("austere command");
      }catch(Exception e){ return false; }
    }

    private int tappedCreatureCount(){
      int n=0; try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature() && c.isTapped()) n++; }catch(Exception ignored){} return n;
    }
    private int totalCreaturePower(){
      int n=0; try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature()) n += Math.max(0,c.getNetPower()); }catch(Exception ignored){} return n;
    }
    private int maxCreaturePower(){
      int n=0; try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature()) n=Math.max(n,Math.max(0,c.getNetPower())); }catch(Exception ignored){} return n;
    }
    private int creaturesWithP1P1Counters(){
      int n=0; try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature() && c.getPowerBonusFromCounters()>0) n++; }catch(Exception ignored){} return n;
    }
    private int usefulCreatureCount(){ return farmerCreatureCount(); }

    // v15 Rhys timing: the six-mana ability is primarily an instant-speed setup play.
    // Default to the end step immediately before our turn so copied tokens untap attack-ready
    // and opponents get the smallest possible response window. MAIN1 doubling is reserved for
    // boards where token ETBs immediately increase the damage/evasion of creatures that can
    // already attack (Cathars' Crusade / Champion / Rosie), rather than merely making bodies.
    private boolean isPreTurnEndStep(){
      try{
        forge.game.phase.PhaseHandler h=me.getGame().getPhaseHandler();
        return h.getPhase()==PhaseType.END_OF_TURN && !h.isPlayerTurn(me) && h.getNextTurn()==me;
      }catch(Exception e){ return false; }
    }
    private int attackReadyCreatureCount(){
      int n=0;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!c.getType().isCreature() || c.isTapped()) continue;
          boolean can=false;
          for(Player opp:me.getOpponents()) if(!opp.hasLost() && forge.game.combat.CombatUtil.canAttack(c,opp)){ can=true; break; }
          if(can) n++;
        }
      }catch(Exception ignored){}
      return n;
    }
    private boolean rhysMainPhaseDoubleHasImmediateCombatValue(){
      try{
        if(!me.getGame().getPhaseHandler().isPlayerTurn(me) || me.getGame().getPhaseHandler().getPhase()!=PhaseType.MAIN1) return false;
        if(attackReadyCreatureCount()<3) return false;
        // These engines can turn token ETBs into immediate power/evasion on creatures that are already attack-ready.
        return hasNamed("Cathars' Crusade",ZoneType.Battlefield) || hasNamed("Champion of Lambholt",ZoneType.Battlefield) || hasNamed("Rosie Cotton of South Lane",ZoneType.Battlefield);
      }catch(Exception e){ return false; }
    }
    private int rhysDoublePriority(int tok){
      if(tok<3 || tok>=24) return 0;
      if(isPreTurnEndStep()) return 4050 + Math.min(350,tok*20);
      if(rhysMainPhaseDoubleHasImmediateCombatValue()) return 3500 + Math.min(250,tok*15);
      return 0;
    }
    private Card rhysCard(){
      try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if("Rhys the Redeemed".equals(c.getName())) return c; }catch(Exception ignored){}
      return null;
    }
    private boolean rhysReservePlanActive(){
      try{
        PhaseType p=me.getGame().getPhaseHandler().getPhase();
        if(!me.getGame().getPhaseHandler().isPlayerTurn(me) || (p!=PhaseType.MAIN1 && p!=PhaseType.MAIN2)) return false;
        int tok=countThopters(); if(tok<6 || tok>=24 || rhysMainPhaseDoubleHasImmediateCombatValue()) return false;
        Card r=rhysCard(); if(r==null || r.isTapped()) return false;
        boolean ok=ComputerUtilMana.getAvailableManaEstimate(me,true)>=6;
        if(ok){ rhysReserveArmed=true; rhysReserveStartedTurn=me.getGame().getPhaseHandler().getTurn(); }
        return ok;
      }catch(Exception e){ return false; }
    }
    private SpellAbility chooseSurplusDevelopmentHoldingRhys(){
      try{
        int available=Math.max(0,ComputerUtilMana.getAvailableManaEstimate(me,true));
        int surplus=available-6;
        if(surplus<=0) return null;
        List<SpellAbility> candidates=new ArrayList<>();
        candidates.addAll(ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand),me));
        SpellAbility best=null; int bestScore=0;
        for(SpellAbility sa:candidates){
          if(sa==null || !sa.isSpell() || sa.isLandAbility() || !sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
          Card host=sa.getHostCard(); if(host==null) continue;
          try{ if(host.getManaCost()!=null && host.getManaCost().countX()>0) continue; }catch(Exception ignored){}
          int cmc=Math.max(0,host.getCMC());
          if(cmc>surplus) continue;
          if((INTERACTION.contains(host.getName()) || PROTECTION.contains(host.getName()) || isAuraRamp(host.getName())) && !prepareRequiredTargets(sa)) continue;
          int sc=actionPriority(sa);
          if(sc>bestScore){ bestScore=sc; best=sa; }
        }
        if(best!=null && auditEnabled) System.out.println("FARMER_RHYS_RESERVE_SURPLUS available="+available+" reserve=6 surplus="+surplus+" action="+best.getHostCard().getName()+" cmc="+best.getHostCard().getCMC()+" score="+bestScore);
        return bestScore>0?best:null;
      }catch(Exception e){ return null; }
    }

    private String activeDrawEngines(){
      List<String> xs=new ArrayList<>();
      try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(DRAW_ENGINES.contains(c.getName())) xs.add(c.getName()); }catch(Exception ignored){}
      Collections.sort(xs);
      return String.join("|",xs);
    }
    private String drawEnginesInHand(){
      List<String> xs=new ArrayList<>();
      try{ for(Card c:me.getCardsIn(ZoneType.Hand)) if(DRAW_ENGINES.contains(c.getName())) xs.add(c.getName()); }catch(Exception ignored){}
      Collections.sort(xs);
      return String.join("|",xs);
    }
    private Set<String> activeDrawEngineSet(){
      Set<String> xs=new HashSet<>();
      try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(DRAW_ENGINES.contains(c.getName())) xs.add(c.getName()); }catch(Exception ignored){}
      return xs;
    }
    private Set<String> handDrawEngineSet(){
      Set<String> xs=new HashSet<>();
      try{ for(Card c:me.getCardsIn(ZoneType.Hand)) if(DRAW_ENGINES.contains(c.getName())) xs.add(c.getName()); }catch(Exception ignored){}
      return xs;
    }
    private void auditDrawEngineLifecycle(int turn,String phase){
      if(!auditEnabled) return;
      Set<String> active=activeDrawEngineSet(), hand=handDrawEngineSet();
      for(String n:active) if(!lastActiveDrawEngineSet.contains(n)) System.out.println("FARMER_DRAW_ENGINE_ENTER card="+n+" turn="+turn+" phase="+phase+" hand="+me.getCardsIn(ZoneType.Hand).size()+" tokens="+countThopters());
      for(String n:lastActiveDrawEngineSet) if(!active.contains(n)) System.out.println("FARMER_DRAW_ENGINE_EXIT card="+n+" turn="+turn+" phase="+phase+" hand="+me.getCardsIn(ZoneType.Hand).size()+" tokens="+countThopters());
      for(String n:hand) if(!lastHandDrawEngineSet.contains(n)) System.out.println("FARMER_DRAW_ENGINE_IN_HAND card="+n+" turn="+turn+" phase="+phase+" mana="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" tokens="+countThopters());
      for(String n:lastHandDrawEngineSet) if(!hand.contains(n) && !active.contains(n)) System.out.println("FARMER_DRAW_ENGINE_LEFT_HAND card="+n+" turn="+turn+" phase="+phase+" mana="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" tokens="+countThopters());
      for(String n:active) System.out.println("FARMER_DRAW_ENGINE_ACTIVE card="+n+" turn="+turn+" phase="+phase+" hand="+me.getCardsIn(ZoneType.Hand).size()+" tokens="+countThopters()+" creatures="+farmerCreatureCount());
      lastActiveDrawEngineSet.clear(); lastActiveDrawEngineSet.addAll(active);
      lastHandDrawEngineSet.clear(); lastHandDrawEngineSet.addAll(hand);
    }
    private void noteDrawEngineAction(SpellAbility sa,String context){
      try{
        if(sa==null||sa.getHostCard()==null)return; String n=sa.getHostCard().getName(); int t=me.getGame().getPhaseHandler().getTurn();
        if(auditEnabled && (DRAW_ENGINES.contains(n)||"War Room".equals(n)||"Bonders' Enclave".equals(n))) System.out.println("FARMER_DRAW_ENGINE_ACTION card="+n+" context="+context+" turn="+t+" phase="+me.getGame().getPhaseHandler().getPhase()+" mana="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" hand="+me.getCardsIn(ZoneType.Hand).size()+" tokens="+countThopters());
        if("Return of the Wildspeaker".equals(n)||"Rishkar's Expertise".equals(n)||"War Room".equals(n)||"Bonders' Enclave".equals(n)){pendingImmediateDrawSource=n;pendingImmediateDrawTurn=t;}
      }catch(Exception ignored){}
    }
    private boolean riteCommitActive(){
      if(!riteCommitted) return false;
      int t=me.getGame().getPhaseHandler().getTurn();
      if(t!=riteCommitTurn || riteCommitRemaining<=0){ riteCommitted=false; riteCommitRemaining=0; return false; }
      return true;
    }
    private int riteFollowupEntries(SpellAbility a){
      if(a==null||a.getHostCard()==null||!a.isSpell()) return 0;
      String n=a.getHostCard().getName();
      int mana=Math.max(0,ComputerUtilMana.getAvailableManaEstimate(me,true));
      if("Secure the Wastes".equals(n)) return Math.max(0,mana-1);
      if("Finale of Glory".equals(n)||"White Sun's Twilight".equals(n)) return Math.max(0,mana-2);
      if("March of the Multitudes".equals(n)) return Math.max(0,mana-3);
      if("Avenger of Zendikar".equals(n)) return 1+Math.max(0,battlefieldLandCount());
      try{ if(a.getHostCard().getType().isCreature() || a.getHostCard().getType().isEnchantment()) return 1; }catch(Exception ignored){}
      return 0;
    }
    private void noteRiteChosen(SpellAbility a){
      if(a==null||a.getHostCard()==null) return;
      if("Rite of Harmony".equals(a.getHostCard().getName()) && a.isSpell()){
        riteCommitTurn=me.getGame().getPhaseHandler().getTurn(); riteCommitRemaining=Math.max(2,riteExpectedTriggers()); riteCommitted=true;
        if(auditEnabled) System.out.println("FARMER_RITE_COMMIT turn="+riteCommitTurn+" expected="+riteCommitRemaining);
      } else if(riteCommitActive()){
        int e=riteFollowupEntries(a); if(e>0){ riteCommitRemaining=Math.max(0,riteCommitRemaining-e); if(auditEnabled) System.out.println("FARMER_RITE_FOLLOWUP card="+a.getHostCard().getName()+" entries="+e+" remaining="+riteCommitRemaining); if(riteCommitRemaining<=0) riteCommitted=false; }
      }
    }

    private int battlefieldLandCount(){
      int n=0; for(Card c:me.getCardsIn(ZoneType.Battlefield)) try{ if(c.isLand()) n++; }catch(Exception ignored){} return n;
    }
    private int battlefieldRampCount(){
      int n=0; for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(RAMP.contains(c.getName())) n++; return n;
    }
    private int approximateManaDevelopment(){ return battlefieldLandCount()+battlefieldRampCount(); }
    private boolean manaStarved(){
      int lands=battlefieldLandCount(), dev=approximateManaDevelopment();
      return lands<=3 || dev<=4;
    }
    // Premium tutors are not routine ramp spells in a two-color deck.  Only spend one on mana
    // when the current position is genuinely nonfunctional: severe raw-source shortage or a
    // color is completely absent while multiple cards in hand require it.
    private boolean emergencyManaRepair(){
      CardCollectionView hand=me.getCardsIn(ZoneType.Hand);
      int lands=battlefieldLandCount(), dev=approximateManaDevelopment();
      int greenSources=0, whiteSources=0;
      for(Card c:me.getCardsIn(ZoneType.Battlefield)){
        if(openingLandMakesGreen(c)) greenSources++;
        if(openingLandMakesWhite(c)) whiteSources++;
        String n=c.getName();
        if("Birds of Paradise".equals(n)||"Bloom Tender".equals(n)||"Incubation Druid".equals(n)||"Delighted Halfling".equals(n)) { greenSources++; whiteSources++; }
        else if("Avacyn's Pilgrim".equals(n)) whiteSources++;
        else if("Llanowar Elves".equals(n)||"Noble Hierarch".equals(n)) greenSources++;
        else if("Arcane Signet".equals(n)||"Talisman of Unity".equals(n)) { greenSources++; whiteSources++; }
      }
      // v8.9: battlefield land count by itself NEVER authorizes spending a premium tutor on mana.
      // In this two-color deck, if both colors are already represented, Worldly/Enlightened Tutor
      // must find an engine, value piece, body producer, interaction, or kill converter instead.
      // Mana repair is reserved only for a truly absent required color.
      boolean greenScrew = greenSources==0 && greenDemand(hand)>=2;
      boolean whiteScrew = whiteSources==0 && whiteDemand(hand)>=2;
      boolean emergency = greenScrew || whiteScrew;
      if(auditEnabled && emergency) System.out.println("FARMER_MANA_EMERGENCY lands="+lands+" dev="+dev+" green_sources="+greenSources+" white_sources="+whiteSources+" green_demand="+greenDemand(hand)+" white_demand="+whiteDemand(hand)+" green_screw="+greenScrew+" white_screw="+whiteScrew);
      return emergency;
    }
    private boolean manaHungry(){
      int dev=approximateManaDevelopment();
      if(dev<=5) return true;
      // Farmer itself and the deck's X-spells reward continued development well past the opening turns.
      if(hasNamed("Farmer Cotton",ZoneType.Command) && dev<7) return true;
      for(Card c:me.getCardsIn(ZoneType.Hand)){
        String n=c.getName();
        if("March of the Multitudes".equals(n)||"Secure the Wastes".equals(n)||"White Sun's Twilight".equals(n)||"Finale of Glory".equals(n)) return dev<7;
        try{ if(c.getManaCost().getCMC()>=5 && dev<6) return true; }catch(Exception ignored){}
      }
      return false;
    }
    private boolean tokenProducerInHand(){
      try{ for(Card c:me.getCardsIn(ZoneType.Hand)){ String n=c.getName(); if(TOKEN_ENGINES.contains(n) && !"Primal Vigor".equals(n) && !"Elspeth, Storm Slayer".equals(n)) return true; } }catch(Exception ignored){}
      return false;
    }
    private int nontokenCreaturesInHand(){
      int n=0; try{ for(Card c:me.getCardsIn(ZoneType.Hand)) if(c.getType().isCreature()) n++; }catch(Exception ignored){} return n;
    }
    private static final Set<String> DIRECT_BODY_RECOVERY = new HashSet<>(Arrays.asList(
      "Hop to It","Esika's Chariot","Tendershoot Dryad","Adeline, Resplendent Cathar","Torens, Fist of the Angels",
      "Queen Allenal of Ruadach","Emmara, Soul of the Accord","Scute Swarm","Scurry Oak","Herd Baloth",
      "Avenger of Zendikar","Felidar Retreat","Finale of Glory","Secure the Wastes","March of the Multitudes",
      "Nissa, Voice of Zendikar","Elspeth, Storm Slayer"
    ));
    private int recoveryBodyEstimate(SpellAbility sa){
      if(sa==null || sa.getHostCard()==null) return 0;
      String n=sa.getHostCard().getName();
      int mana=Math.max(0,conservativeUntappedManaCapacity());
      if("Rhys the Redeemed".equals(n)) return 1;
      if("Hop to It".equals(n)) return 3;
      if("Esika's Chariot".equals(n)) return 2;
      if("Secure the Wastes".equals(n)) return Math.max(0,mana-1);
      if("Finale of Glory".equals(n)) return Math.max(0,mana-2);
      if("March of the Multitudes".equals(n)) return Math.max(0,ComputerUtilMana.getAvailableManaEstimate(me,true)-3);
      if("Avenger of Zendikar".equals(n)) return Math.max(1,battlefieldLandCount());
      if("Tendershoot Dryad".equals(n)) return 4; // repeatable, one token each upkeep around table
      if("Adeline, Resplendent Cathar".equals(n)) return 3;
      if("Torens, Fist of the Angels".equals(n)) return 3;
      if("Queen Allenal of Ruadach".equals(n)) return 2;
      if("Emmara, Soul of the Accord".equals(n)) return 2;
      if("Scute Swarm".equals(n)) return 3;
      if("Scurry Oak".equals(n)||"Herd Baloth".equals(n)) return 2;
      if("Felidar Retreat".equals(n)) return 3;
      if("Nissa, Voice of Zendikar".equals(n)) return 1;
      if("Elspeth, Storm Slayer".equals(n)) return 3;
      return 1;
    }
    private boolean counterInitiatorOnline(){
      return hasNamed("Good-Fortune Unicorn",ZoneType.Battlefield) ||
             hasNamed("Rosie Cotton of South Lane",ZoneType.Battlefield) ||
             hasNamed("Tribute to the World Tree",ZoneType.Battlefield) ||
             hasNamed("Cathars' Crusade",ZoneType.Battlefield);
    }
    private int bodyBurstEstimateWithMana(String n,int mana){
      if(mana<0 || n==null) return 0;
      if("Hop to It".equals(n)) return mana>=3?3:0;
      if("Esika's Chariot".equals(n)) return mana>=4?2:0;
      if("Secure the Wastes".equals(n)) return Math.max(0,mana-1);
      if("Finale of Glory".equals(n)) return Math.max(0,mana-2);
      if("March of the Multitudes".equals(n)) return Math.max(0,mana-3); // conservative: ignores convoke upside
      if("Avenger of Zendikar".equals(n)) return mana>=7?Math.max(1,battlefieldLandCount()):0;
      if("Elspeth, Storm Slayer".equals(n)) return mana>=5?3:0;
      if("Nissa, Voice of Zendikar".equals(n)) return mana>=3?1:0;
      return 0;
    }
    private int bestBurstBodiesAfterManaSpend(int spent){
      int mana=Math.max(0,conservativeUntappedManaCapacity()-Math.max(0,spent));
      int best=0;
      try{for(Card c:me.getCardsIn(ZoneType.Hand)) best=Math.max(best,bodyBurstEstimateWithMana(c.getName(),mana));}catch(Exception ignored){}
      return best;
    }
    private SpellAbility chooseCounterInitiatorBeforeBurst(){
      // v18.5: if a real multi-body burst is available this turn, start the +1/+1-counter engine first
      // when we can still afford a meaningful burst afterward. Multipliers are not initiators.
      if(counterInitiatorOnline() || me.getLife()<=5) return null;
      int mana=conservativeUntappedManaCapacity();
      if(bestBurstBodiesAfterManaSpend(0)<2) return null;
      Set<String> initiators=new HashSet<>(Arrays.asList("Good-Fortune Unicorn","Rosie Cotton of South Lane","Tribute to the World Tree","Cathars' Crusade"));
      SpellAbility best=null; int bestScore=Integer.MIN_VALUE; int bestBodies=0; int bestCmc=0;
      try{
        for(SpellAbility sa:ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand),me)){
          if(sa==null || sa.getHostCard()==null || !sa.isSpell()) continue;
          String n=sa.getHostCard().getName(); if(!initiators.contains(n)) continue;
          int cmc=cardCmc(sa.getHostCard());
          int bodies=bestBurstBodiesAfterManaSpend(cmc);
          if(bodies<2 || cmc>mana || !sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
          int quality=("Good-Fortune Unicorn".equals(n)?500:("Rosie Cotton of South Lane".equals(n)?450:("Tribute to the World Tree".equals(n)?425:350)));
          int sc=bodies*1000 + quality - cmc*25;
          if(sc>bestScore){bestScore=sc;best=sa;bestBodies=bodies;bestCmc=cmc;}
        }
      }catch(Exception ignored){}
      if(best!=null && auditEnabled) System.out.println("FARMER_INITIATOR_BEFORE_BURST action="+best.getHostCard().getName()+" cmc="+bestCmc+" burst_bodies_after="+bestBodies+" mana="+mana+" tokens="+countThopters());
      return best;
    }
    private boolean emergencyImmediateProducer(String n){
      return "Hop to It".equals(n)||"Esika's Chariot".equals(n)||"Secure the Wastes".equals(n)||
             "Finale of Glory".equals(n)||"March of the Multitudes".equals(n)||"Avenger of Zendikar".equals(n)||
             "Nissa, Voice of Zendikar".equals(n)||"Elspeth, Storm Slayer".equals(n)||"Rhys the Redeemed".equals(n);
    }
    private SpellAbility chooseEmergencySurvivalProductionAction(){
      // v18.5: at critical life, immediate blockers/bodies outrank generic draw/setup development.
      if(me.getLife()>5) return null;
      List<SpellAbility> candidates=new ArrayList<>();
      try{candidates.addAll(ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand),me));}catch(Exception ignored){}
      try{for(Card c:me.getCardsIn(ZoneType.Battlefield)){
        String n=c.getName(); if(!"Rhys the Redeemed".equals(n)&&!"Nissa, Voice of Zendikar".equals(n)&&!"Elspeth, Storm Slayer".equals(n)) continue;
        for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null&&!sa.isManaAbility()) candidates.add(sa);
      }}catch(Exception ignored){}
      SpellAbility best=null; int bestBodies=0; int bestScore=Integer.MIN_VALUE;
      for(SpellAbility sa:candidates){
        try{
          if(sa==null||sa.getHostCard()==null) continue;
          String n=sa.getHostCard().getName(); if(!emergencyImmediateProducer(n)) continue;
          if(sa.costHasManaX() && !configurePayableX(sa)) continue;
          if(!sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
          if("Rhys the Redeemed".equals(n) && sa.isActivatedAbility()){
            String d=String.valueOf(sa); if(!(d.contains("Create a 1/1")||d.contains("Elf Warrior")) || d.contains("For each creature token")) continue;
          }
          int bodies=recoveryBodyEstimate(sa);
          int sc=bodies*2000 + actionPriority(sa);
          if(sc>bestScore){bestScore=sc;best=sa;bestBodies=bodies;}
        }catch(Exception ignored){}
      }
      if(best!=null && auditEnabled) System.out.println("FARMER_EMERGENCY_SURVIVAL_FORCE action="+best.getHostCard().getName()+" bodies="+bestBodies+" life="+me.getLife()+" tokens="+countThopters()+" score="+bestScore);
      return best;
    }

    private SpellAbility chooseBodyStarvedProductionAction(){
      if(countThopters()>5) return null;
      if(libraryCriticalMode() && usefulCreatureCount()>=4 && me.getLife()>5){
        if(auditEnabled)System.out.println("FARMER_LIBRARY_RECOVERY_HOLD library="+libraryCardsRemaining()+" reserve="+librarySafetyReserve()+" creatures="+usefulCreatureCount());
        return null;
      }
      List<SpellAbility> candidates=new ArrayList<>();
      try{ candidates.addAll(ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand),me)); }catch(Exception ignored){}
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          String n=c.getName();
          if(!"Rhys the Redeemed".equals(n) && !"Nissa, Voice of Zendikar".equals(n) && !"Elspeth, Storm Slayer".equals(n)) continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null && sa.isActivatedAbility() && !sa.isManaAbility()) candidates.add(sa);
        }
      }catch(Exception ignored){}
      SpellAbility best=null; int bestScore=0;
      for(SpellAbility sa:candidates){
        try{
          if(sa==null || sa.getHostCard()==null || !sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
          String n=sa.getHostCard().getName();
          boolean rhysStarter=false;
          if("Rhys the Redeemed".equals(n) && sa.isActivatedAbility()){
            String d=String.valueOf(sa);
            rhysStarter=(d.contains("Create a 1/1")||d.contains("Elf Warrior")) && !d.contains("For each creature token");
            if(!rhysStarter) continue;
          } else {
            if(!DIRECT_BODY_RECOVERY.contains(n)) continue;
            if(!sa.isSpell() && !"Nissa, Voice of Zendikar".equals(n) && !"Elspeth, Storm Slayer".equals(n)) continue;
          }
          if((INTERACTION.contains(n)||PROTECTION.contains(n)||isAuraRamp(n)) && !prepareRequiredTargets(sa)) continue;
          int bodies=recoveryBodyEstimate(sa);
          int sc=actionPriority(sa) + bodies*1200;
          // v18.6: persistent/repeatable recovery engines are worth more than a one-shot Rhys starter
          // when both are legal. This fixes Scurry Oak/Herd Baloth losing to "make one Elf" by a tiny score.
          if("Scurry Oak".equals(n)||"Herd Baloth".equals(n)) sc+=1400;
          if("Tendershoot Dryad".equals(n)||"Felidar Retreat".equals(n)||"Adeline, Resplendent Cathar".equals(n)||"Torens, Fist of the Angels".equals(n)) sc+=900;
          if(rhysStarter && bodies==1) sc-=250;
          // v18.1: recovery compares BODY OUTPUT, not just card-class priority.
          // A Secure/Finale/March for 4-6 bodies must beat Rhys making one token.
          if(bodies>=3) sc+=1800;
          if("Tendershoot Dryad".equals(n)||"Avenger of Zendikar".equals(n)) sc+=1000;
          if(auditEnabled) System.out.println("FARMER_RECOVERY_CANDIDATE card="+n+" bodies="+bodies+" score="+sc+" rhys_starter="+rhysStarter+" tokens="+countThopters()+" mana_estimate="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" conservative_mana="+conservativeUntappedManaCapacity());
          if(sc>bestScore){bestScore=sc;best=sa;}
        }catch(Exception ignored){}
      }
      if(best!=null && auditEnabled) System.out.println("FARMER_BODY_STARVED_FORCE action="+best.getHostCard().getName()+" score="+bestScore+" tokens="+countThopters()+" mana_estimate="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" conservative_mana="+conservativeUntappedManaCapacity()+" options=["+tokenRecoveryOptions()+"]");
      return best;
    }

    private String tokenRecoveryOptions(){
      List<String> xs=new ArrayList<>();
      int mana=Math.max(0,ComputerUtilMana.getAvailableManaEstimate(me,true));
      try{
        for(Card c:me.getCardsIn(ZoneType.Hand)){
          String n=c.getName();
          if(TOKEN_ENGINES.contains(n) || TUTORS.contains(n)){
            String extra="";
            if("Secure the Wastes".equals(n)) extra="(X~"+Math.max(0,mana-1)+")";
            else if("Finale of Glory".equals(n)) extra="(X~"+Math.max(0,mana-2)+")";
            else if("March of the Multitudes".equals(n)) extra="(X~"+Math.max(0,mana-3)+")";
            xs.add(n+extra);
          }
        }
        Card r=rhysCard();
        if(r!=null){
          boolean starter=false,doubler=false;
          for(SpellAbility sa:r.getAllPossibleAbilities(me,true)) if(sa!=null && sa.isActivatedAbility()){
            String d=String.valueOf(sa);
            if((d.contains("Create a 1/1")||d.contains("Elf Warrior")) && !d.contains("For each creature token")) starter=sa.canPlay()&&fullyPayableStrategicAction(sa);
            if(d.contains("For each creature token")||d.toLowerCase(Locale.ROOT).contains("copy of that creature")) doubler=sa.canPlay()&&fullyPayableStrategicAction(sa);
          }
          xs.add("RhysStarter(payable="+starter+",tapped="+r.isTapped()+")");
          xs.add("RhysDouble(payable="+doubler+",tapped="+r.isTapped()+")");
        } else xs.add("Rhys(absent)");
      }catch(Exception ignored){}
      return String.join("|",xs);
    }
    private void auditLowTokenRecovery(String context){
      if(!auditEnabled || countThopters()>5) return;
      try{
        Card r=rhysCard();
        System.out.println("FARMER_LOW_TOKEN_AUDIT context="+context+
          " turn="+me.getGame().getPhaseHandler().getTurn()+" phase="+me.getGame().getPhaseHandler().getPhase()+
          " tokens="+countThopters()+" creatures="+farmerCreatureCount()+
          " mana_estimate="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" conservative_mana="+conservativeUntappedManaCapacity()+
          " rhys_present="+(r!=null)+" rhys_tapped="+(r!=null&&r.isTapped())+
          " producer_online="+producerOnline()+" options=["+tokenRecoveryOptions()+"]");
      }catch(Exception ignored){}
    }

    private Card preferredAuraRampLand(SpellAbility sa){
      try{
        if(sa==null || sa.getHostCard()==null) return null;
        String host=sa.getHostCard().getName();
        boolean sprawl="Utopia Sprawl".equals(host), growth="Wild Growth".equals(host);
        if(!sprawl && !growth) return null;
        Card best=null; int bestScore=Integer.MIN_VALUE;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c==null || !c.getType().isLand()) continue;
          if(sprawl && !c.getType().hasSubtype("Forest")) continue;
          try{ if(sa.usesTargeting() && !sa.canTarget(c)) continue; }catch(Exception ignored){}
          int sc=0;
          // Prefer an untapped basic Forest, then untapped green/fixing lands; avoid utility lands when possible.
          try{ if(!c.isTapped()) sc+=200; }catch(Exception ignored){}
          if("Forest".equals(c.getName())) sc+=180;
          try{ if(c.getType().hasSubtype("Forest")) sc+=120; }catch(Exception ignored){}
          String n=c.getName();
          if("Gavony Township".equals(n)) sc-=100;
          if(sc>bestScore){bestScore=sc;best=c;}
        }
        return best;
      }catch(Exception e){ return null; }
    }

    private boolean isAuraRamp(String n){ return "Wild Growth".equals(n) || "Utopia Sprawl".equals(n); }

    private boolean auraRampTargetExists(String n){
      if(!"Wild Growth".equals(n) && !"Utopia Sprawl".equals(n)) return true;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c==null || !c.getType().isLand()) continue;
          if("Utopia Sprawl".equals(n) && !c.getType().hasSubtype("Forest")) continue;
          return true;
        }
      }catch(Exception ignored){}
      return false;
    }

    private int combatDrawPerConnection(){
      int n=0;
      if(hasNamed("Ohran Frostfang",ZoneType.Battlefield)) n++;
      if(hasNamed("Toski, Bearer of Secrets",ZoneType.Battlefield)) n++;
      return n;
    }

    private int libraryCardsRemaining(){
      try{ return me.getCardsIn(ZoneType.Library).size(); }catch(Exception e){ return 99; }
    }

    private int activeNonCombatDrawEngineCount(){
      int n=0;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          String x=c.getName();
          if(DRAW_ENGINES.contains(x) && !"Toski, Bearer of Secrets".equals(x) && !"Ohran Frostfang".equals(x)) n++;
        }
      }catch(Exception ignored){}
      return n;
    }
    private int librarySafetyReserve(){
      // Preserve enough cards for normal draw steps plus incidental/mandatory triggers from several engines.
      return 6 + activeNonCombatDrawEngineCount()*3 + combatDrawPerConnection()*2;
    }
    private boolean libraryDangerMode(){
      int lib=libraryCardsRemaining();
      int reserve=librarySafetyReserve();
      return lib<=Math.max(12,reserve+6);
    }
    private boolean libraryCriticalMode(){ return libraryCardsRemaining()<=Math.max(8,librarySafetyReserve()+2); }

    // Raph v0.3.1: hard library/combat safety. Optional extra combats are never worth decking ourselves.
    private int raphProjectedCombatDraws(){
      int per=combatDrawPerConnection();
      if(per<=0) return 0;
      int connections=0;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c==null||!c.getType().isCreature()) continue;
          if(forge.game.combat.CombatUtil.canAttack(c)) connections++;
        }
      }catch(Exception ignored){}
      // Assume every attacker could connect; this intentionally overestimates to keep a safety margin.
      return Math.max(per,connections*per);
    }
    private boolean raphSafeForAnotherCombat(){
      if(!raphDeck()) return true;
      int projected=raphProjectedCombatDraws();
      if(projected<=0) return true;
      int lib=libraryCardsRemaining();
      int reserve=librarySafetyReserve();
      boolean safe=(lib-projected)>reserve;
      if(auditEnabled&&!safe) System.out.println("RAPH_LIBRARY_COMBAT_LOCK library="+lib+" projected_draws="+projected+" reserve="+reserve+" raph_attacks_this_turn="+raphAttacksThisTurn);
      return safe;
    }

    private boolean preserveEngineCreature(Card c){
      if(c==null) return false;
      String n=c.getName();
      return "Bennie Bracks, Zoologist".equals(n) || "Welcoming Vampire".equals(n) ||
             "Ohran Frostfang".equals(n) || "Bugenhagen, Wise Elder".equals(n) ||
             "Beast Whisperer".equals(n) ||
             "Saryth, the Viper's Fang".equals(n) || "Forgotten Ancient".equals(n) ||
             "Ivy Lane Denizen".equals(n);
    }

    private void applyCombatSafety(forge.game.combat.Combat combat, Player primaryTarget, boolean immediateLethal){
      try{
        // Preserve persistent value engines from irrelevant combat. Toski is excluded because it must attack if able.
        if(!immediateLethal){
          List<Card> pull=new ArrayList<>();
          for(Card c:combat.getAttackers()) if(preserveEngineCreature(c)) pull.add(c);
          for(Card c:pull) combat.removeFromCombat(c);
          if(auditEnabled && !pull.isEmpty()) System.out.println("FARMER_ENGINE_PRESERVE_ATTACK held_back="+cardNames(new CardCollection(pull)));
        }

        // Mandatory Frostfang/Toski combat-damage draws can deck us. Cap potential connections conservatively.
        int per=combatDrawPerConnection();
        if(per>0 && !immediateLethal){
          int lib=libraryCardsRemaining();
          int reserve=librarySafetyReserve(); // v18.6: account for ALL other active draw engines, not just combat draw
          int maxConnections=Math.max(0,(lib-reserve)/per);
          if(combat.getAttackers().size()>maxConnections){
            List<Card> removable=new ArrayList<>();
            for(Card c:combat.getAttackers()){
              if("Toski, Bearer of Secrets".equals(c.getName())) continue; // must attack if able
              removable.add(c);
            }
            // Remove lowest-impact attackers first until even an all-unblocked attack cannot overdraw.
            removable.sort(Comparator.comparingInt(c -> Math.max(0,c.getNetPower()) + impactScore(c.getName())/100));
            int need=combat.getAttackers().size()-maxConnections;
            int removed=0;
            for(Card c:removable){ if(removed>=need) break; combat.removeFromCombat(c); removed++; }
            if(auditEnabled) System.out.println("FARMER_COMBAT_DRAW_SAFETY library="+lib+" draw_per_connection="+per+" max_attackers="+maxConnections+" removed="+removed+" remaining_attackers="+combat.getAttackers().size());
          }
        }
      }catch(Exception e){ if(auditEnabled) System.out.println("FARMER_COMBAT_SAFETY_ERROR "+e.getClass().getSimpleName()); }
    }

    private int drawEnginePriority(String n, boolean urgent, int tok, boolean prod){
      int hand=me.getCardsIn(ZoneType.Hand).size(), bodies=usefulCreatureCount();
      int pwr=maxCreaturePower();
      // v6.2 burst draw: immediate refill rather than conditional recurring engines.
      // Prefer 5+ cards, but allow emergency use with a low hand at 3-4 power.
      if("Return of the Wildspeaker".equals(n)){
        if(pwr>=8) return 3600 + Math.min(500,pwr*12);
        if(pwr>=5) return hand<=5 ? 3375 : 2925;
        if(pwr>=3 && hand<=2) return 2725;
        return urgent && pwr>=3 ? 2300 : 900;
      }
      if("Rishkar's Expertise".equals(n)){
        if(pwr>=8) return 3725 + Math.min(550,pwr*12);
        if(pwr>=5) return hand<=5 ? 3475 : 3025;
        if(pwr>=3 && hand<=2) return 2825;
        return urgent && pwr>=3 ? 2350 : 850;
      }
      if("Sylvan Library".equals(n)) return urgent?2050:(me.getLife()>=25?3375:3050);
      if("Skullclamp".equals(n)) return urgent?1500:(tok>0?3000:(prod?2575:2150));
      if("Tribute to the World Tree".equals(n)){
        // Every creature entry is valuable: P>=3 draws, smaller creatures receive two +1/+1 counters.
        // Sequence Tribute BEFORE creature/token production whenever practical.
        int future=nontokenCreaturesInHand() + (tokenProducerInHand()?2:0) + (prod?1:0);
        return urgent?1750:(future>=3?3225:(future>=1?2925:2450));
      }
      if("Guardian Project".equals(n)){
        // Project only pays on nontoken creature entries, so value the actual creature density in hand.
        int future=nontokenCreaturesInHand();
        return urgent?1550:(future>=3?2925:(future>=2?2675:(future>=1?2275:1750)));
      }
      if("Caretaker's Talent".equals(n)) return urgent?1600:((tok>0||prod||tokenProducerInHand())?2850:2350);
      if("Welcoming Vampire".equals(n)) return urgent?1600:((prod||tokenProducerInHand())?2675:2250);
      if("Bennie Bracks, Zoologist".equals(n)) return urgent?1600:((prod||tok>0)?2550:2150);
      if("Toski, Bearer of Secrets".equals(n)) return urgent?1700:(bodies>=3?2825:(bodies>=2?2525:2050));
      if("Ohran Frostfang".equals(n)) return urgent?1850:(bodies>=3?2925:(bodies>=2?2525:1950));
      if("Bugenhagen, Wise Elder".equals(n)){
        // Ramp is the floor; conditional upkeep draw is upside once a 7-power creature exists.
        if(maxCreaturePower()>=7) return hand<=4?2775:2450;
        return rampPriority(n,urgent);
      }
      if("Elder Gargaroth".equals(n)) return urgent?2400:(hand<=4?2650:2350);
      return urgent?1550:(hand<=4?2325:(tok>=2?2050:1850));
    }

    private int rampPriority(String n, boolean urgent){
      int lands=battlefieldLandCount(), dev=approximateManaDevelopment();
      if(("Wild Growth".equals(n)||"Utopia Sprawl".equals(n)) && !auraRampTargetExists(n)) return 0;
      boolean oneMana = "Birds of Paradise".equals(n)||"Llanowar Elves".equals(n)||"Avacyn's Pilgrim".equals(n)||"Delighted Halfling".equals(n)||"Noble Hierarch".equals(n)||"Sol Ring".equals(n)||"Wild Growth".equals(n)||"Utopia Sprawl".equals(n);
      boolean landRamp = "Nature's Lore".equals(n)||"Three Visits".equals(n);
      if(manaStarved()){
        if(oneMana) return urgent?2600:3175;
        if(landRamp) return urgent?2525:3025;
        return urgent?2450:2850;
      }
      if(manaHungry()){
        if(oneMana) return urgent?1950:2525;
        if(landRamp) return urgent?1900:2425;
        return urgent?1750:2250;
      }
      if(dev<=7 && (oneMana||landRamp||"Arcane Signet".equals(n)||"Talisman of Unity".equals(n))) return 1500;
      return 800;
    }
    private void markTutorUse(SpellAbility sa,String source){
      if(sa==null||sa.getHostCard()==null||!TUTORS.contains(sa.getHostCard().getName())||!sa.isSpell()) return;
      String n=sa.getHostCard().getName();
      TUTOR_CASTS.incrementAndGet();
      if("Worldly Tutor".equals(n)) WORLDLY_TUTOR_CASTS.incrementAndGet();
      if("Enlightened Tutor".equals(n)) ENLIGHTENED_TUTOR_CASTS.incrementAndGet();
      if(auditEnabled) System.out.println("FARMER_TUTOR_CAST tutor="+n+" source="+source+" lands="+battlefieldLandCount()+" mana_dev="+approximateManaDevelopment()+" tokens="+countThopters()+" food="+countFoodTokens()+" hand="+me.getCardsIn(ZoneType.Hand).size());
    }

    private int farmerCreatureCount(){
      int n=0; try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature()) n++; }catch(Exception ignored){} return n;
    }
    private int opposingCreatureArtifactCount(){
      int n=0; try{ for(Player p:me.getOpponents()) for(Card c:p.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature() || c.getType().isArtifact()) n++; }catch(Exception ignored){} return n;
    }
    private int planeswalkerPriority(SpellAbility a){
      if(a==null||a.getHostCard()==null) return 0;
      String n=a.getHostCard().getName(); String d=String.valueOf(a); int creatures=farmerCreatureCount();
      if("Nissa, Voice of Zendikar".equals(n)){
        if(d.contains("draw") && d.contains("land")) return battlefieldLandCount()>=6 ? 3000 : 1500;
        if(d.contains("+1/+1 counter") || d.contains("each creature")) return creatures>=3 ? 2925 + Math.min(250,creatures*25) : 1650;
        if(d.contains("Plant") || d.contains("0/1")) return creatures<=2 ? 2850 : 2350;
        return 2200;
      }
      if("Ajani, Strength of the Pride".equals(n)){
        if(d.contains("exile") && d.contains("artifact") && d.contains("creature")) return (me.getLife()>=55 && opposingCreatureArtifactCount()>=5) ? 3450 : 250;
        if(d.contains("Cat") || d.contains("Pridemate") || d.contains("2/2")) return creatures<=3 ? 2825 : 2450;
        if(d.contains("gain") && d.contains("life")) return lifegainPayoffOnline()?2700:2200;
        return 2200;
      }
      if("Elspeth, Storm Slayer".equals(n)){
        // Static token doubling makes Elspeth a premium token engine even before loyalty activation.
        String dl=d.toLowerCase(Locale.ROOT);
        if(dl.contains("destroy") && dl.contains("creature")) return survivalMode()?3450:(opposingCreatureArtifactCount()>=3?3000:2050);
        if(dl.contains("flying") || (dl.contains("+1/+1 counter") && dl.contains("each creature"))) return creatures>=4?3375:(creatures>=2?2925:1800);
        if(dl.contains("soldier") || dl.contains("1/1")) return creatures<=2?3025:2725;
        return 2850;
      }
      return 0;
    }

    private boolean opponentControls(String name){
      try{ for(Player p:me.getOpponents()) for(Card c:p.getCardsIn(ZoneType.Battlefield)) if(name.equals(c.getName())) return true; }catch(Exception ignored){}
      return false;
    }

    // Rite of Harmony is a burst-draw setup spell.  Evaluate what can ACTUALLY enter
    // after reserving/paying GW, rather than treating it as a generic two-mana draw card.
    private int riteExpectedTriggers(){
      int mana=Math.max(0,ComputerUtilMana.getAvailableManaEstimate(me,true)-2); // reserve GW for Rite
      int triggers=0;
      try{
        for(Card c:me.getCardsIn(ZoneType.Hand)){
          String n=c.getName(); if("Rite of Harmony".equals(n)) continue;
          if("Secure the Wastes".equals(n)){ triggers=Math.max(triggers,Math.max(0,mana-1)); continue; }
          if("Finale of Glory".equals(n)||"White Sun's Twilight".equals(n)){ triggers=Math.max(triggers,Math.max(0,mana-2)); continue; }
          if("March of the Multitudes".equals(n)){ triggers=Math.max(triggers,Math.max(0,mana-3)); continue; } // conservative: no invented convoke
          if("Avenger of Zendikar".equals(n) && mana>=7){ triggers=Math.max(triggers,1+Math.max(0,battlefieldLandCount())); continue; }
          try{
            boolean permanent=c.getType().isCreature() || c.getType().isEnchantment();
            int cmc=(int)c.getManaCost().getCMC();
            if(permanent && cmc<=mana) triggers=Math.max(triggers,1);
          }catch(Exception ignored){}
        }
        // Existing repeatable/token sources that can create entries this turn.
        if(hasNamed("Nissa, Voice of Zendikar",ZoneType.Battlefield) && farmerCreatureCount()<=2) triggers=Math.max(triggers,1);
        if(hasNamed("Felidar Retreat",ZoneType.Battlefield) && !ComputerUtilAbility.getAvailableLandsToPlay(me.getGame(),me).isEmpty()) triggers=Math.max(triggers,1);
      }catch(Exception ignored){}
      return triggers;
    }

    private int actionPriority(SpellAbility a){
      if(a==null||a.getHostCard()==null)return 0;
      String n=a.getHostCard().getName(); int turn=me.getGame().getPhaseHandler().getTurn(); int tok=countThopters(); boolean prod=producerOnline();
      boolean urgent=survivalMode(); boolean threatenedStack=stackThreatensUs();
      if(raphDeck()) return raphActionScore(a,!me.getGame().getStack().isEmpty());
      if(faldornDeck()){
        // A legal cast from exile is both the card itself and a Faldorn Wolf trigger; use it
        // before ordinary hand development so impulse cards do not expire.
        try{ if(faldornBattlefield() && a.isSpell() && a.getHostCard().isInZone(ZoneType.Exile)) return 5000; }catch(Exception ignored){}
        if(PROTECTION.contains(n)){ int pp=protectionPriority(n); if(pp>0)return pp; if(!INTERACTION.contains(n))return 0; }
        if("Faldorn, Dread Wolf Herald".equals(n)){ if(a.isSpell()) return FALDORN_EXILE_ENGINES.stream().anyMatch(x->hasNamed(x,ZoneType.Hand))?4100:3650; if(a.isActivatedAbility()) return (neutralFaldornDiscardValueInHand()||neutralFaldornSafeDiscardAvailable())?4300:-1800; }
        if(FALDORN_DOUBLERS.contains(n)&&a.isSpell()) return faldornBattlefield()?3475:2925;
        if("Formidable Speaker".equals(n)&&a.isSpell()) return pendingTutorTarget.equals(n)?3950:3225;
        if("Imperial Recruiter".equals(n)&&a.isSpell()) return 3200;
        if(("Worldly Tutor".equals(n)||"Green Sun's Zenith".equals(n)||"Chord of Calling".equals(n))&&a.isSpell()) return 3375;
        if("Gamble".equals(n)&&a.isSpell()) return 3200;
        if(FALDORN_EXILE_ENGINES.contains(n)){ if(a.isSpell()) return faldornBattlefield()?3900:(("Laelia, the Blade Reforged".equals(n)||"Professional Face-Breaker".equals(n)||"Valakut Exploration".equals(n))?2200:900); if(a.isActivatedAbility()) return faldornBattlefield()?3750:900; }
        if(FALDORN_PACK_PAYOFFS.contains(n)&&a.isSpell()) return countThopters()>=3?3425:(countThopters()>=1?2900:2050);
        if(("Toski, Bearer of Secrets".equals(n)||"Ohran Frostfang".equals(n)||"Sylvan Library".equals(n)||"Rishkar's Expertise".equals(n)||"Return of the Wildspeaker".equals(n))&&a.isSpell()) return me.getCardsIn(ZoneType.Hand).size()<=4?3150:2700;
        if(RAMP.contains(n)&&a.isSpell()) return rampPriority(n,urgent);
        if(INTERACTION.contains(n)&&a.isSpell()) return urgent?3400:1750;
      }
      if(mowuDeck()){
        if(PROTECTION.contains(n)){ int pp=protectionPriority(n); if(pp>0) return pp; if(!INTERACTION.contains(n)) return 0; }
        if(MOWU_FIGHT.contains(n)) return mowuFightOpportunity(n);
        if("Mowu, Loyal Companion".equals(n) && a.isSpell()){ int dev=approximateManaDevelopment(),casts=mowuCommanderCastCount(); if(casts>=2 && mowuSecondaryThreatInHand() && !mowuProtectionInHand()) return 1850; return dev>=4?3300:(dev>=3?2925:2450); }
        if("Hardened Scales".equals(n)&&a.isSpell()) return 3225;
        if("Branching Evolution".equals(n)&&a.isSpell()) return 3150;
        if("Ozolith, the Shattered Spire".equals(n)&&a.isSpell()) return 3075;
        if("Innkeeper's Talent".equals(n)&&a.isSpell()) return 3000;
        if(MOWU_COUNTER_STARTERS.contains(n)&&a.isSpell()) return mowuBattlefield()!=null?2875:2450;
        if("Kalonian Hydra".equals(n)&&a.isSpell()) return 3200;
        if("Hydra's Growth".equals(n)&&a.isSpell()) return mowuBattlefield()!=null?3250:1900;
        if("Alpha Authority".equals(n)&&a.isSpell()) return mowuBattlefield()!=null?2725:1500;
        if("Swiftfoot Boots".equals(n)&&a.isSpell()) return mowuBattlefield()!=null?2850:2300;
        if("Swiftfoot Boots".equals(n)&&a.isActivatedAbility()) return mowuBattlefield()!=null?3375:0;
        if("Saryth, the Viper's Fang".equals(n)&&a.isSpell()) return mowuBattlefield()!=null?3050:2425;
        if("Wondrous Crucible".equals(n)&&a.isSpell()){
          if(mowuBattlefield()!=null){ if(auditEnabled)System.out.println("MOWU_CRUCIBLE_PRIORITY commander_out=true mana="+ComputerUtilMana.getAvailableManaEstimate(me,true)); return urgent?2100:3675; }
          return 1450;
        }
        if("Sylvan Library".equals(n)&&a.isSpell()) return me.getLife()>=25?3475:3150;
        if("Forgotten Ancient".equals(n)&&a.isSpell()) return mowuBattlefield()!=null?3025:2700;
        if("Ivy Lane Denizen".equals(n)&&a.isSpell()) return nontokenCreaturesInHand()>=1?2925:2425;
        if("Tale of Katara and Toph".equals(n)&&a.isSpell()) return mowuBattlefield()!=null?3225:2875;
        if("Ouroboroid".equals(n)&&a.isSpell()) return mowuBattlefield()!=null?3425:2975;
        if("Rogue's Passage".equals(n)&&a.isActivatedAbility()){Card m=mowuBattlefield();if(m==null)return 0;int p=Math.max(0,m.getNetPower());return p>=10?3400:(p>=7?2775:0);}
        if("Karn's Bastion".equals(n)&&a.isActivatedAbility()) return mowuBattlefield()!=null?2550:1500;
        if("Tyrite Sanctum".equals(n)&&a.isActivatedAbility()) return mowuBattlefield()!=null?2500:1400;
        if("War Room".equals(n)&&a.isActivatedAbility()) return me.getCardsIn(ZoneType.Hand).size()<=3?2650:0;
        if("Bonders' Enclave".equals(n)&&a.isActivatedAbility()) return (me.getCardsIn(ZoneType.Hand).size()<=3&&maxCreaturePower()>=4)?2700:0;
        if("Castle Garenbrig".equals(n)&&a.isActivatedAbility()) return 0; // mana ability is handled by Forge; never treat its six green as unrestricted mana
        if("Fog".equals(n)&&a.isSpell()){ try{ String ph=String.valueOf(me.getGame().getPhaseHandler().getPhase()).toLowerCase(Locale.ROOT); boolean combat=ph.contains("combat")||ph.contains("attack")||ph.contains("block"); return (urgent||combat)?3900:0; }catch(Exception e){ return urgent?3900:0; } }
        if(("Warg Tactics".equals(n)||"Origin of Metalbending".equals(n))&&a.isSpell()) return urgent?3250:0;
        if("Saryth, the Viper's Fang".equals(n)&&a.isActivatedAbility()){
          Card m=mowuBattlefield(); return (m!=null && m.isTapped())?3200:900;
        }
        if(TUTORS.contains(n)&&a.isSpell()) return urgent?3250:(me.getCardsIn(ZoneType.Hand).size()<=3?2950:2625);
      }
      // v18.6 global library safety: when optional draw triggers can deck us, stop adding more draw
      // infrastructure and strongly suppress nonessential entry engines. Existing engines are handled
      // by combat/close-now safety below.
      if(libraryDangerMode() && DRAW_ENGINES.contains(n) && a.isSpell()){
        if(auditEnabled) System.out.println("FARMER_LIBRARY_DRAW_HOLD card="+n+" library="+libraryCardsRemaining()+" reserve="+librarySafetyReserve());
        return 0;
      }
      int pwScore=planeswalkerPriority(a); if(pwScore>0) return pwScore;
      if(a.isSpell() && !pendingTutorTarget.isEmpty() && pendingTutorTarget.equals(n)){
        // We intentionally spent a tutor to obtain this card.  When it reaches hand and is legal/payable,
        // follow through instead of immediately wandering to a different shiny object.
        return pendingTutorUrgent ? 3900 : 3150;
      }
      if(TUTORS.contains(n) && a.isSpell()){
        if(opponentControls("Perplexing Chimera") && !urgent) return 0;
        if(tok<=5 && !urgent && !hasHighValueTutorTargetSoon(n)){if(auditEnabled)System.out.println("FARMER_TUTOR_HOLD tutor="+n+" tokens="+tok+" reason=no_high_value_target_castable_soon mana_cap="+tutorSoonManaCap(n));return 0;}
        return urgent ? 3050 : (me.getCardsIn(ZoneType.Hand).size()>=7 ? 2600 : 2250);
      }
      if("Food Token".equals(n) && a.isActivatedAbility()){
        if(!lifegainPayoffOnline()) return 0;
        // Food is fuel: turn it into life/counter triggers when that creates real value.
        return urgent ? 2550 : (countFoodTokens()>=2 ? 1950 : 1750);
      }
      if(PROTECTION.contains(n)) return threatenedStack ? 3400 : (urgent && "Galadriel's Dismissal".equals(n)?2100:0);
      if(INTERACTION.contains(n)){
        if("Path to Exile".equals(n)||"Swords to Plowshares".equals(n)) return urgent?3300:1650;
        if("Dromoka's Command".equals(n)||"Inscription of Abundance".equals(n)) return urgent?2850:1500;
        return urgent?2750:1450;
      }
      if("Farmer Cotton".equals(n) && a.isSpell()) {
        int x=0; try{ if(a.getXManaCostPaid()!=null) x=a.getXManaCostPaid(); }catch(Exception ignored){}
        if(x<=0) return 0; // Never intentionally cast Farmer for X=0.
        int synergy=0;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)) {
          String cn=c.getName();
          if(COUNTER_ENGINES.contains(cn) || FOOD_ENGINES.contains(cn) || DRAW_ENGINES.contains(cn) || "Champion of Lambholt".equals(cn)) synergy++;
        }
        // X>=2 is a premium engine/rebuild play. X=1 is acceptable when it immediately feeds an engine,
        // but should not jump ahead of early ramp on an otherwise empty board.
        if(x>=3) return 2850 + Math.min(300,x*35);
        if(x==2) return 2550 + Math.min(200,synergy*40);
        return synergy>0 ? 2225 + Math.min(160,synergy*40) : (turn>=6 ? 2050 : 1725);
      }
      if("Rhys the Redeemed".equals(n)){
        if(a.isSpell()) return turn<=4 ? 2475 : 2075;
        if(a.isActivatedAbility()){
          String d=String.valueOf(a);
          if(d.contains("copy") || d.contains("For each creature token")) return rhysDoublePriority(tok);
          // v18: on a body-starved board Rhys is a REAL recovery engine, not decorative fallback.
          // Immediate multi-body producers may still beat this score, but generic setup/amplifiers should not.
          if(d.contains("1/1") || d.contains("Elf Warrior")) return tok==0 ? 3150 : (tok<=2 ? 3000 : (tok<=5 ? 2725 : 1250));
        }
      }
      if("Adeline, Resplendent Cathar".equals(n) && a.isSpell()) return farmerCreatureCount()>=2 ? 2925 : 2525;
      if("Hop to It".equals(n) && a.isSpell()) return tok<=2 ? 3025 : (tok<6 ? 2575 : 1850);
      if("Felidar Retreat".equals(n) && a.isSpell()){boolean landReady=false;try{landReady=!ComputerUtilAbility.getAvailableLandsToPlay(me.getGame(),me).isEmpty();}catch(Exception ignored){}if(tok<=5&&landReady)return 3425;return tok<=5?3050:2400;}
      if("Tendershoot Dryad".equals(n) && a.isSpell()) return tok<=2 ? 3275 : (tok<=5 ? 3125 : 2675);
      if("Emmara, Soul of the Accord".equals(n) && a.isSpell()) return tok<=2 ? 2575 : 2225;
      if("Queen Allenal of Ruadach".equals(n) && a.isSpell()) return producerOnline() ? 2775 : 2425;
      if("Esika's Chariot".equals(n) && a.isSpell()) return tok<=2 ? 2875 : 2475;
      if("Oketra's Monument".equals(n) && a.isSpell()) return tok<=2 ? 2675 : 2325;
      if("Torens, Fist of the Angels".equals(n) && a.isSpell()) return tok<=2 ? 2825 : 2475;
      if("Finneas, Ace Archer".equals(n) && a.isSpell()) return tok>=2 ? 2475 : 2175;
      if("Hamza, Guardian of Arashin".equals(n) && a.isSpell()){
        // Hamza becomes premium once the deck has begun building a counter/token board; Forge handles the actual reduced mana cost.
        int cc=creaturesWithP1P1Counters(); return cc>=3 ? 2775 : (cc>=1 ? 2325 : (turn>=7 ? 1750 : 1250));
      }
      if("Bugenhagen, Wise Elder".equals(n) && a.isSpell()) return drawEnginePriority(n,urgent,tok,prod);
      if("Halo Fountain".equals(n)){
        if(a.isSpell()) return tok>=2 ? 2225 : 1925;
        if(a.isActivatedAbility()){
          String d=String.valueOf(a).toLowerCase(Locale.ROOT); int tapped=tappedCreatureCount();
          if(d.contains("win the game")) return tapped>=15 ? 5000 : 0;
          if(d.contains("draw a card")) return tapped>=2 ? (me.getCardsIn(ZoneType.Hand).size()<=4 ? 2825 : 2325) : 0;
          if(d.contains("citizen") || d.contains("1/1")) return tapped>=1 ? (tok<3 ? 2225 : 1725) : 0;
        }
      }
      if("Animation Module".equals(n)){
        if(a.isSpell()) return (tok==0 ? 2475 : 2225);
        if(a.isActivatedAbility()){
          String d=String.valueOf(a).toLowerCase(Locale.ROOT);
          if(d.contains("counter")) return (creaturesWithP1P1Counters()>0 || tok>0) ? 2025 : 1300;
        }
      }
      if("Avenger of Zendikar".equals(n) && a.isSpell()) return battlefieldLandCount()>=7 ? 3050 : 2700;
      if("Primal Vigor".equals(n) && a.isSpell()){
        int engines=0; for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(TOKEN_ENGINES.contains(c.getName())||COUNTER_ENGINES.contains(c.getName())) engines++;
        // LOCKED high-priority bridge: doubles BOTH tokens and +1/+1 counters. Sequence before producers when possible.
        if(tokenProducerInHand() || tok>0 || prod || engines>=1) return 3375;
        return 2925;
      }
      if("Elspeth, Storm Slayer".equals(n) && a.isSpell()){
        // Static token doubler + token maker + team counters/evasion + removal.
        return (tokenProducerInHand()||tok>0||prod) ? 3325 : 2975;
      }
      if("Incubation Druid".equals(n) && a.isActivatedAbility()){
        String d=String.valueOf(a).toLowerCase(Locale.ROOT);
        if(d.contains("adapt") || d.contains("+1/+1")) return creaturesWithP1P1Counters()==0 ? (turn>=5?2150:1650) : 0;
      }
      if("Caretaker's Talent".equals(n) && a.isActivatedAbility()){
        String d=String.valueOf(a).toLowerCase(Locale.ROOT);
        if(d.contains("level 2") || d.contains("level2")) return tok>0 ? 2675 : 0;
        if(d.contains("level 3") || d.contains("level3")) return tok>=3 ? 2875 : (tok>=1?2150:0);
      }
      if("Innkeeper's Talent".equals(n) && a.isActivatedAbility()){
        String d=String.valueOf(a).toLowerCase(Locale.ROOT);
        if(d.contains("level 2") || d.contains("level2")) return creaturesWithP1P1Counters()>0 ? 2350 : 1900;
        if(d.contains("level 3") || d.contains("level3")) return (creaturesWithP1P1Counters()>0 || COUNTER_ENGINES.stream().anyMatch(x->hasNamed(x,ZoneType.Battlefield))) ? 3025 : 2150;
      }
      if("Colossal Majesty".equals(n) && a.isSpell()) return maxCreaturePower()>=4 ? (me.getCardsIn(ZoneType.Hand).size()<=4?2525:2200) : 1350;
      if("Sundering Growth".equals(n) && a.isSpell()) return urgent ? 2825 : (tok>0 ? 1900 : 1500);
      if("Aura Mutation".equals(n) && a.isSpell()) return urgent ? 2925 : 1800;
      if(riteCommitActive() && a.isSpell() && !"Rite of Harmony".equals(n)){
        int e=riteFollowupEntries(a); if(e>0) return 3650 + Math.min(500,e*35);
        // Once Rite is committed, optional non-entry development waits until the planned burst is executed.
        if(!INTERACTION.contains(n) && !PROTECTION.contains(n)) return 700;
      }
      if(RAMP.contains(n) && a.isSpell()) return rampPriority(n,urgent);
      if("Rite of Harmony".equals(n) && a.isSpell()){
        int exp=riteExpectedTriggers();
        if(exp>=3) return 3300 + Math.min(400,exp*30);
        if(exp>=2) return 2750;
        // A one-card Rite is only acceptable under real pressure; zero is never intentional.
        return (urgent && exp==1) ? 1900 : 0;
      }
      if("Aura Shards".equals(n) && a.isSpell()) return urgent ? 2450 : 2050;
      if("Cathars' Crusade".equals(n) && a.isSpell()){
        // Core conversion engine: deploy BEFORE creature/token production when follow-up entries exist.
        int future=nontokenCreaturesInHand() + (tokenProducerInHand()?3:0) + (prod?2:0);
        int bodies=usefulCreatureCount();
        if(future>=4 || (bodies>=3 && future>=2)) return 3425;
        if(future>=2 || bodies>=3) return 3075;
        if(future>=1) return 2575;
        return 2050;
      }
      if(TOKEN_ENGINES.contains(n)) return urgent ? 1500 : (prod ? 1700 : 1875);
      if(FOOD_ENGINES.contains(n)) return urgent ? 1500 : 1700;
      if(DRAW_ENGINES.contains(n)) return drawEnginePriority(n,urgent,tok,prod);
      if(COUNTER_ENGINES.contains(n)) return urgent ? 1500 : ((tok>=2||prod)?1825:1400);
      if(PAYOFFS.contains(n)) return urgent ? 1450 : (tok>=3?1850:1450);
      if("Mikaeus, the Lunarch".equals(n) && !a.isSpell()) { int bodies=usefulCreatureCount(); return urgent?1300:(bodies>=3?2225:(bodies>=1?1800:900)); }
      if("Gavony Township".equals(n) && !a.isSpell()) { int bodies=usefulCreatureCount(); return urgent?1300:(bodies>=3?2300:(bodies>=1?1750:0)); }
      if("The Shire".equals(n) && !a.isSpell()) return urgent?900:1500;
      return 0;
    }

    private boolean forceStrategicAbility(Card c){
      if(c==null)return false; String n=c.getName();
      return (raphDeck() && ("Raph & Mikey, Troublemakers".equals(n)||RAPH_CARD_PLAN.containsKey(n))) || (faldornDeck() && ("Faldorn, Dread Wolf Herald".equals(n)||FALDORN_EXILE_ENGINES.contains(n)||FALDORN_DOUBLERS.contains(n)||FALDORN_PACK_PAYOFFS.contains(n)||"Formidable Speaker".equals(n)||"Gamble".equals(n))) || TOKEN_ENGINES.contains(n) || COUNTER_ENGINES.contains(n) || FOOD_ENGINES.contains(n) || DRAW_ENGINES.contains(n) || PAYOFFS.contains(n) || RAMP.contains(n) || INTERACTION.contains(n) || PROTECTION.contains(n) || TUTORS.contains(n) || MOWU_COUNTER_STARTERS.contains(n) || MOWU_TARGETED_SUPPORT.contains(n) || "Mowu, Loyal Companion".equals(n) || "Kalonian Hydra".equals(n) || "Hydra's Growth".equals(n) || "Hardened Scales".equals(n) || "Branching Evolution".equals(n) || "Swiftfoot Boots".equals(n) || "Rogue's Passage".equals(n) || "Karn's Bastion".equals(n) || "Tyrite Sanctum".equals(n) || "War Room".equals(n) || "Bonders' Enclave".equals(n) || "Rhys the Redeemed".equals(n) || "Finneas, Ace Archer".equals(n) || "Aura Shards".equals(n) || "Food Token".equals(n) || "Gavony Township".equals(n) || "The Shire".equals(n);
    }

    private boolean protectedStrategicCard(Card c){
      if(c==null)return false; String n=c.getName();
      return (raphDeck() && raphPremiumProtectedCard(n)) || (faldornDeck() && ("Faldorn, Dread Wolf Herald".equals(n)||FALDORN_DOUBLERS.contains(n)||FALDORN_EXILE_ENGINES.contains(n)||"Formidable Speaker".equals(n)||"Shared Animosity".equals(n))) || TOKEN_ENGINES.contains(n)||COUNTER_ENGINES.contains(n)||DRAW_ENGINES.contains(n)||FOOD_ENGINES.contains(n)||PAYOFFS.contains(n)||TUTORS.contains(n)||"Aura Shards".equals(n)||"Elder Gargaroth".equals(n)||"Restoration Magic".equals(n);
    }

    private CardCollection protectDiscards(CardCollection chosen, CardCollectionView available, int amount){
      if(chosen==null) return null;
      List<Card> replacements=new ArrayList<>();
      for(Card c:available) if(!chosen.contains(c) && !protectedStrategicCard(c)) replacements.add(c);
      replacements.sort((a,b)->{
        int aa = MANA_DORKS.contains(a.getName())?0:(isLand(a)?50:impactScore(a.getName()));
        int bb = MANA_DORKS.contains(b.getName())?0:(isLand(b)?50:impactScore(b.getName()));
        return Integer.compare(aa,bb);
      });
      CardCollection out=new CardCollection();
      for(Card c:chosen){
        if(protectedStrategicCard(c) && !replacements.isEmpty()){
          Card r=replacements.remove(0); out.add(r);
          if(auditEnabled) System.out.println("FARMER_STRATEGY_OVERRIDE discard_protect="+c.getName()+" replacement="+r.getName());
        } else out.add(c);
      }
      while(out.size()>amount) out.remove(out.size()-1);
      return out;
    }

    private int cardCmc(Card c){try{return (int)c.getManaCost().getCMC();}catch(Exception e){return 99;}}
    private int tutorSoonManaCap(String host){return Math.max(1,Math.max(conservativeUntappedManaCapacity(),approximateManaDevelopment())+1);}
    private int nearTermColorSources(boolean green){
      int n=0; boolean handLand=false;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c.getType().isLand()){ if(green?openingLandMakesGreen(c):openingLandMakesWhite(c)) n++; continue; }
          String x=c.getName();
          if("Arcane Signet".equals(x)||"Talisman of Unity".equals(x)) n++;
          else if(c.getType().isCreature() && RAMP.contains(x)){ boolean sick=false; try{sick=c.isSick();}catch(Exception ignored){} if(!sick) n++; }
        }
        for(Card c:me.getCardsIn(ZoneType.Hand)) if(c.getType().isLand() && (green?openingLandMakesGreen(c):openingLandMakesWhite(c))){handLand=true;break;}
      }catch(Exception ignored){}
      return n+(handLand?1:0);
    }
    private int manaSymbolDemand(Card c,char symbol){
      try{ String m=c.getManaCost().toString(); int n=0; for(int i=0;i<m.length();i++) if(m.charAt(i)==symbol)n++; return n; }catch(Exception e){return 0;}
    }
    private boolean tutorTargetDeployableSoon(Card c,int cap){
      if(c==null||cardCmc(c)>cap)return false;
      int g=manaSymbolDemand(c,'G'),w=manaSymbolDemand(c,'W');
      boolean ok=g<=nearTermColorSources(true) && w<=nearTermColorSources(false);
      if(!ok&&auditEnabled)System.out.println("FARMER_TUTOR_COLOR_STRAIN target="+c.getName()+" green_need="+g+" green_sources="+nearTermColorSources(true)+" white_need="+w+" white_sources="+nearTermColorSources(false)+" cmc="+cardCmc(c)+" cap="+cap);
      return ok;
    }
    private boolean targetTypeMatchesTutor(String host,Card c){try{if("Worldly Tutor".equals(host)||"Chord of Calling".equals(host)||"Green Sun's Zenith".equals(host)||"Eladamri's Call".equals(host))return c.getType().isCreature();if("Gamble".equals(host))return true;if("Idyllic Tutor".equals(host))return c.getType().isEnchantment();if("Enlightened Tutor".equals(host))return c.getType().isEnchantment()||c.getType().isArtifact();}catch(Exception ignored){}return false;}
    private boolean highValueTutorName(String n){return DIRECT_BODY_RECOVERY.contains(n)||DRAW_ENGINES.contains(n)||COUNTER_ENGINES.contains(n)||PAYOFFS.contains(n)||MOWU_COUNTER_STARTERS.contains(n)||"Kalonian Hydra".equals(n)||"Saryth, the Viper's Fang".equals(n)||"Foundation Breaker".equals(n)||"Aura Shards".equals(n)||"Champion of Lambholt".equals(n);}
    private boolean hasHighValueTutorTargetSoon(String host){int cap=tutorSoonManaCap(host),tok=countThopters();try{for(Card c:me.getCardsIn(ZoneType.Library)){String n=c.getName();if(!targetTypeMatchesTutor(host,c)||!highValueTutorName(n))continue;if(tok<=5&&!DIRECT_BODY_RECOVERY.contains(n)&&!"Felidar Retreat".equals(n)&&!"Esika's Chariot".equals(n))continue;if(tutorTargetDeployableSoon(c,cap))return true;}}catch(Exception ignored){}return false;}
    private Card firstTutorTargetByOrder(CardCollection options,String host,String[] order){if(order==null)return null;int cap=tutorSoonManaCap(host);for(String n:order){Card c=findNamed(options,n);if(c!=null&&tutorTargetDeployableSoon(c,cap))return c;}for(String n:order){Card c=findNamed(options,n);if(c!=null){if(auditEnabled)System.out.println("FARMER_TUTOR_CASTABILITY_STRAIN tutor="+host+" target="+n+" cmc="+cardCmc(c)+" soon_mana_cap="+cap+" action=HOLD_NOT_FORCE");}}return null;}

    private static final Set<String> MOWU_COUNTER_STARTER_TUTOR_TARGETS = new LinkedHashSet<>(Arrays.asList(
      "Bristly Bill, Spine Sower","Rishkar, Peema Renegade","Verdurous Gearhulk","Oran-Rief Ooze",
      "Ouroboroid","Duskshell Crawler","Ironshell Beetle","Biophagus","Michelangelo, Weirdness to 11","Defiler of Vigor"
    ));
    private static final Set<String> MOWU_COUNTER_AMPLIFIER_TUTOR_TARGETS = new LinkedHashSet<>(Arrays.asList(
      "Kalonian Hydra"
    ));
    private int mowuBoardCounterTotal(){
      int total=0; try{for(Card c:me.getCardsIn(ZoneType.Battlefield))if(c.getType().isCreature())total+=cardP1Counters(c);}catch(Exception ignored){} return total;
    }
    private int mowuOwnCounterTotal(){ Card m=mowuBattlefield(); return m==null?0:cardP1Counters(m); }
    private boolean mowuStarterOnline(){
      try{for(Card c:me.getCardsIn(ZoneType.Battlefield)){String n=c.getName();
        if("Bristly Bill, Spine Sower".equals(n)||"Retreat to Kazandu".equals(n)||"Innkeeper's Talent".equals(n)||
           "Origin of Metalbending".equals(n)||"Ozolith, the Shattered Spire".equals(n)||"Rishkar, Peema Renegade".equals(n)||
           "Oran-Rief Ooze".equals(n)||"Ouroboroid".equals(n)||"Tale of Katara and Toph".equals(n)||"Defiler of Vigor".equals(n)||"Managorger Hydra".equals(n)) return true;
      }}catch(Exception ignored){} return false;
    }
    private boolean mowuCounterDrought(){ return mowuBoardCounterTotal()<=1 && !mowuStarterOnline(); }
    private String mowuTutorTargetRole(String n){
      if(n==null)return "unknown";
      if(MOWU_COUNTER_STARTER_TUTOR_TARGETS.contains(n))return "counter_starter";
      if(MOWU_COUNTER_AMPLIFIER_TUTOR_TARGETS.contains(n))return "counter_amplifier";
      if(DRAW_ENGINES.contains(n)||"Toski, Bearer of Secrets".equals(n)||"Augur of Autumn".equals(n)||"Beast Whisperer".equals(n))return "draw_value";
      if(PROTECTION.contains(n)||"Saryth, the Viper's Fang".equals(n))return "protection";
      if(INTERACTION.contains(n)||"Foundation Breaker".equals(n))return "interaction";
      return "payoff_other";
    }
    private boolean mowuCastableStarterAvailable(CardCollection options,String host){
      int cap=tutorSoonManaCap(host);
      try{for(Card c:options)if(MOWU_COUNTER_STARTER_TUTOR_TARGETS.contains(c.getName())&&tutorTargetDeployableSoon(c,cap))return true;}catch(Exception ignored){}
      return false;
    }
    private boolean isGeneralCreatureTutor(String host){ return "Worldly Tutor".equals(host); }
    private Card raphOffensiveTutorChoice(CardCollection options,String host){
      if(options==null||options.isEmpty())return null;
      String[] order=raphOffensiveTutorStage<=0 ? new String[]{"Ancient Copper Dragon","Old Gnawbone","Goldspan Dragon"} : new String[]{"Hellkite Tyrant"};
      for(String want:order) for(Card c:options){
        if(!want.equals(c.getName()))continue;
        if(("Worldly Tutor".equals(host)||"Sylvan Tutor".equals(host))&&!c.getType().isCreature())continue;
        int stage=raphOffensiveTutorStage+1;
        if(auditEnabled)System.out.println("RAPH_TUTOR_STAGE stage="+stage+" tutor="+host+" target="+want);
        raphOffensiveTutorStage=Math.min(2,stage);
        if(auditEnabled)System.out.println("RAPH_TUTOR_STAGE_COMMIT stage="+raphOffensiveTutorStage+" tutor="+host+" target="+want);
        return c;
      }
      // If stage two target is unavailable, do not regress to mana fixing; take best premium Raph hit.
      if(raphOffensiveTutorStage>=1){Card h=raphBestTopHit(options);if(h!=null)return h;}
      return null;
    }
    private Card preferredTutorCard(CardCollection options, SpellAbility sa){
      if(options==null||options.isEmpty()) return null;
      String host=(sa==null||sa.getHostCard()==null)?"?":sa.getHostCard().getName();
      int tok=countThopters(); boolean urgent=survivalMode(); boolean prod=producerOnline();
      String[] order=null;
      boolean starved=manaStarved();
      // v18.3 HARD RULE: premium tutors are never mana-fixing tools. If mana is bad,
      // that is a mulligan/deck-development problem, not a reason to burn a premium tutor.
      boolean emergencyMana=false;
      if(raphDeck()){
        boolean attackNow=raphBattlefield()&&raphReadyToAttack();
        Card pick=null;
        if("Worldly Tutor".equals(host)||"Sylvan Tutor".equals(host)){
          pick=raphOffensiveTutorChoice(options,host);
        } else if("Pia, Aether Ascetic".equals(host)){
          pick=raphPiaTutorChoice(options);
        } else if("Formidable Speaker".equals(host)){
          pick=raphSpeakerTutorChoice(options);
        } else if("Gamble".equals(host)){
          pick=raphOffensiveTutorChoice(options,host);
        }
        if(pick!=null){
          if("Pia, Aether Ascetic".equals(host) && sa!=null && sa.getHostCard()!=null) piaNativeTargetById.put(sa.getHostCard().getId(),pick.getName());
          if("Formidable Speaker".equals(host) && sa!=null && sa.getHostCard()!=null){ speakerNativeTargetById.put(sa.getHostCard().getId(),pick.getName()); speakerEtbProcessed.add(sa.getHostCard().getId()); if(auditEnabled)System.out.println("RAPH_SPEAKER_NATIVE_PATH target="+pick.getName()+" fallback=LOCKED_OUT"); }
          lastRaphTutorTarget=pick.getName();pendingTutorTarget=pick.getName();pendingTutorSource=host;pendingTutorUrgent=attackNow;
          if(auditEnabled)System.out.println("RAPH_TUTOR_CHOICE tutor="+host+" target="+pick.getName()+" attack_now="+attackNow+" raph_attacks_this_turn="+raphAttacksThisTurn+" top_hit_score="+raphTopHitScoreName(pick.getName()));return pick;}
      }
      if((faldornDeck()||raphDeck()) && ("Nature's Lore".equals(host)||"Three Visits".equals(host))){
        Card stomp=findNamed(options,"Stomping Ground");
        int red=nearTermColorSources(false), redNeed=whiteDemand(me.getCardsIn(ZoneType.Hand));
        if(stomp!=null && (red==0||redNeed>=2)){if(auditEnabled)System.out.println("FALDORN_RAMP_FETCH spell="+host+" target=Stomping Ground red_sources="+red+" red_demand="+redNeed);return stomp;}
        Card forest=findNamed(options,"Forest"); if(forest!=null){if(auditEnabled)System.out.println("FALDORN_RAMP_FETCH spell="+host+" target=Forest");return forest;}
        if(stomp!=null)return stomp;
      }
      if(faldornDeck() && ("Worldly Tutor".equals(host)||"Chord of Calling".equals(host)||"Green Sun's Zenith".equals(host))){
        Card speaker=findNamed(options,"Formidable Speaker");
        if(speaker!=null){ pendingTutorTarget="Formidable Speaker"; pendingTutorUrgent=true; pendingTutorSource=host; if(auditEnabled)System.out.println("FALDORN_TUTOR_CHAIN tutor="+host+" target=Formidable Speaker stage=1"); return speaker; }
        order=new String[]{"Professional Face-Breaker","Laelia, the Blade Reforged","Ohran Frostfang","Toski, Bearer of Secrets","Nightpack Ambusher","Immerwolf","Imperial Recruiter","Anger","Brawn"};
      } else if(faldornDeck() && "Formidable Speaker".equals(host)){
        int hand=me.getCardsIn(ZoneType.Hand).size();
        if(hand<=3) order=new String[]{"Ohran Frostfang","Toski, Bearer of Secrets","Professional Face-Breaker","Laelia, the Blade Reforged","Nightpack Ambusher","Immerwolf","Anger","Brawn"};
        else if(countThopters()>=3) order=new String[]{"Immerwolf","Nightpack Ambusher","Ohran Frostfang","Professional Face-Breaker","Laelia, the Blade Reforged","Toski, Bearer of Secrets","Anger","Brawn"};
        else order=new String[]{"Professional Face-Breaker","Laelia, the Blade Reforged","Ohran Frostfang","Toski, Bearer of Secrets","Nightpack Ambusher","Immerwolf","Anger","Brawn"};
      } else if(faldornDeck() && "Imperial Recruiter".equals(host)){
        order=new String[]{"Formidable Speaker","Professional Face-Breaker","Spider-Ham, Peter Porker","Immerwolf","Birds of Paradise","Delighted Halfling","Arbor Elf","Llanowar Elves","Elvish Mystic","Fyndhorn Elves"};
      } else if(faldornDeck() && "Gamble".equals(host)){
        order=new String[]{"Parallel Lives","Doubling Season","Primal Vigor"};
      } else if(mowuDeck() && ("Worldly Tutor".equals(host)||"Chord of Calling".equals(host)||"Green Sun's Zenith".equals(host)||"Eladamri's Call".equals(host))){
        int hand=me.getCardsIn(ZoneType.Hand).size(); Card m=mowuBattlefield();
        boolean drought=mowuCounterDrought();
        if(urgent) order=new String[]{"Saryth, the Viper's Fang","Foundation Breaker","Bristly Bill, Spine Sower","Rishkar, Peema Renegade","Verdurous Gearhulk","Defiler of Vigor","Kalonian Hydra","Toski, Bearer of Secrets","Managorger Hydra"};
        else if(drought) order=new String[]{"Bristly Bill, Spine Sower","Rishkar, Peema Renegade","Verdurous Gearhulk","Oran-Rief Ooze","Ouroboroid","Forgotten Ancient","Ivy Lane Denizen","Biophagus","Michelangelo, Weirdness to 11","Defiler of Vigor","Toski, Bearer of Secrets","Beast Whisperer","Augur of Autumn","Kalonian Hydra","Managorger Hydra","Saryth, the Viper's Fang"};
        else if(hand<=3) order=new String[]{"Guardian Project","Ohran Frostfang","Toski, Bearer of Secrets","Beast Whisperer","Augur of Autumn","Bristly Bill, Spine Sower","Rishkar, Peema Renegade","Kalonian Hydra","Saryth, the Viper's Fang"};
        else if(m!=null) order=new String[]{"Bristly Bill, Spine Sower","Rishkar, Peema Renegade","Kalonian Hydra","Verdurous Gearhulk","Saryth, the Viper's Fang","Toski, Bearer of Secrets","Defiler of Vigor","Managorger Hydra"};
        else order=new String[]{"Bristly Bill, Spine Sower","Rishkar, Peema Renegade","Verdurous Gearhulk","Defiler of Vigor","Kalonian Hydra","Managorger Hydra","Toski, Bearer of Secrets","Saryth, the Viper's Fang"};
      } else if("Worldly Tutor".equals(host) || "Eladamri's Call".equals(host)){
        if(tok<=5 && me.getLife()>8) order=new String[]{"Tendershoot Dryad","Adeline, Resplendent Cathar","Torens, Fist of the Angels","Avenger of Zendikar","Queen Allenal of Ruadach","Emmara, Soul of the Accord","Scurry Oak","Herd Baloth","Rosie Cotton of South Lane","Elder Gargaroth","Ohran Frostfang","Toski, Bearer of Secrets","Champion of Lambholt"};
        else if(urgent) order=new String[]{"Tendershoot Dryad","Ohran Frostfang","Elder Gargaroth","Adeline, Resplendent Cathar","Torens, Fist of the Angels","Scurry Oak","Herd Baloth","Champion of Lambholt"};
        else if(tok>=4) order=new String[]{"Champion of Lambholt","Ohran Frostfang","Toski, Bearer of Secrets","Elder Gargaroth","Rosie Cotton of South Lane","Herd Baloth","Scurry Oak","Bugenhagen, Wise Elder"};
        else order=new String[]{"Ohran Frostfang","Toski, Bearer of Secrets","Elder Gargaroth","Champion of Lambholt","Mycoloth","Rosie Cotton of South Lane","Herd Baloth","Scurry Oak","Hamza, Guardian of Arashin","Bugenhagen, Wise Elder"};
      }
      if("Enlightened Tutor".equals(host)){
        // v18.3 HARD RULE: never spend Enlightened Tutor on Sol Ring/Signet/Talisman merely to fix mana.
        if(tok<=5 && me.getLife()>8) order=new String[]{"Esika's Chariot","Felidar Retreat","Caretaker's Talent","Skullclamp","Tribute to the World Tree","Primal Vigor","Ozolith, the Shattered Spire","Innkeeper's Talent","Hardened Scales","Cathars' Crusade","Branching Evolution","Aura Shards"};
        else if(urgent) order=new String[]{"Aura Shards","Esika's Chariot","Felidar Retreat","Caretaker's Talent","Skullclamp","Tribute to the World Tree","Cathars' Crusade","Ozolith, the Shattered Spire"};
        else if(tok>=4) order=new String[]{"Primal Vigor","Cathars' Crusade","Branching Evolution","Ozolith, the Shattered Spire","Hardened Scales","Tribute to the World Tree","Innkeeper's Talent","Skullclamp","Aura Shards","Caretaker's Talent"};
        else order=new String[]{"Esika's Chariot","Felidar Retreat","Caretaker's Talent","Skullclamp","Tribute to the World Tree","Primal Vigor","Ozolith, the Shattered Spire","Innkeeper's Talent","Hardened Scales","Cathars' Crusade","Branching Evolution","Aura Shards"};
      }
      if("Idyllic Tutor".equals(host)){
        // Idyllic can only find enchantments: prioritize card flow or a board-conversion engine appropriate to the current state.
        int hand=me.getCardsIn(ZoneType.Hand).size();
        if(tok<=5 && me.getLife()>8) order=new String[]{"Felidar Retreat","Caretaker's Talent","Tribute to the World Tree","Guardian Project","Paradox Zone","Innkeeper's Talent","Hardened Scales","Primal Vigor","Branching Evolution"};
        else if(urgent) order=new String[]{"Felidar Retreat","Aura Shards","Caretaker's Talent","Tribute to the World Tree","Guardian Project","Cathars' Crusade","Innkeeper's Talent","Branching Evolution","Paradox Zone","Hardened Scales"};
        else if(tok>=6) order=new String[]{"Primal Vigor","Cathars' Crusade","Branching Evolution","Tribute to the World Tree","Innkeeper's Talent","Aura Shards","Guardian Project","Caretaker's Talent","Felidar Retreat","Paradox Zone","Hardened Scales"};
        else order=new String[]{"Primal Vigor","Tribute to the World Tree","Guardian Project","Caretaker's Talent","Innkeeper's Talent","Cathars' Crusade","Branching Evolution","Felidar Retreat","Aura Shards","Hardened Scales"};
      }
      if(order!=null){Card c=null;if((mowuDeck()||faldornDeck()) && ("Chord of Calling".equals(host)||"Green Sun's Zenith".equals(host))){for(String want:order){c=findNamed(options,want);if(c!=null)break;}}else c=firstTutorTargetByOrder(options,host,order);if(c!=null){String n=c.getName();pendingTutorTarget=n;pendingTutorUrgent=emergencyMana||urgent;pendingTutorSource=host;if(auditEnabled){boolean drought=mowuDeck()&&mowuCounterDrought();boolean starterAvail=drought&&mowuCastableStarterAvailable(options,host);String role=mowuDeck()?mowuTutorTargetRole(n):"legacy";System.out.println("FARMER_TUTOR_CHOICE tutor="+host+" target="+n+" target_role="+role+" target_cmc="+cardCmc(c)+" soon_mana_cap="+tutorSoonManaCap(host)+" urgent="+urgent+" body_starved="+(tok<=5)+" mana_starved="+starved+" mana_dev="+approximateManaDevelopment()+" tokens="+tok+" producer="+prod+" board_p1_counters="+(mowuDeck()?mowuBoardCounterTotal():-1)+" mowu_p1_counters="+(mowuDeck()?mowuOwnCounterTotal():-1)+" counter_drought="+drought+" starter_online="+(mowuDeck()&&mowuStarterOnline())+" castable_starter_available="+starterAvail+" recovery_options=["+tokenRecoveryOptions()+"]");if(drought&&starterAvail&&"counter_amplifier".equals(role))System.out.println("MOWU_TUTOR_MISPRIORITY tutor="+host+" target="+n+" reason=amplifier_selected_during_counter_drought_with_castable_starter_available");}return c;}}
      return null;
    }

    private boolean handRepairMove(ZoneType destination, List<ZoneType> origin, SpellAbility sa){
      if(origin==null||!origin.contains(ZoneType.Hand))return false;
      String host=(sa==null||sa.getHostCard()==null)?"?":sa.getHostCard().getName();
      if(destination==ZoneType.Library && HAND_REPAIR.contains(host))return true;
      return destination==ZoneType.Exile && "Scroll Rack".equals(host);
    }
    private List<Card> chooseRepairCards(CardCollection options,int min,int max){
      List<Card> bombs=new ArrayList<>(), rest=new ArrayList<>();
      for(Card c:options){ if(BIG_HITS.contains(c.getName()))bombs.add(c); else rest.add(c); }
      bombs.sort((a,b)->Integer.compare(impactScore(b.getName()),impactScore(a.getName())));
      rest.sort((a,b)->Integer.compare(impactScore(a.getName()),impactScore(b.getName())));
      List<Card> out=new ArrayList<>();
      int desired=Math.max(min,Math.min(max,Math.max(1,bombs.size())));
      for(Card c:bombs){if(out.size()>=desired)break;out.add(c);}
      for(Card c:rest){if(out.size()>=desired)break;out.add(c);}
      return out;
    }

    private CardCollection chooseBottoms(CardCollectionView cards,int amount){
      CardCollection bottoms=new CardCollection(); if(amount<=0)return bottoms;
      List<Card> pool=new ArrayList<>();for(Card c:cards)pool.add(c);
      if(raphDeck()){
        while(bottoms.size()<amount&&!pool.isEmpty()){
          Card worst=null; int ws=Integer.MAX_VALUE; int lands=0; for(Card x:pool)if(x.isLand())lands++;
          for(Card c:pool){int v=raphOpeningKeepValue(c); if(c.isLand()){if(lands<=2)v+=7000;else if(lands>4)v-=2200;} if(v<ws){ws=v;worst=c;}}
          if(worst==null)break; bottoms.add(worst); pool.remove(worst);
        }
        if(auditEnabled)System.out.println("RAPH_MULLIGAN_BOTTOM cards=["+cardNames(bottoms)+"]");
        return bottoms;
      }
      if(faldornDeck()){
        while(bottoms.size()<amount && !pool.isEmpty()){
          Card worst=null; int ws=Integer.MAX_VALUE; int lands=0; for(Card x:pool)if(isLand(x))lands++;
          for(Card c:pool){
            int v=faldornKeepValue(c);
            if(isLand(c)){ if(lands<=2)v+=6000; else if(lands>4)v-=1600; }
            if(v<ws){ws=v;worst=c;}
          }
          if(worst==null)break; bottoms.add(worst); pool.remove(worst);
        }
        if(auditEnabled)System.out.println("FALDORN_MULLIGAN_BOTTOM cards=["+cardNames(bottoms)+"]");
        return bottoms;
      }
      // Keep 2-4 lands where possible, then keep cheap ramp/energy setup, then interaction.
      Set<String> keepers=new HashSet<>(Arrays.asList(
        "Birds of Paradise","Llanowar Elves","Avacyn's Pilgrim","Delighted Halfling","Noble Hierarch","Bloom Tender","Incubation Druid","Bugenhagen, Wise Elder",
        "Talisman of Unity","Sol Ring","Arcane Signet","Wild Growth","Utopia Sprawl","Nature's Lore","Three Visits",
        "Rosie Cotton of South Lane","Good-Fortune Unicorn","Hardened Scales","Branching Evolution","Cathars' Crusade",
        "Felidar Retreat","Scute Swarm","Primal Vigor","Tribute to the World Tree","Caretaker's Talent","Bennie Bracks, Zoologist","Ohran Frostfang","Guardian Project","Welcoming Vampire","Toski, Bearer of Secrets","Skullclamp","Elspeth, Storm Slayer"
      ));
      while(bottoms.size()<amount && !pool.isEmpty()){
        Card worst=null;int worstScore=Integer.MAX_VALUE;int lands=landCount(new CardCollection(pool));
        for(Card c:pool){
          int s=0;
          if(isLand(c)) s=(lands<=2?10000:(lands>4?0:55));
          else {
            String n=c.getName();
            s=keepers.contains(n)?80:40;
            try { s-=Math.max(0,c.getManaCost().getCMC())*3; } catch(Exception ignored){}
            if(BIG_HITS.contains(n))s-=30;
          }
          if(s<worstScore){worstScore=s;worst=c;}
        }
        if(worst==null)break;bottoms.add(worst);pool.remove(worst);
      }
      return bottoms;
    }

    private boolean openingLandMakesGreen(Card c){
      if(c==null || !isLand(c)) return false;
      String n=c.getName();
      if(faldornDeck()||raphDeck()) return "Forest".equals(n)||"Stomping Ground".equals(n)||"Karplusan Forest".equals(n)||"Copperline Gorge".equals(n)||"Spire Garden".equals(n)||"Command Tower".equals(n)||"City of Brass".equals(n)||"Evolving Wilds".equals(n)||"Terramorphic Expanse".equals(n);
      return "Forest".equals(n)||"Brushland".equals(n)||"Razorverge Thicket".equals(n)||"Bountiful Promenade".equals(n)||"Command Tower".equals(n)||"City of Brass".equals(n)||"The Shire".equals(n);
    }
    private boolean openingLandMakesWhite(Card c){
      if(c==null || !isLand(c)) return false;
      String n=c.getName();
      if(faldornDeck()||raphDeck()) return "Mountain".equals(n)||"Stomping Ground".equals(n)||"Karplusan Forest".equals(n)||"Copperline Gorge".equals(n)||"Spire Garden".equals(n)||"Command Tower".equals(n)||"City of Brass".equals(n)||"Evolving Wilds".equals(n)||"Terramorphic Expanse".equals(n);
      return "Plains".equals(n)||"Brushland".equals(n)||"Razorverge Thicket".equals(n)||"Bountiful Promenade".equals(n)||"Command Tower".equals(n)||"City of Brass".equals(n);
    }
    private boolean genericTwoManaColorFixInHand(CardCollectionView hand,int lands){
      if(lands<2) return false;
      for(Card c:hand){
        String n=c.getName();
        if("Talisman of Unity".equals(n)||"Talisman of Impulse".equals(n)||"Selesnya Signet".equals(n)||"Gruul Signet".equals(n)||"Arcane Signet".equals(n)||"Fellwar Stone".equals(n)) return true;
      }
      return false;
    }
    private int greenDemand(CardCollectionView hand){
      int n=0;
      for(Card c:hand){
        if(isLand(c)) continue;
        try{ if(c.getManaCost().toString().contains("G")) n++; }catch(Exception ignored){}
      }
      return n;
    }
    private int whiteDemand(CardCollectionView hand){
      int n=0;
      for(Card c:hand){
        if(isLand(c)) continue;
        try{ if(c.getManaCost().toString().contains((faldornDeck()||raphDeck())?"R":"W")) n++; }catch(Exception ignored){}
      }
      return n;
    }
    private int earlyGreenDemand(CardCollectionView hand){
      int n=0;
      for(Card c:hand){
        if(isLand(c)) continue;
        try{ if(c.getManaCost().toString().contains("G") && c.getManaCost().getCMC()<=3) n++; }catch(Exception ignored){}
      }
      return n;
    }
    private int earlyWhiteDemand(CardCollectionView hand){
      int n=0;
      for(Card c:hand){
        if(isLand(c)) continue;
        try{ if(c.getManaCost().toString().contains((faldornDeck()||raphDeck())?"R":"W") && c.getManaCost().getCMC()<=3) n++; }catch(Exception ignored){}
      }
      return n;
    }

    private boolean keepHand(int cardsToReturn){
      auditState("mulligan-decision");
      CardCollectionView hand=me.getCardsIn(ZoneType.Hand);int lands=landCount(hand);
      if(raphDeck()) return raphKeepHand(hand,cardsToReturn);
      // v18.3 HARD RULE: never keep one land while the eventual hand would still be 6+ cards.
      // The playgroup's one-land opener gets the extra free-mulligan credit. Only once the London
      // mulligan would leave five cards (cardsToReturn>=2) may a one-land seven be considered.
      if(lands==1 && cardsToReturn<2){extraOneLandFreeCredit=true;System.out.println((auditEnabled?"FARMER_MULLIGAN":"AI_MULLIGAN")+" REJECT one-land hand hard_rule=true return="+cardsToReturn);return false;}
      // Aggressive multiplayer mulligan: use the free first mulligan for a real plan, not merely a castable card.
      int early=0, ramp=0, oneManaRamp=0, engine=0, interaction=0;
      for(Card c:hand){
        if(isLand(c))continue;
        String n=c.getName();
        try { if(c.getManaCost().getCMC()<=3)early++; } catch(Exception ignored){}
        if(RAMP.contains(n)){ ramp++; try { if(c.getManaCost().getCMC()<=1) oneManaRamp++; } catch(Exception ignored){} }
        if(EARLY_ENGINES.contains(n)) engine++;
        if(faldornDeck() && ("Sylvan Library".equals(n)||"Valakut Exploration".equals(n)||"Laelia, the Blade Reforged".equals(n)||"Professional Face-Breaker".equals(n)||"Formidable Speaker".equals(n)||"Light Up the Stage".equals(n)||"Stromkirk Occultist".equals(n))) engine++;
        if(INTERACTION.contains(n)) interaction++;
      }
      boolean firstFree=(cardsToReturn==0);
      boolean actualPlan=(ramp>=1 || engine>=1);
      // v1.5: the first multiplayer mulligan is free, so "cheap interaction + lands" is not a Faldorn plan.
      // Demand development or engine access, and reject low-density hands that merely function.
      boolean strongFirstPlan=(oneManaRamp>=1 || engine>=1 || (ramp>=1 && early>=2));
      int greenSources=0,whiteSources=0;
      for(Card c:hand){ if(openingLandMakesGreen(c))greenSources++; if(openingLandMakesWhite(c))whiteSources++; }
      int gDemand=greenDemand(hand),wDemand=whiteDemand(hand);
      int earlyGDemand=earlyGreenDemand(hand),earlyWDemand=earlyWhiteDemand(hand);
      boolean artifactFix=genericTwoManaColorFixInHand(hand,lands);
      boolean hasWorldly=false, hasEnlightened=false;
      for(Card c:hand){ if("Worldly Tutor".equals(c.getName()))hasWorldly=true; if("Enlightened Tutor".equals(c.getName()))hasEnlightened=true; }
      // Do not keep a color-bad hand merely because a premium tutor could be burned on mana repair.
      // The deck is two colors; mulligan toward naturally functional mana and preserve tutors for impact.
      boolean tutorFixWhite = false;
      boolean tutorFixGreen = false;
      boolean colorFunctional=true;
      // A hand with zero natural access to a required color is not functional merely because it has 4+ lands.
      // Reject when that missing color strands any early (MV<=3) spell, or multiple spells overall,
      // unless a castable two-mana artifact actually fixes the missing color.
      boolean greenStranded = greenSources==0 && (earlyGDemand>=1 || gDemand>=2) && !artifactFix && !tutorFixGreen;
      boolean whiteStranded = whiteSources==0 && (earlyWDemand>=1 || wDemand>=2) && !artifactFix && !tutorFixWhite;
      if(greenStranded || whiteStranded) colorFunctional=false;
      boolean fragileTwoLand = firstFree && lands==2 && !artifactFix && oneManaRamp==0 && engine>=2;
      boolean keep=lands>=2 && lands<=4 && early>=1 && colorFunctional && (!firstFree || (actualPlan && strongFirstPlan)) && !fragileTwoLand;
      if(cardsToReturn>=2 && lands>=2 && lands<=5 && early>=1 && colorFunctional) keep=true;
      // User exception: at the five-card threshold, one land may be kept rather than mulliganing lower,
      // but tutors still do NOT count as mana repair. Require at least something castable/developing.
      if(cardsToReturn>=2 && lands==1 && early>=1 && colorFunctional) keep=true;
      // Even on deep mulligans, never override a known color-stranded hand. Relax plan quality, not castability.
      if(cardsToReturn>=4 && lands>=1 && lands<=5 && colorFunctional) keep=true;
      System.out.println((auditEnabled?"FARMER_MULLIGAN ":"AI_MULLIGAN ")+(keep?"KEEP":"REJECT")+" lands="+lands+" green_sources="+greenSources+" white_sources="+whiteSources+" green_demand="+gDemand+" white_demand="+wDemand+" early_green_demand="+earlyGDemand+" early_white_demand="+earlyWDemand+" green_stranded="+greenStranded+" white_stranded="+whiteStranded+" artifact_fix="+artifactFix+" tutor_fix_g="+tutorFixGreen+" tutor_fix_w="+tutorFixWhite+" color_ok="+colorFunctional+" early="+early+" ramp="+ramp+" one_mana_ramp="+oneManaRamp+" engine="+engine+" interaction="+interaction+" strong_first_plan="+strongFirstPlan+" free_first="+firstFree+" return="+cardsToReturn+" hand=["+cardNames(hand)+"]");
      return keep;
    }

    @Override public boolean mulliganKeepHand(Player ignored,int cardsToReturn){
      if(pendingKeepDecision!=null){boolean v=pendingKeepDecision;pendingKeepDecision=null;return v;}
      return keepHand(cardsToReturn);
    }
    @Override public CardCollectionView tuckCardsViaMulligan(CardCollectionView cards,int amount){
      int adjusted=amount;
      if(extraOneLandFreeCredit && amount>0){adjusted=amount-1;extraOneLandFreeCredit=false;System.out.println((auditEnabled?"FARMER_MULLIGAN":"AI_MULLIGAN")+" extra-one-land-free-credit bottom="+adjusted+" instead_of="+amount);}
      if(adjusted<=0)return new CardCollection();
      boolean keep=keepHand(adjusted);pendingKeepDecision=keep;
      if(!keep){CardCollection arbitrary=new CardCollection();int i=0;for(Card c:cards){if(i++>=adjusted)break;arbitrary.add(c);}return arbitrary;}
      CardCollection b=chooseBottoms(cards,adjusted);System.out.println((auditEnabled?"FARMER_MULLIGAN":"AI_MULLIGAN")+" BOTTOM: "+cardNames(b));auditState("post-bottom-selection");return b;
    }

    @Override public CardCollectionView chooseCardsToDiscardToMaximumHandSize(int numDiscard){
      CardCollectionView avail=me.getCardsIn(ZoneType.Hand);
      CardCollection base=new CardCollection(super.chooseCardsToDiscardToMaximumHandSize(numDiscard));
      return protectDiscards(base,avail,numDiscard);
    }
    @Override public CardCollection chooseCardsToDiscardFrom(Player p, SpellAbility sa, CardCollection valid, int min, int max, CardCollectionView chosen){
      if(p==me && raphDeck() && sa!=null && sa.getHostCard()!=null){
        String rh=sa.getHostCard().getName(); Card pick=null;
        if("Formidable Speaker".equals(rh)) pick=raphSpeakerDiscardChoice(valid);
        else if("Pia, Aether Ascetic".equals(rh)) pick=raphPiaDiscardChoice(valid);
        else if("Big Score".equals(rh)||"Unexpected Windfall".equals(rh)) pick=raphStrategicDiscardChoice(valid,rh);
        if(pick!=null){if("Formidable Speaker".equals(rh))speakerEtbProcessed.add(sa.getHostCard().getId());if("Pia, Aether Ascetic".equals(rh))piaEtbProcessed.add(sa.getHostCard().getId());CardCollection out=new CardCollection();out.add(pick);if(auditEnabled)System.out.println("RAPH_COST_DISCARD source="+rh+" card="+pick.getName());return out;}
      }
      if(p==me && faldornDeck() && sa!=null && sa.getHostCard()!=null && ("Faldorn, Dread Wolf Herald".equals(sa.getHostCard().getName())||"Formidable Speaker".equals(sa.getHostCard().getName()))){
        String[] pref={"Anger","Brawn","Arrogant Wurm","Fiery Temper","Avacyn's Judgment","Ancient Grudge","Blazing Rootwalla","Basking Rootwalla","Stromkirk Occultist"};
        for(String want:pref) for(Card c:valid) if(want.equals(c.getName())){ CardCollection out=new CardCollection(); out.add(c); if(auditEnabled)System.out.println("FALDORN_DISCARD_VALUE source="+sa.getHostCard().getName()+" card="+want); return out; }
        Card fallback=null; int fv=Integer.MAX_VALUE; int lands=0; for(Card c:valid)if(c.isLand())lands++;
        for(Card c:valid){ String n=c.getName(); int v=faldornKeepValue(c);
          if(FALDORN_DOUBLERS.contains(n)||"Shared Animosity".equals(n)||"Sylvan Library".equals(n)||"Worldly Tutor".equals(n)||"Green Sun's Zenith".equals(n)||"Chord of Calling".equals(n)||"Gamble".equals(n)||PROTECTION.contains(n)) continue;
          if(c.isLand() && lands<2 && battlefieldLandCount()<5) continue;
          if(v<fv){fv=v;fallback=c;}
        }
        if(fallback!=null){CardCollection out=new CardCollection();out.add(fallback);if(auditEnabled)System.out.println("FALDORN_DISCARD_SAFE_FALLBACK source="+sa.getHostCard().getName()+" card="+fallback.getName()+" keep_value="+fv);return out;}
      }
      CardCollection base=super.chooseCardsToDiscardFrom(p,sa,valid,min,max,chosen);
      if(p==me) return protectDiscards(base,valid,base==null?min:base.size());
      return base;
    }
    @Override public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection options, forge.game.player.DelayedReveal reveal, String title, boolean optional, Player decider){
      if(isKinnanActivationMove(destination,origin,sa,decider)){
        String top5=topFiveNow();
        Card chosen=super.chooseSingleCardForZoneChange(destination,origin,sa,options,reveal,title,optional,decider);
        logKinnanResolution(sa,options,chosen,top5);
        return chosen;
      }
      if(decider==me && handRepairMove(destination,origin,sa)){
        List<Card> r=chooseRepairCards(options,1,1);
        if(!r.isEmpty()){ if(auditEnabled) System.out.println("FARMER_TOPDECK_REPAIR single="+r.get(0).getName()+" via="+(sa==null||sa.getHostCard()==null?"?":sa.getHostCard().getName())); return r.get(0); }
      }
      if(decider==me && origin!=null && origin.contains(ZoneType.Library)){
        Card pref=preferredTutorCard(options,sa);
        if(pref!=null){ if(auditEnabled) System.out.println("FARMER_COMBO_OVERRIDE tutor_single="+pref.getName()+" via="+(sa==null||sa.getHostCard()==null?"?":sa.getHostCard().getName())); return pref; }
      }
      return super.chooseSingleCardForZoneChange(destination,origin,sa,options,reveal,title,optional,decider);
    }
    @Override public List<Card> chooseCardsForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection options, int min, int max, forge.game.player.DelayedReveal reveal, String title, Player decider){
      if(isKinnanActivationMove(destination,origin,sa,decider)){
        String top5=topFiveNow();
        List<Card> chosen=super.chooseCardsForZoneChange(destination,origin,sa,options,min,max,reveal,title,decider);
        Card pick=(chosen==null||chosen.isEmpty())?null:chosen.get(0);
        logKinnanResolution(sa,options,pick,top5);
        return chosen;
      }
      if(decider==me && handRepairMove(destination,origin,sa)){
        List<Card> r=chooseRepairCards(options,min,max);
        if(!r.isEmpty()){ if(auditEnabled) System.out.println("FARMER_TOPDECK_REPAIR multi="+cardNames(new CardCollection(r))+" via="+(sa==null||sa.getHostCard()==null?"?":sa.getHostCard().getName())); return r; }
      }
      if(decider==me && origin!=null && origin.contains(ZoneType.Library) && max>=1){
        Card pref=preferredTutorCard(options,sa);
        if(pref!=null){
          List<Card> out=new ArrayList<>(); out.add(pref);
          if(auditEnabled) System.out.println("FARMER_COMBO_OVERRIDE tutor_multi="+pref.getName()+" via="+(sa==null||sa.getHostCard()==null?"?":sa.getHostCard().getName()));
          if(min<=1) return out;
        }
      }
      return super.chooseCardsForZoneChange(destination,origin,sa,options,min,max,reveal,title,decider);
    }

    @Override public <T extends forge.game.GameEntity> T chooseSingleEntityForEffect(forge.util.collect.FCollectionView<T> optionList, forge.game.player.DelayedReveal reveal, SpellAbility sa, String title, boolean isOptional, Player targetedPlayer, Map<String,Object> params){
      try{
        if(optionList!=null && !optionList.isEmpty() && sa!=null && sa.getHostCard()!=null){
          String host=sa.getHostCard().getName();
        if(raphDeck() && "Seize the Day".equals(host)){
            for(T e:optionList)if(e instanceof Card&&"Raph & Mikey, Troublemakers".equals(((Card)e).getName())){if(auditEnabled)System.out.println("RAPH_EXTRA_COMBAT_TARGET host=Seize_the_Day target=Raph_&_Mikey");return e;}
          }
        if(raphDeck() && "Formidable Speaker".equals(host) && sa.isActivatedAbility()){
            String[] order={"Raph & Mikey, Troublemakers","Port Razer","Ancient Copper Dragon","Old Gnawbone","Balefire Dragon","Terror of the Peaks","Wulfgar of Icewind Dale"};
            for(String want:order)for(T e:optionList)if(e instanceof Card&&want.equals(((Card)e).getName())&&((Card)e).isTapped()){if(auditEnabled)System.out.println("RAPH_SPEAKER_UNTAP target="+want);return e;}
          }
          boolean removal=INTERACTION.contains(host) || "Aura Shards".equals(host);
          boolean protection=PROTECTION.contains(host);
          if(mowuDeck() && (MOWU_COUNTER_STARTERS.contains(host)||"Innkeeper's Talent".equals(host)||"Ozolith, the Shattered Spire".equals(host)||"Retreat to Kazandu".equals(host))){
            for(T e:optionList) if(e instanceof Card && "Mowu, Loyal Companion".equals(((Card)e).getName())){ if(auditEnabled)System.out.println("MOWU_COUNTER_TARGET host="+host+" target=Mowu, Loyal Companion"); return e; }
          }
          if(MOWU_FIGHT.contains(host)){
            Card src=bestMowuFightSource(sa); T own=null, opp=null; int os=Integer.MIN_VALUE;
            for(T e:optionList) if(e instanceof Card){Card c=(Card)e;if(c.getController()==me){if(src!=null&&c==src)own=e;}else if(c.getType().isCreature()){int sc=permanentThreatScore(c);if(src!=null&&src.getNetPower()>=c.getNetToughness())sc+=500;if("Tail Swipe".equals(host)&&src!=null&&src.getNetToughness()<=c.getNetPower())sc-=600;if(sc>os){os=sc;opp=e;}}}
            T pick=own!=null?own:opp; if(pick!=null){if(auditEnabled)System.out.println("MOWU_FIGHT_TARGET card="+host+" target="+pick);return pick;}
          }
          if(MOWU_TARGETED_SUPPORT.contains(host)||"Rogue's Passage".equals(host)||"Saryth, the Viper's Fang".equals(host)||"Forgotten Ancient".equals(host)||"Ivy Lane Denizen".equals(host)){
            for(T e:optionList) if(e instanceof Card && "Mowu, Loyal Companion".equals(((Card)e).getName())) return e;
            String[] alts={"Kalonian Hydra","Managorger Hydra","Defiler of Vigor","Forgotten Ancient"};
            for(String want:alts) for(T e:optionList) if(e instanceof Card && want.equals(((Card)e).getName())) return e;
          }
          if("Haliya, Ascendant Cadet".equals(host)){
            T best=null; int bs=Integer.MIN_VALUE;
            for(T e:optionList) if(e instanceof Card){ Card c=(Card)e; if(c.getController()!=me||!c.isCreature()) continue; int sc=100+impactScore(c.getName()); if("Champion of Lambholt".equals(c.getName())) sc+=500; if("Scurry Oak".equals(c.getName())||"Herd Baloth".equals(c.getName())) sc+=400; if(sc>bs){bs=sc;best=e;} }
            if(best!=null) return best;
          }
          if("Caretaker's Talent".equals(host)){
            T best=null; int bs=Integer.MIN_VALUE;
            for(T e:optionList) if(e instanceof Card){ Card c=(Card)e; if(c.getController()!=me||!c.isToken()) continue; int sc=100; try{sc+=Math.max(0,c.getNetPower())*10;}catch(Exception ignored){} if(sc>bs){bs=sc;best=e;} }
            if(best!=null) return best;
          }
          if("Animation Module".equals(host)){
            T best=null; int bs=Integer.MIN_VALUE;
            for(T e:optionList) if(e instanceof Card){ Card c=(Card)e; if(c.getController()!=me) continue; int sc=impactScore(c.getName()); try{ if(c.getPowerBonusFromCounters()>0) sc+=500; }catch(Exception ignored){} if("Champion of Lambholt".equals(c.getName())) sc+=250; if(sc>bs){bs=sc;best=e;} }
            if(best!=null && bs>=500){ if(auditEnabled) System.out.println("FARMER_ANIMATION_TARGET target="+best); return best; }
          }
          if(removal){
            T best=null; int bestScore=Integer.MIN_VALUE;
            for(T e:optionList){
              int sc=Integer.MIN_VALUE/4;
              if(e instanceof Card){
                Card c=(Card)e;
                if(c.getController()!=me){ sc=permanentThreatScore(c); if("Aura Mutation".equals(host)) try{sc+=Math.max(0,c.getCMC())*18;}catch(Exception ignored){} }
              } else if(e instanceof Player){
                Player p=(Player)e; if(p!=me) sc=playerThreatScore(p);
              }
              if(sc>bestScore){bestScore=sc;best=e;}
            }
            if(best!=null && bestScore>Integer.MIN_VALUE/8){
              if(auditEnabled) System.out.println("FARMER_TARGET threat host="+host+" target="+best+" score="+bestScore);
              return best;
            }
          }
          if(protection){
            // v6.1: when answering a hostile targeted effect, bind protection to the
            // strategic target identified from that exact stack object.  Do not let a
            // generic friendly-target scorer spend protection on a Soldier/token while
            // Mowu or a critical engine is the permanent actually under threat.
            Card threatened=topStackProtectedTarget();
            if(threatened!=null){
              for(T e:optionList) if(e instanceof Card && e==threatened){
                if(auditEnabled) System.out.println("FALDORN_PROTECTION_TARGET_LOCK host="+host+" target="+threatened.getName()+" reason=hostile_stack_target");
                return e;
              }
            }
            T best=null; int bestScore=Integer.MIN_VALUE;
            for(T e:optionList){
              if(!(e instanceof Card)) continue; Card c=(Card)e; if(c.getController()!=me) continue;
              int sc=impactScore(c.getName());
              if(faldornDeck()){
                String cn=c.getName();
                if("Faldorn, Dread Wolf Herald".equals(cn))sc+=12000;
                if(FALDORN_DOUBLERS.contains(cn)||"Shared Animosity".equals(cn)||"Formidable Speaker".equals(cn))sc+=5000;
                if("Toski, Bearer of Secrets".equals(cn)||"Ohran Frostfang".equals(cn)||"Professional Face-Breaker".equals(cn)||"Laelia, the Blade Reforged".equals(cn))sc+=3000;
              }
              if(raphDeck() && "Raph & Mikey, Troublemakers".equals(c.getName())) sc+=14000;
              if(raphDeck() && ("Wulfgar of Icewind Dale".equals(c.getName())||"Port Razer".equals(c.getName())||"Terror of the Peaks".equals(c.getName()))) sc+=5000;
              if("Mowu, Loyal Companion".equals(c.getName())) sc+=10000;
              try{ sc += Math.max(0,c.getNetPower())*2; }catch(Exception ignored){}
              if(sc>bestScore){bestScore=sc;best=e;}
            }
            if(best!=null){
              if(auditEnabled) System.out.println("FARMER_TARGET protect host="+host+" target="+best+" score="+bestScore);
              return best;
            }
          }
        }
      }catch(Exception e){ if(auditEnabled) System.out.println("FARMER_TARGET_FALLBACK error="+e.getClass().getSimpleName()); }
      return super.chooseSingleEntityForEffect(optionList,reveal,sa,title,isOptional,targetedPlayer,params);
    }

    @Override public SpellAbility getAbilityToPlay(Card card, List<SpellAbility> abilities, forge.util.ITriggerEvent triggerEvent){
      SpellAbility base=super.getAbilityToPlay(card,abilities,triggerEvent);
      if(!forceStrategicAbility(card)) return base;
      if(abilities!=null){
        SpellAbility best=null; int bestScore=-1;
        for(SpellAbility sa:abilities){
          try {
            if(sa!=null && sa.canPlay()){
              int sc=actionPriority(sa);
              if(sc>bestScore){bestScore=sc;best=sa;}
            }
          } catch(Exception ignored){}
        }
        if(best!=null && (base==null || bestScore>0)){
          if(auditEnabled) System.out.println("FARMER_STRATEGY_FORCE legal_action="+card.getName()+" score="+bestScore+" generic_ai="+(base==null?"filtered":"accepted"));
          return best;
        }
      }
      return base;
    }

    private Player chooseCombatFocusTarget(int boardPower){
      Player bestCmdKill=null,bestRawKill=null,best=null; int bestCmdKillScore=Integer.MIN_VALUE,bestRawKillScore=Integer.MIN_VALUE,bestScore=Integer.MIN_VALUE;
      Card m=mowuBattlefield(); int mp=m==null?0:Math.max(0,m.getNetPower()); StringBuilder audit=new StringBuilder();
      try{
        for(Player p:me.getOpponents()){
          if(p.hasLost()) continue; int th=playerThreatScore(p),cd=mowuCommanderDamageTo(p),remain=Math.max(0,21-cd); boolean cmdKill=m!=null&&mp>=remain; boolean rawKill=boardPower>=p.getLife();
          int strategic=th+cd*18-Math.max(0,remain-mp)*5;
          if(audit.length()>0)audit.append("|"); audit.append(p.getName()).append(":life=").append(p.getLife()).append(",threat=").append(th).append(",mowu_cd=").append(cd).append(",cd_remain=").append(remain).append(",cmd_kill=").append(cmdKill).append(",raw_kill=").append(rawKill);
          if(cmdKill&&strategic>bestCmdKillScore){bestCmdKillScore=strategic;bestCmdKill=p;}
          if(rawKill&&th>bestRawKillScore){bestRawKillScore=th;bestRawKill=p;}
          if(strategic>bestScore){bestScore=strategic;best=p;}
        }
      }catch(Exception ignored){}
      Player pick=bestCmdKill!=null?bestCmdKill:(bestRawKill!=null?bestRawKill:best);
      if(auditEnabled)System.out.println("MOWU_COMBAT_TARGET board_power="+boardPower+" mowu_power="+mp+" opponents=["+audit+"] chosen="+(pick==null?"NONE":pick.getName())+" reason="+(bestCmdKill!=null?"commander_lethal":(bestRawKill!=null?"raw_lethal":"commander_progress_plus_threat")));
      return pick;
    }

    @Override public void declareAttackers(Player attacker, forge.game.combat.Combat combat){
      if(attacker!=me){ super.declareAttackers(attacker,combat); return; }
      if(raphDeck()){
        super.declareAttackers(attacker,combat);
        try{
          int turn=me.getGame().getPhaseHandler().getTurn(); if(turn!=lastRaphAttackTurn)raphAttacksThisTurn=0;
          if(raphCriticalCombatDrawLock()){
            List<Card> emergencyPull=new ArrayList<>();
            for(Card c:combat.getAttackers())if(!"Toski, Bearer of Secrets".equals(c.getName()))emergencyPull.add(c);
            for(Card c:emergencyPull)combat.removeFromCombat(c);
            Player blocked=raphMostBlockedOpponent();
            if(blocked!=null)for(Card c:me.getCardsIn(ZoneType.Battlefield))if("Toski, Bearer of Secrets".equals(c.getName())&&forge.game.combat.CombatUtil.canAttack(c,blocked)){if(combat.getAttackers().contains(c))combat.removeFromCombat(c);combat.addAttacker(c,blocked);}
            if(auditEnabled)System.out.println("RAPH_SELF_DECK_EMERGENCY library="+libraryCardsRemaining()+" draw_per_connection="+combatDrawPerConnection()+" removed_optional_attackers="+emergencyPull.size()+" forced_toski_target="+(blocked==null?"NONE":blocked.getName()));
          }
          boolean combatCap=raphAttacksThisTurn>=6 || raphCriticalCombatDrawLock();
          if(combatCap){
            // End pathological combat chains. Keep only creatures that are forced to attack (Toski).
            List<Card> stop=new ArrayList<>();
            for(Card c:combat.getAttackers()) if(!"Toski, Bearer of Secrets".equals(c.getName())) stop.add(c);
            for(Card c:stop) combat.removeFromCombat(c);
            if(auditEnabled)System.out.println("RAPH_COMBAT_LOOP_LOCK turn="+turn+" prior_raph_attacks="+raphAttacksThisTurn+" removed_attackers="+stop.size());
          }
          Card r=raphBattlefieldCard();
          if(r!=null && !combatCap){
            boolean already=combat.getAttackers().contains(r);
            if(!already){Player t=raphBestAttackTarget(r,false); if(t!=null){combat.addAttacker(r,t);already=true;}}
            if(already){lastRaphAttackTurn=turn;raphAttacksThisTurn++;}
          } else if(r!=null && combat.getAttackers().contains(r)){ combat.removeFromCombat(r); }
          Card pr=null;for(Card c:me.getCardsIn(ZoneType.Battlefield))if("Port Razer".equals(c.getName())){pr=c;break;}
          if(pr!=null && forge.game.combat.CombatUtil.canAttack(pr) && !combatCap){
            Player easy=raphBestAttackTarget(pr,true);
            if(easy!=null){if(combat.getAttackers().contains(pr))combat.removeFromCombat(pr);combat.addAttacker(pr,easy);}
          } else if(pr!=null && combatCap && combat.getAttackers().contains(pr)){ combat.removeFromCombat(pr); }
          String[] connectPayoffs={"Ancient Copper Dragon","Old Gnawbone","Balefire Dragon","Giant Adephage","Ragavan, Nimble Pilferer","Professional Face-Breaker","Toski, Bearer of Secrets","Ohran Frostfang"};
          if(!combatCap) for(String want:connectPayoffs)for(Card c:me.getCardsIn(ZoneType.Battlefield))if(want.equals(c.getName())&&forge.game.combat.CombatUtil.canAttack(c)){
            Player easy=raphBestAttackTarget(c,true);if(easy!=null){if(combat.getAttackers().contains(c))combat.removeFromCombat(c);combat.addAttacker(c,easy);}
          }
          applyCombatSafety(combat,raphBestAttackTarget(r!=null?r:(pr!=null?pr:(combat.getAttackers().isEmpty()?null:combat.getAttackers().get(0))),true),false);
          if(auditEnabled)System.out.println("RAPH_COMBAT turn="+turn+" raph_attacks_this_turn="+raphAttacksThisTurn+" raph_in_combat="+(r!=null&&combat.getAttackers().contains(r))+" port_razer_in_combat="+(pr!=null&&combat.getAttackers().contains(pr))+" attackers="+combat.getAttackers().size()+" library="+libraryCardsRemaining());
        }catch(Exception e){if(auditEnabled)System.out.println("RAPH_COMBAT_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());}
        return;
      }
      if(dedicatedFaldornPilot()){ declareNeutralFaldornAttackers(attacker,combat); return; }
      try {
        int boardPower=0;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature() && c.isUntapped()) boardPower += Math.max(0,c.getNetPower());
        Player target=chooseCombatFocusTarget(boardPower);
        int targetLife=target==null?Integer.MAX_VALUE:target.getLife();
        Card mowu=mowuBattlefield(); int mowuCd=target==null?0:mowuCommanderDamageTo(target); boolean mowuCommanderLethal=target!=null&&mowu!=null&&mowuCd+Math.max(0,mowu.getNetPower())>=21;
        boolean closeMode = target!=null && (mowuCommanderLethal || boardPower >= targetLife || countThopters()>=5 || boardPower>=25);
        boolean preserveRhysMana=rhysReserveArmed;
        if(closeMode){
          java.util.LinkedHashMap<Card,forge.game.GameEntity> focus=new java.util.LinkedHashMap<>();
          int focusPower=0;
          for(Card c:me.getCardsIn(ZoneType.Battlefield)){
            if(c.getType().isCreature() && forge.game.combat.CombatUtil.canAttack(c,target)){
              if(preserveRhysMana && ("Rhys the Redeemed".equals(c.getName()) || RAMP.contains(c.getName()))) continue;
              focus.put(c,target); focusPower += Math.max(0,c.getNetPower());
            }
          }
          int violations=combat.getAttackConstraints().countViolations(focus);
          if(!focus.isEmpty() && focusPower>0 && violations==0){
            combat.clearAttackers();
            for(java.util.Map.Entry<Card,forge.game.GameEntity> e:focus.entrySet()) combat.addAttacker(e.getKey(),e.getValue());
            boolean immediateLethal = focusPower >= target.getLife() && me.getOpponents().stream().filter(p->!p.hasLost()).count()==1;
            applyCombatSafety(combat,target,immediateLethal);
            System.out.println("FARMER_FAST_CLOSE turn="+me.getGame().getPhaseHandler().getTurn()+" target="+target.getName()+" target_life="+target.getLife()+" attack_power="+sumPower(combat.getAttackers())+" attackers="+combat.getAttackers().size()+" violations=0");
            System.out.println("FARMER_COMBAT_PLAN turn="+me.getGame().getPhaseHandler().getTurn()+" attackers="+combat.getAttackers().size()+" attack_power="+sumPower(combat.getAttackers())+" board_power="+boardPower+" target_life="+targetLife+" close_mode=true");
            return;
          }
          if((countThopters()>=10 || boardPower>=25) && violations>0){
            try{
              org.apache.commons.lang3.tuple.Pair<java.util.Map<Card,forge.game.GameEntity>,Integer> legal=combat.getAttackConstraints().getLegalAttackers();
              if(legal!=null && legal.getLeft()!=null && !legal.getLeft().isEmpty() && legal.getRight()!=null && legal.getRight()==0){
                combat.clearAttackers();
                int legalPower=0;
                for(java.util.Map.Entry<Card,forge.game.GameEntity> e:legal.getLeft().entrySet()){ combat.addAttacker(e.getKey(),e.getValue()); legalPower+=Math.max(0,e.getKey().getNetPower()); }
                boolean immediateLethal = target!=null && legalPower>=target.getLife() && me.getOpponents().stream().filter(p->!p.hasLost()).count()==1;
                applyCombatSafety(combat,target,immediateLethal);
                System.out.println("FARMER_FAST_CONSTRAINT_CLOSE turn="+me.getGame().getPhaseHandler().getTurn()+" attackers="+combat.getAttackers().size()+" attack_power="+sumPower(combat.getAttackers())+" violations=0");
                System.out.println("FARMER_COMBAT_PLAN turn="+me.getGame().getPhaseHandler().getTurn()+" attackers="+combat.getAttackers().size()+" attack_power="+sumPower(combat.getAttackers())+" board_power="+boardPower+" target_life="+targetLife+" close_mode=true");
                return;
              }
            }catch(Exception constraintError){ System.out.println("FARMER_FAST_CONSTRAINT_ERROR "+constraintError.getClass().getSimpleName()); }
          }
          System.out.println("FARMER_FAST_CLOSE_FALLBACK turn="+me.getGame().getPhaseHandler().getTurn()+" target="+(target==null?"NONE":target.getName())+" focus_power="+focusPower+" violations="+violations);
        }
        super.declareAttackers(attacker, combat);
        if(mowuDeck() && target!=null){
          Card m=mowuBattlefield();
          try{ if(m!=null && combat.getAttackers().contains(m) && forge.game.combat.CombatUtil.canAttack(m,target)){ combat.removeFromCombat(m); combat.addAttacker(m,target); if(auditEnabled)System.out.println("MOWU_ATTACK_RETARGET target="+target.getName()+" prior_cd="+mowuCommanderDamageTo(target)+" power="+m.getNetPower()); } }catch(Exception ignored){}
        }
        if(preserveRhysMana){
          List<Card> pull=new ArrayList<>();
          for(Card c:combat.getAttackers()) if("Rhys the Redeemed".equals(c.getName()) || RAMP.contains(c.getName())) pull.add(c);
          for(Card c:pull) combat.removeFromCombat(c);
          if(auditEnabled && !pull.isEmpty()) System.out.println("FARMER_RHYS_RESERVE_COMBAT held_back="+cardNames(new CardCollection(pull))+" reserve=6");
        }
        boolean immediateLethal = target!=null && sumPower(combat.getAttackers())>=target.getLife() && me.getOpponents().stream().filter(p->!p.hasLost()).count()==1;
        applyCombatSafety(combat,target,immediateLethal);
        System.out.println("FARMER_COMBAT_PLAN turn="+me.getGame().getPhaseHandler().getTurn()+" attackers="+combat.getAttackers().size()+" attack_power="+sumPower(combat.getAttackers())+" board_power="+boardPower+" target_life="+(targetLife==Integer.MAX_VALUE?-1:targetLife)+" close_mode="+closeMode);
      } catch(Exception e){
        System.out.println("FARMER_COMBAT_PLAN_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());
        super.declareAttackers(attacker,combat);
      }
    }
    @Override public void declareBlockers(Player defender, forge.game.combat.Combat combat){
      if(defender!=me){ super.declareBlockers(defender,combat); return; }
      super.declareBlockers(defender,combat);
      // Combat audit 2026-10-08: retain Forge's legal blocker assignments.
      // The previous post-processing removed engine blockers based on gross attacker
      // power and could undo strategically necessary blocks. Never erase a legal
      // Forge block without a damage-aware, legality-aware replacement.
      // Always emit this bounded defensive record; auditEnabled is off in normal runs.
      int incoming=0, blocked=0, unblocked=0;
      for(Card a:combat.getAttackers()){
        try{
          incoming+=Math.max(0,a.getNetPower());
          boolean isBlocked=combat.isBlocked(a);
          if(isBlocked) blocked++; else unblocked++;
          System.out.println("MINSTREL_DEFENSE_ATTACKER name="+a.getName().replace(' ', '_')+" power="+a.getNetPower()+" toughness="+a.getNetToughness()+" blocked="+isBlocked+" commander="+a.isCommander());
        }catch(Exception ex){ System.out.println("MINSTREL_DEFENSE_TELEMETRY_ERROR type="+ex.getClass().getSimpleName()); }
      }
      if(!combat.getAttackers().isEmpty()) System.out.println("MINSTREL_DEFENSE_AUDIT life="+me.getLife()+" attackers="+combat.getAttackers().size()+" blocked="+blocked+" unblocked="+unblocked+" raw_power="+incoming+" policy=preserve_forge_blocks");
    }

    private int sumPower(CardCollectionView cards){ int n=0; for(Card c:cards)n+=Math.max(0,c.getNetPower()); return n; }

    private int minimumMeaningfulX(SpellAbility sa){
      if(sa==null || sa.getHostCard()==null) return 1;
      String n=sa.getHostCard().getName();
      if("Farmer Cotton".equals(n)) return 1; // Hard floor: commander is never cast for X=0.
      if("Secure the Wastes".equals(n)) return 2;
      if("March of the Multitudes".equals(n)) return 3;
      if("Finale of Glory".equals(n)) return 3;
      if("White Sun's Twilight".equals(n)) return 3;
      if("Mikaeus, the Lunarch".equals(n)) return 2;
      return 1;
    }

    private boolean configurePayableX(SpellAbility sa){
      try{
        if(sa==null || !sa.costHasManaX()) return true;
        int min=minimumMeaningfulX(sa);
        int ceiling=Math.max(min,Math.min(40,ComputerUtilMana.getAvailableManaEstimate(me,true)+8));
        for(int x=ceiling;x>=min;x--){
          // v18.2: Forge's X-cost payability depends on the SA carrying the proposed X.
          // The older code tested first and set X second, causing payable Secure/Finale/March lines
          // to be rejected as unpayable during recovery scoring.
          sa.setXManaCostPaid(x);
          boolean payable=ComputerUtilMana.canPayManaCost(sa,me,0,false);
          if(!payable) payable=ComputerUtilMana.canPayManaCost(sa,me,x,false);
          if(payable){
            if(auditEnabled) System.out.println("FARMER_X_CHOICE card="+sa.getHostCard().getName()+" x="+x+" min="+min+" preconfigured=true");
            return true;
          }
        }
        sa.setXManaCostPaid(0);
        if(auditEnabled) System.out.println("FARMER_X_SKIP card="+sa.getHostCard().getName()+" reason=no_meaningful_payable_x min="+min);
        return false;
      }catch(Exception e){
        if(auditEnabled) System.out.println("FARMER_X_SKIP reason=exception card="+(sa!=null&&sa.getHostCard()!=null?sa.getHostCard().getName():"UNKNOWN")+" error="+e.getClass().getSimpleName());
        return false;
      }
    }

    private int conservativeUntappedManaCapacity(){
      int total=0;
      boolean anyUntappedLand=false;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c==null || c.isTapped()) continue;
          String n=c.getName();
          if(c.getType().isLand()){ total+=1; anyUntappedLand=true; continue; }
          if("Sol Ring".equals(n)){ total+=2; continue; }
          if("Arcane Signet".equals(n)||"Talisman of Unity".equals(n)){ total+=1; continue; }
          if(RAMP.contains(n) && c.getType().isCreature()){
            boolean sick=false; try{sick=c.isSick();}catch(Exception ignored){}
            if(!sick) total+=1;
          }
        }
        // Each resolved aura can add one extra mana when at least one land remains untapped.
        if(anyUntappedLand){
          for(Card c:me.getCardsIn(ZoneType.Battlefield)){
            String n=c.getName(); if("Wild Growth".equals(n)||"Utopia Sprawl".equals(n)) total+=1;
          }
        }
      }catch(Exception ignored){}
      return total;
    }
    private int conservativeUntappedSelesnyaSources(){
      int colored=0;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c==null || c.isTapped()) continue;
          String n=c.getName();
          if(c.getType().isLand()){
            if(openingLandMakesGreen(c)||openingLandMakesWhite(c)) colored++;
            continue;
          }
          if("Arcane Signet".equals(n)||"Talisman of Unity".equals(n)){ colored++; continue; }
          if(RAMP.contains(n) && c.getType().isCreature()){
            boolean sick=false; try{sick=c.isSick();}catch(Exception ignored){}
            if(!sick) colored++;
          }
        }
      }catch(Exception ignored){}
      return colored;
    }

    private boolean fullyPayableStrategicAction(SpellAbility sa){
      try{
        if(sa==null || !sa.canPlay()) return false;
        if(sa.costHasManaX()) return configurePayableX(sa);
        // Forge 2.0.14 can incorrectly reject Rhys's hybrid {4}{G/W}{G/W} activated cost
        // during opponent end steps even when the board has six valid Selesnya mana.
        // Rhys's deck is strictly G/W, so the available-mana estimate is a safe fallback
        // for these two known activated abilities while Forge still performs the real payment.
        if(sa.isActivatedAbility() && sa.getHostCard()!=null && "Rhys the Redeemed".equals(sa.getHostCard().getName())){
          String d=String.valueOf(sa);
          int need=(d.contains("For each creature token") || d.contains("copy of that creature")) ? 6 : 3;
          boolean generic=ComputerUtilMana.canPayManaCost(sa,me,0,false);
          if(generic) return true;
          int estimate=ComputerUtilMana.getAvailableManaEstimate(me,true);
          int conservative=conservativeUntappedManaCapacity();
          int selesnya=conservativeUntappedSelesnyaSources();
          int coloredNeed=(need==6?2:1);
          boolean fallback=conservative>=need && selesnya>=coloredNeed;
          if(auditEnabled && (estimate>=need || fallback)) System.out.println("FARMER_RHYS_HYBRID_PAYABILITY_FALLBACK need="+need+" forge_estimate="+estimate+" conservative_capacity="+conservative+" selesnya_sources="+selesnya+" accepted="+fallback+" ability="+d.replace('\n',' '));
          return fallback;
        }
        return ComputerUtilMana.canPayManaCost(sa,me,0,false);
      }catch(Exception e){ return false; }
    }

    private boolean faldornDeck(){ return hasNamed("Faldorn, Dread Wolf Herald",ZoneType.Command,ZoneType.Battlefield,ZoneType.Graveyard,ZoneType.Exile,ZoneType.Hand); }
    private boolean dedicatedFaldornPilot(){ return faldornDeck(); }
    private static final Map<String,String> RAPH_CARD_PLAN = new LinkedHashMap<>();
    static {
      RAPH_CARD_PLAN.put("Raph & Mikey, Troublemakers","commander_engine: Cast in Main 1 as soon as legal/payable; haste means attack immediately. Each declared attack is a creature-cheat trigger. Protect/recast aggressively; extra combats multiply the engine.");
      RAPH_CARD_PLAN.put("Sol Ring","turbo_ramp: Premium turn-1/early acceleration toward seven mana; deploy before medium value unless doing so costs a protected lethal line.");
      RAPH_CARD_PLAN.put("Arcane Signet","color_ramp: Early two-mana acceleration/fixing; prioritize before commander and expensive setup.");
      RAPH_CARD_PLAN.put("Talisman of Impulse","color_ramp: Early two-mana acceleration/fixing; life payment is trivial when it advances Raph timing.");
      RAPH_CARD_PLAN.put("Gruul Signet","color_ramp: Early acceleration/fixing; sequence with an untapped mana source so it can be activated immediately when useful.");
      RAPH_CARD_PLAN.put("Fellwar Stone","color_ramp: Early two-mana acceleration; treat as commander-speed resource, not a late luxury.");
      RAPH_CARD_PLAN.put("Mind Stone","ramp_then_card: Early acceleration first; when mana is abundant and hand is low, cash it in for a card only if it does not jeopardize Raph/extra-combat mana.");
      RAPH_CARD_PLAN.put("Thran Dynamo","burst_ramp: Four mana now to jump into Raph/recast and top-end turns; high priority before commander when seven mana is not yet available.");
      RAPH_CARD_PLAN.put("Ruby Medallion","cost_ramp: Red cost reduction helps Raph because the hybrid commander is red, and discounts red extra-combat/interaction spells; deploy early when it accelerates a real line.");
      RAPH_CARD_PLAN.put("Emerald Medallion","cost_ramp: Green cost reduction helps Raph because the hybrid commander is green, plus green ramp/value spells; deploy early when it advances the commander clock.");
      RAPH_CARD_PLAN.put("Nature's Lore","land_ramp: Fetch Stomping Ground when color fixing matters, otherwise Forest; prioritize early and preserve premium tutors for wins.");
      RAPH_CARD_PLAN.put("Three Visits","land_ramp: Same role as Nature's Lore; fetch typed Stomping Ground when useful, otherwise basic Forest.");
      RAPH_CARD_PLAN.put("Rampant Growth","land_ramp: Early permanent mana development; fix the missing color first.");
      RAPH_CARD_PLAN.put("Cultivate","land_ramp: Three-mana two-land development; use before commander if it materially advances seven mana, but do not waste a post-Raph kill window on routine ramp.");
      RAPH_CARD_PLAN.put("Kodama's Reach","land_ramp: Same strategic role as Cultivate; develop toward seven and fix colors.");
      RAPH_CARD_PLAN.put("Wild Growth","aura_ramp: Enchant an untapped Forest/green source when possible so mana can be used immediately; early commander acceleration.");
      RAPH_CARD_PLAN.put("Utopia Sprawl","aura_ramp: Must enchant a Forest; choose red when red sources are thinner, green otherwise; premium early acceleration and an occasional Speaker untap target.");
      RAPH_CARD_PLAN.put("Lotus Cobra","landfall_ramp: Cast before Raph when land drops remain; convert fetch/ramp lands into the seven-mana jump. Weak Raph hit because entering attacking does not itself create mana.");
      RAPH_CARD_PLAN.put("Ragavan, Nimble Pilferer","early_pressure_ramp: Best as an early hard-cast attacker. Ragavan has a COMBAT-DAMAGE trigger (not an attack trigger), so if Raph cheats it in attacking and it connects, it still creates a Treasure and exiles the opponent's top card; aim it at an open defender when practical.");
      RAPH_CARD_PLAN.put("Tireless Provisioner","landfall_ramp: Deploy before future land drops; default Treasure when mana/kill conversion matters, Food only for survival. Mediocre direct Raph hit without a later landfall.");
      RAPH_CARD_PLAN.put("Professional Face-Breaker","combat_value: Hard-cast before combat when multiple players can be hit; combat damage creates Treasures. Spend Treasure for exile access only with low hand and mana/time to use the card.");
      RAPH_CARD_PLAN.put("Xorn","treasure_multiplier: Multiplies Treasure production from Copper Dragon/Gnawbone/Face-Breaker/Goldspan lines; valuable before combat, and a useful Raph hit when Treasure triggers will happen later that combat.");
      RAPH_CARD_PLAN.put("Goldspan Dragon","treasure_mana: Haste flyer and Treasure amplifier. If cheated in attacking, its own attack trigger does not fire because it was not declared as an attacker; still improves Treasure mana and can attack normally in later combats.");
      RAPH_CARD_PLAN.put("Old Gnawbone","premium_raph_hit: Combat-damage trigger works immediately even when cheated in attacking; successful connections create huge Treasure and fuel extra combats/recasts.");
      RAPH_CARD_PLAN.put("Ancient Copper Dragon","premium_raph_hit: Combat-damage trigger works immediately if it connects; choose the defender/path most likely to let it deal player damage.");
      RAPH_CARD_PLAN.put("Big Score","burst_draw_ramp: Instant discard-to-draw plus two Treasures. Use when the discard is genuinely expendable and preferably before a Raph/recast/extra-combat turn; never pitch protection or a live win piece casually.");
      RAPH_CARD_PLAN.put("Unexpected Windfall","burst_draw_ramp: Same role as Big Score; convert a weak/redundant card into two cards plus two Treasures while preserving live win pieces.");
      RAPH_CARD_PLAN.put("Worldly Tutor","topdeck_kill_tutor: Premium Raph setup. Cast immediately before a Raph attack/draw window and stack the best creature that matters on entry; never burn it for mana fixing.");
      RAPH_CARD_PLAN.put("Sylvan Tutor","topdeck_kill_tutor: Sorcery-speed version of Worldly Tutor; use only when Raph is expected to attack before the top card is naturally drawn.");
      RAPH_CARD_PLAN.put("Formidable Speaker","hand_tutor_utility: ETB trades a safe discard for a creature in hand, which is weaker here than top-deck tutoring. Use mainly when hand is low or a hard-cast utility creature is needed; untap ability is mana utility, not a reason to waste mana without a useful tapped permanent.");
      RAPH_CARD_PLAN.put("Gamble","win_tutor: Tutors any card to hand with random-discard risk. Hold until it can find an extra-combat/kill piece and the hand is large enough to reduce disaster risk; tutors are for winning.");
      RAPH_CARD_PLAN.put("Sylvan Library","selection_topdeck_setup: Early card selection and draw; when Raph will attack, value leaving a premium creature on top over paying life to keep every extra card. Life is a resource, not the primary goal.");
      RAPH_CARD_PLAN.put("Pia, Aether Ascetic","raph_value_conversion: When Pia is put in by Raph & Mikey, her ETB defaults to Sylvan Library whenever Library is still available. This converts a small Raph body into the deck's persistent selection/draw engine. If Library is unavailable, use Garruk's Uprising / Aggravated Assault / interaction or ramp contextually.");
      RAPH_CARD_PLAN.put("Garruk's Uprising","draw_engine: Deploy before Raph when practical; Raph and most premium hits have power 4+, turning entries into cards. Trample redundancy also improves connections.");
      RAPH_CARD_PLAN.put("Toski, Bearer of Secrets","combat_draw: Deploy with attackers and connect broadly; indestructible and must-attack are relevant. Raph-cheated Toski itself did not attack for trigger purposes, but its combat-damage draw ability works for creatures that connect.");
      RAPH_CARD_PLAN.put("Ohran Frostfang","combat_draw_control: Premium precombat draw engine; attacking creatures gain deathtouch and player connections draw cards. Preserve unless trading it is necessary for lethal/survival.");
      RAPH_CARD_PLAN.put("Aerid Konstrari","etb_mana_body: Raph hit creates a Heartwood mana artifact immediately; death creates another. Six-mana activation is only an excess-mana/meaningful-pump play after Raph/protection/extra-combat needs are satisfied.");
      RAPH_CARD_PLAN.put("Rishkar's Expertise","burst_draw: Cast when greatest power is high and hand is low enough that the refill matters; exploit the free MV<=5 spell, but do not spend six mana for a tiny draw or when a kill line is available.");
      RAPH_CARD_PLAN.put("Return of the Wildspeaker","burst_draw_or_finisher: Default DRAW based on greatest non-Human power. Use +3/+3 mode only for lethal/elimination or critical survival, matching the locked project rule.");
      RAPH_CARD_PLAN.put("Etali, Primal Conqueror","premium_raph_hit: Excellent guaranteed ETB value when cheated in; does not need to connect or be declared attacking. Hard-cast only when commander line is unavailable/secured. Its sorcery-speed transform is an excess-mana finisher after Raph/extra-combat/protection needs are covered, not a reason to divert early mana.");
      RAPH_CARD_PLAN.put("Balefire Dragon","premium_connection_hit: If cheated in attacking and it deals combat damage to a player, its damage-to-creatures trigger works immediately; aim at the opponent where a connection is most likely and most valuable.");
      RAPH_CARD_PLAN.put("Terror of the Peaks","chain_payoff: ETB body does not trigger itself, but every later creature entering—including later Raph hits—becomes direct damage. Best before multiple remaining creature entries/extra combats.");
      RAPH_CARD_PLAN.put("Kogla and Yidaro","flex_hit_recycle: ETB can fight a meaningful creature, so it has immediate Raph value. When stranded in hand, its 2RG discard ability can destroy up to one artifact/enchantment, shuffle itself back into the library, and draw—use this to recycle it into future Raph hits even with no target when hand conversion is poor.");
      RAPH_CARD_PLAN.put("Giant Adephage","connection_snowball: If cheated in attacking and it connects, create another Adephage token; prioritize an open defender. Solid but connection-dependent hit.");
      RAPH_CARD_PLAN.put("Wulfgar of Icewind Dale","future_attack_multiplier: Doubles attack-trigger abilities of creatures actually declared attacking. If Raph cheats Wulfgar in, it cannot double the Raph trigger that already happened; it becomes premium when another combat/turn will let Raph be declared again.");
      RAPH_CARD_PLAN.put("Aggravated Assault","repeatable_extra_combat: Primary noncreature kill engine. Activate only after the normal Raph attack, with 3RR and enough board/mana reason; Treasure engines can fuel repeated activations.");
      RAPH_CARD_PLAN.put("Seize the Day","targeted_extra_combat: Postcombat main only after Raph attacked; target/untap Raph first so the extra combat produces another commander trigger. Flashback is real late-game value.");
      RAPH_CARD_PLAN.put("Relentless Assault","extra_combat: Cast in postcombat main after Raph attacked; it untaps creatures that attacked and schedules another combat/main. Never cast before the first attack.");
      RAPH_CARD_PLAN.put("World at War","extra_combat_rebound: Cast postcombat after Raph attacked; additional combat now plus rebound next upkeep can create another explosive turn. Never burn precombat without a special reason.");
      RAPH_CARD_PLAN.put("Port Razer","best_raph_hit: Entering tapped and attacking is ideal because its combat-damage trigger can immediately create another combat and untap the team. Top creature-tutor target when it has a credible connection.");
      RAPH_CARD_PLAN.put("Moraug, Fury of Akoum","landfall_extra_combat: Landfall only creates extra combat during your main phase. If Moraug appears during combat, hold/play a land in the following main phase to convert it; when already on battlefield, preserve Main-1 land drop for postcombat when appropriate.");
      RAPH_CARD_PLAN.put("Heroic Intervention","reactive_protection: Hold for meaningful targeted/mass destroy or damage effects that hexproof/indestructible actually answers; prioritize preserving Raph and a winning board. It does not answer sacrifice, -X/-X, or exile sweepers.");
      RAPH_CARD_PLAN.put("Tamiyo's Safekeeping","reactive_protection: One-mana targeted hexproof+indestructible; Raph is first priority, then live engine/kill piece. Never cast proactively.");
      RAPH_CARD_PLAN.put("Tyvar's Stand","reactive_protection_finisher: X can be zero/small for hexproof+indestructible or large when pump creates lethal. Raph protection is primary; do not dump mana into X unless damage matters.");
      RAPH_CARD_PLAN.put("Tibalt's Trickery","emergency_counter: Counter only a genuinely dangerous opposing spell—sweeper, lethal, or high-impact answer—because replacement is random. Never target an empty stack or our own spell as a gimmick.");
      RAPH_CARD_PLAN.put("Bolt Bend","reactive_redirect: Usually costs R with a 4+ power creature. Redirect hostile single-target removal/interaction away from Raph or a key permanent; never cast without a legal useful stack target.");
      RAPH_CARD_PLAN.put("Untimely Malfunction","modal_interaction: Use redirect mode on hostile single-target spells/abilities, destroy-artifact mode on a meaningful artifact, or can't-block mode only when it materially opens lethal/commander-combat damage. Not reactive-only in the abstract.");
      RAPH_CARD_PLAN.put("Lightning Bolt","cheap_interaction_reach: Remove a meaningful small engine/utility creature or finish a player; do not spend three damage on irrelevant bodies.");
      RAPH_CARD_PLAN.put("Abrade","flex_interaction: Kill a meaningful small creature or artifact; choose mode based on actual threat and preserve when no material target exists.");
      RAPH_CARD_PLAN.put("Shattering Spree","artifact_interaction: Cast only with legal meaningful artifact targets; replicate when additional high-value artifacts justify mana. Never select it into no-target state.");
      RAPH_CARD_PLAN.put("Nature's Claim","efficient_interaction: One-mana artifact/enchantment removal; opponent gaining 4 life is secondary to removing a real engine/stax/kill piece.");
      RAPH_CARD_PLAN.put("Beast Within","premium_broad_interaction: Save for must-answer permanent or survival; the 3/3 is negligible. Do not discard casually to Speaker/loot effects.");
      RAPH_CARD_PLAN.put("Kenrith's Transformation","creature_neutralization_draw: Turn a dangerous creature/commander into a 3/3 Elk and draw. Use on high-impact creature where losing abilities matters; note it is also an enchantment Pia can find in emergency.");
      RAPH_CARD_PLAN.put("Tail Swipe","fight_interaction: Use a large own creature—usually Raph/premium body—to kill a meaningful opposing creature when the fight is safe; sorcery timing outside main phase loses the +1/+1 bonus.");
      RAPH_CARD_PLAN.put("Stomping Ground","dual_land: Untapped when tempo/mana matters; typed Forest makes it a Nature's Lore/Three Visits target and supplies both colors.");
      RAPH_CARD_PLAN.put("Karplusan Forest","dual_land: Use colored damage when needed; life is a resource for commander speed.");
      RAPH_CARD_PLAN.put("Copperline Gorge","dual_land: Early untapped color source; sequence early before later lands when possible.");
      RAPH_CARD_PLAN.put("Spire Garden","dual_land: Multiplayer untapped RG source while two or more opponents remain; reliable early commander fixing.");
      RAPH_CARD_PLAN.put("Command Tower","dual_land: Best clean RG fixing; preserve color flexibility when sequencing mana.");
      RAPH_CARD_PLAN.put("City of Brass","dual_land: Any-color source; accept life payment to execute tempo/interaction lines.");
      RAPH_CARD_PLAN.put("Evolving Wilds","fetch_land: Fix colors and create an extra landfall event for Cobra/Provisioner/Moraug; timing matters—bank for a live landfall payoff when mana permits.");
      RAPH_CARD_PLAN.put("Terramorphic Expanse","fetch_land: Same as Evolving Wilds; bank for Cobra/Provisioner/Moraug when that creates more value than immediate fixing.");
      RAPH_CARD_PLAN.put("Forest","basic_land: Primary green source and typed target; support Utopia Sprawl/Wild Growth and green ramp density.");
      RAPH_CARD_PLAN.put("Goblin Anarchomancer","ramp_discount: Cheap persistent Gruul spell discount; deploy before Raph when it materially accelerates commander timing.");
      RAPH_CARD_PLAN.put("Shadow in the Warp","ramp_tax: Reduces our creature costs while taxing opposing noncreatures; pre-Raph acceleration piece.");
      RAPH_CARD_PLAN.put("Jeska's Will","burst_ramp: Before Raph use red-mana mode to bridge commander; with Raph controlled use both modes when legal. Preserve post-Raph mana for recast unless decisive.");
      RAPH_CARD_PLAN.put("Seething Song","burst_ramp: Primary red ritual bridge into Raph; after Raph is established preserve for commander recast unless decisive.");
      RAPH_CARD_PLAN.put("Pyretic Ritual","burst_ramp: Cheap ritual bridge into Raph; preserve after deployment for recast unless decisive.");
      RAPH_CARD_PLAN.put("Desperate Ritual","burst_ramp: Cheap ritual bridge into Raph; preserve after deployment for recast unless decisive.");
      RAPH_CARD_PLAN.put("Geosurge","burst_ramp: Creature/artifact-restricted burst mana; use to bridge Raph or a decisive creature line, otherwise preserve post-Raph for recast.");
      RAPH_CARD_PLAN.put("Irencrag Feat","burst_ramp: Seven-red bridge for one additional spell; premium Raph/recast accelerator.");
      RAPH_CARD_PLAN.put("Rite of Flame","burst_ramp: One-mana ritual bridge; do not burn it when it does not improve Raph timing.");
      RAPH_CARD_PLAN.put("Strike It Rich","treasure_setup: Converts one mana into a banked Treasure; useful before Raph and as recast insurance afterward.");
      RAPH_CARD_PLAN.put("Brainstone","topdeck_setup: With Raph ready to attack, activate before hard-casting premium creatures and put best Raph hits from hand on top.");
      RAPH_CARD_PLAN.put("Hellkite Tyrant","premium_raph_hit: Flying trample artifact theft on combat damage; alternate win is bonus, not the primary Treasure plan.");
      RAPH_CARD_PLAN.put("Ghalta, Stampede Tyrant","premium_raph_hit: Raph cheat causes Ghalta ETB, allowing creature-heavy hand conversion immediately.");
      RAPH_CARD_PLAN.put("Apex Altisaur","premium_raph_hit: Large body whose enrage repeatedly converts damage into creature removal.");
      RAPH_CARD_PLAN.put("Sandstone Needle","burst_land: Depletion land banking red burst mana for Raph and recasts.");
      RAPH_CARD_PLAN.put("Hickory Woodlot","burst_land: Depletion land banking green burst mana for Raph and recasts.");
      RAPH_CARD_PLAN.put("Mountain","basic_land: Primary red source; ensure enough red for extra-combat spells and reactive red interaction.");
    }
    private void raphCardPlanCoverageAudit(){
      if(raphCoverageAudited)return; raphCoverageAudited=true;
      java.util.LinkedHashSet<String> seen=new java.util.LinkedHashSet<>();
      try{ for(ZoneType z:new ZoneType[]{ZoneType.Command,ZoneType.Library,ZoneType.Hand,ZoneType.Battlefield,ZoneType.Graveyard,ZoneType.Exile}) for(Card c:me.getCardsIn(z)) seen.add(c.getName()); }catch(Exception ignored){}
      java.util.ArrayList<String> missing=new java.util.ArrayList<>(); for(String n:seen) if(!"Commander Effect".equals(n)&&!RAPH_CARD_PLAN.containsKey(n)) missing.add(n);
      if(auditEnabled){ System.out.println("RAPH_CARD_AUDIT covered="+(seen.size()-missing.size())+" total_seen="+seen.size()+" missing="+missing.size()+" missing_names="+missing); for(String n:seen) if(RAPH_CARD_PLAN.containsKey(n)) System.out.println("RAPH_CARD_PLAN card=["+n+"] plan=["+RAPH_CARD_PLAN.get(n)+"]"); }
    }
    private boolean raphDeck(){ return true; }
    private boolean raphBattlefield(){ return hasNamed("Raph & Mikey, Troublemakers",ZoneType.Battlefield); }
    private Card raphBattlefieldCard(){ try{for(Card c:me.getCardsIn(ZoneType.Battlefield))if("Raph & Mikey, Troublemakers".equals(c.getName()))return c;}catch(Exception ignored){}return null; }
    private boolean raphReadyToAttack(){
      Card r=raphBattlefieldCard(); if(r==null)return false;
      try{for(Player p:me.getOpponents())if(!p.hasLost()&&forge.game.combat.CombatUtil.canAttack(r,p))return true;}catch(Exception ignored){}
      return false;
    }
    private boolean raphAttackedThisTurn(){ try{return lastRaphAttackTurn==me.getGame().getPhaseHandler().getTurn() && raphAttacksThisTurn>0;}catch(Exception e){return false;} }
    private int raphCommanderApproxCost(){
      int cost=7; try{cost+=2*Math.max(0,me.getTotalCommanderCast());}catch(Exception ignored){}
      if(hasNamed("Ruby Medallion",ZoneType.Battlefield))cost--; if(hasNamed("Emerald Medallion",ZoneType.Battlefield))cost--;
      return Math.max(2,cost);
    }
    private int raphTreasureCount(){
      int n=0; try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isToken() && c.getName().toLowerCase(Locale.ROOT).contains("treasure")) n++;}catch(Exception ignored){} return n;
    }
    private int raphNextRecastApproxCost(){
      // While Raph is on the battlefield, getTotalCommanderCast() already includes the current cast,
      // so the next command-zone cast is base seven plus the next accumulated commander tax.
      int cost=7; try{cost+=2*Math.max(0,me.getTotalCommanderCast());}catch(Exception ignored){}
      if(hasNamed("Ruby Medallion",ZoneType.Battlefield))cost--; if(hasNamed("Emerald Medallion",ZoneType.Battlefield))cost--;
      return Math.max(2,cost);
    }
    private int raphTreasureReserveTarget(){
      if(!raphBattlefield())return 0;
      int treasures=raphTreasureCount(); if(treasures<=0)return 0;
      int total=Math.max(0,ComputerUtilMana.getAvailableManaEstimate(me,true));
      // Treat each Treasure as one conservative mana even when Goldspan can make it worth two.
      // That intentionally biases the reserve toward commander insurance rather than greed.
      int persistent=Math.max(0,total-treasures);
      return Math.min(treasures,Math.max(0,raphNextRecastApproxCost()-persistent));
    }
    private boolean raphReserveMayBreak(SpellAbility a){
      if(a==null||a.getHostCard()==null)return false;
      String n=a.getHostCard().getName();
      if(survivalMode())return true;
      if(INTERACTION.contains(n)||PROTECTION.contains(n))return true;
      if(raphExtraCombatCard(n)&&raphBattlefield()&&raphAttackedThisTurn())return true;
      if("Aggravated Assault".equals(n)&&a.isActivatedAbility()&&raphBattlefield()&&raphAttackedThisTurn())return true;
      return false;
    }
    private boolean raphTreasureReserveAllows(SpellAbility a){
      int reserve=raphTreasureReserveTarget(); if(reserve<=0||a==null||a.getHostCard()==null)return true;
      if(raphReserveMayBreak(a)){if(auditEnabled)System.out.println("RAPH_TREASURE_RESERVE_BREAK card="+a.getHostCard().getName()+" reserve="+reserve+" reason=decisive_or_survival");return true;}
      int total=Math.max(0,ComputerUtilMana.getAvailableManaEstimate(me,true));
      int cmc=0; try{cmc=(int)a.getHostCard().getManaCost().getCMC();}catch(Exception ignored){}
      if(a.isActivatedAbility()){
        // Known Treasure-consuming/non-mana conversions are handled explicitly; unknown activations
        // are already rejected elsewhere in the Raph pilot.
        if("Professional Face-Breaker".equals(a.getHostCard().getName()) && raphTreasureCount()<=reserve){
          if(auditEnabled)System.out.println("RAPH_TREASURE_RESERVE_HOLD card=Professional_Face-Breaker treasures="+raphTreasureCount()+" reserve="+reserve);
          return false;
        }
        return true;
      }
      boolean ok=(total-cmc)>=reserve;
      if(!ok&&auditEnabled)System.out.println("RAPH_TREASURE_RESERVE_HOLD card="+a.getHostCard().getName()+" total_mana="+total+" cmc="+cmc+" treasures="+raphTreasureCount()+" reserve="+reserve+" next_raph="+raphNextRecastApproxCost());
      return ok;
    }
    private boolean raphHasExtraCombatInHand(){
      try{for(Card c:me.getCardsIn(ZoneType.Hand))if(raphExtraCombatCard(c.getName()))return true;}catch(Exception ignored){} return false;
    }
    private boolean raphHasLiveExtraCombatLine(){
      int mana=ComputerUtilMana.getAvailableManaEstimate(me,true);
      if(hasNamed("Aggravated Assault",ZoneType.Battlefield)&&mana>=5)return true;
      try{for(Card c:me.getCardsIn(ZoneType.Hand)){String n=c.getName(); if("Seize the Day".equals(n)&&mana>=4)return true; if("Relentless Assault".equals(n)&&mana>=4)return true; if("World at War".equals(n)&&mana>=5)return true;}}catch(Exception ignored){}
      if(hasNamed("Moraug, Fury of Akoum",ZoneType.Battlefield)){try{if(!ComputerUtilAbility.getAvailableLandsToPlay(me.getGame(),me).isEmpty())return true;}catch(Exception ignored){}}
      return false;
    }
    private boolean raphTutorCanConvertBeforeDraw(){
      try{PhaseType ph=me.getGame().getPhaseHandler().getPhase(); if(!me.getGame().getPhaseHandler().isPlayerTurn(me)||!raphBattlefield())return false; if(ph==PhaseType.MAIN1&&raphReadyToAttack())return true; if(ph==PhaseType.MAIN2&&raphAttackedThisTurn()&&raphHasLiveExtraCombatLine())return true;}catch(Exception ignored){}return false;
    }
    private int raphOpenOpponentCount(){
      int n=0; try{for(Player p:me.getOpponents()){if(p.hasLost())continue;int blockers=0;for(Card c:p.getCardsIn(ZoneType.Battlefield))if(c.isCreature()&&!c.isTapped())blockers++;if(blockers<=1)n++;}}catch(Exception ignored){}return n;
    }
    private int raphTopHitScoreName(String n){
      boolean open=raphOpenOpponentCount()>0, extra=raphHasLiveExtraCombatLine()||raphAttackedThisTurn();
      if("Port Razer".equals(n))return open?10000:7200;
      if("Etali, Primal Conqueror".equals(n))return 9000;
      if("Old Gnawbone".equals(n))return open?8800:6500;
      if("Ancient Copper Dragon".equals(n))return open?8600:6200;
      if("Balefire Dragon".equals(n))return open?8300:6100;
      if("Terror of the Peaks".equals(n))return extra?8400:6900;
      if("Wulfgar of Icewind Dale".equals(n))return extra?8000:5400;
      if("Moraug, Fury of Akoum".equals(n))return 7200;
      if("Giant Adephage".equals(n))return open?6900:5000;
      if("Xorn".equals(n))return 4300;
      if("Aerid Konstrari".equals(n))return 4700;
      if("Kogla and Yidaro".equals(n))return 6000;
      if("Goldspan Dragon".equals(n))return 5600;
      if("Ohran Frostfang".equals(n))return 5100;
      if("Toski, Bearer of Secrets".equals(n))return 4700;
      if("Professional Face-Breaker".equals(n))return 4400;
      if("Tireless Provisioner".equals(n)||"Lotus Cobra".equals(n)||"Ragavan, Nimble Pilferer".equals(n)||"Pia, Aether Ascetic".equals(n)||"Formidable Speaker".equals(n))return 2200;
      return 3500;
    }
    private Card raphBestTopHit(CardCollection options){
      Card best=null;int bs=Integer.MIN_VALUE;for(Card c:options){if(!c.getType().isCreature())continue;int sc=raphTopHitScoreName(c.getName());if(sc>bs){bs=sc;best=c;}}return best;
    }
    private boolean raphPremiumProtectedCard(String n){
      return PROTECTION.contains(n)||"Worldly Tutor".equals(n)||"Sylvan Tutor".equals(n)||"Gamble".equals(n)||raphExtraCombatCard(n)||"Port Razer".equals(n)||"Wulfgar of Icewind Dale".equals(n)||"Terror of the Peaks".equals(n)||"Etali, Primal Conqueror".equals(n)||"Beast Within".equals(n);
    }
    private int raphDiscardValue(Card c){
      if(c==null)return 99999; String n=c.getName();
      if(raphPremiumProtectedCard(n))return 10000;
      if("Kogla and Yidaro".equals(n))return 8500; // recycle through its own ability instead
      if(c.isLand()){int lands=landCount(me.getCardsIn(ZoneType.Hand));return (battlefieldLandCount()>=6||lands>=4)?350:7000;}
      if("Formidable Speaker".equals(n))return 700;
      if("Shattering Spree".equals(n))return 850;
      if("Lightning Bolt".equals(n)||"Tail Swipe".equals(n))return 1050;
      if(raphCheapRamp(n)&&battlefieldLandCount()>=7)return 900;
      try{int cmc=(int)c.getManaCost().getCMC(); if(c.getType().isCreature()&&cmc>=6)return 1150+raphTopHitScoreName(n)/20; if(cmc<=2)return 2600;}catch(Exception ignored){}
      return 2000+raphOpeningKeepValue(c);
    }
    private Card raphStrategicDiscardChoice(CardCollectionView cards,String source){
      Card best=null;int bv=Integer.MAX_VALUE;for(Card c:cards){int v=raphDiscardValue(c);if(v<bv){bv=v;best=c;}}
      if(best!=null&&bv<9000){if(auditEnabled)System.out.println("RAPH_DISCARD_CHOICE source="+source+" card="+best.getName()+" value="+bv);return best;}return null;
    }
    private boolean raphPiaHasUsefulTarget(){
      try{for(Card c:me.getCardsIn(ZoneType.Library)){String n=c.getName();if("Aggravated Assault".equals(n)||"Sylvan Library".equals(n)||"Garruk's Uprising".equals(n)||"Kenrith's Transformation".equals(n)||"Utopia Sprawl".equals(n)||"Wild Growth".equals(n))return true;}}catch(Exception ignored){}return false;
    }
    private boolean raphSpeakerHasUsefulTarget(){
      try{CardCollection creatures=new CardCollection();for(Card c:me.getCardsIn(ZoneType.Library))if(c.getType().isCreature())creatures.add(c);return raphSpeakerTutorChoice(creatures)!=null;}catch(Exception ignored){return false;}
    }
    private Card raphPiaDiscardChoice(CardCollectionView cards){
      if(!raphPiaHasUsefulTarget())return null;
      if(raphAttackedThisTurn() && hasNamed("Sylvan Library",ZoneType.Library)){
        Card forced=raphPiaForcedSylvanDiscardChoice(cards);
        if(forced!=null)return forced;
      }
      return raphStrategicDiscardChoice(cards,"Pia, Aether Ascetic");
    }
    private Card raphPiaForcedSylvanDiscardChoice(CardCollectionView cards){
      Card best=null; int bv=Integer.MAX_VALUE;
      // User-locked rule: when Raph has produced Pia and Sylvan Library is available, Pia converts.
      // Preserve protection if any non-protection option exists, but do not decline merely because the
      // remaining card is otherwise premium (Gamble/extra combat/etc.).
      boolean haveNonProtection=false; for(Card c:cards) if(!PROTECTION.contains(c.getName())) {haveNonProtection=true;break;}
      for(Card c:cards){ if(haveNonProtection && PROTECTION.contains(c.getName())) continue; int v=raphDiscardValue(c); if(v<bv){bv=v;best=c;} }
      if(best!=null && auditEnabled)System.out.println("RAPH_PIA_FORCED_SYLVAN_DISCARD card="+best.getName()+" value="+bv);
      return best;
    }
    private Card raphGambleWinChoice(CardCollection options){
      if(options==null||options.isEmpty()||!raphBattlefield())return null;
      int avail=ComputerUtilMana.getAvailableManaEstimate(me,true), rem=Math.max(0,avail-1); PhaseType ph=me.getGame().getPhaseHandler().getPhase();
      String[] order;
      if(ph==PhaseType.MAIN1&&raphReadyToAttack()&&rem>=4){
        order=rem>=8?new String[]{"Wulfgar of Icewind Dale","Aggravated Assault","Seize the Day","Relentless Assault","World at War"}:new String[]{"Wulfgar of Icewind Dale","Seize the Day","Relentless Assault","World at War"};
      } else if(ph==PhaseType.MAIN2&&raphAttackedThisTurn()&&rem>=4){
        order=rem>=8?new String[]{"Aggravated Assault","Seize the Day","Relentless Assault","World at War"}:new String[]{"Seize the Day","Relentless Assault","World at War"};
      } else return null;
      for(String want:order){Card c=findNamed(options,want);if(c==null)continue;if("World at War".equals(want)&&rem<5)continue;if("Aggravated Assault".equals(want)&&rem<8)continue;return c;}return null;
    }
    private Card raphPiaTutorChoice(CardCollection options){
      if(options==null||options.isEmpty())return null;
      String[] order; int mana=ComputerUtilMana.getAvailableManaEstimate(me,true);
      // Locked Raph/Mikey strategy: Pia converts a small Raph hit into Sylvan Library whenever Library remains available.
      // This intentionally overrides the previous Aggravated Assault-first live-combat heuristic.
      order=new String[]{"Sylvan Library","Garruk's Uprising","Aggravated Assault","Kenrith's Transformation","Utopia Sprawl","Wild Growth"};
      for(String want:order)for(Card c:options)if(want.equals(c.getName())){if(auditEnabled)System.out.println("RAPH_PIA_TUTOR target="+want+" mana="+mana+" raph="+raphBattlefield()+" attacked="+raphAttackedThisTurn());return c;}return null;
    }
    private boolean raphMeaningfulArtifactTarget(){
      try{for(Player p:me.getOpponents())for(Card c:p.getCardsIn(ZoneType.Battlefield))if(c.getType().isArtifact()&&permanentThreatScore(c)>=35)return true;}catch(Exception ignored){}return false;
    }
    private boolean raphAbradeCreatureTarget(){
      try{
        for(Player p:me.getOpponents()) for(Card c:p.getCardsIn(ZoneType.Battlefield)){
          if(!c.isCreature()) continue;
          if(c.getNetToughness()<=3 && permanentThreatScore(c)>=25) return true;
        }
      }catch(Exception ignored){}
      return false;
    }
    private Card raphBestArtifactTargetFor(SpellAbility sa){
      Card best=null; int bs=Integer.MIN_VALUE;
      try{
        for(Player p:me.getOpponents()) for(Card c:p.getCardsIn(ZoneType.Battlefield)){
          if(!c.getType().isArtifact()) continue;
          if(sa!=null && sa.usesTargeting() && !sa.canTarget(c)) continue;
          int sc=permanentThreatScore(c)+Math.max(0,playerThreatScore(p)/8);
          if(sc>bs){bs=sc;best=c;}
        }
      }catch(Exception ignored){}
      return best;
    }
    private boolean raphKoglaRecycleUseful(){
      int mana=ComputerUtilMana.getAvailableManaEstimate(me,true), hand=me.getCardsIn(ZoneType.Hand).size();
      return mana>=4 && (raphMeaningfulArtifactTarget() || hand>=5 || (raphBattlefield()&&hand>=3));
    }
    private int raphProtectionScore(String n){
      Card threatened=topStackProtectedTarget(); boolean sweep=destructiveSweepOnStack(); String d="";
      try{if(!me.getGame().getStack().isEmpty())d=stackThreatText(me.getGame().getStack().peekAbility());}catch(Exception ignored){}
      if(threatened!=null){int sc="Raph & Mikey, Troublemakers".equals(threatened.getName())?7600:6200; if("Heroic Intervention".equals(n))sc-=250; return sc;}
      if(sweep){ if("Heroic Intervention".equals(n) && !(d.contains("-x/-x")||d.contains("gets -")||d.contains("exile all")||d.contains("farewell")))return 7400; if(("Tamiyo's Safekeeping".equals(n)||"Tyvar's Stand".equals(n))&&raphBattlefield()&&!(d.contains("exile all")||d.contains("farewell")||d.contains("-x/-x")||d.contains("gets -")))return 6000; }
      return 0;
    }
    private boolean raphMustCounterTopSpell(){
      try{if(me.getGame().getStack().isEmpty())return false;SpellAbility top=me.getGame().getStack().peekAbility();if(top==null||top.getActivatingPlayer()==me)return false;String d=stackThreatText(top);return stackThreatensUs()||destructiveSweepOnStack()||d.contains("exile all")||d.contains("farewell")||d.contains("cyclonic rift")||d.contains("toxic deluge")||d.contains("all creatures get -")||d.contains("each creature gets -");}catch(Exception ignored){}return false;
    }
    private int raphProtectionReserveNeeded(){
      if(!raphBattlefield())return 0;int best=99;try{for(Card c:me.getCardsIn(ZoneType.Hand)){String n=c.getName();if("Tamiyo's Safekeeping".equals(n)||"Tyvar's Stand".equals(n))best=Math.min(best,1);else if("Heroic Intervention".equals(n))best=Math.min(best,2);else if("Bolt Bend".equals(n)&&maxCreaturePower()>=4)best=Math.min(best,1);}}catch(Exception ignored){}return best==99?0:best;
    }
    private SpellAbility raphPreCommanderSetupAction(){
      if(raphBattlefield())return null;int avail=ComputerUtilMana.getAvailableManaEstimate(me,true), cmd=raphCommanderApproxCost();if(avail<cmd+3)return null;
      String[] order={"Wulfgar of Icewind Dale","Garruk's Uprising","Professional Face-Breaker","Xorn","Ohran Frostfang","Toski, Bearer of Secrets"};
      for(String want:order){for(Card c:me.getCardsIn(ZoneType.Hand))if(want.equals(c.getName()))for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){if(sa!=null&&sa.isSpell()&&sa.canPlay()&&fullyPayableStrategicAction(sa)){int cmc=cardCmc(c);if(avail-cmc>=cmd){if(auditEnabled)System.out.println("RAPH_PRECOMMAND_SETUP card="+want+" avail="+avail+" cmd_est="+cmd);return sa;}}}}return null;
    }
    private boolean raphCheapRamp(String n){ return new HashSet<>(Arrays.asList(
      "Sol Ring","Arcane Signet","Talisman of Impulse","Gruul Signet","Fellwar Stone","Mind Stone",
      "Nature's Lore","Three Visits","Rampant Growth","Cultivate","Kodama's Reach","Wild Growth","Utopia Sprawl","Ruby Medallion","Emerald Medallion","Thran Dynamo","Goblin Anarchomancer","Shadow in the Warp","Jeska's Will","Seething Song","Pyretic Ritual","Desperate Ritual","Geosurge","Irencrag Feat","Rite of Flame","Strike It Rich"
    )).contains(n); }
    private boolean raphEarlyEngine(String n){ return new HashSet<>(Arrays.asList(
      "Sylvan Library","Tireless Provisioner","Professional Face-Breaker","Garruk's Uprising","Pia, Aether Ascetic","Ragavan, Nimble Pilferer"
    )).contains(n); }
    private boolean raphExtraCombatCard(String n){ return "Aggravated Assault".equals(n)||"Seize the Day".equals(n)||"Relentless Assault".equals(n)||"World at War".equals(n); }
    private int raphOpeningKeepValue(Card c){
      if(c==null)return 0; String n=c.getName(); if(c.isLand())return 3200;
      if(raphCheapRamp(n)){ try{return 5200-(int)c.getManaCost().getCMC()*120;}catch(Exception ignored){return 4800;} }
      if(raphEarlyEngine(n))return 4000;
      if("Worldly Tutor".equals(n)||"Sylvan Tutor".equals(n)||"Gamble".equals(n))return 2700;
      if(PROTECTION.contains(n))return 2300;
      if(INTERACTION.contains(n))return 1900;
      if("Wulfgar of Icewind Dale".equals(n)||"Port Razer".equals(n)||raphExtraCombatCard(n))return 1800;
      try{int cmc=(int)c.getManaCost().getCMC(); if(cmc>=7)return 400; if(cmc>=5)return 900; if(cmc<=3)return 2200;}catch(Exception ignored){}
      return 1400;
    }
    private boolean raphKeepHand(CardCollectionView hand,int cardsToReturn){
      int lands=landCount(hand), cheapRamp=0, engines=0, expensive=0, early=0, payoffClog=0;
      int greenSources=0,redSources=0;
      for(Card c:hand){
        if(openingLandMakesGreen(c))greenSources++; if(openingLandMakesWhite(c))redSources++;
        if(c.isLand())continue; String n=c.getName();
        if(raphCheapRamp(n))cheapRamp++; if(raphEarlyEngine(n))engines++;
        if(raphExtraCombatCard(n)||"Port Razer".equals(n)||"Moraug, Fury of Akoum".equals(n)||"Ancient Copper Dragon".equals(n)||"Old Gnawbone".equals(n)||"Balefire Dragon".equals(n)||"Etali, Primal Conqueror".equals(n)||"Giant Adephage".equals(n))payoffClog++;
        try{int cmc=(int)c.getManaCost().getCMC(); if(cmc<=3)early++; if(cmc>=5)expensive++;}catch(Exception ignored){}
      }
      // Locked house rule: every 0-1 land opener gets another free look until the five-card emergency threshold.
      if(lands<=1 && cardsToReturn<2){extraOneLandFreeCredit=true;System.out.println("RAPH_MULLIGAN REJECT lands="+lands+" reason=house_rule return="+cardsToReturn+" hand=["+cardNames(hand)+"]");return false;}
      boolean colorOk=(greenSources>0 && redSources>0) || genericTwoManaColorFixInHand(hand,lands);
      boolean realPlan=cheapRamp>=1 || engines>=1 || (lands>=3 && early>=2 && expensive<=2);
      boolean strongPlan=cheapRamp>=1 || (engines>=1 && lands>=3);
      boolean keep=lands>=2 && lands<=4 && colorOk && realPlan;
      if(cardsToReturn==0) keep = keep && strongPlan; // spend the multiplayer free mulligan aggressively
      if(cardsToReturn==1) keep = keep && ((cheapRamp>=2) || (cheapRamp>=1&&engines>=1) || (engines>=1&&lands>=3&&early>=2)); // six-card hands need a real development plan, not one rock plus dead payoffs
      if(cardsToReturn<=1 && payoffClog>=3 && cheapRamp<2 && engines==0) keep=false;
      if(cardsToReturn>=2 && lands>=2 && lands<=5 && colorOk && (early>=1||cheapRamp>=1||engines>=1)) keep=true;
      if(cardsToReturn>=2 && lands==1 && early>=1 && colorOk) keep=true; // emergency five-card exception
      if(cardsToReturn>=4 && lands>=1 && lands<=5 && colorOk) keep=true;
      // Explicitly reject the Game-5 pattern: two lands, no development, hand clogged at 4-5+ mana.
      if(lands==2 && cheapRamp==0 && engines==0 && expensive>=2 && cardsToReturn<2) keep=false;
      System.out.println("RAPH_MULLIGAN "+(keep?"KEEP":"REJECT")+" lands="+lands+" green_sources="+greenSources+" red_sources="+redSources+" cheap_ramp="+cheapRamp+" engines="+engines+" early="+early+" expensive="+expensive+" payoff_clog="+payoffClog+" color_ok="+colorOk+" return="+cardsToReturn+" hand=["+cardNames(hand)+"]");
      return keep;
    }
    private boolean raphSafeSpeakerDiscard(Card c){
      if(c==null)return false; String n=c.getName();
      if(c.isLand()) return battlefieldLandCount()>=5 || landCount(me.getCardsIn(ZoneType.Hand))>=4;
      if(PROTECTION.contains(n)||"Beast Within".equals(n)||"Worldly Tutor".equals(n)||"Sylvan Tutor".equals(n)||"Gamble".equals(n)||raphExtraCombatCard(n))return false;
      if("Wulfgar of Icewind Dale".equals(n)||"Port Razer".equals(n)||"Terror of the Peaks".equals(n)||"Etali, Primal Conqueror".equals(n))return false;
      if(raphBattlefield() && raphExtraCombatCard(n))return false;
      return true;
    }
    private Card raphSpeakerDiscardChoice(CardCollectionView cards){
      Card best=null; int bv=Integer.MAX_VALUE;
      for(Card c:cards){ if(!raphSafeSpeakerDiscard(c))continue; int v=raphOpeningKeepValue(c); try{v+=(int)c.getManaCost().getCMC()*80;}catch(Exception ignored){} if(v<bv){bv=v;best=c;} }
      return best;
    }
    private Card raphSpeakerTutorChoice(CardCollection options){
      if(options==null||options.isEmpty())return null;
      int mana=ComputerUtilMana.getAvailableManaEstimate(me,true), hand=me.getCardsIn(ZoneType.Hand).size();
      String[] order;
      if(hand<=3) order=new String[]{"Ohran Frostfang","Toski, Bearer of Secrets","Wulfgar of Icewind Dale","Professional Face-Breaker","Xorn","Aerid Konstrari","Kogla and Yidaro"};
      else if(raphBattlefield()&&mana>=4) order=new String[]{"Wulfgar of Icewind Dale","Ohran Frostfang","Toski, Bearer of Secrets","Professional Face-Breaker","Xorn"};
      else order=new String[]{"Ohran Frostfang","Toski, Bearer of Secrets","Professional Face-Breaker","Xorn","Lotus Cobra","Tireless Provisioner"};
      for(String want:order)for(Card c:options)if(want.equals(c.getName()))return c;
      return null; // do not turn a card into an expensive creature that is better left in the library for Raph
    }
    private Player raphBestAttackTarget(Card attacker, boolean connectionPriority){
      Player best=null; int bs=Integer.MIN_VALUE;
      try{for(Player p:me.getOpponents()){ if(p.hasLost()||!forge.game.combat.CombatUtil.canAttack(attacker,p))continue; int blockers=0; for(Card c:p.getCardsIn(ZoneType.Battlefield))if(c.isCreature()&&!c.isTapped())blockers++; int sc=playerThreatScore(p)-p.getLife()*4; if(connectionPriority)sc-=blockers*240; if(attacker.getNetPower()>=p.getLife())sc+=3000; if(sc>bs){bs=sc;best=p;} }}catch(Exception ignored){}
      return best;
    }
    private int raphActionScore(SpellAbility a, boolean stackNonEmpty){
      if(a==null||a.getHostCard()==null)return Integer.MIN_VALUE; String n=a.getHostCard().getName();
      int hand=me.getCardsIn(ZoneType.Hand).size(), mana=ComputerUtilMana.getAvailableManaEstimate(me,true);
      PhaseType ph=me.getGame().getPhaseHandler().getPhase(); boolean own=me.getGame().getPhaseHandler().isPlayerTurn(me);
      if(stackNonEmpty){
        if(PROTECTION.contains(n)){int pp=raphProtectionScore(n);return pp;}
        if("Tibalt's Trickery".equals(n)) return raphMustCounterTopSpell()?7000:0;
        if("Bolt Bend".equals(n))return stackThreatensUs()?6800:0;
        if("Untimely Malfunction".equals(n))return stackThreatensUs()?6700:0;
        if(INTERACTION.contains(n))return stackThreatensUs()?5600:0;
        return 0;
      }
      if(PROTECTION.contains(n))return 0; // reactive only
      if("Tibalt's Trickery".equals(n)||"Bolt Bend".equals(n))return 0;
      if("Untimely Malfunction".equals(n)&&a.isSpell()){
        if(raphMeaningfulArtifactTarget())return 3600;
        return 0; // unblock mode is reserved for explicit lethal logic, not casual use
      }
      if(raphExtraCombatCard(n)){
        if(!raphBattlefield()||!raphAttackedThisTurn())return 0;
        if(raphAttacksThisTurn>=6){ if(auditEnabled)System.out.println("RAPH_EXTRA_COMBAT_LOCK reason=turn_combat_cap attacks="+raphAttacksThisTurn+" card="+n); return 0; }
        if(!raphSafeForAnotherCombat()){ if(auditEnabled)System.out.println("RAPH_EXTRA_COMBAT_LOCK reason=library_safety card="+n); return 0; }
        if("Aggravated Assault".equals(n)&&a.isActivatedAbility()){
          int t=me.getGame().getPhaseHandler().getTurn();
          if(t==lastAggravatedSelectionTurn && raphAttacksThisTurn==lastAggravatedSelectionAttackCount){if(auditEnabled)System.out.println("RAPH_AGGRAVATED_LOCK reason=no_intervening_combat attacks="+raphAttacksThisTurn);return 0;}
          return 6500;
        }
        if(own && ph==PhaseType.MAIN2 && a.isSpell())return 6400;
        return 0;
      }
      if("Wulfgar of Icewind Dale".equals(n)&&a.isSpell())return raphBattlefield()&&raphReadyToAttack()?5850:3900;
      if(("Worldly Tutor".equals(n)||"Sylvan Tutor".equals(n))&&a.isSpell()) return 5750;
      if("Formidable Speaker".equals(n)&&a.isSpell()){
        if(raphBattlefield()&&raphReadyToAttack())return 0; // do not pull a premium Raph hit into hand immediately before combat
        boolean live=raphSpeakerHasUsefulTarget()&&raphSpeakerDiscardChoice(me.getCardsIn(ZoneType.Hand))!=null;
        return live?(hand<=2?3400:2200):900;
      }
      if("Pia, Aether Ascetic".equals(n)&&a.isSpell()){
        boolean live=raphPiaHasUsefulTarget()&&raphPiaDiscardChoice(me.getCardsIn(ZoneType.Hand))!=null;
        if(!live)return 950;
        if(raphBattlefield()&&raphAttackedThisTurn()&&hasNamed("Aggravated Assault",ZoneType.Library))return 4300;
        return !raphBattlefield()&&battlefieldLandCount()<=5?3650:2600;
      }
      if("Gamble".equals(n)&&a.isSpell()) return hand>=2?5350:4300;
      if(raphCheapRamp(n)&&a.isSpell())return !raphBattlefield()?(mana<7?5200:3900):2450;
      if(raphEarlyEngine(n)&&a.isSpell())return !raphBattlefield()?4350:3200;
      if("Toski, Bearer of Secrets".equals(n)||"Ohran Frostfang".equals(n)){
        // v0.3.4: do not add mandatory combat-draw engines when the library is already unsafe.
        if(libraryDangerMode()){if(auditEnabled)System.out.println("RAPH_DRAW_ENGINE_HOLD card="+n+" reason=library_danger library="+libraryCardsRemaining());return 0;}
        return hand<=4?4300:3300;
      }
      if("Sylvan Library".equals(n)||"Garruk's Uprising".equals(n))return hand<=4?4300:3300;
      if("Return of the Wildspeaker".equals(n)||"Rishkar's Expertise".equals(n)){
        if(libraryCriticalMode()){if(auditEnabled)System.out.println("RAPH_BURST_DRAW_HOLD card="+n+" reason=library_critical library="+libraryCardsRemaining());return 0;}
        return hand<=3?4500:2200;
      }
      if(("Big Score".equals(n)||"Unexpected Windfall".equals(n))&&a.isSpell()){
        if(libraryCriticalMode()){if(auditEnabled)System.out.println("RAPH_BURST_DRAW_HOLD card="+n+" reason=library_critical library="+libraryCardsRemaining());return 0;}
        Card d=raphStrategicDiscardChoice(me.getCardsIn(ZoneType.Hand),n);
        if(d==null)return 0;
        if(!raphBattlefield()&&mana<raphCommanderApproxCost())return 4200; // treasures can bridge into commander mana
        return hand<=3?4000:2450;
      }
      if("Kogla and Yidaro".equals(n)&&a.isActivatedAbility())return raphKoglaRecycleUseful()?4550:0;
      if("Aerid Konstrari".equals(n)&&a.isActivatedAbility())return (raphBattlefield()&&mana>=11&&hand<=3)?2300:0;
      if("Mind Stone".equals(n)&&a.isActivatedAbility())return (raphBattlefield()&&hand<=2&&mana>=3)?2550:0;
      if("Etali, Primal Conqueror".equals(n)&&a.isActivatedAbility())return (raphBattlefield()&&mana>=10&&hand<=4)?3050:0;
      if(("Evolving Wilds".equals(n)||"Terramorphic Expanse".equals(n))&&a.isActivatedAbility()){
        if(own&&ph==PhaseType.MAIN1&&hasNamed("Moraug, Fury of Akoum",ZoneType.Battlefield)&&raphReadyToAttack())return 0;
        if(own&&ph==PhaseType.MAIN2&&hasNamed("Moraug, Fury of Akoum",ZoneType.Battlefield)&&raphAttackedThisTurn())return 5900;
        if(hasNamed("Lotus Cobra",ZoneType.Battlefield)||hasNamed("Tireless Provisioner",ZoneType.Battlefield))return 3300;
        return battlefieldLandCount()<=4?2800:1700;
      }
      if("Shattering Spree".equals(n)&&a.isSpell())return raphMeaningfulArtifactTarget()?3900:0;
      if("Abrade".equals(n)&&a.isSpell())return (raphMeaningfulArtifactTarget()||raphAbradeCreatureTarget())?(survivalMode()?4700:3600):0;
      if("Tail Swipe".equals(n)&&a.isSpell())return mowuFightOpportunity("Tail Swipe");
      if("Garruk's Uprising".equals(n)&&a.isSpell())return !raphBattlefield()?4700:(hand<=4?4300:3500);
      if("Terror of the Peaks".equals(n)||"Etali, Primal Conqueror".equals(n)||"Ancient Copper Dragon".equals(n)||"Old Gnawbone".equals(n)||"Goldspan Dragon".equals(n)||"Balefire Dragon".equals(n)||"Port Razer".equals(n)||"Moraug, Fury of Akoum".equals(n))return raphBattlefield()?3900:2700;
      if(INTERACTION.contains(n)&&a.isSpell())return survivalMode()?4700:2100;
      if(a.isActivatedAbility() && "Professional Face-Breaker".equals(n)){
        int treasures=0; try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isToken() && c.getName().toLowerCase(Locale.ROOT).contains("treasure")) treasures++;}catch(Exception ignored){}
        // Exile access is a mana/resource conversion tool, not something to spam into a full hand.
        // Only spend a Treasure here when we are actually short on cards and can use the access this turn.
        return (treasures>raphTreasureReserveTarget() && hand<=3 && own && (ph==PhaseType.MAIN1||ph==PhaseType.MAIN2)) ? 3000 : 0;
      }
      // Never improvise with unfamiliar non-mana activated abilities from Etali/Ragavan/opponent cards.
      // Every non-mana activation in the canonical Raph 100 is scored explicitly above.
      if(raphDeck()&&a.isActivatedAbility())return 0;
      try{ if(a.isSpell()&&a.getHostCard().getType().isCreature())return 2500+Math.min(700,(int)a.getHostCard().getManaCost().getCMC()*60); }catch(Exception ignored){}
      if(raphDeck()&&!RAPH_CARD_PLAN.containsKey(n))return 0;
      return 1600;
    }
    private boolean faldornBattlefield(){ return hasNamed("Faldorn, Dread Wolf Herald",ZoneType.Battlefield); }
    private boolean faldornDoublerOnline(){ return hasNamed("Parallel Lives",ZoneType.Battlefield)||hasNamed("Doubling Season",ZoneType.Battlefield)||hasNamed("Primal Vigor",ZoneType.Battlefield); }
    private static final Set<String> FALDORN_EXILE_ENGINES = new HashSet<>(Arrays.asList("Light Up the Stage","Ignite the Future","Escape to the Wilds","Jeska's Will","Valakut Exploration","Commune with Lava","Professional Face-Breaker","Chandra, Torch of Defiance","Laelia, the Blade Reforged","Stromkirk Occultist"));
    private static final Set<String> FALDORN_DOUBLERS = new HashSet<>(Arrays.asList("Parallel Lives","Doubling Season","Primal Vigor"));
    private static final Set<String> FALDORN_PACK_PAYOFFS = new HashSet<>(Arrays.asList("Shared Animosity","Howlpack Resurgence","Nightpack Ambusher","Immerwolf","Parallel Evolution","Spider-Ham, Peter Porker"));
    private static final Set<String> FALDORN_DISCARD_VALUE = new HashSet<>(Arrays.asList("Anger","Brawn","Arrogant Wurm","Fiery Temper","Avacyn's Judgment","Ancient Grudge","Blazing Rootwalla","Basking Rootwalla","Stromkirk Occultist"));
    private static final Map<String,String> FALDORN_CARD_PLAN = new LinkedHashMap<>();
    static {
      FALDORN_CARD_PLAN.put("Faldorn, Dread Wolf Herald","commander_engine: Deploy before impulse/exile bursts when practical; every cast/play from exile is material; activate with discard-value cards or excess/dead cards, never casually pitch premium engines/protection.");
      FALDORN_CARD_PLAN.put("Anger","graveyard_payoff: Prefer discard to Faldorn/Speaker while a Mountain is or will be online; graveyard haste converts new Wolves and creatures immediately; hard-cast only when discard route is unavailable and body matters.");
      FALDORN_CARD_PLAN.put("Brawn","graveyard_payoff: Prefer discard to Faldorn/Speaker while a Forest is or will be online; graveyard trample is a major wide-board kill converter; hard-cast only when needed as a body.");
      FALDORN_CARD_PLAN.put("Blazing Rootwalla","madness_value: Premium Faldorn/Speaker discard because madness is free; cast for madness/exile to turn discard into body plus Faldorn value; pump only when damage/combat math matters.");
      FALDORN_CARD_PLAN.put("Basking Rootwalla","madness_value: Premium Faldorn/Speaker discard because madness is free; preserve mana unless pump changes combat/lethal; body helps exile/Wolf pressure.");
      FALDORN_CARD_PLAN.put("Fiery Temper","madness_interaction: Prefer madness through Faldorn/Speaker when a meaningful creature or lethal target exists; otherwise hold as cheap interaction/reach, not random damage.");
      FALDORN_CARD_PLAN.put("Arrogant Wurm","madness_value: Prefer discard when 2G is available to convert a discard into a 4/4 trampler; avoid hard-casting for five unless no better line.");
      FALDORN_CARD_PLAN.put("Avacyn's Judgment","madness_interaction: Prefer madness when X can remove meaningful creatures or create lethal reach; set X after reserving required engine mana; do not fire for trivial damage.");
      FALDORN_CARD_PLAN.put("Ancient Grudge","graveyard_interaction: Artifact interaction with built-in discard value; first cast or discard can set up G flashback; prioritize high-impact artifacts and exploit both halves.");
      FALDORN_CARD_PLAN.put("Stromkirk Occultist","madness_exile_engine: Madness is preferred when available; attack when a player connection is realistic because combat damage creates an exile play and therefore a potential Wolf.");
      FALDORN_CARD_PLAN.put("Laelia, the Blade Reforged","exile_attack_engine: Cast early with Faldorn or when pressure is useful; attack when reasonably safe because attack trigger creates exile access and counters grow Laelia from library/graveyard exile events.");
      FALDORN_CARD_PLAN.put("Light Up the Stage","impulse_engine: Prefer spectacle after easy opponent life loss; with Faldorn active use before lower-value development and maximize both exiled plays before expiry.");
      FALDORN_CARD_PLAN.put("Ignite the Future","impulse_engine: Three-card impulse burst; prefer Faldorn active; remember flashback and its free-cast clause as a late-game reload/Wolf burst.");
      FALDORN_CARD_PLAN.put("Escape to the Wilds","impulse_engine: Premium Faldorn burst; cast when enough mana/land-drop capacity exists to exploit the five cards; use the extra land drop deliberately.");
      FALDORN_CARD_PLAN.put("Jeska's Will","impulse_mana_engine: With commander controlled, prefer both modes; choose opponent with largest hand for mana; sequence red mana into exiled cards and Wolf generation.");
      FALDORN_CARD_PLAN.put("Beast Within","interaction: Flexible must-answer removal; reserve for commanders, engines, alt-win pieces, or a blocker/permanent stopping decisive combat; accept the 3/3 only when exchange is worthwhile.");
      FALDORN_CARD_PLAN.put("Heroic Intervention","protection: Hold for sweepers or multi-permanent destroy threats and critical lethal setup; never proactive with an empty threat stack.");
      FALDORN_CARD_PLAN.put("Veil of Summer","protection_stack: Use only when blue/black spell/ability or counter war makes the protection/draw relevant; do not cast merely because stack is nonempty.");
      FALDORN_CARD_PLAN.put("Pyroblast","interaction_stack: Counter a meaningful blue spell or destroy a meaningful blue permanent; preserve for high-impact blue interaction/engine pieces.");
      FALDORN_CARD_PLAN.put("Vandalblast","artifact_sweeper: Use overload when multiple opposing artifacts justify five mana; use one-mana mode only for a must-answer artifact or tempo-critical target.");
      FALDORN_CARD_PLAN.put("Kenrith's Transformation","interaction_value: Turn off dangerous creature/commander while replacing itself; prefer ability-centric creatures where 3/3 body is acceptable.");
      FALDORN_CARD_PLAN.put("Lightning Bolt","interaction_reach: Kill an important <=3-toughness creature or finish a player/planeswalker; do not spend on low-value chip damage.");
      FALDORN_CARD_PLAN.put("Flame Slash","interaction: Efficient sorcery creature removal; use on meaningful <=4-toughness threats or blockers that stop a decisive attack.");
      FALDORN_CARD_PLAN.put("Parallel Lives","token_doubler: Premium asymmetrical Wolf doubler; deploy before exile/Wolf bursts when practical and protect it if it is central to the next lethal turn.");
      FALDORN_CARD_PLAN.put("Doubling Season","token_doubler: Premium Wolf doubler plus Laelia/Chandra counter synergy; prioritize before token production when tempo permits.");
      FALDORN_CARD_PLAN.put("Primal Vigor","token_doubler_symmetric: Powerful Wolf/counter doubler but symmetrical; deploy when our immediate production/counter benefit outweighs opponent benefit.");
      FALDORN_CARD_PLAN.put("Formidable Speaker","tutor_chain: Green-tutor primary target when legal; deploy promptly, discard value card, then tutor the best board-aware creature; activated untap prioritizes Faldorn for a second activation or an aura-enchanted Forest for mana.");
      FALDORN_CARD_PLAN.put("Parallel Evolution","token_copy_burst: Cast when our token count is substantial and opponent copy benefit is acceptable; count doubler multiplication; remember flashback as rebuild/second explosion.");
      FALDORN_CARD_PLAN.put("Howlpack Resurgence","flash_pack_payoff: Prefer last-opponent end step or precombat surprise; +1/+1 and trample convert Wolves into damage, especially with wide boards/Shared Animosity.");
      FALDORN_CARD_PLAN.put("Nightpack Ambusher","flash_pack_engine: Prefer opponent end step; anthem Wolves and deliberately decide whether passing our own turn without spells is worth the end-step Wolf.");
      FALDORN_CARD_PLAN.put("Spider-Ham, Peter Porker","pack_anthem: Cheap body plus Food and +1/+1 to other Wolves/animals; priority rises with existing or expected Wolf production.");
      FALDORN_CARD_PLAN.put("Shared Animosity","combat_finisher: Premium precombat finisher; calculate type-sharing attack bonus before more development and prioritize player elimination/lethal.");
      FALDORN_CARD_PLAN.put("Immerwolf","pack_anthem: Cheap Wolf anthem; deploy when Wolves exist/are imminent and use intimidate body when attack is profitable.");
      FALDORN_CARD_PLAN.put("Worldly Tutor","green_tutor: Formidable Speaker first whenever available; time near a draw when possible; if unavailable, select the creature that best solves current board state.");
      FALDORN_CARD_PLAN.put("Green Sun's Zenith","green_tutor: Formidable Speaker first when X can legally reach a green Speaker; otherwise best legal green creature; set X before legality/payment checks.");
      FALDORN_CARD_PLAN.put("Chord of Calling","green_tutor_flash: Formidable Speaker first when legal; prefer last-opponent end step/convoke to preserve tempo, but use main/combat when it creates immediate decisive value.");
      FALDORN_CARD_PLAN.put("Gamble","red_tutor: Tutor a token doubler first: Parallel Lives, Doubling Season, then Primal Vigor based on castability/board; account for random-discard risk and do not burn it as mana fixing.");
      FALDORN_CARD_PLAN.put("Imperial Recruiter","restricted_tutor: Cannot find enchantment doublers; default to Formidable Speaker if available (power 2), enabling the Speaker chain; otherwise best legal <=2-power utility creature.");
      FALDORN_CARD_PLAN.put("Birds of Paradise","ramp: Early one-mana acceleration/color fixing; avoid throwing away in combat unless survival/lethal requires it.");
      FALDORN_CARD_PLAN.put("Llanowar Elves","ramp: Early one-mana green acceleration; preserve as mana unless expendable late.");
      FALDORN_CARD_PLAN.put("Elvish Mystic","ramp: Early one-mana green acceleration; preserve as mana unless expendable late.");
      FALDORN_CARD_PLAN.put("Fyndhorn Elves","ramp: Early one-mana green acceleration; preserve as mana unless expendable late.");
      FALDORN_CARD_PLAN.put("Delighted Halfling","ramp_legend: Early acceleration; prefer colored uncounterable mana for Faldorn/Laelia/Chandra/other legendary spells when applicable.");
      FALDORN_CARD_PLAN.put("Arbor Elf","ramp_synergy: Early acceleration; untap a tapped Forest, prioritizing one enchanted by Utopia Sprawl/Wild Growth for net mana gain.");
      FALDORN_CARD_PLAN.put("Utopia Sprawl","ramp_aura: Enchant an appropriate Forest, preferably one Arbor Elf can leverage; choose red when red demand is tighter, otherwise green; avoid illegal/non-Forest targets.");
      FALDORN_CARD_PLAN.put("Wild Growth","ramp_aura: Enchant a land early, preferably a Forest that synergizes with Arbor Elf; prioritize untapped/basic land when possible.");
      FALDORN_CARD_PLAN.put("Arcane Signet","ramp: Early two-mana color fixing; deploy before slower setup when mana development is behind.");
      FALDORN_CARD_PLAN.put("Sol Ring","ramp: Highest-priority cheap acceleration; use colorless mana to free colored sources for Faldorn/exile spells.");
      FALDORN_CARD_PLAN.put("Talisman of Impulse","ramp: Two-mana fixing; use colored mode when needed and avoid unnecessary life loss when colorless pays the cost.");
      FALDORN_CARD_PLAN.put("Nature's Lore","ramp_fix: Fetch Stomping Ground when red fixing matters, otherwise a Forest; battlefield untapped Forest typing can enable immediate mana and Brawn/Arbor Elf synergy.");
      FALDORN_CARD_PLAN.put("Three Visits","ramp_fix: Same role as Nature's Lore: fetch legal Forest, favor Stomping Ground for red fixing when needed.");
      FALDORN_CARD_PLAN.put("Toski, Bearer of Secrets","combat_draw: Persistent draw engine; must attack if able, so build attacks to create safe player connections; preserve other engines while maximizing connection count.");
      FALDORN_CARD_PLAN.put("Ohran Frostfang","combat_draw: Deathtouch makes wide attacks profitable and each player connection draws; prioritize when a board exists, preserve from low-value combat, exploit favorable blocks.");
      FALDORN_CARD_PLAN.put("Sylvan Library","selection_draw: Always value the extra look; pay 4 life only when life total and card need justify keeping extras; no Mowu-specific counter/protection criteria.");
      FALDORN_CARD_PLAN.put("Return of the Wildspeaker","draw_or_finisher: Default to draw when hand is low and a non-Human has meaningful power; choose +3/+3 when it creates player elimination, lethal, or a decisive combat swing; can be held as instant-speed draw.");
      FALDORN_CARD_PLAN.put("Rishkar's Expertise","draw_tempo: Cast with a high-power creature and useful <=5 MV follow-up; maximize both draw quantity and free-spell tempo, avoid firing for tiny draws.");
      FALDORN_CARD_PLAN.put("Tamiyo's Safekeeping","protection: Reactive protection for Faldorn/critical engine/permanent; use on real targeted destroy/exile/damage or lethal-combat need, never empty-stack prophylaxis.");
      FALDORN_CARD_PLAN.put("Snakeskin Veil","protection: Reactive hexproof plus counter; save threatened key creature, with counter as bonus rather than reason to fire proactively.");
      FALDORN_CARD_PLAN.put("Gaea's Gift","protection_combat: Reactive hexproof/indestructible; can also use trample/reach/counter when it decisively changes combat, otherwise hold for a real threat.");
      FALDORN_CARD_PLAN.put("Deflecting Swat","stack_redirection: With commander, exploit free cast to redirect meaningful hostile targeted spell/ability; do not fire on harmless/beneficial stack objects.");
      FALDORN_CARD_PLAN.put("Valakut Awakening","hand_repair_mdfc: Play as land when mana development needs it; otherwise bottom genuinely stranded/redundant cards, protect engines/protection/tutors, and turn a weak hand into fresh cards.");
      FALDORN_CARD_PLAN.put("Valakut Exploration","landfall_exile_engine: Deploy before land drops when possible; each landfall creates exile access/Wolf potential; use cards before end-step graveyard conversion and value the end-step damage.");
      FALDORN_CARD_PLAN.put("Commune with Lava","instant_impulse_engine: Prefer last-opponent end step with Faldorn active; choose X from usable mana/card window rather than maximum possible X; untap to exploit exiled cards.");
      FALDORN_CARD_PLAN.put("Professional Face-Breaker","combat_exile_engine: Create Treasures through player connections; use Treasures for mana first when needed, otherwise sacrifice for exile access with Faldorn active and enough time/mana to play the card.");
      FALDORN_CARD_PLAN.put("Chandra, Torch of Defiance","planeswalker_exile_engine: Default +1 exile with Faldorn when usable; +1 mana when it enables a stronger turn; -3 on a meaningful creature; ultimate when available; protect her only when value justifies it.");
      FALDORN_CARD_PLAN.put("Spire Garden","land_dual: Early untapped RG fixing in multiplayer; use as colored source to satisfy Faldorn/red impulse requirements.");
      FALDORN_CARD_PLAN.put("Stomping Ground","land_forest_dual: Premium RG Forest; fetchable by Lore/Visits, enables Brawn/Arbor Elf/Utopia Sprawl; pay 2 life when immediate untapped mana matters.");
      FALDORN_CARD_PLAN.put("Copperline Gorge","land_dual: Prioritize early while it enters untapped; later treat as normal RG source despite tapped entry.");
      FALDORN_CARD_PLAN.put("City of Brass","land_fix: Perfect color fixing; use only when colored mana is needed if equivalent painless source exists, especially at low life.");
      FALDORN_CARD_PLAN.put("Command Tower","land_fix: Best painless RG fixing; preserve flexible color choice for current hand requirements.");
      FALDORN_CARD_PLAN.put("War Room","land_draw: Use as a late mana sink when hand is low and no higher-priority action/protection mana is needed; account for two-life payment in two-color deck.");
      FALDORN_CARD_PLAN.put("Bonders' Enclave","land_draw: Use as late mana sink when legal (power 4+ creature), hand is low, and mana is not needed for stronger Faldorn/exile/protection line.");
      FALDORN_CARD_PLAN.put("Forest","basic_land: Core green source; enables Brawn, Arbor Elf, Utopia Sprawl and fetch/ramp lines.");
      FALDORN_CARD_PLAN.put("Mountain","basic_land: Core red source; enables Anger and red impulse/madness spells.");
    }

    private boolean mowuDeck(){ return hasNamed("Mowu, Loyal Companion",ZoneType.Command,ZoneType.Battlefield,ZoneType.Graveyard,ZoneType.Exile,ZoneType.Hand); }
    private Card mowuBattlefield(){ try{for(Card c:me.getCardsIn(ZoneType.Battlefield))if("Mowu, Loyal Companion".equals(c.getName()))return c;}catch(Exception ignored){}return null; }
    // Mowu protection audit state.  This is deliberately separate from gameplay state so
    // instrumentation cannot manufacture an action; it only records what the pilot saw/did.
    private Card pendingProtectionAuditTarget=null;
    private String pendingProtectionAuditThreat="";
    private String pendingProtectionAuditChosen="";
    private int pendingProtectionAuditTurn=-1;
    private int sylvanDecisionTurn=-1;
    private int sylvanLifePaymentsThisTurn=0;
    private boolean faldornCardCoverageLogged=false;

    private String stackThreatText(SpellAbility top){
      try{return (String.valueOf(top)+" "+(top.getHostCard()==null?"":top.getHostCard().getName())).replace('\n',' ').toLowerCase(Locale.ROOT);}catch(Exception e){return "";}
    }
    private boolean harmfulTargetedStackEffect(SpellAbility top){
      if(top==null||top.getActivatingPlayer()==me)return false;
      boolean targetsOurs=false;
      try{ for(forge.game.GameObject o:top.getTargets()) if(o instanceof Card && ((Card)o).getController()==me){targetsOurs=true;break;} }catch(Exception ignored){}
      if(!targetsOurs)return false;
      try{
        forge.game.ability.ApiType api=top.getApi();
        if(api==forge.game.ability.ApiType.Destroy || api==forge.game.ability.ApiType.DealDamage || api==forge.game.ability.ApiType.Fight || api==forge.game.ability.ApiType.Debuff || api==forge.game.ability.ApiType.ChangeZone) return true;
      }catch(Exception ignored){}
      String d=stackThreatText(top);
      // Text fallback for compound/sub-abilities and cards whose primary API is not the harmful step.
      return d.contains("destroy") || d.contains("exile") ||
             (d.contains("return") && (d.contains("hand")||d.contains("owner"))) ||
             d.contains("damage") || d.contains("-x/-x") || d.contains("gets -") || d.contains("fight") ||
             d.contains("path to exile") || d.contains("swords to plowshares") || d.contains("beast within") ||
             d.contains("pongify") || d.contains("rapid hybridization") || d.contains("generous gift") ||
             d.contains("chaos warp") || d.contains("reality shift") || d.contains("resculpt") ||
             d.contains("infernal grasp") || d.contains("go for the throat") || d.contains("terminate") || d.contains("hero's downfall") || d.contains("murder");
    }
    private Card topStackProtectedTarget(){
      try{ if(me.getGame().getStack().isEmpty())return null; SpellAbility top=me.getGame().getStack().peekAbility(); if(!harmfulTargetedStackEffect(top))return null;
        Card best=null; int bs=Integer.MIN_VALUE;
        for(forge.game.GameObject o:top.getTargets()) if(o instanceof Card){
          Card c=(Card)o; if(c.getController()!=me)continue;
          int sc=impactScore(c.getName())+Math.max(0,c.getNetPower())*3;
          if("Faldorn, Dread Wolf Herald".equals(c.getName()))sc+=12000;
          if("Mowu, Loyal Companion".equals(c.getName()))sc+=10000;
          if("Raph & Mikey, Troublemakers".equals(c.getName()))sc+=15000;
          if("Port Razer".equals(c.getName())||"Terror of the Peaks".equals(c.getName())||"Wulfgar of Icewind Dale".equals(c.getName()))sc+=5000;
          if(sc>bs){bs=sc;best=c;}
        }
        return best;
      }catch(Exception ignored){} return null;
    }
    private boolean indestructibleProtection(String n){ return "Tamiyo's Safekeeping".equals(n)||"Tyvar's Stand".equals(n)||"Gaea's Gift".equals(n)||"Origin of Metalbending".equals(n); }
    private boolean destructiveSweepOnStack(){
      try{ if(me.getGame().getStack().isEmpty())return false; SpellAbility top=me.getGame().getStack().peekAbility(); if(top==null||top.getActivatingPlayer()==me)return false;
        String d=stackThreatText(top);
        return d.contains("destroy all") || d.contains("destroy each") ||
               d.contains("damage to each creature") || d.contains("damage to all creatures") ||
               d.contains("all creatures get -") || d.contains("each creature gets -") ||
               d.contains("blasphemous act") || d.contains("damnation") || d.contains("wrath of god") ||
               d.contains("supreme verdict") || d.contains("no witnesses");
      }catch(Exception ignored){} return false;
    }
    private String protectionInHandAudit(){
      StringBuilder b=new StringBuilder();
      try{for(Card c:me.getCardsIn(ZoneType.Hand))if(PROTECTION.contains(c.getName())){if(b.length()>0)b.append('|');b.append(c.getName());}}catch(Exception ignored){}
      return b.length()==0?"NONE":b.toString();
    }
    private void auditProtectionOpportunity(){
      if(!auditEnabled||!mowuDeck()||me.getGame().getStack().isEmpty())return;
      try{
        SpellAbility top=me.getGame().getStack().peekAbility(); if(top==null||top.getActivatingPlayer()==me)return;
        Card t=topStackProtectedTarget(); boolean sweep=destructiveSweepOnStack();
        if(t==null&&sweep)t=mowuBattlefield();
        String threat=(top.getHostCard()==null?String.valueOf(top):top.getHostCard().getName());
        if(t!=null){
          System.out.println("MOWU_PROTECTION_OPPORTUNITY threat="+threat+" target="+t.getName()+" kind="+(sweep?"destructive_sweep":"harmful_targeted")+" protection_in_hand=["+protectionInHandAudit()+"]");
        } else {
          boolean targetsOurs=false; for(forge.game.GameObject o:top.getTargets())if(o instanceof Card&&((Card)o).getController()==me){targetsOurs=true;break;}
          if(targetsOurs)System.out.println("MOWU_PROTECTION_DECLINE threat="+threat+" reason=not_classified_harmful protection_in_hand=["+protectionInHandAudit()+"]");
        }
      }catch(Exception ignored){}
    }
    private void armProtectionOutcomeAudit(SpellAbility response){
      try{
        if(response==null||response.getHostCard()==null||!PROTECTION.contains(response.getHostCard().getName()))return;
        Card t=topStackProtectedTarget(); if(t==null&&destructiveSweepOnStack())t=mowuBattlefield();
        SpellAbility top=me.getGame().getStack().peekAbility();
        pendingProtectionAuditTarget=t;
        pendingProtectionAuditThreat=top==null?"UNKNOWN":(top.getHostCard()==null?String.valueOf(top):top.getHostCard().getName());
        pendingProtectionAuditChosen=response.getHostCard().getName();
        pendingProtectionAuditTurn=me.getGame().getPhaseHandler().getTurn();
        if(auditEnabled)System.out.println("MOWU_PROTECTION_CAST threat="+pendingProtectionAuditThreat+" target="+(t==null?"UNKNOWN":t.getName())+" chosen="+pendingProtectionAuditChosen);
      }catch(Exception ignored){}
    }
    private void finishProtectionOutcomeAuditIfReady(){
      if(!auditEnabled||pendingProtectionAuditTarget==null)return;
      try{
        if(!me.getGame().getStack().isEmpty())return;
        boolean survived=me.getCardsIn(ZoneType.Battlefield).contains(pendingProtectionAuditTarget);
        System.out.println("MOWU_PROTECTION_OUTCOME threat="+pendingProtectionAuditThreat+" target="+pendingProtectionAuditTarget.getName()+" chosen="+pendingProtectionAuditChosen+" survived_on_battlefield="+survived+" audit_turn="+pendingProtectionAuditTurn+" now_turn="+me.getGame().getPhaseHandler().getTurn());
      }catch(Exception ignored){}
      pendingProtectionAuditTarget=null;pendingProtectionAuditThreat="";pendingProtectionAuditChosen="";pendingProtectionAuditTurn=-1;
    }

    private String mowuSweepKind(){
      try{
        if(me.getGame().getStack().isEmpty()) return "NONE";
        SpellAbility top=me.getGame().getStack().peekAbility();
        if(top==null||top.getActivatingPlayer()==me) return "NONE";
        String d=stackThreatText(top);
        if(d.contains("all creatures get -")||d.contains("each creature gets -")||d.contains("-x/-x")||d.contains("toxic deluge")||d.contains("meathook massacre")||d.contains("golgari charm")) return "SHRINK";
        if(d.contains("damage to each creature")||d.contains("damage to all creatures")||d.contains("blasphemous act")) return "DAMAGE";
        if(d.contains("destroy all")||d.contains("destroy each")||d.contains("damnation")||d.contains("wrath of god")||d.contains("supreme verdict")||d.contains("zombie apocalypse")) return "DESTROY";
      }catch(Exception ignored){}
      return "OTHER";
    }
    private boolean protectionCanAnswerSweep(String n){
      String k=mowuSweepKind();
      if("DESTROY".equals(k)||"DAMAGE".equals(k)) return indestructibleProtection(n);
      if("SHRINK".equals(k)) return "Snakeskin Veil".equals(n)||"Tyvar's Stand".equals(n)||"Gaea's Gift".equals(n)||"Royal Treatment".equals(n)||"Warg Tactics".equals(n);
      return indestructibleProtection(n);
    }
    private int mowuProtectionReserveNeeded(){
      if(!mowuDeck()) return 0;
      boolean valuable=mowuBattlefield()!=null;
      if(!valuable){
        try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature() && impactScore(c.getName())>=900){valuable=true;break;} }catch(Exception ignored){}
      }
      if(!valuable) return 0;
      int best=99;
      try{
        for(Card c:me.getCardsIn(ZoneType.Hand)){
          String n=c.getName(); if(!PROTECTION.contains(n)) continue;
          int need=Math.max(1,cardCmc(c));
          if("Tyvar's Stand".equals(n)) need=1;
          best=Math.min(best,need);
        }
      }catch(Exception ignored){}
      return best==99?0:best;
    }
    private boolean protectedMowuRecastNeedsReserve(SpellAbility sa){
      try{
        if(sa==null||sa.getHostCard()==null||!sa.isSpell()||!"Mowu, Loyal Companion".equals(sa.getHostCard().getName())) return false;
        int casts=mowuCommanderCastCount();
        if(casts<1 || !mowuProtectionInHand()) return false;
        int reserve=99;
        for(Card c:me.getCardsIn(ZoneType.Hand)) if(PROTECTION.contains(c.getName())) reserve=Math.min(reserve,"Gaea's Gift".equals(c.getName())?2:1);
        if(reserve==99) return false;
        int needed=4+(2*casts)+reserve;
        int avail=ComputerUtilMana.getAvailableManaEstimate(me,true);
        boolean hold=avail<needed;
        if(hold&&auditEnabled) System.out.println("MOWU_PROTECTION_RESERVE action=Mowu_recast available="+avail+" commander_casts="+casts+" cast_plus_protection_needed="+needed+" reserve="+reserve+" decision=hold");
        return hold;
      }catch(Exception ignored){return false;}
    }

    private boolean shouldPreserveProtectionMana(SpellAbility sa){
      if(sa==null||sa.getHostCard()==null||!me.getGame().getPhaseHandler().isPlayerTurn(me)||!me.getGame().getStack().isEmpty()) return false;
      String host=sa.getHostCard().getName();
      if(PROTECTION.contains(host)||INTERACTION.contains(host)||raphExtraCombatCard(host)||"Gamble".equals(host)||"Worldly Tutor".equals(host)||"Sylvan Tutor".equals(host)) return false;
      int reserve=raphDeck()?raphProtectionReserveNeeded():mowuProtectionReserveNeeded(); if(reserve<=0) return false;
      int avail=ComputerUtilMana.getAvailableManaEstimate(me,true);
      int spend=sa.isSpell()?Math.max(1,cardCmc(sa.getHostCard())):1;
      boolean block=(avail-spend)<reserve;
      if(block&&auditEnabled) System.out.println((raphDeck()?"RAPH":"MOWU")+"_PROTECTION_RESERVE action="+host+" available="+avail+" estimated_spend="+spend+" reserve="+reserve+" decision=hold");
      return block;
    }
    private SpellAbility chooseForcedProtectionResponse(){
      if(!mowuDeck()||me.getGame().getStack().isEmpty())return null;
      Card threatened=topStackProtectedTarget(); boolean sweep=destructiveSweepOnStack();
      if(pendingProtectionAuditTarget!=null && threatened==pendingProtectionAuditTarget){
        if(auditEnabled) System.out.println("MOWU_PROTECTION_DECLINE threat="+pendingProtectionAuditThreat+" target="+pendingProtectionAuditTarget.getName()+" reason=already_protected_this_threat chosen="+pendingProtectionAuditChosen);
        return null;
      }
      if(threatened==null&&!sweep)return null;
      SpellAbility best=null; int bestScore=Integer.MIN_VALUE;
      try{
        for(Card c:me.getCardsIn(ZoneType.Hand)){
          String n=c.getName(); if(!PROTECTION.contains(n))continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
            if(sa==null||!sa.isSpell())continue;
            // For a sweep, only indestructible-granting protection is useful here.
            if(sweep&&!protectionCanAnswerSweep(n)){
              if(auditEnabled)System.out.println("MOWU_PROTECTION_REJECT card="+n+" reason=sweep_not_answered kind="+mowuSweepKind());
              continue;
            }
            boolean targetOk=true;
            if(SINGLE_TARGET_PROTECTION.contains(n)) targetOk=prepareRequiredTargets(sa);
            if(!targetOk){if(auditEnabled)System.out.println("MOWU_PROTECTION_REJECT card="+n+" reason=no_legal_target");continue;}
            boolean can=false; try{can=sa.canPlay();}catch(Exception ignored){}
            if(!can){if(auditEnabled)System.out.println("MOWU_PROTECTION_REJECT card="+n+" reason=forge_canPlay_false");continue;}
            boolean pay=false; try{pay=fullyPayableStrategicAction(sa);}catch(Exception ignored){}
            if(!pay){if(auditEnabled)System.out.println("MOWU_PROTECTION_REJECT card="+n+" reason=not_payable mana_estimate="+ComputerUtilMana.getAvailableManaEstimate(me,true));continue;}
            int sc=protectionPriority(n);
            if(auditEnabled)System.out.println("MOWU_PROTECTION_CANDIDATE card="+n+" score="+sc+" target="+(threatened==null?"Mowu, Loyal Companion":threatened.getName())+" sweep="+sweep);
            if(sc>bestScore){bestScore=sc;best=sa;}
          }
        }
      }catch(Exception e){if(auditEnabled)System.out.println("MOWU_PROTECTION_REJECT reason=exception detail="+e.getClass().getSimpleName());}
      return bestScore>0?best:null;
    }
    private int mowuCommanderCastCount(){ try{return me.getTotalCommanderCast();}catch(Exception ignored){return 0;} }
    private boolean mowuProtectionInHand(){ try{for(Card c:me.getCardsIn(ZoneType.Hand))if(PROTECTION.contains(c.getName())||"Swiftfoot Boots".equals(c.getName())||"Alpha Authority".equals(c.getName())||"Saryth, the Viper's Fang".equals(c.getName()))return true;}catch(Exception ignored){}return false; }
    private boolean mowuSecondaryThreatInHand(){ try{for(Card c:me.getCardsIn(ZoneType.Hand)){String n=c.getName();if("Kalonian Hydra".equals(n)||"Managorger Hydra".equals(n)||"Defiler of Vigor".equals(n)||"Verdurous Gearhulk".equals(n)||"Bristly Bill, Spine Sower".equals(n))return true;}}catch(Exception ignored){}return false; }
    private int mowuCommanderDamageTo(Player p){
      int n=0; try{for(Map.Entry<Card,Integer> e:p.getCommanderDamage()){Card c=e.getKey();if(c!=null&&"Mowu, Loyal Companion".equals(c.getName()))n+=Math.max(0,e.getValue());}}catch(Exception ignored){} return n;
    }
    private int protectionPriority(String n){
      Card threatened=topStackProtectedTarget();
      if(threatened!=null){ int s=3600; if("Mowu, Loyal Companion".equals(threatened.getName()))s+=650; else s+=Math.min(450,impactScore(threatened.getName())/4); return s; }
      if(destructiveSweepOnStack()&&indestructibleProtection(n)&&mowuBattlefield()!=null) return 3850;
      return 0;
    }
    private Card bestMowuFightSource(SpellAbility sa){
      Card best=null; int bestScore=Integer.MIN_VALUE;
      try{ for(Card c:me.getCardsIn(ZoneType.Battlefield)){ if(!c.getType().isCreature())continue; if(sa!=null&&sa.usesTargeting()&&!sa.canTarget(c))continue; int sc=Math.max(0,c.getNetPower())*12+Math.max(0,c.getNetToughness())*4+impactScore(c.getName()); if("Mowu, Loyal Companion".equals(c.getName()))sc+=500; if(raphDeck()&&"Raph & Mikey, Troublemakers".equals(c.getName()))sc+=900; if(sc>bestScore){bestScore=sc;best=c;} } }catch(Exception ignored){}
      return best;
    }
    private Card bestMowuFightVictim(String host, Card source, Collection<? extends forge.game.GameEntity> opts){
      Card best=null; int bestScore=Integer.MIN_VALUE;
      if(source==null)return null;
      try{ for(forge.game.GameEntity e:opts){ if(!(e instanceof Card))continue; Card c=(Card)e; if(c.getController()==me||!c.getType().isCreature())continue;
          int sc=permanentThreatScore(c); boolean kill=source.getNetPower()>=c.getNetToughness(); boolean survive=!"Tail Swipe".equals(host)||source.getNetToughness()>c.getNetPower();
          if(!kill)sc-=1000; if(!survive)sc-=700; if(MUST_ANSWER_ALT_WIN.contains(c.getName()))sc+=5000; if(sc>bestScore){bestScore=sc;best=c;}
      }}catch(Exception ignored){} return bestScore>0?best:null;
    }
    private int mowuFightOpportunity(String host){
      Card src=bestMowuFightSource(null); if(src==null)return 0;
      List<forge.game.GameEntity> opp=new ArrayList<>(); try{for(Player p:me.getOpponents())for(Card c:p.getCardsIn(ZoneType.Battlefield))opp.add(c);}catch(Exception ignored){}
      Card vic=bestMowuFightVictim(host,src,opp); if(vic==null)return 0; int base=permanentThreatScore(vic); return base>=170?3500:(base>=100?3050:2350);
    }
    private Card bestMowuSupportTarget(SpellAbility sa){
      Card m=mowuBattlefield(); try{if(m!=null&&sa.canTarget(m))return m;}catch(Exception ignored){}
      Card best=null;int bs=Integer.MIN_VALUE;try{for(Card c:me.getCardsIn(ZoneType.Battlefield)){if(!c.getType().isCreature()||!sa.canTarget(c))continue;int sc=c.getNetPower()*8+impactScore(c.getName());if(sc>bs){bs=sc;best=c;}}}catch(Exception ignored){}return best;
    }
    private boolean prepareMowuFightFirstTarget(SpellAbility sa){
      try{
        if(sa==null||!sa.usesTargeting())return true;
        Card src=bestMowuFightSource(sa); if(src==null)return false;
        List<forge.game.GameEntity> opp=new ArrayList<>();
        for(Player p:me.getOpponents()) for(Card c:p.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature()) opp.add(c);
        Card victim=bestMowuFightVictim(sa.getHostCard().getName(),src,opp);
        if(victim==null){ if(auditEnabled)System.out.println("MOWU_FIGHT_REJECT card="+sa.getHostCard().getName()+" reason=no_strategically_valid_victim source="+src.getName()); return false; }
        sa.resetTargets(); sa.getTargets().add(src);
        if(!sa.isTargetNumberValid()){ if(auditEnabled)System.out.println("MOWU_FIGHT_REJECT card="+sa.getHostCard().getName()+" reason=invalid_first_target source="+src.getName()); sa.resetTargets(); return false; }
        if(auditEnabled)System.out.println("MOWU_FIGHT_SOURCE card="+sa.getHostCard().getName()+" source="+src.getName()+" power="+src.getNetPower()+" toughness="+src.getNetToughness()+" planned_victim="+victim.getName());
        return true;
      }catch(Exception e){return false;}
    }
    private boolean prepareMowuSupportTarget(SpellAbility sa){
      try{if(sa==null||!sa.usesTargeting())return true;Card t=bestMowuSupportTarget(sa);if(t==null)return false;sa.resetTargets();sa.getTargets().add(t);if(auditEnabled)System.out.println("MOWU_SUPPORT_TARGET card="+sa.getHostCard().getName()+" target="+t.getName());return sa.isTargetNumberValid();}catch(Exception e){return false;}
    }

    private Card bestThreatTargetFor(SpellAbility sa){
      Card best=null; int bestScore=Integer.MIN_VALUE;
      for(Player p:me.getOpponents()) for(Card c:p.getCardsIn(ZoneType.Battlefield)){
        try{
          if(!sa.canTarget(c)) continue;
          int sc=permanentThreatScore(c) + Math.max(0,playerThreatScore(p)/8);
          if(sc>bestScore){bestScore=sc;best=c;}
        }catch(Exception ignored){}
      }
      return best;
    }

    private Card bestProtectionTargetFor(SpellAbility sa){
      try{
        if(!me.getGame().getStack().isEmpty()){
          Card threatened=topStackProtectedTarget();
          if(threatened!=null && sa.canTarget(threatened)){
            if(auditEnabled) System.out.println("MOWU_PROTECTION_TARGET_LOCK host="+sa.getHostCard().getName()+" target="+threatened.getName()+" reason=best_protection_target");
            return threatened;
          }
        }
      }catch(Exception ignored){}
      Card m=mowuBattlefield();
      try{ if(m!=null && sa.canTarget(m) && destructiveSweepOnStack() && indestructibleProtection(sa.getHostCard().getName())) return m; }catch(Exception ignored){}
      if(raphDeck())try{Card r=raphBattlefieldCard();if(r!=null&&sa.canTarget(r)){if(auditEnabled)System.out.println("RAPH_PROTECTION_TARGET_FALLBACK target=Raph_&_Mikey");return r;}}catch(Exception ignored){}
      Card best=null; int bestScore=Integer.MIN_VALUE;
      for(Card c:me.getCardsIn(ZoneType.Battlefield)){
        try{
          if(!c.isCreature() || !sa.canTarget(c)) continue;
          int sc=impactScore(c.getName()) + Math.max(0,c.getNetPower())*2;
          if(sc>bestScore){bestScore=sc;best=c;}
        }catch(Exception ignored){}
      }
      return best;
    }

    private Card mustAnswerAltWinPermanent(){
      Card best=null; int sc=Integer.MIN_VALUE;
      try{for(Player p:me.getOpponents()) for(Card c:p.getCardsIn(ZoneType.Battlefield)) if(MUST_ANSWER_ALT_WIN.contains(c.getName())){int x=permanentThreatScore(c);if(x>sc){sc=x;best=c;}}}catch(Exception ignored){}
      return best;
    }
    private SpellAbility chooseMustAnswerInteraction(){
      Card threat=mustAnswerAltWinPermanent(); if(threat==null)return null;
      SpellAbility best=null; int bestScore=Integer.MIN_VALUE;
      try{
        for(SpellAbility sa:ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand),me)){
          if(sa==null||sa.getHostCard()==null||!INTERACTION.contains(sa.getHostCard().getName())||!sa.isSpell())continue;
          if(!sa.canPlay()||!fullyPayableStrategicAction(sa))continue;
          if(!sa.usesTargeting()||!sa.canTarget(threat))continue;
          sa.resetTargets();sa.getTargets().add(threat);
          if(!sa.isTargetNumberValid())continue;
          int score=100000-cardCmc(sa.getHostCard())*100;
          if(score>bestScore){bestScore=score;best=sa;}
        }
      }catch(Exception ignored){}
      if(best!=null&&auditEnabled)System.out.println("FARMER_MUST_ANSWER_ALT_WIN threat="+threat.getName()+" action="+best.getHostCard().getName()+" controller="+threat.getController().getName());
      return best;
    }

    private boolean prepareRequiredTargets(SpellAbility sa){
      try{
        if(sa==null || !sa.usesTargeting()) return true;
        String host=sa.getHostCard()==null?"":sa.getHostCard().getName();
        if("Wild Growth".equals(host) || "Utopia Sprawl".equals(host)){
          Card land=preferredAuraRampLand(sa);
          if(land==null){
            if(auditEnabled) System.out.println("FARMER_AURA_RAMP_SKIP card="+host+" reason=no_legal_land_target");
            try{sa.resetTargets();}catch(Exception ignored){} return false;
          }
          if(sa.usesTargeting()){
            sa.resetTargets(); sa.getTargets().add(land);
            if(!sa.isTargetNumberValid()) return false;
          }
          if(auditEnabled) System.out.println("FARMER_AURA_RAMP_TARGET card="+host+" land="+land.getName()+" tapped="+land.isTapped());
          return true;
        }
        if(raphDeck() && "Shattering Spree".equals(host)){
          Card art=raphBestArtifactTargetFor(sa);
          if(art==null){sa.resetTargets();if(auditEnabled)System.out.println("RAPH_SHATTERING_SPREE_TARGET decline=no_artifact");return false;}
          sa.resetTargets();sa.getTargets().add(art);
          if(auditEnabled)System.out.println("RAPH_SHATTERING_SPREE_TARGET target="+art.getName());
          return sa.isTargetNumberValid();
        }
        if(raphDeck() && "Abrade".equals(host)){
          Card target=bestThreatTargetFor(sa);
          if(target==null){sa.resetTargets();if(auditEnabled)System.out.println("RAPH_ABRADE_TARGET decline=no_legal_target");return false;}
          sa.resetTargets();sa.getTargets().add(target);
          if(auditEnabled)System.out.println("RAPH_ABRADE_TARGET target="+target.getName());
          return sa.isTargetNumberValid();
        }
        if(raphDeck() && "Kogla and Yidaro".equals(host) && sa.isActivatedAbility()){
          Card art=raphBestArtifactTargetFor(sa);
          if(art!=null){sa.resetTargets();sa.getTargets().add(art);if(auditEnabled)System.out.println("RAPH_KOGLA_RECYCLE_TARGET target="+art.getName());return sa.isTargetNumberValid();}
          sa.resetTargets();
          boolean ok=sa.isTargetNumberValid();
          if(auditEnabled)System.out.println("RAPH_KOGLA_RECYCLE_TARGET none_optional="+ok);
          return ok;
        }
        if(raphDeck() && SINGLE_TARGET_PROTECTION.contains(host) && me.getGame().getStack().isEmpty()){
          try{sa.resetTargets();}catch(Exception ignored){}
          if(auditEnabled)System.out.println("RAPH_PROTECTION_TARGET decline=empty_stack card="+host);
          return false;
        }
        if(raphDeck() && "Seize the Day".equals(host)){
          String[] order={"Raph & Mikey, Troublemakers","Port Razer","Ancient Copper Dragon","Old Gnawbone","Balefire Dragon","Terror of the Peaks","Wulfgar of Icewind Dale"};
          for(String want:order) for(Card x:me.getCardsIn(ZoneType.Battlefield)) if(want.equals(x.getName()) && sa.canTarget(x)){
            sa.resetTargets(); sa.getTargets().add(x);
            if(sa.isTargetNumberValid()){ if(auditEnabled)System.out.println("RAPH_SEIZE_TARGET target="+x.getName()); return true; }
          }
          for(Card x:me.getCardsIn(ZoneType.Battlefield)) if(x.getType().isCreature() && sa.canTarget(x)){
            sa.resetTargets(); sa.getTargets().add(x);
            if(sa.isTargetNumberValid()){ if(auditEnabled)System.out.println("RAPH_SEIZE_TARGET target="+x.getName()+" reason=fallback_creature"); return true; }
          }
          sa.resetTargets(); if(auditEnabled)System.out.println("RAPH_SEIZE_TARGET decline=no_legal_creature"); return false;
        }
        if(raphDeck() && "Formidable Speaker".equals(host) && sa.isActivatedAbility()){
          String[] order={"Raph & Mikey, Troublemakers","Port Razer","Ancient Copper Dragon","Old Gnawbone","Balefire Dragon","Terror of the Peaks","Wulfgar of Icewind Dale"};
          for(String want:order) for(Card x:me.getCardsIn(ZoneType.Battlefield)) if(want.equals(x.getName()) && x.isTapped() && sa.canTarget(x)){
            sa.resetTargets(); sa.getTargets().add(x);
            if(sa.isTargetNumberValid()){ if(auditEnabled)System.out.println("RAPH_SPEAKER_UNTAP target="+x.getName()+" reason=tapped_priority"); return true; }
          }
          for(Card x:me.getCardsIn(ZoneType.Battlefield)) if(x.isLand() && x.isTapped() && sa.canTarget(x) && (hasAttachedAuraNamed(x,"Utopia Sprawl")||hasAttachedAuraNamed(x,"Wild Growth"))){
            sa.resetTargets(); sa.getTargets().add(x);
            if(sa.isTargetNumberValid()){ if(auditEnabled)System.out.println("RAPH_SPEAKER_UNTAP target="+x.getName()+" reason=aura_mana"); return true; }
          }
          sa.resetTargets(); if(auditEnabled)System.out.println("RAPH_SPEAKER_UNTAP decline=no_useful_tapped_target"); return false;
        }
        if(raphDeck() && "Tibalt's Trickery".equals(host)){
          if(me.getGame().getStack().isEmpty()||!raphMustCounterTopSpell()){sa.resetTargets();return false;}
          SpellAbility top=me.getGame().getStack().peekAbility();
          if(top!=null&&top.getActivatingPlayer()!=me&&sa.canTarget(top)){sa.resetTargets();sa.getTargets().add(top);if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("RAPH_TIBALT_STACK_TARGET threat="+(top.getHostCard()==null?String.valueOf(top):top.getHostCard().getName()));return true;}}
          sa.resetTargets();return false;
        }
        if(raphDeck() && ("Bolt Bend".equals(host)||"Untimely Malfunction".equals(host)) && !me.getGame().getStack().isEmpty()){
          SpellAbility top=me.getGame().getStack().peekAbility();
          if(top!=null&&top.getActivatingPlayer()!=me&&stackThreatensUs()&&sa.canTarget(top)){sa.resetTargets();sa.getTargets().add(top);if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("RAPH_REDIRECT_STACK_TARGET card="+host+" threat="+(top.getHostCard()==null?String.valueOf(top):top.getHostCard().getName()));return true;}}
          sa.resetTargets();return false;
        }
        if(MOWU_FIGHT.contains(host)){ return prepareMowuFightFirstTarget(sa); }
        if(MOWU_TARGETED_SUPPORT.contains(host)){ return prepareMowuSupportTarget(sa); }
        if("Rogue's Passage".equals(host)){ return prepareMowuSupportTarget(sa); }
        if(SINGLE_TARGET_REMOVAL.contains(host)){
          Card target=bestThreatTargetFor(sa);
          if(target==null){
            if(auditEnabled) System.out.println("FARMER_TARGET_SKIP card="+host+" reason=no_valid_threat_target");
            sa.resetTargets(); return false;
          }
          sa.resetTargets(); sa.getTargets().add(target);
          if(auditEnabled) System.out.println("FARMER_TARGET_READY card="+host+" target="+target.getName()+" controller="+target.getController().getName()+" threat_score="+permanentThreatScore(target));
          return sa.isTargetNumberValid();
        }
        if(SINGLE_TARGET_PROTECTION.contains(host)){
          Card target=bestProtectionTargetFor(sa);
          if(target==null){
            if(auditEnabled) System.out.println("FARMER_TARGET_SKIP card="+host+" reason=no_protection_target");
            sa.resetTargets(); return false;
          }
          sa.resetTargets(); sa.getTargets().add(target);
          if(auditEnabled) System.out.println("FARMER_TARGET_READY card="+host+" target="+target.getName()+" protect=true");
          return sa.isTargetNumberValid();
        }
        if(sa.isTargetNumberValid()) return true;
        sa.resetTargets();
        boolean ok=chooseTargetsFor(sa);
        if(!ok || !sa.isTargetNumberValid()){
          if(auditEnabled) System.out.println("FARMER_TARGET_SKIP card="+host+" reason=no_valid_target");
          sa.resetTargets(); return false;
        }
        if(auditEnabled) System.out.println("FARMER_TARGET_READY card="+host+" targets="+sa.getTargets());
        return true;
      }catch(Exception e){
        if(auditEnabled) System.out.println("FARMER_TARGET_SKIP card="+(sa!=null&&sa.getHostCard()!=null?sa.getHostCard().getName():"UNKNOWN")+" reason="+e.getClass().getSimpleName());
        try{ if(sa!=null) sa.resetTargets(); }catch(Exception ignored){}
        return false;
      }
    }

    private boolean nissaTokenEngineOnline(){
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          String n=c.getName();
          if("Skullclamp".equals(n) || "Caretaker's Talent".equals(n) || "Bennie Bracks, Zoologist".equals(n) ||
             "Cathars' Crusade".equals(n) || "Good-Fortune Unicorn".equals(n) || "Aura Shards".equals(n) ||
             "Champion of Lambholt".equals(n) || "Tribute to the World Tree".equals(n) || "Soul Warden".equals(n) ||
             "Essence Warden".equals(n) || "Authority of the Consuls".equals(n) || "Ocelot Pride".equals(n) ||
             "Lathiel, the Bounteous Dawn".equals(n) || "Aerith Gainsborough".equals(n) ||
             "Abzan Battle Priest".equals(n) || "Hardened Scales".equals(n) || "Branching Evolution".equals(n) ||
             "Ozolith, the Shattered Spire".equals(n) || "Innkeeper's Talent".equals(n)) return true;
        }
      }catch(Exception ignored){}
      return false;
    }


    private SpellAbility debugRhysActivation(){
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!"Rhys the Redeemed".equals(c.getName())) continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
            if(auditEnabled) System.out.println("FARMER_RHYS_SCAN ability="+String.valueOf(sa).replace('\n',' ')+" activated="+sa.isActivatedAbility()+" canPlay="+sa.canPlay()+" payable="+fullyPayableStrategicAction(sa)+" tokens="+countThopters());
          }
        }
      }catch(Exception ignored){}
      return null;
    }

    private SpellAbility forcedFaldornActivation(){
      // v1.7: Faldorn is a primary engine, not an optional mana sink. If she is untapped,
      // we have a card to discard, and the activation is otherwise legal/payable, spin her.
      // One-card hands are NOT exempt. Discard selection protects premium cards whenever
      // any alternative exists, but will use the lowest-value last-resort card if necessary.
      if(!faldornBattlefield() || me.getCardsIn(ZoneType.Hand).isEmpty()) return null;
      if(!(neutralFaldornDiscardValueInHand() || neutralFaldornSafeDiscardAvailable())){
        if(auditEnabled) System.out.println("FALDORN_FORCE_ACTIVATE_SKIP reason=no_strategic_discard hand="+me.getCardsIn(ZoneType.Hand).size());
        return null;
      }
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!"Faldorn, Dread Wolf Herald".equals(c.getName())) continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
            if(sa==null || !sa.isActivatedAbility() || !sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
            String d=String.valueOf(sa).toLowerCase(Locale.ROOT);
            // Forge represents Faldorn's discard as a COST, not in the effect text.  Requiring
            // the word "discard" here silently disabled the forced activation path in older builds.
            if(d.contains("exile the top card")){
              if(auditEnabled) System.out.println("FALDORN_FORCE_ACTIVATE hand="+me.getCardsIn(ZoneType.Hand).size()+" mana="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" before_land="+ownTurnMain1());
              return sa;
            }
          }
        }
      }catch(Exception ignored){}
      return null;
    }

    private SpellAbility forcedRhysStarterActivation(){
      try{
        int tokens=countThopters();
        if(tokens>=6) return null;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!"Rhys the Redeemed".equals(c.getName())) continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
            if(sa==null || !sa.isActivatedAbility() || !sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
            String d=String.valueOf(sa);
            if((d.contains("Create a 1/1") || d.contains("Elf Warrior")) && !d.contains("For each creature token")){
              if(auditEnabled) System.out.println("FARMER_RHYS_FORCE_STARTER tokens="+tokens+" creatures="+farmerCreatureCount()+" ability="+d.replace('\n',' '));
              return sa;
            }
          }
        }
      }catch(Exception ignored){}
      return null;
    }

    private SpellAbility forcedRhysPremiumActivation(){
      try{
        int tokens=countThopters();
        if(tokens<3 || tokens>=24) return null;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!"Rhys the Redeemed".equals(c.getName())) continue;
          SpellAbility best=null; int bestScore=0;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
            if(sa==null || !sa.isActivatedAbility() || !sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
            String d=String.valueOf(sa);
            if(!(d.contains("For each creature token") || d.toLowerCase(Locale.ROOT).contains("copy of that creature"))) continue;
            int sc=actionPriority(sa);
            if(sc>bestScore){ bestScore=sc; best=sa; }
          }
          if(best!=null && bestScore>=3000){
            if(auditEnabled) System.out.println("FARMER_RHYS_FORCE_DOUBLE tokens="+tokens+" score="+bestScore+" ability="+String.valueOf(best).replace('\n',' '));
            return best;
          }
        }
      }catch(Exception ignored){}
      return null;
    }

    private SpellAbility forcedNissaTokenActivation(){
      try{
        int creatures=farmerCreatureCount(), tokens=countThopters();
        // Nissa is an engine starter first. If the board is body-starved, or a fresh Plant
        // turns on one of our token/counter/draw/lifegain engines, force the +1 before
        // spending the main-phase budget on generic development. Do not force it once
        // a real creature board exists; then normal scoring may correctly choose the -2.
        boolean urgent = tokens==0 || creatures<=2 || (tokens<=2 && nissaTokenEngineOnline());
        if(!urgent) return null;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!"Nissa, Voice of Zendikar".equals(c.getName())) continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
            if(sa==null || !sa.isActivatedAbility() || !sa.canPlay() || !fullyPayableStrategicAction(sa)) continue;
            String d=String.valueOf(sa);
            if(d.contains("Plant") || d.contains("0/1")){
              if(auditEnabled) System.out.println("FARMER_NISSA_FORCE_PLUS1 creatures="+creatures+" tokens="+tokens+" engine="+nissaTokenEngineOnline());
              return sa;
            }
          }
        }
      }catch(Exception ignored){}
      return null;
    }

    // v6.1: explicit recovery from the Game-4 failure mode.  If multiple real draw
    // engines are stranded in hand, none is active, and mana is available, deploy one
    // before spending the whole turn on another commander recast/development line.
    private SpellAbility chooseMowuStrandedDrawEngine(){
      if(!mowuDeck() || !me.getGame().getPhaseHandler().isPlayerTurn(me) || !me.getGame().getStack().isEmpty()) return null;
      PhaseType ph=me.getGame().getPhaseHandler().getPhase();
      if(ph!=PhaseType.MAIN1 && ph!=PhaseType.MAIN2) return null;
      if(libraryDangerMode()) return null;
      String[] engines={"Guardian Project","Toski, Bearer of Secrets","Beast Whisperer","Sylvan Library","Augur of Autumn","Ohran Frostfang"};
      Set<String> engineSet=new HashSet<>(Arrays.asList(engines));
      int inHand=0; boolean active=false;
      try{
        for(Card c:me.getCardsIn(ZoneType.Hand)) if(engineSet.contains(c.getName())) inHand++;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(engineSet.contains(c.getName())) {active=true;break;}
      }catch(Exception ignored){}
      if(inHand<2 || active) return null;
      SpellAbility best=null; int bestScore=Integer.MIN_VALUE;
      try{
        for(Card c:me.getCardsIn(ZoneType.Hand)){
          if(!engineSet.contains(c.getName())) continue;
          for(SpellAbility sa:c.getSpellAbilities()){
            if(sa==null||!sa.isSpell()||!sa.canPlay()||!fullyPayableStrategicAction(sa)) continue;
            if(shouldPreserveProtectionMana(sa)) continue;
            int sc=drawEnginePriority(c.getName(),false,countThopters(),producerOnline());
            // Prefer engines that immediately reward the current hand/board.
            if("Guardian Project".equals(c.getName()) && nontokenCreaturesInHand()>=2) sc+=500;
            if("Beast Whisperer".equals(c.getName()) && nontokenCreaturesInHand()>=2) sc+=475;
            if("Toski, Bearer of Secrets".equals(c.getName()) && usefulCreatureCount()>=2) sc+=350;
            if(sc>bestScore){bestScore=sc;best=sa;}
          }
        }
      }catch(Exception ignored){}
      if(best!=null && auditEnabled) System.out.println("MOWU_DRAW_ENGINE_RECOVERY action="+best.getHostCard().getName()+" stranded_engines="+inHand+" score="+bestScore+" hand="+me.getCardsIn(ZoneType.Hand).size()+" mana="+ComputerUtilMana.getAvailableManaEstimate(me,true));
      return best;
    }

    private SpellAbility forcedMainPhaseStrategicPlay(){
      SpellAbility best=null; int bestScore=0;
      CardCollection strategicSources=new CardCollection();
      strategicSources.addAll(me.getCardsIn(ZoneType.Hand));
      // v9 fix: commander spells live in the command zone, not the hand. v8 never scanned this zone,
      // so Little Brother literally could not choose Farmer Cotton as a strategic main-phase play.
      strategicSources.addAll(me.getCardsIn(ZoneType.Command));
      // Faldorn: cards we own in exile are real temporary hand. Scan them explicitly so the
      // controller does not let impulse-draw/Faldorn cards expire just because the old harness
      // only searched hand + command zone.
      if(faldornDeck()) strategicSources.addAll(me.getCardsIn(ZoneType.Exile));
      // Rite of Harmony has flashback: treat the graveyard copy as a real second-use opportunity.
      for(Card gc:me.getCardsIn(ZoneType.Graveyard)) if("Rite of Harmony".equals(gc.getName())) strategicSources.add(gc);
      for(Card c:strategicSources){
        boolean faldornExilePlay = faldornBattlefield() && c.isInZone(ZoneType.Exile);
        if(!forceStrategicAbility(c) && !faldornExilePlay) continue;
        try{
          // Command-zone commander casts are exposed by getAllPossibleAbilities(), not reliably by getSpellAbilities().
          Iterable<SpellAbility> strategicAbilities = (c.isInZone(ZoneType.Command) || (faldornDeck() && c.isInZone(ZoneType.Exile))) ? c.getAllPossibleAbilities(me,true) : c.getSpellAbilities();
          if(faldornDeck() && c.isInZone(ZoneType.Exile) && auditEnabled) System.out.println("FALDORN_EXILE_SCAN card="+c.getName()+" abilities="+String.valueOf(c.getAllPossibleAbilities(me,true)).replace('\n',' '));
          for(SpellAbility sa:strategicAbilities){
            if(sa==null || !sa.isSpell() || !fullyPayableStrategicAction(sa)) continue;
            if(protectedMowuRecastNeedsReserve(sa) || shouldPreserveProtectionMana(sa)) continue;
            if((INTERACTION.contains(c.getName()) || PROTECTION.contains(c.getName()) || MOWU_TARGETED_SUPPORT.contains(c.getName()) || "Rogue's Passage".equals(c.getName()) || isAuraRamp(c.getName())) && !prepareRequiredTargets(sa)) continue;
            if(!prepareSamwiseIfUseful(sa)) continue;
            int sc=actionPriority(sa);
            if(sc>bestScore){bestScore=sc;best=sa;}
          }
        }catch(Exception ignored){}
      }
      if(best!=null){ noteRiteChosen(best); if(auditEnabled) System.out.println("FARMER_FORCED_MAIN action="+best.getHostCard().getName()+" score="+bestScore+" x="+(best.getXManaCostPaid()==null?"NA":best.getXManaCostPaid())+" phase="+me.getGame().getPhaseHandler().getPhase()); }
      return best;
    }

    private int farmerBudgetTurn=-1;
    private String farmerBudgetPhase="";
    private int farmerActionsThisPhase=0;
    private String farmerLastStackSig="";

    private void resetFarmerBudgetIfNeeded(){
      int t=me.getGame().getPhaseHandler().getTurn();
      String p=String.valueOf(me.getGame().getPhaseHandler().getPhase());
      if(t!=farmerBudgetTurn || !p.equals(farmerBudgetPhase)){
        farmerBudgetTurn=t; farmerBudgetPhase=p; farmerActionsThisPhase=0; lastNoActionSig=""; farmerLastStackSig="";
      }
    }

    private boolean shouldHoldLandForFelidarFirst(){if(countThopters()>5)return false;try{Card fel=null;for(Card c:me.getCardsIn(ZoneType.Hand))if("Felidar Retreat".equals(c.getName())){fel=c;break;}if(fel==null)return false;CardCollection lands=ComputerUtilAbility.getAvailableLandsToPlay(me.getGame(),me);if(lands==null||lands.isEmpty()||conservativeUntappedManaCapacity()<4)return false;CardCollection one=new CardCollection();one.add(fel);for(SpellAbility sa:ComputerUtilAbility.getSpellAbilities(one,me))if(sa!=null&&sa.isSpell()&&sa.canPlay()&&fullyPayableStrategicAction(sa)){if(auditEnabled)System.out.println("FARMER_FELIDAR_SEQUENCE hold_land=true tokens="+countThopters()+" conservative_mana="+conservativeUntappedManaCapacity());return true;}}catch(Exception ignored){}return false;}

    private SpellAbility chooseFarmerLand(){
      try{
        CardCollection lands=ComputerUtilAbility.getAvailableLandsToPlay(me.getGame(),me);
        if(lands==null || lands.isEmpty()) return null;
        Card best=null;
        // Prefer named utility/fixing lands only when they are legal; otherwise first legal land.
        int bfLands=battlefieldLandCount();
        for(Card c:lands){
          if(best==null) best=c;
          if(bfLands<3 && "Razorverge Thicket".equals(c.getName())) { best=c; break; }
          if("Command Tower".equals(c.getName()) || "City of Brass".equals(c.getName())) best=c;
        }
        if(best==null) return null;
        for(SpellAbility sa:best.getAllPossibleAbilities(me,true)) if(sa.isLandAbility() && sa.canPlay()) return sa;
      }catch(Exception ignored){}
      return null;
    }

    private Card preferredSkullclampTarget(SpellAbility sa){
      Card fallback=null;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c==null || !c.isCreature()) continue;
          if(!sa.canTarget(c)) continue;
          int t=c.getNetToughness();
          String n=c.getName();
          boolean key=n.equals("Champion of Lambholt") || n.equals("Scurry Oak") || n.equals("Herd Baloth") || n.equals("Rosie Cotton of South Lane") || n.equals("Good-Fortune Unicorn") || n.equals("Lathiel, the Bounteous Dawn") || n.equals("Academy Manufactor") || n.equals("Peregrin Took") || n.equals("Samwise Gamgee") || n.equals("Tireless Provisioner") || n.equals("Ohran Frostfang") || n.equals("Bennie Bracks, Zoologist") || n.equals("Haliya, Ascendant Cadet");
          if(c.isToken() && t<=1) return c;
          if(!key && t<=1 && fallback==null) fallback=c;
        }
      }catch(Exception ignored){}
      return fallback;
    }

    private boolean prepareSkullclampIfUseful(SpellAbility sa){
      try{
        if(sa==null || sa.getHostCard()==null || !"Skullclamp".equals(sa.getHostCard().getName()) || !sa.isActivatedAbility()) return true;
        Card target=preferredSkullclampTarget(sa);
        if(target==null){
          if(auditEnabled) System.out.println("FARMER_SKULLCLAMP_SKIP reason=no_sensible_legal_target");
          return false;
        }
        if(sa.usesTargeting()){
          sa.resetTargets();
          sa.getTargets().add(target);
        }
        if(auditEnabled) System.out.println("FARMER_SKULLCLAMP_TARGET target="+target.getName()+" toughness="+target.getNetToughness()+" token="+target.isToken());
        return true;
      }catch(Exception e){ return false; }
    }

    private Card preferredOzolithTarget(SpellAbility sa){
      Card m=mowuBattlefield(); try{if(m!=null&&sa.canTarget(m))return m;}catch(Exception ignored){}
      Card best=null; int bestScore=Integer.MIN_VALUE;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(c==null || !sa.canTarget(c)) continue;
          int sc=0;
          if(c.isCreature()) sc+=100;
          try { sc += Math.max(0,c.getNetPower())*3; } catch(Exception ignored){}
          String n=c.getName();
          if("Champion of Lambholt".equals(n)) sc+=220;
          if("Scurry Oak".equals(n) || "Herd Baloth".equals(n)) sc+=200;
          if("Trelasarra, Moon Dancer".equals(n) || "Lathiel, the Bounteous Dawn".equals(n)) sc+=140;
          if("Rosie Cotton of South Lane".equals(n) || "Good-Fortune Unicorn".equals(n)) sc+=110;
          if(c.isToken()) sc+=20;
          if(sc>bestScore){bestScore=sc;best=c;}
        }
      }catch(Exception ignored){}
      return best;
    }

    private boolean prepareOzolithIfUseful(SpellAbility sa){
      try{
        if(sa==null || sa.getHostCard()==null || !"Ozolith, the Shattered Spire".equals(sa.getHostCard().getName()) || !sa.isActivatedAbility()) return true;
        if(!sa.usesTargeting()) return true;
        Card target=preferredOzolithTarget(sa);
        if(target==null){
          if(auditEnabled) System.out.println("FARMER_OZOLITH_SKIP reason=no_legal_target");
          return false;
        }
        sa.resetTargets(); sa.getTargets().add(target);
        boolean ok=sa.isTargetNumberValid();
        if(auditEnabled) System.out.println("FARMER_OZOLITH_TARGET target="+target.getName()+" valid="+ok+" power="+(target.isCreature()?target.getNetPower():0));
        if(!ok) sa.resetTargets();
        return ok;
      }catch(Exception e){
        if(auditEnabled) System.out.println("FARMER_OZOLITH_SKIP reason="+e.getClass().getSimpleName());
        return false;
      }
    }

    private void noteTutorFollowThrough(SpellAbility sa){
      if(sa==null||sa.getHostCard()==null||pendingTutorTarget.isEmpty()) return;
      String n=sa.getHostCard().getName();
      if(pendingTutorTarget.equals(n)){
        if(auditEnabled) System.out.println("FARMER_TUTOR_FOLLOWTHROUGH tutor="+pendingTutorSource+" target="+pendingTutorTarget+" urgent="+pendingTutorUrgent+" action=cast");
        pendingTutorTarget=""; pendingTutorUrgent=false; pendingTutorSource="";
      }
    }

    private SpellAbility chooseBoundedFarmerAction(boolean stackNonEmpty){
      List<SpellAbility> candidates=new ArrayList<>();
      try{ candidates.addAll(ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand),me)); }catch(Exception ignored){}
      try{ for(Card gc:me.getCardsIn(ZoneType.Graveyard)) if("Rite of Harmony".equals(gc.getName())) candidates.addAll(ComputerUtilAbility.getSpellAbilities(new CardCollection(gc),me)); }catch(Exception ignored){}
      try{
        for(Card c:me.getCardsIn(ZoneType.Command)){
          if(!forceStrategicAbility(c)) continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null && sa.isSpell()) candidates.add(sa);
        }
      }catch(Exception ignored){}
      if(!stackNonEmpty){
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          try{ for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa.isActivatedAbility() && !sa.isManaAbility()) candidates.add(sa); }catch(Exception ignored){}
        }
      }
      SpellAbility best=null; int bestScore=Integer.MIN_VALUE;
      for(SpellAbility sa:candidates){
        try{
          if(sa==null || sa.isLandAbility() || !sa.canPlay()) continue;
          if(!fullyPayableStrategicAction(sa)) continue;
          if(!stackNonEmpty && (protectedMowuRecastNeedsReserve(sa) || shouldPreserveProtectionMana(sa))) continue;
          if((INTERACTION.contains(sa.getHostCard().getName()) || PROTECTION.contains(sa.getHostCard().getName()) || MOWU_TARGETED_SUPPORT.contains(sa.getHostCard().getName()) || "Rogue's Passage".equals(sa.getHostCard().getName()) || isAuraRamp(sa.getHostCard().getName()) || (raphDeck() && "Seize the Day".equals(sa.getHostCard().getName())) || (raphDeck() && "Formidable Speaker".equals(sa.getHostCard().getName()) && sa.isActivatedAbility())) && !prepareRequiredTargets(sa)) continue;
          if(!prepareSamwiseIfUseful(sa)) continue;
          if(!prepareSkullclampIfUseful(sa)) continue;
          if(!prepareOzolithIfUseful(sa)) continue;
          int sc=actionPriority(sa);
          if(auditEnabled && "Rhys the Redeemed".equals(sa.getHostCard().getName()) && sa.isActivatedAbility()) System.out.println("FARMER_RHYS_ABILITY ability="+String.valueOf(sa).replace('\n',' ')+" score="+sc+" tokens="+countThopters());
          // On stack, prefer interaction/protection over development by requiring positive strategic value.
          if(stackNonEmpty && sc<=0) continue;
          if(sc>bestScore){bestScore=sc;best=sa;}
        }catch(Exception ignored){}
      }
      if(bestScore<=0) return null;
      if(best!=null) noteRiteChosen(best);
      if(best!=null && auditEnabled) {
        System.out.println("FARMER_BOUNDED_CHOICE action="+best.getHostCard().getName()+" score="+bestScore+" stack="+stackNonEmpty);
        try{ if(best.getHostCard().getType().isPlaneswalker() && best.isActivatedAbility()) System.out.println("FARMER_PLANESWALKER_CHOICE card="+best.getHostCard().getName()+" ability="+String.valueOf(best).replace('\n',' ')+" creatures="+farmerCreatureCount()+" life="+me.getLife()); }catch(Exception ignored){}
      }
      return best;
    }

    @Override public boolean chooseBinary(SpellAbility sa, String question, forge.game.player.PlayerController.BinaryChoiceType kind, Boolean defaultVal){
      try{
        if(sa!=null && sa.getHostCard()!=null && "Formidable Speaker".equals(sa.getHostCard().getName())){
          boolean yes=raphDeck()?(raphSpeakerHasUsefulTarget()&&raphSpeakerDiscardChoice(me.getCardsIn(ZoneType.Hand))!=null):(neutralFaldornDiscardValueInHand()||neutralFaldornSafeDiscardAvailable());
          if(auditEnabled) System.out.println("FALDORN_SPEAKER_BINARY decision="+(yes?"YES":"NO")+" kind="+kind+" question="+String.valueOf(question).replace(' ','_'));
          return yes;
        }
        if(raphDeck() && sa!=null && sa.getHostCard()!=null && "Pia, Aether Ascetic".equals(sa.getHostCard().getName())){
          boolean yes=raphPiaHasUsefulTarget()&&raphPiaDiscardChoice(me.getCardsIn(ZoneType.Hand))!=null;
          if(auditEnabled)System.out.println("RAPH_PIA_BINARY decision="+(yes?"YES":"NO")+" question="+String.valueOf(question).replace(' ','_'));return yes;
        }
        if(sa!=null && sa.getHostCard()!=null && "Sylvan Library".equals(sa.getHostCard().getName())){
          boolean pay=sylvanShouldPayFour();
          if(auditEnabled) System.out.println("FALDORN_SYLVAN_BINARY life="+me.getLife()+" hand="+me.getCardsIn(ZoneType.Hand).size()+" pay4="+pay+" kind="+kind+" question="+String.valueOf(question).replace(' ','_'));
          return pay;
        }
      }catch(Exception ignored){}
      return super.chooseBinary(sa,question,kind,defaultVal);
    }

    @Override public boolean confirmAction(SpellAbility sa, forge.game.player.PlayerActionConfirmMode mode, String message, List<String> options, Card card, Map<String,Object> params){
      try{
        // v1.4: Commander replacement is not a generic AI choice. Faldorn normally belongs back in
        // the command zone after leaving the battlefield so the engine can be rebuilt.
        String hostName=(sa!=null&&sa.getHostCard()!=null)?sa.getHostCard().getName():"";
        String cardName=card==null?"":card.getName();
        if(raphDeck() && mode==forge.game.player.PlayerActionConfirmMode.ChangeZoneToAltDestination &&
           ("Raph & Mikey, Troublemakers".equals(hostName)||"Raph & Mikey, Troublemakers".equals(cardName))){
          if(auditEnabled) System.out.println("RAPH_MIKEY_COMMAND_ZONE_DECISION decision=COMMAND_ZONE message="+String.valueOf(message).replace(' ','_'));
          return true;
        }
        if(faldornDeck() && mode==forge.game.player.PlayerActionConfirmMode.ChangeZoneToAltDestination &&
           ("Faldorn, Dread Wolf Herald".equals(hostName)||"Faldorn, Dread Wolf Herald".equals(cardName))){
          if(auditEnabled) System.out.println("FALDORN_COMMAND_ZONE_DECISION card=Faldorn decision=COMMAND_ZONE message="+String.valueOf(message).replace(' ','_'));
          return true;
        }
        // Speaker's ETB is optional at the discard gate. Say yes whenever our discard selector can
        // provide a sensible card; chooseCardsForEffect/chooseCardsToDiscardFrom then picks it.
        if((faldornDeck()||raphDeck()) && "Formidable Speaker".equals(hostName)){
          String m=String.valueOf(message).toLowerCase(Locale.ROOT);
          if(m.contains("discard")||m.contains("search your library")||m.contains("search_library")){
            if(auditEnabled) System.out.println("FALDORN_SPEAKER_ETB_CONFIRM decision=YES message="+String.valueOf(message).replace(' ','_'));
            return true;
          }
        }
        if(sa!=null && sa.getHostCard()!=null && "Sylvan Library".equals(sa.getHostCard().getName())){
          boolean pay=sylvanShouldPayFour();
          if(auditEnabled) System.out.println("FALDORN_SYLVAN_CONFIRM life="+me.getLife()+" hand="+me.getCardsIn(ZoneType.Hand).size()+" pay4="+pay+" mode="+mode+" message="+String.valueOf(message).replace(' ','_'));
          return pay;
        }
      }catch(Exception ignored){}
      return super.confirmAction(sa,mode,message,options,card,params);
    }

    private int faldornKeepValue(Card c){
      if(c==null)return 0; String n=c.getName();
      if("Faldorn, Dread Wolf Herald".equals(n)||FALDORN_DOUBLERS.contains(n)||"Shared Animosity".equals(n)||"Sylvan Library".equals(n))return 5000;
      if(TUTORS.contains(n)||PROTECTION.contains(n))return 4300;
      if(FALDORN_EXILE_ENGINES.contains(n)||DRAW_ENGINES.contains(n))return 3600;
      if(FALDORN_DISCARD_VALUE.contains(n))return faldornBattlefield()?1800:2600;
      if(RAMP.contains(n))return battlefieldLandCount()<=4?3200:1200;
      if(c.isLand())return battlefieldLandCount()<4?3500:1300;
      return 2000+Math.max(0,impactScore(n));
    }
    @Override public CardCollectionView chooseCardsForEffect(CardCollectionView options, SpellAbility sa, String title, int min, int max, boolean isOptional, Map<String,Object> params){
      try{
        if(raphDeck() && sa!=null && sa.getHostCard()!=null && options!=null && !options.isEmpty() && "Formidable Speaker".equals(sa.getHostCard().getName())){Card pick=raphSpeakerDiscardChoice(options);if(pick!=null){speakerEtbProcessed.add(sa.getHostCard().getId());CardCollection out=new CardCollection();out.add(pick);if(auditEnabled)System.out.println("RAPH_SPEAKER_EFFECT_DISCARD card="+pick.getName());return out;}}
        if(faldornDeck() && sa!=null && sa.getHostCard()!=null && options!=null && !options.isEmpty()){
          String host=sa.getHostCard().getName();
          String ttl=String.valueOf(title).toLowerCase(Locale.ROOT);
          if(("Faldorn, Dread Wolf Herald".equals(host)||"Formidable Speaker".equals(host)) && (ttl.contains("discard")||min==1)){
            String[] pref={"Anger","Brawn","Arrogant Wurm","Fiery Temper","Avacyn's Judgment","Ancient Grudge","Blazing Rootwalla","Basking Rootwalla","Stromkirk Occultist"};
            for(String want:pref) for(Card c:options) if(want.equals(c.getName())){ CardCollection pick=new CardCollection(); pick.add(c); if(auditEnabled)System.out.println("FALDORN_DISCARD_EFFECT source="+host+" card="+want+" title="+String.valueOf(title).replace(' ','_')); return pick; }
            Card fb=neutralFaldornDiscardChoice(options);
            if(fb!=null){CardCollection pick=new CardCollection();pick.add(fb);if(auditEnabled)System.out.println("FALDORN_DISCARD_EFFECT_TIERED source="+host+" card="+fb.getName()+" tier="+neutralFaldornDiscardTier(fb,options)+" keep_value="+faldornKeepValue(fb));return pick;}
          }
        }
      }catch(Exception ignored){}
      try{
        if(faldornDeck() && sa!=null && sa.getHostCard()!=null && "Valakut Awakening".equals(sa.getHostCard().getName()) && options!=null){
          java.util.ArrayList<Card> list=new java.util.ArrayList<>(); for(Card c:options) list.add(c);
          list.sort((a,b)->Integer.compare(faldornKeepValue(a),faldornKeepValue(b)));
          CardCollection pick=new CardCollection();
          int want=Math.max(min,Math.min(max,Math.max(0,list.size()-3)));
          for(Card c:list){ if(pick.size()>=want)break; if(faldornKeepValue(c)<1700)pick.add(c); }
          if(pick.size()>=min){ if(auditEnabled)System.out.println("FALDORN_VALAKUT_AWAKENING_BOTTOM cards=["+cardNames(pick)+"]"); return pick; }
        }
      }catch(Exception ignored){}
      CardCollectionView out=super.chooseCardsForEffect(options,sa,title,min,max,isOptional,params);
      try{
        if(sa!=null && sa.getHostCard()!=null && "Sylvan Library".equals(sa.getHostCard().getName()) && auditEnabled){
          System.out.println("FALDORN_SYLVAN_CARD_CHOICE title="+String.valueOf(title).replace(' ','_')+" min="+min+" max="+max+" options=["+cardNames(options)+"] chosen=["+cardNames(out)+"] life="+me.getLife());
        }
      }catch(Exception ignored){}
      return out;
    }

    @Override public byte chooseColor(String message, SpellAbility sa, forge.card.ColorSet colors){
      try{
        if((faldornDeck()||raphDeck()) && sa!=null && sa.getHostCard()!=null && "Utopia Sprawl".equals(sa.getHostCard().getName())){
          int red=nearTermColorSources(false), green=nearTermColorSources(true);
          byte pick=(red<=green)?forge.card.MagicColor.RED:forge.card.MagicColor.GREEN;
          if(colors.hasAnyColor(pick)){if(auditEnabled)System.out.println("FALDORN_UTOPIA_COLOR choice="+(pick==forge.card.MagicColor.RED?"R":"G")+" red_sources="+red+" green_sources="+green);return pick;}
        }
      }catch(Exception ignored){}
      return super.chooseColor(message,sa,colors);
    }

    @Override public List<forge.game.spellability.AbilitySub> chooseModeForAbility(SpellAbility sa, List<forge.game.spellability.AbilitySub> choices, int min, int num, boolean allowRepeat){
      try{
        if(sa!=null && sa.getHostCard()!=null && choices!=null && !choices.isEmpty()){
          String host=sa.getHostCard().getName();
          forge.game.spellability.AbilitySub pick=null;
          if(raphDeck() && "Untimely Malfunction".equals(host)){
            boolean redirect=!me.getGame().getStack().isEmpty()&&stackThreatensUs();
            for(forge.game.spellability.AbilitySub x:choices){String d=String.valueOf(x).toLowerCase(Locale.ROOT);if(redirect&&d.contains("change")&&d.contains("target"))pick=x;else if(!redirect&&raphMeaningfulArtifactTarget()&&d.contains("destroy")&&d.contains("artifact"))pick=x;}
            if(pick==null)return Collections.emptyList();
          } else if(raphDeck() && "Kogla and Yidaro".equals(host)){
            boolean safeFight=false;try{for(Player opp:me.getOpponents())for(Card c:opp.getCardsIn(ZoneType.Battlefield))if(c.isCreature()&&c.getNetToughness()<=7&&c.getNetPower()<7&&permanentThreatScore(c)>=35){safeFight=true;break;}}catch(Exception ignored){}
            for(forge.game.spellability.AbilitySub x:choices){String d=String.valueOf(x).toLowerCase(Locale.ROOT);if(safeFight&&d.contains("fight"))pick=x;else if(pick==null&&!safeFight&&(d.contains("trample")||d.contains("haste")))pick=x;}
          } else if(raphDeck() && "Abrade".equals(host)){
            boolean art=raphMeaningfulArtifactTarget();
            for(forge.game.spellability.AbilitySub x:choices){String d=String.valueOf(x).toLowerCase(Locale.ROOT);if(art&&d.contains("artifact"))pick=x;else if(!art&&d.contains("damage")&&d.contains("creature"))pick=x;}
          } else if(raphDeck() && "Tireless Provisioner".equals(host)){
            boolean food=me.getLife()<=8&&survivalMode();
            for(forge.game.spellability.AbilitySub x:choices){String d=String.valueOf(x).toLowerCase(Locale.ROOT);if(food&&d.contains("food"))pick=x;else if(!food&&d.contains("treasure"))pick=x;}
          } else if("Felidar Retreat".equals(host)){
            boolean needBody=farmerCreatureCount()<=2 || countThopters()==0;
            for(forge.game.spellability.AbilitySub x:choices){ String d=String.valueOf(x).toLowerCase(Locale.ROOT); if(needBody && (d.contains("cat")||d.contains("token"))) pick=x; if(!needBody && (d.contains("+1/+1")||d.contains("counter"))) pick=x; }
          } else if("Elder Gargaroth".equals(host)){
            int hand=me.getCardsIn(ZoneType.Hand).size(); boolean urgent=survivalMode();
            for(forge.game.spellability.AbilitySub x:choices){ String d=String.valueOf(x).toLowerCase(Locale.ROOT); if(hand<=4 && d.contains("draw")) pick=x; else if(pick==null && !urgent && (d.contains("beast")||d.contains("token"))) pick=x; else if(pick==null && urgent && d.contains("life")) pick=x; }
          } else if("Restoration Magic".equals(host)){
            boolean sweep=stackThreatensUs() && farmerCreatureCount()>=2;
            for(forge.game.spellability.AbilitySub x:choices){ String d=String.valueOf(x).toLowerCase(Locale.ROOT); if(sweep && d.contains("permanents you control")) pick=x; else if(pick==null && d.contains("target permanent")) pick=x; }
          } else if("Origin of Metalbending".equals(host)){
            boolean defend=stackThreatensUs();
            for(forge.game.spellability.AbilitySub x:choices){ String d=String.valueOf(x).toLowerCase(Locale.ROOT); if(defend && d.contains("indestructible")) pick=x; else if(!defend && d.contains("destroy") && (d.contains("artifact")||d.contains("enchantment"))) pick=x; }
          } else if("Warg Tactics".equals(host)){
            boolean defend=stackThreatensUs();
            for(forge.game.spellability.AbilitySub x:choices){ String d=String.valueOf(x).toLowerCase(Locale.ROOT); if(defend && d.contains("hexproof")) pick=x; else if(!defend && d.contains("flying") && d.contains("destroy")) pick=x; }
          } else if("Jeska's Will".equals(host)){
            forge.game.spellability.AbilitySub manaMode=null,exileMode=null;
            for(forge.game.spellability.AbilitySub x:choices){String d=String.valueOf(x).toLowerCase(Locale.ROOT); if(d.contains("add")&&d.contains("red")) manaMode=x; if(d.contains("exile the top three")) exileMode=x;}
            java.util.ArrayList<forge.game.spellability.AbilitySub> modes=new java.util.ArrayList<>();
            if(num>=2){ if(manaMode!=null)modes.add(manaMode); if(exileMode!=null)modes.add(exileMode); if(modes.size()>=min){if(auditEnabled)System.out.println("FALDORN_JESKAS_WILL_MODE both=true");return modes;} }
            pick=faldornBattlefield()&&exileMode!=null?exileMode:(manaMode!=null?manaMode:exileMode);
          } else if("Return of the Wildspeaker".equals(host)){
            forge.game.spellability.AbilitySub drawMode=null,pumpMode=null;
            for(forge.game.spellability.AbilitySub x:choices){
              String d=String.valueOf(x).toLowerCase(Locale.ROOT);
              if(d.contains("draw") && d.contains("greatest power")) drawMode=x;
              if(d.contains("+3/+3")) pumpMode=x;
            }
            boolean decisivePump=raphDeck()?raphDecisiveWildspeakerPump():neutralFaldornDecisiveWildspeakerPump();
            if(auditEnabled) System.out.println((raphDeck()?"RAPH_RETURN_MODE":"FALDORN_RETURN_MODE")+" decisive_pump="+decisivePump+" hand="+me.getCardsIn(ZoneType.Hand).size()+" default="+(decisivePump?"pump":"draw"));
            pick=decisivePump && pumpMode!=null ? pumpMode : (drawMode!=null?drawMode:pumpMode);
          }
          if(pick!=null){ if(auditEnabled) System.out.println("FARMER_MODE_CHOICE host="+host+" mode="+String.valueOf(pick).replace('\n',' ')); return Collections.singletonList(pick); }
        }
      }catch(Exception ignored){}
      return super.chooseModeForAbility(sa,choices,min,num,allowRepeat);
    }


    private boolean sylvanShouldPayFour(){
      int t=me.getGame().getPhaseHandler().getTurn();
      if(sylvanDecisionTurn!=t){ sylvanDecisionTurn=t; sylvanLifePaymentsThisTurn=0; }
      int life=me.getLife(), hand=me.getCardsIn(ZoneType.Hand).size();
      int need=neutralFaldornHandQualityNeed();
      boolean pay;
      if(sylvanLifePaymentsThisTurn==0) pay = life>=31 || (life>=24 && need>=2) || (life>=20 && hand<=2);
      else pay = life>=37 && need>=3 && hand<=3;
      if(pay && life-4<=10) pay=false;
      if(pay) sylvanLifePaymentsThisTurn++;
      return pay;
    }

    @Override public forge.game.card.CardCollectionView chooseCardsForCost(forge.game.card.CardCollectionView options, SpellAbility sa, forge.game.cost.CostPartWithList cost, int amount, boolean isOptional, String prompt){
      try{
        if(raphDeck() && sa!=null && sa.getHostCard()!=null && cost instanceof forge.game.cost.CostDiscard && options!=null && !options.isEmpty() && amount>0){
          String h=sa.getHostCard().getName(); Card pick=null;
          if("Formidable Speaker".equals(h))pick=raphSpeakerDiscardChoice(options);
          else if("Pia, Aether Ascetic".equals(h))pick=raphPiaDiscardChoice(options);
          else if("Big Score".equals(h)||"Unexpected Windfall".equals(h))pick=raphStrategicDiscardChoice(options,h);
          if(pick!=null){if("Formidable Speaker".equals(h))speakerEtbProcessed.add(sa.getHostCard().getId());if("Pia, Aether Ascetic".equals(h))piaEtbProcessed.add(sa.getHostCard().getId());forge.game.card.CardCollection out=new forge.game.card.CardCollection();out.add(pick);if(auditEnabled)System.out.println("RAPH_CHOOSE_COST_CARD source="+h+" card="+pick.getName()+" prompt="+String.valueOf(prompt).replace(' ','_'));return out;}
        }
        if(faldornDeck() && sa!=null && sa.getHostCard()!=null && ("Formidable Speaker".equals(sa.getHostCard().getName())||"Faldorn, Dread Wolf Herald".equals(sa.getHostCard().getName())) && cost instanceof forge.game.cost.CostDiscard && options!=null && !options.isEmpty() && amount>0){
          Card pick=null;
          String[] pref={"Anger","Brawn","Arrogant Wurm","Fiery Temper","Avacyn's Judgment","Ancient Grudge","Blazing Rootwalla","Basking Rootwalla","Stromkirk Occultist"};
          for(String want:pref){for(Card c:options)if(want.equals(c.getName())){pick=c;break;}if(pick!=null)break;}
          if(pick==null) pick=neutralFaldornDiscardChoice(options);
          if(pick!=null){forge.game.card.CardCollection out=new forge.game.card.CardCollection();out.add(pick);if(auditEnabled)System.out.println("FALDORN_CHOOSE_COST_CARD source="+sa.getHostCard().getName()+" card="+pick.getName()+" protected="+PROTECTION.contains(pick.getName())+" prompt="+String.valueOf(prompt).replace(' ','_'));return out;}
        }
      }catch(Exception e){if(auditEnabled)System.out.println("FALDORN_SPEAKER_CHOOSE_COST_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());}
      return super.chooseCardsForCost(options,sa,cost,amount,isOptional,prompt);
    }

    @Override public forge.game.cost.CostDecisionMakerBase getCostDecisionMaker(Player payer, SpellAbility sa, boolean effect, String description){
      try{
        if(payer==me && raphDeck() && sa!=null && sa.getHostCard()!=null){
          final String h=sa.getHostCard().getName();
          if("Formidable Speaker".equals(h)||"Pia, Aether Ascetic".equals(h)||"Big Score".equals(h)||"Unexpected Windfall".equals(h)){
            return new forge.ai.AiCostDecision(payer,sa,effect){
              @Override public forge.game.cost.PaymentDecision visit(forge.game.cost.CostDiscard cost){
                Card pick="Formidable Speaker".equals(h)?raphSpeakerDiscardChoice(payer.getCardsIn(ZoneType.Hand)):("Pia, Aether Ascetic".equals(h)?raphPiaDiscardChoice(payer.getCardsIn(ZoneType.Hand)):raphStrategicDiscardChoice(payer.getCardsIn(ZoneType.Hand),h));
                if(pick!=null){if("Formidable Speaker".equals(h))speakerEtbProcessed.add(sa.getHostCard().getId());if("Pia, Aether Ascetic".equals(h))piaEtbProcessed.add(sa.getHostCard().getId());if(auditEnabled)System.out.println("RAPH_COST_DISCARD source="+h+" card="+pick.getName());return forge.game.cost.PaymentDecision.card(pick);}return null;
              }
            };
          }
        }
        if(payer==me && faldornDeck() && sa!=null && sa.getHostCard()!=null && ("Formidable Speaker".equals(sa.getHostCard().getName())||"Faldorn, Dread Wolf Herald".equals(sa.getHostCard().getName()))){
          final String costHost=sa.getHostCard().getName();
          return new forge.ai.AiCostDecision(payer,sa,effect){
            @Override public forge.game.cost.PaymentDecision visit(forge.game.cost.CostDiscard cost){
              Card pick=null;
              String[] pref={"Anger","Brawn","Arrogant Wurm","Fiery Temper","Avacyn's Judgment","Ancient Grudge","Blazing Rootwalla","Basking Rootwalla","Stromkirk Occultist"};
              CardCollectionView hand=payer.getCardsIn(ZoneType.Hand);
              for(String want:pref){ for(Card c:hand) if(want.equals(c.getName())){pick=c;break;} if(pick!=null)break; }
              if(pick==null) pick=neutralFaldornDiscardChoice(hand);
              if(pick!=null){if(auditEnabled)System.out.println("FALDORN_COST_DISCARD source="+costHost+" card="+pick.getName()+" protected="+PROTECTION.contains(pick.getName()));return forge.game.cost.PaymentDecision.card(pick);}
              if(auditEnabled)System.out.println("FALDORN_COST_DISCARD source="+costHost+" decline=no_card");
              return null;
            }
          };
        }
      }catch(Exception e){if(auditEnabled)System.out.println("FALDORN_SPEAKER_COST_DECISION_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());}
      return super.getCostDecisionMaker(payer,sa,effect,description);
    }

    @Override public boolean confirmPayment(forge.game.cost.CostPart cost, String question, SpellAbility sa){
      try{
        if(raphDeck() && sa!=null && sa.getHostCard()!=null){
          String h=sa.getHostCard().getName();
          if("Formidable Speaker".equals(h)){boolean pay=raphSpeakerHasUsefulTarget()&&raphSpeakerDiscardChoice(me.getCardsIn(ZoneType.Hand))!=null;if(auditEnabled)System.out.println("RAPH_SPEAKER_PAYMENT pay="+pay);return pay;}
          if("Pia, Aether Ascetic".equals(h)){boolean pay=raphPiaHasUsefulTarget()&&raphPiaDiscardChoice(me.getCardsIn(ZoneType.Hand))!=null;if(auditEnabled)System.out.println("RAPH_PIA_PAYMENT pay="+pay);return pay;}
        }
        if(faldornDeck() && sa!=null && sa.getHostCard()!=null && "Formidable Speaker".equals(sa.getHostCard().getName())){
          boolean pay=neutralFaldornDiscardValueInHand()||neutralFaldornSafeDiscardAvailable();
          if(auditEnabled) System.out.println("FALDORN_SPEAKER_ETB_PAYMENT decision="+(pay?"PAY_DISCARD":"DECLINE")+" question="+String.valueOf(question).replace(' ','_'));
          return pay;
        }
        if(sa!=null && sa.getHostCard()!=null && "Animation Module".equals(sa.getHostCard().getName())){
          boolean pay=ComputerUtilMana.getAvailableManaEstimate(me,true)>=1;
          if(auditEnabled) System.out.println("FARMER_ANIMATION_PAYMENT pay="+pay+" mana="+ComputerUtilMana.getAvailableManaEstimate(me,true));
          return pay;
        }
        if(sa!=null && sa.getHostCard()!=null && "Sylvan Library".equals(sa.getHostCard().getName())){
          boolean pay=sylvanShouldPayFour();
          if(auditEnabled) System.out.println("MOWU_SYLVAN_LIFE_PAYMENT route=confirmPayment life="+me.getLife()+" hand="+me.getCardsIn(ZoneType.Hand).size()+" pay4="+pay+" question="+question.replace(' ', '_'));
          return pay;
        }
      }catch(Exception ignored){}
      return super.confirmPayment(cost,question,sa);
    }

    @Override public boolean confirmTrigger(WrappedAbility wa){
      try{
        Card host=wa==null?null:wa.getHostCard();
        if(host!=null && "Animation Module".equals(host.getName())) return ComputerUtilMana.getAvailableManaEstimate(me,true)>=1;
        if(host!=null && "Sylvan Library".equals(host.getName())){ if(auditEnabled)System.out.println("FALDORN_SYLVAN_TRIGGER life="+me.getLife()+" hand="+me.getCardsIn(ZoneType.Hand).size()+" decision=draw_two"); return true; }
        if(host!=null && "Forgotten Ancient".equals(host.getName())) return true;
        if(host!=null && "Avenger of Zendikar".equals(host.getName())) return true;
        if(host!=null && ("Herd Baloth".equals(host.getName()) || "Scurry Oak".equals(host.getName())) && wa.isOptionalTrigger()){
          int toks=countThopters();
          // Herd Baloth + Cathars' Crusade / similar counter-on-ETB can form an optional
          // unbounded token loop.  Take enough iterations for an overwhelming/lethal
          // board, then decline the optional trigger so Forge can continue the game.
          if(toks>=24){
            if(auditEnabled) System.out.println("FARMER_LOOP_CAP card="+host.getName()+" creature_tokens="+toks+" decision=decline");
            return false;
          }
        }
      }catch(Exception ignored){}
      return super.confirmTrigger(wa);
    }

    private boolean overwhelmingCombatBoard(){
      try{
        int attackablePower=0; int liveOpp=0; int minLife=Integer.MAX_VALUE;
        for(Player p:me.getOpponents()) if(!p.hasLost()){ liveOpp++; minLife=Math.min(minLife,p.getLife()); }
        if(liveOpp==0) return false;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isCreature() && c.isUntapped()) attackablePower+=Math.max(0,c.getNetPower());
        return countThopters()>=24 || attackablePower>=Math.max(30,minLife*2);
      }catch(Exception ignored){ return false; }
    }


    private void neutralFaldornCoverageAudit(){
      if(faldornCardCoverageLogged) return;
      faldornCardCoverageLogged=true;
      java.util.LinkedHashSet<String> seen=new java.util.LinkedHashSet<>();
      java.util.ArrayList<String> missing=new java.util.ArrayList<>();
      try{
        ZoneType[] zones={ZoneType.Command,ZoneType.Library,ZoneType.Hand,ZoneType.Battlefield,ZoneType.Graveyard,ZoneType.Exile};
        for(ZoneType z:zones) for(Card c:me.getCardsIn(z)) seen.add(c.getName());
        for(String n:seen) if(!"Commander Effect".equals(n) && !FALDORN_CARD_PLAN.containsKey(n)) missing.add(n);
      }catch(Exception ignored){}
      if(auditEnabled) System.out.println("FALDORN_CARD_COVERAGE unique_seen="+seen.size()+" plan_entries="+FALDORN_CARD_PLAN.size()+" missing="+missing);
      if(!missing.isEmpty()) throw new IllegalStateException("Faldorn strategy coverage missing: "+missing);
    }
    private boolean neutralFaldornCombatWindow(){
      try{
        if(!me.getGame().getPhaseHandler().isPlayerTurn(me)) return false;
        PhaseType p=me.getGame().getPhaseHandler().getPhase();
        return p==PhaseType.COMBAT_BEGIN || p==PhaseType.COMBAT_DECLARE_ATTACKERS || p==PhaseType.COMBAT_DECLARE_BLOCKERS;
      }catch(Exception ignored){return false;}
    }
    private boolean neutralFaldornLibraryHasDoubler(){
      try{for(Card c:me.getCardsIn(ZoneType.Library)) if(FALDORN_DOUBLERS.contains(c.getName())) return true;}catch(Exception ignored){}
      return false;
    }
    private boolean neutralFaldornHasMountain(){
      try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isLand() && ("Mountain".equals(c.getName())||"Stomping Ground".equals(c.getName()))) return true;}catch(Exception ignored){}
      return false;
    }
    private int neutralFaldornHandQualityNeed(){
      int hand=me.getCardsIn(ZoneType.Hand).size();
      int score=hand<=2?3:(hand<=4?2:0);
      if(!faldornBattlefield()) score++;
      if(!neutralFaldornHasExileBurstInHand()) score++;
      return score;
    }
    private boolean neutralFaldornOpponentCastBlueBlack(){
      try{
        if(me.getGame().getStack().isEmpty()) return false;
        SpellAbility top=me.getGame().getStack().peekAbility();
        if(top==null||top.getActivatingPlayer()==me||top.getHostCard()==null) return false;
        Card h=top.getHostCard();
        return h.isBlue()||h.isBlack();
      }catch(Exception ignored){return false;}
    }
    private boolean neutralFaldornHostileTargetedStack(){
      try{
        if(me.getGame().getStack().isEmpty()) return false;
        SpellAbility top=me.getGame().getStack().peekAbility();
        return harmfulTargetedStackEffect(top);
      }catch(Exception ignored){return false;}
    }
    private boolean neutralFaldornVeilCanAnswer(SpellAbility top){
      if(top==null||top.getActivatingPlayer()==me||top.getHostCard()==null) return false;
      Card h=top.getHostCard();
      if(!(h.isBlue()||h.isBlack())) return false;
      // Veil matters when a blue/black opponent spell or ability is interacting with us or our permanents.
      if(harmfulTargetedStackEffect(top) || destructiveSweepOnStack()) return true;
      try{for(forge.game.GameObject o:top.getTargets()) if(o==me || (o instanceof Card && ((Card)o).getController()==me)) return true;}catch(Exception ignored){}
      String d=stackThreatText(top); return d.contains("counter target") || d.contains("discard") || d.contains("target player");
    }
    private boolean neutralFaldornSwatCanAnswer(SpellAbility top){
      if(top==null||top.getActivatingPlayer()==me) return false;
      // Swat is reserved for hostile targeting of us, our permanents, or our spells/abilities.
      // Explicit ownership/controller checks avoid false positives from opponent self-targeted value abilities.
      try{
        if(!top.usesTargeting() || top.getTargets()==null || top.getTargets().isEmpty()) return false;
        for(forge.game.GameObject o:top.getTargets()){
          if(o==me) return true;
          if(o instanceof Card && ((Card)o).getController()==me) return true;
          if(o instanceof SpellAbility && ((SpellAbility)o).getActivatingPlayer()==me) return true;
        }
      }catch(Exception ignored){}
      return false;
    }
    private boolean neutralFaldornPyroblastCanAnswer(SpellAbility top){
      if(top==null||top.getActivatingPlayer()==me||top.getHostCard()==null) return false;
      try{return top.getHostCard().isBlue();}catch(Exception ignored){return false;}
    }
    private boolean neutralFaldornProtectionCanAnswer(String n, SpellAbility top){
      if(top==null||top.getActivatingPlayer()==me) return false;
      boolean targeted=harmfulTargetedStackEffect(top);
      boolean sweep=destructiveSweepOnStack();
      String d=stackThreatText(top);
      boolean exileOrBounce=d.contains("exile") || (d.contains("return") && (d.contains("hand")||d.contains("owner")));
      boolean destroyOrDamage=d.contains("destroy")||d.contains("damage")||d.contains("fight");
      boolean shrink=d.contains("-x/-x")||d.contains("gets -")||d.contains("all creatures get -")||d.contains("each creature gets -");
      if("Heroic Intervention".equals(n)) return sweep ? (destroyOrDamage || d.contains("destroy all") || d.contains("destroy each")) : targeted;
      if("Tamiyo's Safekeeping".equals(n)) return targeted || (sweep && (destroyOrDamage || d.contains("destroy all")||d.contains("destroy each")));
      if("Gaea's Gift".equals(n)) return targeted || (sweep && (destroyOrDamage || d.contains("destroy all")||d.contains("destroy each"))) || shrink;
      if("Snakeskin Veil".equals(n)) return targeted && !sweep; // hexproof stops targeted removal; +1 counter may also save small damage/shrink.
      // Future/legacy targeted shields remain conservative rather than firing on arbitrary stack objects.
      return targeted && !sweep && !exileOrBounce;
    }
    private void auditFaldornProtectionOpportunity(SpellAbility top){
      if(!auditEnabled||top==null||top.getActivatingPlayer()==me) return;
      try{
        Card target=topStackProtectedTarget(); boolean sweep=destructiveSweepOnStack();
        String threat=top.getHostCard()==null?String.valueOf(top):top.getHostCard().getName();
        if(target!=null||sweep){
          StringBuilder legal=new StringBuilder();
          for(Card c:me.getCardsIn(ZoneType.Hand)){
            String n=c.getName(); if(!PROTECTION.contains(n) && !"Pyroblast".equals(n))continue;
            boolean ok="Veil of Summer".equals(n)?neutralFaldornVeilCanAnswer(top):"Deflecting Swat".equals(n)?neutralFaldornSwatCanAnswer(top):"Pyroblast".equals(n)?neutralFaldornPyroblastCanAnswer(top):neutralFaldornProtectionCanAnswer(n,top);
            if(ok){if(legal.length()>0)legal.append('|');legal.append(n);}
          }
          System.out.println("FALDORN_PROTECTION_OPPORTUNITY threat="+threat+" target="+(target==null?"BOARD":target.getName())+" sweep="+sweep+" legal_answers=["+(legal.length()==0?"NONE":legal.toString())+"] in_hand=["+protectionInHandAudit()+"]");
        }
      }catch(Exception ignored){}
    }
    private int neutralFaldornLikelyAttackers(){
      int n=0; try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isCreature() && !c.isTapped()) n++;}catch(Exception ignored){} return n;
    }
    private int neutralFaldornGreatestPower(){
      int p=0; try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isCreature())p=Math.max(p,Math.max(0,c.getNetPower()));}catch(Exception ignored){} return p;
    }
    private int neutralFaldornGreatestNonHumanPower(){
      int p=0; try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isCreature()&&!c.getType().hasSubtype("Human"))p=Math.max(p,Math.max(0,c.getNetPower()));}catch(Exception ignored){} return p;
    }
    private int neutralFaldornOwnCreatureTokenCount(){ return countThopters(); }
    private int neutralFaldornOpponentCreatureTokenCount(){
      int n=0; try{for(Player p:me.getOpponents()) if(!p.hasLost()) for(Card c:p.getCardsIn(ZoneType.Battlefield)) if(c.isToken()&&c.isCreature()) n++;}catch(Exception ignored){} return n;
    }
    private boolean neutralFaldornTappedFaldornCanReuse(){
      try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) if("Faldorn, Dread Wolf Herald".equals(c.getName())&&c.isTapped()) return me.getCardsIn(ZoneType.Hand).size()>=2 && ComputerUtilMana.getAvailableManaEstimate(me,true)>=2;}catch(Exception ignored){} return false;
    }
    private boolean raphDecisiveWildspeakerPump(){
      try{
        if(!neutralFaldornCombatWindow())return false;
        // Locked rule: DRAW by default. Pump only when +3/+3 converts the current combat into
        // an elimination/lethal or a truly decisive survival swing. Creatures put in attacking by
        // Raph still receive the static pump if they are non-Human, but they do not get attack triggers.
        for(Player opp:me.getOpponents()) if(!opp.hasLost()){
          int base=0, nonHumans=0;
          for(Card c:me.getCardsIn(ZoneType.Battlefield)){
            if(!c.isCreature()||c.isTapped())continue;
            if(!forge.game.combat.CombatUtil.canAttack(c,opp))continue;
            base+=Math.max(0,c.getNetPower());
            if(!c.getType().hasSubtype("Human"))nonHumans++;
          }
          int pumped=base+3*nonHumans;
          if(pumped>=opp.getLife())return true;
          // Critical survival/elimination threshold: only use pump if it adds a very large swing
          // and leaves the opponent effectively dead to trivial follow-up.
          if(nonHumans>=4 && pumped-base>=12 && opp.getLife()-pumped<=3)return true;
        }
      }catch(Exception ignored){}
      return false;
    }

    private boolean neutralFaldornDecisiveWildspeakerPump(){
      try{
        if(!neutralFaldornCombatWindow())return false;
        for(Player opp:me.getOpponents()) if(!opp.hasLost()){
          int base=neutralFaldornEstimatedAttackPower(opp,true), nonHumans=0;
          for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isCreature()&&!c.getType().hasSubtype("Human")&&forge.game.combat.CombatUtil.canAttack(c,opp)) nonHumans++;
          int pumped=base+nonHumans*3;
          if(pumped>=opp.getLife() || (pumped-base>=9 && pumped>=Math.max(12,opp.getLife()/2))) return true;
        }
      }catch(Exception ignored){}
      return false;
    }
    private int neutralFaldornExplicitScore(SpellAbility sa){
      if(sa==null||sa.getHostCard()==null) return Integer.MIN_VALUE;
      Card c=sa.getHostCard(); String n=c.getName(); String d=String.valueOf(sa.getDescription()).toLowerCase(Locale.ROOT);
      int tok=countThopters(); int hand=me.getCardsIn(ZoneType.Hand).size(); int mana=ComputerUtilMana.getAvailableManaEstimate(me,true);
      boolean combat=neutralFaldornCombatWindow();
      if("Anger".equals(n)) return (faldornBattlefield()||hasNamed("Formidable Speaker",ZoneType.Hand,ZoneType.Battlefield))?250:1250;
      if("Brawn".equals(n)) return (faldornBattlefield()||hasNamed("Formidable Speaker",ZoneType.Hand,ZoneType.Battlefield))?250:1250;
      if("Blazing Rootwalla".equals(n)||"Basking Rootwalla".equals(n)){
        if(sa.isActivatedAbility()) return combat?2400:-500;
        return faldornBattlefield()?600:1350;
      }
      if("Arrogant Wurm".equals(n) && sa.isSpell() && c.isInZone(ZoneType.Hand)) return faldornBattlefield()?550:1500;
      if("Stromkirk Occultist".equals(n) && sa.isSpell() && c.isInZone(ZoneType.Hand)) return faldornBattlefield()?2600:2100;
      if("Imperial Recruiter".equals(n) && sa.isSpell()) return 4300;
      if("Parallel Evolution".equals(n) && sa.isSpell()){
        int ours=neutralFaldornOwnCreatureTokenCount(), theirs=neutralFaldornOpponentCreatureTokenCount();
        if(ours<2) return 300;
        return 3200 + ours*260 - theirs*90 + (faldornDoublerOnline()?700:0);
      }
      if("Valakut Awakening".equals(n) && sa.isSpell()) return hand>=5?3200:(hand<=2?1100:2250);
      if("War Room".equals(n) && sa.isActivatedAbility()) return (hand<=3&&mana>=4&&me.getLife()>8)?3150:-400;
      if("Bonders' Enclave".equals(n) && sa.isActivatedAbility()) return (hand<=3&&mana>=4)?3200:-400;
      if("Professional Face-Breaker".equals(n) && sa.isActivatedAbility()) return faldornBattlefield()&&mana>=1?4100:(hand<=3?2500:1200);
      if("Formidable Speaker".equals(n) && sa.isActivatedAbility()) return neutralFaldornTappedFaldornCanReuse()?4450:(mana>=2?1800:300);
      if("Chandra, Torch of Defiance".equals(n) && sa.isActivatedAbility()){
        if(d.contains("exile the top card")) return faldornBattlefield()?4700:3600;
        if(d.contains("add {r}{r}")) return mana<=4?3500:2100;
        if(d.contains("4 damage") && d.contains("creature")) return neutralFaldornInteractionScore(sa);
        if(d.contains("emblem")) return 6500;
      }
      if("Vandalblast".equals(n) && sa.isSpell()){
        String alt=String.valueOf(sa.getAlternativeCost()).toLowerCase(Locale.ROOT);
        boolean overload=alt.contains("overload")||String.valueOf(sa.getPayCosts()).contains("4 R");
        int arts=neutralFaldornOpponentArtifactCount();
        if(overload) return arts>=3?5200:(arts==2?3400:500);
      }
      if("Gamble".equals(n) && sa.isSpell() && !neutralFaldornLibraryHasDoubler()) return -700;
      if("Commune with Lava".equals(n) && sa.isSpell() && neutralFaldornOpponentEndStep()) return 5800;
      if("Howlpack Resurgence".equals(n) && sa.isSpell() && combat && tok>=2) return 5300;
      if("Return of the Wildspeaker".equals(n) && sa.isSpell() && combat) return (raphDeck()?raphDecisiveWildspeakerPump():neutralFaldornDecisiveWildspeakerPump())?5000:900;
      return Integer.MIN_VALUE;
    }

    private boolean neutralFaldornDiscardValueInHand(){
      try{for(Card c:me.getCardsIn(ZoneType.Hand)) if(FALDORN_DISCARD_VALUE.contains(c.getName())) return true;}catch(Exception ignored){}
      return false;
    }
    private boolean neutralFaldornHasExileBurstInHand(){
      try{for(Card c:me.getCardsIn(ZoneType.Hand)) if(FALDORN_EXILE_ENGINES.contains(c.getName())) return true;}catch(Exception ignored){}
      return false;
    }
    private boolean neutralFaldornHardProtectedDiscard(Card c){
      if(c==null) return true;
      String n=c.getName();
      if(PROTECTION.contains(n)||TUTORS.contains(n)||FALDORN_DOUBLERS.contains(n)) return true;
      if("Shared Animosity".equals(n)||"Sylvan Library".equals(n)||"Formidable Speaker".equals(n)) return true;
      // Repeatable exile infrastructure is worth more than a blind spin.
      if("Valakut Exploration".equals(n)||"Laelia, the Blade Reforged".equals(n)||
         "Professional Face-Breaker".equals(n)||"Chandra, Torch of Defiance".equals(n)) return true;
      // Parallel Evolution is a proven ceiling card once Wolves exist; preserve it on a live board.
      if("Parallel Evolution".equals(n) && countThopters()>=3) return true;
      return false;
    }

    private int neutralFaldornDiscardTier(Card c, CardCollectionView pool){
      if(c==null||neutralFaldornHardProtectedDiscard(c)) return 99;
      String n=c.getName();
      if(FALDORN_DISCARD_VALUE.contains(n)) return 0; // madness / graveyard-value fuel
      int lands=0; try{for(Card x:pool)if(x.isLand())lands++;}catch(Exception ignored){}
      // Preserve the current land drop.  Two-plus lands in hand means one is genuine excess fuel.
      if(c.isLand()){
        if(lands>=3) return 1;
        if(lands>=2 && battlefieldLandCount()>=4) return 1;
        return 8;
      }
      // Late one-mana acceleration is expendable once mana is developed.
      if(("Llanowar Elves".equals(n)||"Elvish Mystic".equals(n)||"Fyndhorn Elves".equals(n)||
          "Arbor Elf".equals(n)||"Birds of Paradise".equals(n)||"Delighted Halfling".equals(n)||
          "Wild Growth".equals(n)||"Utopia Sprawl".equals(n)) && battlefieldLandCount()>=5) return 1;
      if(("Nature's Lore".equals(n)||"Three Visits".equals(n)||"Arcane Signet".equals(n)||
          "Talisman of Impulse".equals(n)||"Sol Ring".equals(n)) && battlefieldLandCount()>=6) return 2;
      // Removal with no meaningful current target can fuel the commander before we skip a whole spin.
      if(("Lightning Bolt".equals(n)||"Flame Slash".equals(n)||"Kenrith's Transformation".equals(n)||
          "Beast Within".equals(n)||"Vandalblast".equals(n)) && topThreatScore()<180) return 2;
      // Parallel Evolution has flashback.  With no meaningful token board, discarding it is future value.
      if("Parallel Evolution".equals(n) && countThopters()<3) return 2;
      // Medium pack bodies/payoffs are expendable before we let Faldorn sit idle.
      if("Spider-Ham, Peter Porker".equals(n)||"Immerwolf".equals(n)) return countThopters()>=2?4:2;
      if("Howlpack Resurgence".equals(n)||"Nightpack Ambusher".equals(n)) return countThopters()>=3?5:3;
      // One-shot impulse cards are normally fuel generators themselves, so preserve them unless hand is rich.
      if("Light Up the Stage".equals(n)||"Ignite the Future".equals(n)||"Escape to the Wilds".equals(n)||
         "Jeska's Will".equals(n)||"Commune with Lava".equals(n)) return me.getCardsIn(ZoneType.Hand).size()>=6?5:8;
      // Draw engines remain valuable, but a redundant/awkward expensive copy is preferable to skipping many spins.
      if(DRAW_ENGINES.contains(n)) return 7;
      // Ordinary non-premium cards are legitimate Faldorn fuel.  This is the key v1.8 change.
      return 4 + Math.min(3,Math.max(0,faldornKeepValue(c)-2000)/900);
    }

    private Card neutralFaldornDiscardChoice(CardCollectionView pool){
      if(pool==null||pool.isEmpty()) return null;
      Card best=null; int bestTier=99, bestKeep=Integer.MAX_VALUE;
      for(Card c:pool){
        int tier=neutralFaldornDiscardTier(c,pool);
        int keep=faldornKeepValue(c);
        if(tier<bestTier || (tier==bestTier && keep<bestKeep)){ best=c; bestTier=tier; bestKeep=keep; }
      }
      // Tier 8 means "preserve unless there is no engine choice"; v1.8 still refuses truly bad blind spins.
      if(best==null||bestTier>=8) return null;
      return best;
    }

    private boolean neutralFaldornSafeDiscardAvailable(){
      try{return neutralFaldornDiscardChoice(me.getCardsIn(ZoneType.Hand))!=null;}catch(Exception ignored){return false;}
    }
    private boolean neutralFaldornCanCastNamedFromHand(String name){
      try{
        for(Card c:me.getCardsIn(ZoneType.Hand)) if(name.equals(c.getName()))
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null&&sa.isSpell()&&sa.canPlay()&&ComputerUtilMana.canPayManaCost(sa,me,0,false)) return true;
      }catch(Exception ignored){}
      return false;
    }
    private boolean neutralFaldornHoldLandForValakut(){
      if(!me.getGame().getPhaseHandler().isPlayerTurn(me) || me.getGame().getPhaseHandler().getPhase()!=PhaseType.MAIN1) return false;
      if(hasNamed("Valakut Exploration",ZoneType.Battlefield)) return false;
      boolean hold=neutralFaldornCanCastNamedFromHand("Valakut Exploration");
      if(hold && auditEnabled) System.out.println("FALDORN_LAND_SEQUENCE hold_for=Valakut_Exploration");
      return hold;
    }
    private boolean neutralFaldornCanEnableSpectacleThisCombat(){
      try{
        if(me.getGame().getPhaseHandler().getPhase()!=PhaseType.MAIN1) return false;
        for(Player opp:me.getOpponents()) if(!opp.hasLost())
          for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.isCreature()&&forge.game.combat.CombatUtil.canAttack(c,opp) && c.getNetPower()>0) return true;
      }catch(Exception ignored){}
      return false;
    }
    private int neutralFaldornUsableImpulseCapacity(){
      // Roughly how much follow-through exists after resolving an impulse burst. Higher capacity means
      // more of the exiled cards can become Wolves instead of expiring unused.
      int mana=ComputerUtilMana.getAvailableManaEstimate(me,true), cap=0;
      if(mana>=2) cap++; if(mana>=4) cap++; if(mana>=6) cap++;
      if(battlefieldLandCount()<=5) cap++;
      if(hasNamed("Sol Ring",ZoneType.Battlefield)||hasNamed("Arcane Signet",ZoneType.Battlefield)||hasNamed("Talisman of Impulse",ZoneType.Battlefield)) cap++;
      return cap;
    }
    private boolean neutralFaldornOpponentEndStep(){
      try{
        forge.game.phase.PhaseHandler h=me.getGame().getPhaseHandler();
        return h.getPhase()==PhaseType.END_OF_TURN && !h.isPlayerTurn(me) && h.getNextTurn()==me && me.getGame().getStack().isEmpty();
      }catch(Exception ignored){return false;}
    }
    private int neutralFaldornOpponentArtifactCount(){
      int n=0; try{for(Player p:me.getOpponents()) if(!p.hasLost()) for(Card c:p.getCardsIn(ZoneType.Battlefield)) if(c.getType().isArtifact()) n++;}catch(Exception ignored){}
      return n;
    }
    private Card neutralFaldornInteractionTarget(SpellAbility sa){
      Card best=null; int bs=Integer.MIN_VALUE;
      try{
        for(Player p:me.getOpponents()) if(!p.hasLost()) for(Card c:p.getCardsIn(ZoneType.Battlefield)){
          if(!sa.canTarget(c)) continue;
          int sc=permanentThreatScore(c);
          String n=c.getName();
          if("Winota, Joiner of Forces".equals(n)||"Chiss-Goria, Forge Tyrant".equals(n)||"Deadpool, Trading Card".equals(n)||"Betor, Ancestor's Voice".equals(n)||"Shroofus Sproutsire".equals(n)) sc+=55;
          if("Smothering Tithe".equals(n)||"Black Market Connections".equals(n)||"Sylvan Library".equals(n)||"Parallel Lives".equals(n)||"Doubling Season".equals(n)||"Primal Vigor".equals(n)) sc+=55;
          sc+=Math.max(0,playerThreatScore(p)/10);
          if(sc>bs){bs=sc;best=c;}
        }
      }catch(Exception ignored){}
      if(best==null) return null;
      int threshold=105;
      try{
        if(me.getLife()<=18 || topThreatScore()>=210) threshold=85;
        if(MUST_ANSWER_ALT_WIN.contains(best.getName())) threshold=0;
      }catch(Exception ignored){}
      return bs>=threshold?best:null;
    }
    private int neutralFaldornInteractionScore(SpellAbility sa){
      if(sa==null||sa.getHostCard()==null) return -900;
      String n=sa.getHostCard().getName();
      if("Vandalblast".equals(n) && neutralFaldornOpponentArtifactCount()>=4) return 4850;
      Card t=neutralFaldornInteractionTarget(sa);
      if(t==null) return -900;
      int sc=3850+Math.min(900,permanentThreatScore(t)*4);
      if(auditEnabled) System.out.println("FALDORN_NEUTRAL_INTERACTION_CANDIDATE action="+n+" target="+t.getName()+" threat_score="+permanentThreatScore(t)+" score="+sc);
      return sc;
    }
    private boolean neutralFaldornBrawnLive(){
      try{
        boolean forest=false;
        for(Card c:me.getCardsIn(ZoneType.Battlefield)) if(c.getType().isLand() && ("Forest".equals(c.getName())||"Stomping Ground".equals(c.getName()))){forest=true;break;}
        return forest && hasNamed("Brawn",ZoneType.Graveyard);
      }catch(Exception ignored){return false;}
    }
    private int neutralFaldornEstimatedAttackPower(Player target, boolean includeEngines){
      int base=0,attackers=0;
      try{
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!c.isCreature()||!forge.game.combat.CombatUtil.canAttack(c,target)) continue;
          if(!includeEngines && neutralFaldornEngineCreature(c) && !"Toski, Bearer of Secrets".equals(c.getName())) continue;
          attackers++; base+=Math.max(0,c.getNetPower());
        }
        int wolves=Math.min(countThopters(),attackers);
        if(hasNamed("Shared Animosity",ZoneType.Battlefield) && wolves>=2) base += wolves*(wolves-1);
      }catch(Exception ignored){}
      return base;
    }
    private boolean ownTurnMain1(){ try{return me.getGame().getPhaseHandler().isPlayerTurn(me)&&me.getGame().getPhaseHandler().getPhase()==PhaseType.MAIN1;}catch(Exception e){return false;} }
    private int neutralFaldornScore(SpellAbility sa, boolean stackNonEmpty){
      if(sa==null||sa.getHostCard()==null) return Integer.MIN_VALUE;
      Card c=sa.getHostCard(); String n=c.getName(); int tok=countThopters(); int mana=ComputerUtilMana.getAvailableManaEstimate(me,true); boolean oppEnd=neutralFaldornOpponentEndStep();
      try{
        if(c.isInZone(ZoneType.Exile) && sa.isSpell()){
          // v1.5: exile is temporary hand, but never burn reactive protection purely for a Wolf.
          // Protection still uses the live-stack card-aware rules below.
          if(PROTECTION.contains(n) || "Pyroblast".equals(n)) return -1700;
          return faldornBattlefield()?6200:2600;
        }
      }catch(Exception ignored){}
      int explicit=neutralFaldornExplicitScore(sa); if(explicit!=Integer.MIN_VALUE) return explicit;
      if(oppEnd){
        if("Commune with Lava".equals(n) && sa.isSpell()) return faldornBattlefield()?5750:3900;
        if("Chord of Calling".equals(n) && sa.isSpell()) return 5550;
        if("Nightpack Ambusher".equals(n) && sa.isSpell()) return 5200;
        if("Howlpack Resurgence".equals(n) && sa.isSpell()) return tok>=2?5050:3600;
        if("Faldorn, Dread Wolf Herald".equals(n) && sa.isActivatedAbility()) return neutralFaldornDiscardValueInHand()?5000:(neutralFaldornSafeDiscardAvailable()?4550:-1800);
      }
      if(stackNonEmpty){
        SpellAbility top=null; try{top=me.getGame().getStack().peekAbility();}catch(Exception ignored){}
        // v1.5: protection is card-aware.  A protection card scores highly ONLY when that exact
        // effect can answer the live threat.  This prevents Veil/Swat/hexproof from being treated
        // as interchangeable generic shields.
        if("Veil of Summer".equals(n)) return neutralFaldornVeilCanAnswer(top)?6200:-1800;
        if("Deflecting Swat".equals(n)) return neutralFaldornSwatCanAnswer(top)?6250:-1800;
        if("Pyroblast".equals(n)) return neutralFaldornPyroblastCanAnswer(top)?6150:-1800;
        if(PROTECTION.contains(n)) return neutralFaldornProtectionCanAnswer(n,top)?6300:-1800;
        return -1200;
      }
      if("Faldorn, Dread Wolf Herald".equals(n) && sa.isSpell()) return neutralFaldornHasExileBurstInHand()?5400:4800;
      if("Faldorn, Dread Wolf Herald".equals(n) && sa.isActivatedAbility()){
        if(neutralFaldornDiscardValueInHand()) return 5000;
        if(neutralFaldornSafeDiscardAvailable()) return 4550;
        return -1800;
      }
      if(FALDORN_DOUBLERS.contains(n) && sa.isSpell()) return faldornBattlefield()?5000:3450;
      if(("Worldly Tutor".equals(n)||"Green Sun's Zenith".equals(n)||"Chord of Calling".equals(n)) && sa.isSpell()) return 4700;
      if("Imperial Recruiter".equals(n) && sa.isSpell()) return 4300;
      if("Gamble".equals(n) && sa.isSpell()) return 4450;
      if("Formidable Speaker".equals(n) && sa.isSpell()) return 4250;
      if(FALDORN_EXILE_ENGINES.contains(n)){
        // Repeatable exile engines are infrastructure: deploy them before one-shot bursts and before
        // the land/combat event that turns them on. One-shot impulse effects care about follow-through.
        if("Commune with Lava".equals(n) && sa.isSpell() && !oppEnd) return 500;
        if("Light Up the Stage".equals(n) && sa.isSpell() && ownTurnMain1() && neutralFaldornCanEnableSpectacleThisCombat()) return 700;
        if("Valakut Exploration".equals(n) && sa.isSpell()) return faldornBattlefield()?5350:4150;
        if("Laelia, the Blade Reforged".equals(n) && sa.isSpell()) return faldornBattlefield()?5200:4050;
        if("Professional Face-Breaker".equals(n) && sa.isSpell()) return faldornBattlefield()?5150:4000;
        if("Stromkirk Occultist".equals(n) && sa.isSpell()) return faldornBattlefield()?4850:3650;
        if(faldornBattlefield()){
          int cap=neutralFaldornUsableImpulseCapacity();
          int base=4600 + Math.min(3,cap)*180;
          if("Escape to the Wilds".equals(n)) base += cap>=3?350:-300;
          if("Ignite the Future".equals(n)) base += cap>=2?225:-200;
          if("Jeska's Will".equals(n)) base += 300;
          return sa.isSpell()?base:4450;
        }
        return 650; // hold one-shot impulse effects for Faldorn when practical
      }
      if("Shared Animosity".equals(n)) return tok>=3?4550:(tok>=1?3000:1200);
      if("Nightpack Ambusher".equals(n) && sa.isSpell() && !oppEnd) return tok>=5?2550:800;
      if("Howlpack Resurgence".equals(n) && sa.isSpell() && !oppEnd) return tok>=5?2750:700;
      if("Chord of Calling".equals(n) && sa.isSpell() && !oppEnd){
        // Still allow main-phase Chord when the board is developed enough that Speaker/another creature can matter immediately.
        return tok>=5?3600:1450;
      }
      if(FALDORN_PACK_PAYOFFS.contains(n)) return tok>=3?4050:(tok>=1?3150:2100);
      if("Sylvan Library".equals(n) && sa.isSpell()) return battlefieldLandCount()<=5?4550:3500;
      if("Toski, Bearer of Secrets".equals(n) && sa.isSpell()){
        int attackers=neutralFaldornLikelyAttackers();
        return attackers>=3?4450:(attackers>=1?3650:2700);
      }
      if("Ohran Frostfang".equals(n) && sa.isSpell()){
        int attackers=neutralFaldornLikelyAttackers();
        return attackers>=3?4500:(attackers>=1?3700:2750);
      }
      if("Return of the Wildspeaker".equals(n) && sa.isSpell()){
        if(raphDeck()?raphDecisiveWildspeakerPump():neutralFaldornDecisiveWildspeakerPump()) return 5000;
        int power=neutralFaldornGreatestNonHumanPower();
        return (power>=5&&me.getCardsIn(ZoneType.Hand).size()<=5)?4550:(power>=3&&me.getCardsIn(ZoneType.Hand).size()<=3?3900:1450);
      }
      if("Rishkar's Expertise".equals(n) && sa.isSpell()){
        int power=neutralFaldornGreatestPower();
        return (power>=6&&me.getCardsIn(ZoneType.Hand).size()<=5)?4700:(power>=4&&me.getCardsIn(ZoneType.Hand).size()<=3?4100:1300);
      }
      if(RAMP.contains(n)){
        int lands=battlefieldLandCount();
        return lands<=4?3650:1850;
      }
      if(PROTECTION.contains(n)) return -1400; // reserve strictly; reactive use is scored only on a real stack threat
      if(INTERACTION.contains(n)) return neutralFaldornInteractionScore(sa);
      try{
        int sc=900 + c.getCMC()*35;
        if(c.getType().isCreature()) sc+=350;
        if(c.getType().isPlaneswalker()) sc+=250;
        if(sa.isActivatedAbility()) sc+=100;
        if(mana<=3 && c.getCMC()<=2) sc+=300;
        return sc;
      }catch(Exception ignored){return 700;}
    }
    private boolean hasAttachedAuraNamed(Card land,String aura){
      try{for(Card a:me.getCardsIn(ZoneType.Battlefield)) if(aura.equals(a.getName()) && a.getEnchantingCard()==land) return true;}catch(Exception ignored){}
      return false;
    }
    private boolean prepareNeutralFaldornTargets(SpellAbility sa){
      try{
        if(sa==null || !sa.usesTargeting()) return true;
        if(sa.isTargetNumberValid()) return true;
        sa.resetTargets();
        if(sa.getHostCard()!=null){
          String hn=sa.getHostCard().getName();
          // v1.6: Deflecting Swat targets the hostile spell/ability itself, not the permanent
          // being threatened.  Treating it like creature-targeted protection made the free response
          // disappear exactly when Faldorn was tapped out after deployment.
          if("Deflecting Swat".equals(hn)){
            SpellAbility top=null; try{top=me.getGame().getStack().peekAbility();}catch(Exception ignored){}
            if(top==null || !neutralFaldornSwatCanAnswer(top)) return false;
            // Forge's ChangeTargets AI encodes the target by placing the SpellAbility itself into
            // TargetChoices.  Do the same directly; generic card-target selection cannot represent it.
            sa.resetTargets();
            sa.getTargets().add(top);
            if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_SWAT_STACK_TARGET threat="+(top.getHostCard()==null?String.valueOf(top):top.getHostCard().getName()));return true;}
            try{sa.resetTargets();}catch(Exception ignored){}
            return false;
          }
          // Pyroblast's counter mode likewise targets the blue spell on the stack.
          if("Pyroblast".equals(hn) && !me.getGame().getStack().isEmpty()){
            SpellAbility top=null; try{top=me.getGame().getStack().peekAbility();}catch(Exception ignored){}
            if(top!=null && top.getActivatingPlayer()!=me && top.getHostCard()!=null && top.getHostCard().isBlue() && sa.canTarget(top)){
              sa.getTargets().add(top);
              if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_PYROBLAST_STACK_TARGET threat="+top.getHostCard().getName());return true;}
              sa.resetTargets();
            }
          }
          if(PROTECTION.contains(hn)){
            Card lock=topStackProtectedTarget();
            if(lock==null && destructiveSweepOnStack() && indestructibleProtection(hn)){
              for(Card x:me.getCardsIn(ZoneType.Battlefield)) if("Faldorn, Dread Wolf Herald".equals(x.getName()) && sa.canTarget(x)){lock=x;break;}
            }
            if(lock!=null && sa.canTarget(lock)){
              sa.getTargets().add(lock);
              if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_PROTECTION_TARGET action="+hn+" target="+lock.getName()+" sweep="+destructiveSweepOnStack());return true;}
              sa.resetTargets();
            }
            // A targeted protection spell with no relevant own target is not allowed to fall through
            // to generic AI target selection. Non-targeting protection (Heroic Intervention/Veil)
            // returned above before this block because usesTargeting()==false.
            return false;
          }
          if("Lightning Bolt".equals(hn)||"Fiery Temper".equals(hn)){
            for(Player p:me.getOpponents()) if(!p.hasLost() && p.getLife()<=3 && sa.canTarget(p)){sa.getTargets().add(p); if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_REACH_TARGET action="+hn+" player="+p.getName()+" life="+p.getLife());return true;} sa.resetTargets();}
          }
          if("Formidable Speaker".equals(hn) && sa.isActivatedAbility()){
            Card fallback=null;
            for(Card x:me.getCardsIn(ZoneType.Battlefield)){
              if(!sa.canTarget(x)||x==sa.getHostCard()) continue;
              if("Faldorn, Dread Wolf Herald".equals(x.getName())&&x.isTapped()&&neutralFaldornTappedFaldornCanReuse()){sa.getTargets().add(x);if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_SPEAKER_UNTAP target=Faldorn");return true;}sa.resetTargets();}
              if(x.isLand()&&x.isTapped()&&(hasAttachedAuraNamed(x,"Utopia Sprawl")||hasAttachedAuraNamed(x,"Wild Growth"))) fallback=x;
            }
            if(fallback!=null){sa.getTargets().add(fallback);if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_SPEAKER_UNTAP target="+fallback.getName()+" reason=aura_mana");return true;}sa.resetTargets();}
            if(auditEnabled)System.out.println("FALDORN_SPEAKER_UNTAP decline=no_useful_own_target");
            return false;
          }
          if(("Utopia Sprawl".equals(hn)||"Wild Growth".equals(hn)) && sa.isSpell()){
            Card best=null;
            for(Card x:me.getCardsIn(ZoneType.Battlefield)) if(x.isLand()&&sa.canTarget(x)){
              if(best==null)best=x;
              if(x.isUntapped() && "Forest".equals(x.getName())){best=x;break;}
              if(x.isUntapped() && x.getType().hasSubtype("Forest"))best=x;
            }
            if(best!=null){sa.getTargets().add(best);if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_AURA_RAMP_TARGET aura="+hn+" land="+best.getName());return true;}sa.resetTargets();}
          }
          if("Jeska's Will".equals(hn)){
            Player best=null; int cards=-1; for(Player opp:me.getOpponents())if(!opp.hasLost()&&sa.canTarget(opp)){int h=opp.getCardsIn(ZoneType.Hand).size();if(h>cards){cards=h;best=opp;}}
            if(best!=null){sa.getTargets().add(best);if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_JESKAS_WILL_TARGET opponent="+best.getName()+" hand="+cards);return true;}sa.resetTargets();}
          }
          if("Arbor Elf".equals(hn) && sa.isActivatedAbility()){
            Card best=null;
            for(Card x:me.getCardsIn(ZoneType.Battlefield)) if(sa.canTarget(x)&&x.isTapped()){
              if(hasAttachedAuraNamed(x,"Utopia Sprawl")||hasAttachedAuraNamed(x,"Wild Growth")){best=x;break;} if(best==null)best=x;
            }
            if(best!=null){sa.getTargets().add(best);if(sa.isTargetNumberValid()){if(auditEnabled)System.out.println("FALDORN_ARBOR_UNTAP target="+best.getName());return true;}sa.resetTargets();}
          }
        }
        if(sa.getHostCard()!=null && INTERACTION.contains(sa.getHostCard().getName())){
          Card it=neutralFaldornInteractionTarget(sa);
          if(it!=null && sa.canTarget(it)){
            sa.getTargets().add(it);
            if(sa.isTargetNumberValid()){
              if(auditEnabled) System.out.println("FALDORN_NEUTRAL_INTERACTION_TARGET action="+sa.getHostCard().getName()+" target="+it.getName()+" threat_score="+permanentThreatScore(it));
              return true;
            }
            sa.resetTargets();
          }
        }
        boolean ok=chooseTargetsFor(sa);
        if(!ok || !sa.isTargetNumberValid()){sa.resetTargets();return false;}
        return true;
      }catch(Exception e){try{sa.resetTargets();}catch(Exception ignored){}return false;}
    }
    private List<SpellAbility> neutralFaldornCandidates(boolean stackNonEmpty){
      List<SpellAbility> out=new ArrayList<>();
      try{out.addAll(ComputerUtilAbility.getSpellAbilities(me.getCardsIn(ZoneType.Hand),me));}catch(Exception ignored){}
      try{for(Card c:me.getCardsIn(ZoneType.Command)) for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null&&sa.isSpell()) out.add(sa);}catch(Exception ignored){}
      try{for(Card c:me.getCardsIn(ZoneType.Exile)) for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null&&sa.isSpell()) out.add(sa);}catch(Exception ignored){}
      try{for(Card c:me.getCardsIn(ZoneType.Graveyard)) if("Ignite the Future".equals(c.getName())||"Parallel Evolution".equals(c.getName())||"Ancient Grudge".equals(c.getName())) for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null&&sa.isSpell()) out.add(sa);}catch(Exception ignored){}
      if(!stackNonEmpty){
        try{for(Card c:me.getCardsIn(ZoneType.Battlefield)) for(SpellAbility sa:c.getAllPossibleAbilities(me,true)) if(sa!=null&&sa.isActivatedAbility()&&!sa.isManaAbility()) out.add(sa);}catch(Exception ignored){}
      }
      return out;
    }
    private List<SpellAbility> chooseNeutralFaldornAction(){
      boolean stackNonEmpty=!me.getGame().getStack().isEmpty();
      boolean ownTurn=me.getGame().getPhaseHandler().isPlayerTurn(me);
      PhaseType ph=me.getGame().getPhaseHandler().getPhase();
      if(stackNonEmpty){
        SpellAbility top=me.getGame().getStack().peekAbility();
        if(top!=null && top.getActivatingPlayer()==me) return null;
        auditFaldornProtectionOpportunity(top);
      } else if(!ownTurn || (ph!=PhaseType.MAIN1 && ph!=PhaseType.MAIN2)) { if(!neutralFaldornOpponentEndStep() && !neutralFaldornCombatWindow()) return null; }
      String sig="NF:"+actionSig()+":"+me.getGame().getStack().size();
      if(sig.equals(lastNoActionSig)) return null;
      // v1.8 activation-opportunity telemetry: exactly once per own turn, record whether
      // Faldorn was available and what the tiered discard hierarchy considered fuel.
      if(!stackNonEmpty && ownTurn && ph==PhaseType.MAIN1 && faldornBattlefield()){
        int at=me.getGame().getPhaseHandler().getTurn();
        if(lastFaldornActivationOpportunityTurn!=at){
          lastFaldornActivationOpportunityTurn=at;
          Card fc=null; try{for(Card z:me.getCardsIn(ZoneType.Battlefield))if("Faldorn, Dread Wolf Herald".equals(z.getName())){fc=z;break;}}catch(Exception ignored){}
          Card dc=neutralFaldornDiscardChoice(me.getCardsIn(ZoneType.Hand));
          int mana=-1; try{mana=ComputerUtilMana.getAvailableManaEstimate(me,true);}catch(Exception ignored){}
          if(auditEnabled) System.out.println("FALDORN_ACTIVATION_OPPORTUNITY turn="+at+" untapped="+(fc!=null&&!fc.isTapped())+" mana_est="+mana+" hand="+me.getCardsIn(ZoneType.Hand).size()+" discard="+(dc==null?"NONE":dc.getName())+" tier="+(dc==null?99:neutralFaldornDiscardTier(dc,me.getCardsIn(ZoneType.Hand))));
        }
      }

      // v1.7: Faldorn's dedicated neutral loop MUST spin before the land drop.
      // Older builds evaluated chooseFarmerLand() first, so a Faldorn activation that exiled
      // a land frequently stranded it for the turn. Activate once each own Main 1 whenever
      // Faldorn is untapped/payable and a strategic discard exists.
      if(!stackNonEmpty && ownTurn && ph==PhaseType.MAIN1 && faldornBattlefield() &&
         (neutralFaldornDiscardValueInHand() || neutralFaldornSafeDiscardAvailable())){
        for(SpellAbility fsa:neutralFaldornCandidates(false)){
          try{
            if(fsa==null||fsa.getHostCard()==null||!"Faldorn, Dread Wolf Herald".equals(fsa.getHostCard().getName())||!fsa.isActivatedAbility()) continue;
            String fd=String.valueOf(fsa).toLowerCase(Locale.ROOT);
            if(!fd.contains("exile the top card")) continue;
            if(!fsa.canPlay() || !fullyPayableStrategicAction(fsa)) continue;
            farmerActionsThisPhase++; lastNoActionSig=""; FALDORN_ACTIVATIONS.incrementAndGet();
            if(auditEnabled) System.out.println("FALDORN_EARLY_SPIN turn="+me.getGame().getPhaseHandler().getTurn()+" hand="+me.getCardsIn(ZoneType.Hand).size()+" before_land=true");
            return Collections.singletonList(fsa);
          }catch(Exception ignored){}
        }
      }
      if(!stackNonEmpty && ownTurn && !neutralFaldornHoldLandForValakut()){
        SpellAbility land=chooseFarmerLand();
        if(land!=null){
          try{if(land.getHostCard()!=null && land.getHostCard().isInZone(ZoneType.Exile)){FALDORN_EXILE_PLAYS.incrementAndGet();if(auditEnabled)System.out.println("FALDORN_EXILE_LAND_PLAY card="+land.getHostCard().getName());}}catch(Exception ignored){}
          lastNoActionSig=""; if(auditEnabled)System.out.println("FALDORN_NEUTRAL_DECISION action="+land.getHostCard().getName()+" role=land"); return Collections.singletonList(land);
        }
      }
      if(ownTurn && farmerActionsThisPhase>=(faldornBattlefield()?10:6) && !stackNonEmpty){lastNoActionSig=sig;return null;}
      SpellAbility best=null; int bestScore=Integer.MIN_VALUE;
      for(SpellAbility sa:neutralFaldornCandidates(stackNonEmpty)){
        try{
          if(sa==null||sa.getHostCard()==null||sa.isLandAbility()) continue;
          if(sa.costHasManaX() && !configurePayableX(sa)) continue;
          // v1.6: assign mandatory/strategic targets BEFORE asking Forge whether the spell is playable.
          // Targeted protection such as Gaea's Gift / Safekeeping / Snakeskin Veil can report canPlay=false
          // while targetless, which previously caused missed Swords to Plowshares / Chaos Warp responses.
          if(!prepareNeutralFaldornTargets(sa)) continue;
          boolean manaOk;
          if("Deflecting Swat".equals(sa.getHostCard().getName()) && faldornBattlefield()) manaOk=true; // commander alternative cost = 0
          else manaOk=ComputerUtilMana.canPayManaCost(sa,me,0,false);
          if(!sa.canPlay() || !manaOk) continue;
          int sc=neutralFaldornScore(sa,stackNonEmpty);
          if(sc>bestScore){bestScore=sc;best=sa;}
        }catch(Exception ignored){}
      }
      if(best==null || bestScore<=0){lastNoActionSig=sig;return null;}
      if(!stackNonEmpty && ownTurn) farmerActionsThisPhase++;
      lastNoActionSig="";
      markTutorUse(best,"faldorn_neutral"); noteTutorFollowThrough(best); noteDrawEngineAction(best,"faldorn_neutral");
      if(stackNonEmpty && best.getHostCard()!=null && PROTECTION.contains(best.getHostCard().getName())) armProtectionOutcomeAudit(best);
      try{Card ac=best.getHostCard(); if(ac!=null){ if(ac.isInZone(ZoneType.Exile) && best.isSpell()) FALDORN_EXILE_PLAYS.incrementAndGet(); if("Faldorn, Dread Wolf Herald".equals(ac.getName())&&best.isActivatedAbility()) FALDORN_ACTIVATIONS.incrementAndGet(); }}catch(Exception ignored){}
      if(auditEnabled){Card bc=best.getHostCard();String bn=bc.getName();String bp;try{boolean external=(bc.getOwner()!=me);boolean generated=bc.isToken()||bn.endsWith(" Token")||"Commander Effect".equals(bn);bp=(external||generated)?"EXTERNAL_OR_GENERATED":FALDORN_CARD_PLAN.getOrDefault(bn,"UNREGISTERED");}catch(Exception e){bp=FALDORN_CARD_PLAN.getOrDefault(bn,"UNREGISTERED");}System.out.println("FALDORN_NEUTRAL_DECISION action="+bn+" score="+bestScore+" zone="+bc.getZone()+" stack="+stackNonEmpty+" tokens="+countThopters()+" plan=["+bp+"]");}
      return Collections.singletonList(best);
    }
    private Player neutralFaldornCombatTarget(){
      Player lethal=null,best=null; int lethalMargin=Integer.MIN_VALUE,bestScore=Integer.MIN_VALUE;
      try{for(Player p:me.getOpponents()) if(!p.hasLost()){
        int full=neutralFaldornEstimatedAttackPower(p,true);
        int margin=full-p.getLife();
        if(margin>=0 && margin>lethalMargin){lethalMargin=margin;lethal=p;}
        int score=-p.getLife()*12 + playerThreatScore(p) + Math.min(500,full*4);
        if(score>bestScore){bestScore=score;best=p;}
      }}catch(Exception ignored){}
      return lethal!=null?lethal:best;
    }
    private boolean neutralFaldornEngineCreature(Card c){
      if(c==null)return false; String n=c.getName();
      return "Faldorn, Dread Wolf Herald".equals(n)||"Formidable Speaker".equals(n)||"Toski, Bearer of Secrets".equals(n)||"Ohran Frostfang".equals(n)||"Professional Face-Breaker".equals(n)||"Nightpack Ambusher".equals(n);
    }
    private void declareNeutralFaldornAttackers(Player attacker, forge.game.combat.Combat combat){
      try{
        Player target=neutralFaldornCombatTarget();
        if(target==null){super.declareAttackers(attacker,combat);return;}
        int tok=countThopters();
        int full=neutralFaldornEstimatedAttackPower(target,true);
        int preserved=neutralFaldornEstimatedAttackPower(target,false);
        boolean shared=hasNamed("Shared Animosity",ZoneType.Battlefield);
        boolean trample=neutralFaldornBrawnLive()||hasNamed("Howlpack Resurgence",ZoneType.Battlefield);
        int blockers=0; try{for(Card c:target.getCardsIn(ZoneType.Battlefield)) if(c.isCreature()&&!c.isTapped()) blockers++;}catch(Exception ignored){}
        int fullAfterBlocks=Math.max(0,full-blockers*(trample?1:3));
        int preserveAfterBlocks=Math.max(0,preserved-blockers*(trample?1:3));
        boolean lethalFull=fullAfterBlocks>=target.getLife();
        boolean lethalPreserve=preserveAfterBlocks>=target.getLife();
        boolean pressure=tok>=3||shared||trample||full>=target.getLife();
        if(!pressure){
          super.declareAttackers(attacker,combat);
          if(auditEnabled)System.out.println("FALDORN_NEUTRAL_COMBAT mode=forge_default target="+target.getName()+" tokens="+tok+" est_power="+full+" blockers="+blockers);
          return;
        }
        java.util.LinkedHashMap<Card,forge.game.GameEntity> focus=new java.util.LinkedHashMap<>();
        for(Card c:me.getCardsIn(ZoneType.Battlefield)){
          if(!c.isCreature()||!forge.game.combat.CombatUtil.canAttack(c,target))continue;
          if(neutralFaldornEngineCreature(c) && !"Toski, Bearer of Secrets".equals(c.getName()) && !lethalFull && !lethalPreserve) continue;
          if(neutralFaldornEngineCreature(c) && !"Toski, Bearer of Secrets".equals(c.getName()) && lethalPreserve) continue;
          focus.put(c,target);
        }
        if(!focus.isEmpty() && combat.getAttackConstraints().countViolations(focus)==0){
          combat.clearAttackers(); for(java.util.Map.Entry<Card,forge.game.GameEntity> e:focus.entrySet())combat.addAttacker(e.getKey(),e.getValue());
        } else super.declareAttackers(attacker,combat);
        if(auditEnabled)System.out.println("FALDORN_NEUTRAL_COMBAT mode="+(lethalFull?"lethal":"pressure")+" target="+target.getName()+" tokens="+tok+" attackers="+combat.getAttackers().size()+" raw_attack_power="+sumPower(combat.getAttackers())+" est_full="+full+" est_after_blocks="+fullAfterBlocks+" shared_animosity="+shared+" trample="+trample+" blockers="+blockers);
      }catch(Exception e){System.out.println("FALDORN_NEUTRAL_COMBAT_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());super.declareAttackers(attacker,combat);}
    }

    private void ensureFaldornCommandZone(){
      if(!faldornDeck()) return;
      try{
        for(ZoneType z:new ZoneType[]{ZoneType.Graveyard,ZoneType.Exile}){
          CardCollectionView cards=me.getCardsIn(z);
          for(Card c:cards){
            if("Faldorn, Dread Wolf Herald".equals(c.getName()) && c.isCommander()){
              if(auditEnabled) System.out.println("FALDORN_COMMAND_ZONE_ENFORCE from="+z+" card="+c.getName());
              me.getGame().getAction().moveToCommand(c,null);
              return;
            }
          }
        }
      }catch(Exception e){ if(auditEnabled)System.out.println("FALDORN_COMMAND_ZONE_ENFORCE_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage()); }
    }

    private Card neutralSpeakerDiscardChoice(){
      try{
        CardCollectionView hand=me.getCardsIn(ZoneType.Hand);
        String[] pref={"Anger","Brawn","Arrogant Wurm","Fiery Temper","Avacyn's Judgment","Ancient Grudge","Blazing Rootwalla","Basking Rootwalla","Stromkirk Occultist"};
        for(String want:pref) for(Card c:hand) if(want.equals(c.getName())) return c;
        return neutralFaldornDiscardChoice(hand);
      }catch(Exception ignored){return null;}
    }
    private Card neutralSpeakerTutorChoice(CardCollection options){
      if(options==null||options.isEmpty())return null;
      String[] order;
      int hand=me.getCardsIn(ZoneType.Hand).size();
      if(hand<=3) order=new String[]{"Ohran Frostfang","Toski, Bearer of Secrets","Professional Face-Breaker","Laelia, the Blade Reforged","Nightpack Ambusher","Immerwolf","Anger","Brawn"};
      else if(countThopters()>=3) order=new String[]{"Immerwolf","Nightpack Ambusher","Ohran Frostfang","Professional Face-Breaker","Laelia, the Blade Reforged","Toski, Bearer of Secrets","Anger","Brawn"};
      else order=new String[]{"Professional Face-Breaker","Laelia, the Blade Reforged","Ohran Frostfang","Toski, Bearer of Secrets","Nightpack Ambusher","Immerwolf","Anger","Brawn"};
      for(String want:order)for(Card c:options)if(want.equals(c.getName()))return c;
      Card best=null;int bv=Integer.MIN_VALUE;for(Card c:options){int v=faldornKeepValue(c);if(v>bv){bv=v;best=c;}}return best;
    }
    private void resolveSpeakerEtbFallback(){
      // Forge 2.0.14's generic AI can decline Formidable Speaker's optional Discard<1/Card>
      // cost. Only pay when BOTH a safe discard and a useful creature-to-hand target exist.
      try{
        if((!faldornDeck()&&!raphDeck())||!me.getGame().getStack().isEmpty())return;
        for(Card speaker:me.getCardsIn(ZoneType.Battlefield)){
          if(!"Formidable Speaker".equals(speaker.getName())||speakerEtbProcessed.contains(speaker.getId()))continue;
          String nativeSpeakerTarget=speakerNativeTargetById.get(speaker.getId());
          if(nativeSpeakerTarget!=null){speakerEtbProcessed.add(speaker.getId());if(auditEnabled)System.out.println("RAPH_SPEAKER_ETB_NATIVE_SEEN target="+nativeSpeakerTarget+" fallback=SKIP");continue;}
          speakerEtbProcessed.add(speaker.getId());
          CardCollection creatures=new CardCollection();for(Card c:me.getCardsIn(ZoneType.Library))if(c.getType().isCreature())creatures.add(c);
          Card target=raphDeck()?raphSpeakerTutorChoice(creatures):neutralSpeakerTutorChoice(creatures);
          if(target==null){if(auditEnabled)System.out.println((raphDeck()?"RAPH":"FALDORN")+"_SPEAKER_ETB_FALLBACK decision=DECLINE reason=no_useful_tutor_target");continue;}
          Card discard=raphDeck()?raphSpeakerDiscardChoice(me.getCardsIn(ZoneType.Hand)):neutralSpeakerDiscardChoice();
          if(discard==null){if(auditEnabled)System.out.println((raphDeck()?"RAPH":"FALDORN")+"_SPEAKER_ETB_FALLBACK decision=DECLINE reason=no_safe_discard target="+target.getName());continue;}
          java.util.Map<forge.game.ability.AbilityKey,Object> moveParams=new java.util.HashMap<>();
          Card discarded=me.discard(discard,null,false,moveParams);
          if(discarded==null){if(auditEnabled)System.out.println((raphDeck()?"RAPH":"FALDORN")+"_SPEAKER_ETB_FALLBACK decision=DECLINE reason=discard_failed card="+discard.getName());continue;}
          CardCollection shown=new CardCollection();shown.add(target);try{me.getGame().getAction().reveal(shown,me,true,"Formidable Speaker");}catch(Exception ignored){}
          me.getGame().getAction().moveToHand(target,null); me.shuffle(null);
          if(auditEnabled)System.out.println((raphDeck()?"RAPH_SPEAKER_ETB_FALLBACK":"FALDORN_SPEAKER_ETB_FALLBACK")+" decision=PAY discard="+discard.getName()+" tutor="+target.getName()+" creatures_available="+creatures.size());
          return;
        }
      }catch(Exception e){if(auditEnabled)System.out.println("FALDORN_SPEAKER_ETB_FALLBACK_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());}
    }

    private void resolvePiaEtbFallback(){
      // Same Forge 2.0.14 optional-cost issue as Speaker. Execute Pia's printed ETB only when
      // an enchantment target advances the actual Raph plan and a safe discard exists.
      try{
        if(!raphDeck()||!me.getGame().getStack().isEmpty())return;
        for(Card pia:me.getCardsIn(ZoneType.Battlefield)){
          if(!"Pia, Aether Ascetic".equals(pia.getName())||piaEtbProcessed.contains(pia.getId()))continue;
          // If Forge's native Pia resolution already removed the chosen target from the library,
          // the printed ETB succeeded. Do NOT execute the fallback a second time.
          String nativeTarget=piaNativeTargetById.get(pia.getId());
          if(nativeTarget!=null){boolean stillInLibrary=false;for(Card c:me.getCardsIn(ZoneType.Library))if(nativeTarget.equals(c.getName())){stillInLibrary=true;break;}
            if(!stillInLibrary){piaEtbProcessed.add(pia.getId());if(auditEnabled)System.out.println("RAPH_PIA_ETB_NATIVE_SUCCESS target="+nativeTarget+" fallback=SKIP");continue;}}
          piaEtbProcessed.add(pia.getId());
          CardCollection ench=new CardCollection();for(Card c:me.getCardsIn(ZoneType.Library))if(c.getType().isEnchantment())ench.add(c);
          Card target=raphPiaTutorChoice(ench);
          if(target==null){if(auditEnabled)System.out.println("RAPH_PIA_ETB_FALLBACK decision=DECLINE reason=no_useful_enchantment_target");continue;}
          Card discard=raphPiaDiscardChoice(me.getCardsIn(ZoneType.Hand));
          if(discard==null && "Sylvan Library".equals(target.getName()) && raphAttackedThisTurn()) discard=raphPiaForcedSylvanDiscardChoice(me.getCardsIn(ZoneType.Hand));
          if(discard==null){if(auditEnabled)System.out.println("RAPH_PIA_ETB_FALLBACK decision=DECLINE reason=no_safe_discard target="+target.getName());continue;}
          java.util.Map<forge.game.ability.AbilityKey,Object> moveParams=new java.util.HashMap<>();
          Card discarded=me.discard(discard,null,false,moveParams);
          if(discarded==null){if(auditEnabled)System.out.println("RAPH_PIA_ETB_FALLBACK decision=DECLINE reason=discard_failed card="+discard.getName());continue;}
          CardCollection shown=new CardCollection();shown.add(target);try{me.getGame().getAction().reveal(shown,me,true,"Pia, Aether Ascetic");}catch(Exception ignored){}
          me.getGame().getAction().moveToHand(target,null);me.shuffle(null);
          if(auditEnabled)System.out.println("RAPH_PIA_ETB_FALLBACK decision=PAY discard="+discard.getName()+" tutor="+target.getName()+" enchantments_available="+ench.size());
          return;
        }
      }catch(Exception e){if(auditEnabled)System.out.println("RAPH_PIA_ETB_FALLBACK_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());}
    }

    private SpellAbility raphCastableCommanderAction(){
      try{
        for(Card c:me.getCardsIn(ZoneType.Command)){
          if(!"Raph & Mikey, Troublemakers".equals(c.getName())) continue;
          for(SpellAbility sa:c.getAllPossibleAbilities(me,true)){
            if(sa!=null && sa.isSpell() && sa.canPlay() && ComputerUtilMana.canPayManaCost(sa,me,0,false)) return sa;
          }
        }
      }catch(Exception ignored){}
      return null;
    }

    private boolean raphCriticalCombatDrawLock(){
      int per=combatDrawPerConnection();
      if(per<=0) return false;
      return libraryCardsRemaining()<=Math.max(12,librarySafetyReserve()+4);
    }

    private Player raphMostBlockedOpponent(){
      Player best=null; int blockers=-1;
      try{for(Player p:me.getOpponents()){if(p.hasLost())continue;int b=0;for(Card c:p.getCardsIn(ZoneType.Battlefield))if(c.isCreature()&&!c.isTapped())b++;if(b>blockers){blockers=b;best=p;}}}catch(Exception ignored){}
      return best;
    }

    @Override public List<SpellAbility> chooseSpellAbilityToPlay(){
      DECISION_PROGRESS.incrementAndGet(); ensureFaldornCommandZone(); resolveSpeakerEtbFallback(); resolvePiaEtbFallback(); auditState("priority"); resetFarmerBudgetIfNeeded();
      if(raphDeck()){
        try{
          raphCardPlanCoverageAudit();
          boolean ownTurn=me.getGame().getPhaseHandler().isPlayerTurn(me);
          PhaseType ph=me.getGame().getPhaseHandler().getPhase();
          boolean stackNonEmpty=!me.getGame().getStack().isEmpty();
          // Real stack first: answer removal/sweepers and redirect/counter hostile objects before any development.
          if(stackNonEmpty){SpellAbility react=chooseBoundedFarmerAction(true);if(react!=null){if(auditEnabled)System.out.println("RAPH_STACK_RESPONSE action="+react.getHostCard().getName());return Collections.singletonList(react);}return super.chooseSpellAbilityToPlay();}
          if(ownTurn && (ph==PhaseType.MAIN1 || ph==PhaseType.MAIN2)){
            if(auditEnabled && ph==PhaseType.MAIN1) System.out.println("RAPH_MIKEY_COMMAND_SCAN turn="+me.getGame().getPhaseHandler().getTurn()+" command=["+cardNames(me.getCardsIn(ZoneType.Command))+"] mana_est="+ComputerUtilMana.getAvailableManaEstimate(me,true));
            // v0.3.5 COMMANDMENT: RAPH IS THE DECK. If Raph is legally castable in EITHER main phase,
            // every optional development spell is forbidden until Raph is cast.
            SpellAbility raphNow=raphCastableCommanderAction();
            if(raphNow!=null){
              if(auditEnabled)System.out.println("RAPH_COMMANDMENT_LOCK turn="+me.getGame().getPhaseHandler().getTurn()+" phase="+ph+" action=CAST_RAPH other_development=FORBIDDEN");
              pendingRaphCastNo=me.getTotalCommanderCast()+1; pendingRaphTreasuresBefore=raphTreasureCount(); pendingRaphManaBefore=ComputerUtilMana.getAvailableManaEstimate(me,true); pendingRaphPayment=true;
              return Collections.singletonList(raphNow);
            }
            SpellAbility strategic=chooseBoundedFarmerAction(false);
            if(strategic!=null){
              if(!raphTreasureReserveAllows(strategic)) strategic=null;
            }
            if(strategic!=null){
              int liveScore=raphActionScore(strategic,false);
              if(liveScore<=0){
                if(auditEnabled)System.out.println("RAPH_STRATEGIC_HARD_REJECT turn="+me.getGame().getPhaseHandler().getTurn()+" phase="+ph+" action="+strategic.getHostCard().getName()+" reason=live_score_zero");
                strategic=null;
              } else {
                if("Aggravated Assault".equals(strategic.getHostCard().getName()) && strategic.isActivatedAbility()){lastAggravatedSelectionTurn=me.getGame().getPhaseHandler().getTurn();lastAggravatedSelectionAttackCount=raphAttacksThisTurn;}
                if(auditEnabled)System.out.println("RAPH_STRATEGIC_ACTION turn="+me.getGame().getPhaseHandler().getTurn()+" phase="+ph+" action="+strategic.getHostCard().getName()+" score="+liveScore);return Collections.singletonList(strategic);
              }
            }
          }
          List<SpellAbility> base=super.chooseSpellAbilityToPlay();
          // Moraug conversion rule: hold the land through Main 1 when Raph can make the normal combat first.
          if(base!=null&&!base.isEmpty()&&base.get(0)!=null&&base.get(0).getHostCard()!=null){SpellAbility b=base.get(0);String n=b.getHostCard().getName();
            if(ownTurn&&(ph==PhaseType.MAIN1||ph==PhaseType.MAIN2)&&!raphTreasureReserveAllows(b)){if(auditEnabled)System.out.println("RAPH_TREASURE_RESERVE_GENERIC_BLOCK card="+n);return null;}
            if(ownTurn&&ph==PhaseType.MAIN1&&(hasNamed("Moraug, Fury of Akoum",ZoneType.Battlefield)||"Moraug, Fury of Akoum".equals(lastRaphTutorTarget))&&raphReadyToAttack()&&b.isLandAbility()){if(auditEnabled)System.out.println("RAPH_MORAUG_LAND_HOLD land="+n+" reason="+(hasNamed("Moraug, Fury of Akoum",ZoneType.Battlefield)?"save_for_postcombat":"known_top_hit_save_land"));return null;}
            if(ownTurn&&ph==PhaseType.MAIN1&&raphExtraCombatCard(n)){if(auditEnabled)System.out.println("RAPH_EXTRA_COMBAT_HOLD card="+n+" reason=wait_until_after_first_attack");return null;}
            // v0.3.4 hard fallback gates: generic Forge AI may not bypass Raph-specific legality/strategy locks.
            if(me.getGame().getStack().isEmpty() && (SINGLE_TARGET_PROTECTION.contains(n)||"Tibalt's Trickery".equals(n)||"Bolt Bend".equals(n)||"Untimely Malfunction".equals(n))){
              if(auditEnabled)System.out.println("RAPH_HARD_GATE card="+n+" reason=empty_stack");return null;
            }
            if("Shattering Spree".equals(n) && !raphMeaningfulArtifactTarget()){if(auditEnabled)System.out.println("RAPH_HARD_GATE card=Shattering Spree reason=no_artifact");return null;}
            if("Abrade".equals(n) && !(raphMeaningfulArtifactTarget()||raphAbradeCreatureTarget())){if(auditEnabled)System.out.println("RAPH_HARD_GATE card=Abrade reason=no_target");return null;}
            if("Kogla and Yidaro".equals(n) && b.isActivatedAbility() && !raphKoglaRecycleUseful()){if(auditEnabled)System.out.println("RAPH_HARD_GATE card=Kogla_and_Yidaro reason=recycle_not_useful");return null;}
            if(raphExtraCombatCard(n) && !raphSafeForAnotherCombat()){if(auditEnabled)System.out.println("RAPH_HARD_GATE card="+n+" reason=library_combat_lock");return null;}
            if("Aggravated Assault".equals(n) && b.isActivatedAbility() && me.getGame().getPhaseHandler().getTurn()==lastAggravatedSelectionTurn && raphAttacksThisTurn==lastAggravatedSelectionAttackCount){if(auditEnabled)System.out.println("RAPH_HARD_GATE card=Aggravated_Assault reason=no_intervening_combat");return null;}
            if(("Toski, Bearer of Secrets".equals(n)||"Ohran Frostfang".equals(n)) && libraryDangerMode()){if(auditEnabled)System.out.println("RAPH_HARD_GATE card="+n+" reason=library_danger");return null;}
            if(("Return of the Wildspeaker".equals(n)||"Rishkar's Expertise".equals(n)||"Big Score".equals(n)||"Unexpected Windfall".equals(n)) && libraryCriticalMode()){if(auditEnabled)System.out.println("RAPH_HARD_GATE card="+n+" reason=library_critical");return null;}
          }
          if(ownTurn && (ph==PhaseType.MAIN1||ph==PhaseType.MAIN2)){
            SpellAbility raphFinal=raphCastableCommanderAction();
            if(raphFinal!=null){
              if(auditEnabled)System.out.println("RAPH_COMMANDMENT_FINAL_OVERRIDE proposed="+(base==null||base.isEmpty()||base.get(0)==null||base.get(0).getHostCard()==null?"NONE":base.get(0).getHostCard().getName())+" action=CAST_RAPH");
              pendingRaphCastNo=me.getTotalCommanderCast()+1; pendingRaphTreasuresBefore=raphTreasureCount(); pendingRaphManaBefore=ComputerUtilMana.getAvailableManaEstimate(me,true); pendingRaphPayment=true;
              return Collections.singletonList(raphFinal);
            }
          }
          return base;
        }catch(Exception e){if(auditEnabled)System.out.println("RAPH_STRATEGY_ERROR "+e.getClass().getSimpleName()+":"+e.getMessage());return super.chooseSpellAbilityToPlay();}
      }
      // FALDORN_NEUTRAL_V1_1: hard branch before all inherited Rhys/Mowu strategic logic.
      // Forge still owns rules, legality, targets, payments, RNG, triggers and outcomes.
      if(dedicatedFaldornPilot()){ neutralFaldornCoverageAudit(); return chooseNeutralFaldornAction(); }
      finishProtectionOutcomeAuditIfReady();
      PhaseType ph=me.getGame().getPhaseHandler().getPhase();
      boolean ownTurn=me.getGame().getPhaseHandler().isPlayerTurn(me);
      boolean stackNonEmpty=!me.getGame().getStack().isEmpty();
      if(ownTurn && ph==PhaseType.MAIN1 && rhysReserveArmed && me.getGame().getPhaseHandler().getTurn()!=rhysReserveStartedTurn){
        if(auditEnabled) System.out.println("FARMER_RHYS_RESERVE_EXPIRED prior_turn="+rhysReserveStartedTurn+" now="+me.getGame().getPhaseHandler().getTurn());
        rhysReserveArmed=false;
      }

      // v18.6: alternate-win permanents are absolute interaction priority whenever we can legally answer them.
      // This check runs even on an opponent's turn with an empty stack, before Rhys EOT development.
      SpellAbility mustAnswer=chooseMustAnswerInteraction();
      if(mustAnswer!=null){ farmerActionsThisPhase++; lastNoActionSig=""; if(auditEnabled)System.out.println("FARMER_DECISION action="+mustAnswer.getHostCard().getName()+" reason=must_answer_alt_win phase="+ph); return Collections.singletonList(mustAnswer); }

      if(stackNonEmpty){
        SpellAbility top=me.getGame().getStack().peekAbility();
        auditProtectionOpportunity();
        // Do not rescan the whole hand after each of our own resolving spells/triggers.
        // Passing here preserves Forge priority/legality while avoiding pathological
        // repeated response searches on large Cathars' Crusade / token trigger stacks.
        if(top!=null && top.getActivatingPlayer()==me) return null;
        String ss=farmerBudgetTurn+":"+farmerBudgetPhase+":"+me.getGame().getStack().size()+":"+(top==null?"null":top.getId())+":"+actionSig();
        if(ss.equals(farmerLastStackSig)) return null;
        farmerLastStackSig=ss;
        SpellAbility response=chooseForcedProtectionResponse();
        if(response==null) response=chooseBoundedFarmerAction(true);
        if(response==null){
          if(auditEnabled && (topStackProtectedTarget()!=null||destructiveSweepOnStack())) System.out.println("MOWU_PROTECTION_DECLINE threat="+(top==null?"UNKNOWN":(top.getHostCard()==null?String.valueOf(top):top.getHostCard().getName()))+" reason=no_legal_payable_protection protection_in_hand=["+protectionInHandAudit()+"]");
          return null;
        }
        markTutorUse(response,"response"); noteTutorFollowThrough(response); noteDrawEngineAction(response,"response");
        armProtectionOutcomeAudit(response);
        if(auditEnabled) System.out.println("FARMER_DECISION action="+response.getHostCard().getName()+" phase="+ph+" response=true creature_tokens="+countThopters()+" food="+countFoodTokens());
        return Collections.singletonList(response);
      }

      // v15: on the last opponent end step before our turn, spend otherwise-expiring mana on Rhys.
      // Prefer the six-mana double with 3+ tokens; if we are still body-starved, make one starter token.
      if(isPreTurnEndStep()){
        auditLowTokenRecovery("preturn_eot");
        if(auditEnabled) System.out.println("FARMER_RHYS_EOT_CHECK turn="+me.getGame().getPhaseHandler().getTurn()+" armed="+rhysReserveArmed+" tokens="+countThopters()+" available="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" rhys_present="+(rhysCard()!=null)+" rhys_tapped="+(rhysCard()!=null&&rhysCard().isTapped()));
        SpellAbility eotDouble=forcedRhysPremiumActivation();
        if(eotDouble!=null){
          if(auditEnabled) System.out.println("FARMER_RHYS_EOT_DOUBLE turn="+me.getGame().getPhaseHandler().getTurn()+" tokens="+countThopters()+" next_turn=ours");
          rhysReserveArmed=false;
          return Collections.singletonList(eotDouble);
        }
        SpellAbility eotStarter=forcedRhysStarterActivation();
        if(eotStarter!=null){
          if(auditEnabled) System.out.println("FARMER_RHYS_EOT_STARTER turn="+me.getGame().getPhaseHandler().getTurn()+" tokens="+countThopters()+" next_turn=ours");
          rhysReserveArmed=false;
          return Collections.singletonList(eotStarter);
        }
        return null;
      }

      if(!ownTurn || (ph!=PhaseType.MAIN1 && ph!=PhaseType.MAIN2)) return null;

      String sig=actionSig();
      if(sig.equals(lastNoActionSig)) return null;

      // v18.6: with a dangerous library and a board already capable of closing, STOP feeding
      // optional ETB/draw engines and move to combat. This addresses the 42-token/629-counter self-deck loss.
      if(ph==PhaseType.MAIN1 && libraryDangerMode() && (overwhelmingCombatBoard() || usefulCreatureCount()>=8)){
        if(auditEnabled)System.out.println("FARMER_LIBRARY_CLOSE_NOW library="+libraryCardsRemaining()+" reserve="+librarySafetyReserve()+" tokens="+countThopters()+" creatures="+usefulCreatureCount());
        lastNoActionSig=sig; return null;
      }

      // v1.7 Faldorn sequencing: spin BEFORE the land drop and before generic development.
      // This preserves the land drop and mana so an exiled land or cheap spell can actually
      // be played this turn. It also enforces the user's rule to activate every own turn when able.
      if(ph==PhaseType.MAIN1 && me.getGame().getPhaseHandler().isPlayerTurn(me)){
        SpellAbility earlyFaldornSpin=forcedFaldornActivation();
        if(earlyFaldornSpin!=null){
          if(auditEnabled) System.out.println("FALDORN_EARLY_SPIN turn="+me.getGame().getPhaseHandler().getTurn()+" hand="+me.getCardsIn(ZoneType.Hand).size()+" before_land=true");
          farmerActionsThisPhase++; lastNoActionSig=""; return Collections.singletonList(earlyFaldornSpin);
        }
      }

      SpellAbility land=shouldHoldLandForFelidarFirst()?null:chooseFarmerLand();
      if(land!=null){ lastNoActionSig=sig; return Collections.singletonList(land); }

      if(ph==PhaseType.MAIN1 && overwhelmingCombatBoard()){ if(auditEnabled) System.out.println("FARMER_CLOSE_SKIP_DEVELOPMENT tokens="+countThopters()+" creatures="+farmerCreatureCount()); lastNoActionSig=sig; return null; }

      debugRhysActivation();
      auditLowTokenRecovery("main_before_action");
      SpellAbility emergencyBodies=chooseEmergencySurvivalProductionAction();
      if(emergencyBodies!=null){ farmerActionsThisPhase++; lastNoActionSig=""; noteDrawEngineAction(emergencyBodies,"emergency_survival"); return Collections.singletonList(emergencyBodies); }
      SpellAbility initiatorBeforeBurst=chooseCounterInitiatorBeforeBurst();
      if(initiatorBeforeBurst!=null){ farmerActionsThisPhase++; lastNoActionSig=""; noteDrawEngineAction(initiatorBeforeBurst,"initiator_before_burst"); return Collections.singletonList(initiatorBeforeBurst); }
      // v18: low-token recovery is a primary strategic job. Immediate/repeatable body production
      // is chosen before generic setup. This also prevents a tutor/setup spell from consuming Rhys's last 3 mana.
      SpellAbility recoveryBody=chooseBodyStarvedProductionAction();
      if(recoveryBody!=null){ farmerActionsThisPhase++; lastNoActionSig=""; noteDrawEngineAction(recoveryBody,"body_starved_recovery"); return Collections.singletonList(recoveryBody); }
      SpellAbility nissaPlus=forcedNissaTokenActivation();
      if(nissaPlus!=null){ farmerActionsThisPhase++; lastNoActionSig=""; return Collections.singletonList(nissaPlus); }
      SpellAbility rhysDouble=forcedRhysPremiumActivation();
      if(rhysDouble!=null){
        if(auditEnabled) System.out.println("FARMER_RHYS_MAIN_DOUBLE_EXCEPTION turn="+me.getGame().getPhaseHandler().getTurn()+" tokens="+countThopters()+" attack_ready="+attackReadyCreatureCount());
        farmerActionsThisPhase++; lastNoActionSig=""; return Collections.singletonList(rhysDouble);
      }
      if(rhysReservePlanActive()){
        SpellAbility surplusPlay=chooseSurplusDevelopmentHoldingRhys();
        if(surplusPlay!=null){ farmerActionsThisPhase++; lastNoActionSig=""; markTutorUse(surplusPlay,"rhys_reserve_surplus"); noteTutorFollowThrough(surplusPlay); noteDrawEngineAction(surplusPlay,"rhys_reserve_surplus"); return Collections.singletonList(surplusPlay); }
        if(auditEnabled) System.out.println("FARMER_RHYS_RESERVE_PASS turn="+me.getGame().getPhaseHandler().getTurn()+" phase="+ph+" tokens="+countThopters()+" available="+ComputerUtilMana.getAvailableManaEstimate(me,true)+" reserve=6");
        lastNoActionSig=sig; return null;
      }

      SpellAbility strandedDraw=chooseMowuStrandedDrawEngine();
      if(strandedDraw!=null){ farmerActionsThisPhase++; lastNoActionSig=""; noteDrawEngineAction(strandedDraw,"mowu_draw_recovery"); return Collections.singletonList(strandedDraw); }

      SpellAbility faldornSpin=forcedFaldornActivation();
      if(faldornSpin!=null){ farmerActionsThisPhase++; lastNoActionSig=""; return Collections.singletonList(faldornSpin); }

      SpellAbility forced=forcedMainPhaseStrategicPlay();
      if(forced!=null){ markTutorUse(forced,"forced_main"); noteTutorFollowThrough(forced); noteDrawEngineAction(forced,"forced_main"); farmerActionsThisPhase++; lastNoActionSig=""; return Collections.singletonList(forced); }

      if(farmerActionsThisPhase>=5){ lastNoActionSig=sig; return null; }
      SpellAbility best=chooseBoundedFarmerAction(false);
      if(best==null){ lastNoActionSig=sig; return null; }
      farmerActionsThisPhase++; lastNoActionSig="";
      markTutorUse(best,"bounded_main"); noteTutorFollowThrough(best); noteDrawEngineAction(best,"bounded_main");
      if(auditEnabled) System.out.println("FARMER_DECISION action="+best.getHostCard().getName()+" phase="+ph+" creature_tokens="+countThopters()+" food="+countFoodTokens()+" producer="+producerOnline());
      return Collections.singletonList(best);
    }
  }

  static Deck load(String p){Deck d=DeckSerializer.fromFile(new File(p));if(d==null)throw new RuntimeException("Could not load deck "+p);return d;}
  private static void resetGameCounters(){
    DECISION_PROGRESS.set(0); ACTIVATION_COUNT.set(0); PREMIUM_COUNT.set(0); WHIFF_COUNT.set(0); PEAK_THOPTERS.set(0); THOPTER_TOKENS_SEEN.set(0); PEAK_WOLVES.set(0); WOLVES_SEEN.set(0); FALDORN_EXILE_PLAYS.set(0); FALDORN_ACTIVATIONS.set(0); TUTOR_CASTS.set(0); WORLDLY_TUTOR_CASTS.set(0); ENLIGHTENED_TUTOR_CASTS.set(0);
    TOTAL_P1_COUNTERS_ADDED.set(0);PEAK_P1_COUNTERS.set(0);PEAK_COUNTERED_CREATURES.set(0);PEAK_CREATURE_POWER.set(0);FINAL_P1_COUNTERS.set(0);FINAL_COUNTERED_CREATURES.set(0);AMBIGUOUS_COUNTER_ADDS.set(0);COUNTER_SOURCE_HINTS.clear();DRAW_LEDGER_EVENTS.set(0);DRAW_LEDGER_CARDS.set(0);DRAW_LEDGER_TRUE_DRAWS.set(0);DRAW_LEDGER_TOP_ACCESS.set(0);
  }
  private static int outcomeTurn(Game g){
    int turn=-1; for(GameLogEntry e:g.getGameLog().getLogEntries(null)){String x=e.toString();if(x.startsWith("Game Outcome: Turn "))try{turn=Integer.parseInt(x.substring("Game Outcome: Turn ".length()).trim());}catch(Exception ignored){}}
    return turn;
  }
  private static String counterSourceHintsJson(){
    List<String> keys=new ArrayList<>(COUNTER_SOURCE_HINTS.keySet()); Collections.sort(keys);
    StringBuilder b=new StringBuilder("{"); boolean first=true;
    for(String k:keys){ if(!first)b.append(','); first=false; b.append("\"").append(k).append("\":").append(COUNTER_SOURCE_HINTS.get(k).get()); }
    return b.append('}').toString();
  }
  private static void atomicResult(Path target,String winner,int turn,int gameNo,int farmerLife)throws Exception{
    String safe=winner.replace("\\","\\\\").replace("\"","\\\"");
    if(target.getParent()!=null)Files.createDirectories(target.getParent()); Path tmp=Paths.get(target.toString()+".tmp");
    String json="{\n  \"schema\": \"minstrel-neutral-probe-v0.1\",\n  \"pilot_version\": \""+MOWU_PILOT_VERSION+"\",\n  \"valid\": true,\n  \"game\": "+gameNo+",\n  \"winner\": \""+safe+"\",\n  \"ending_turn\": "+(turn<0?"null":turn)+",\n  \"peak_creature_tokens\": "+PEAK_THOPTERS.get()+",\n  \"total_creature_tokens_seen\": "+THOPTER_TOKENS_SEEN.get()+",\n  \"peak_wolves\": "+PEAK_WOLVES.get()+",\n  \"total_wolves_created\": "+WOLVES_SEEN.get()+",\n  \"faldorn_exile_plays\": "+FALDORN_EXILE_PLAYS.get()+",\n  \"faldorn_activations\": "+FALDORN_ACTIVATIONS.get()+",\n  \"total_p1_counters_added\": "+TOTAL_P1_COUNTERS_ADDED.get()+",\n  \"peak_p1_counters_on_battlefield\": "+PEAK_P1_COUNTERS.get()+",\n  \"peak_countered_creatures\": "+PEAK_COUNTERED_CREATURES.get()+",\n  \"peak_creature_power\": "+PEAK_CREATURE_POWER.get()+",\n  \"final_p1_counters\": "+FINAL_P1_COUNTERS.get()+",\n  \"final_countered_creatures\": "+FINAL_COUNTERED_CREATURES.get()+",\n  \"counter_source_hints\": "+counterSourceHintsJson()+",\n  \"ambiguous_counter_adds\": "+AMBIGUOUS_COUNTER_ADDS.get()+",\n  \"farmer_final_life\": "+farmerLife+",\n  \"tutor_casts\": "+TUTOR_CASTS.get()+",\n  \"worldly_tutor_casts\": "+WORLDLY_TUTOR_CASTS.get()+",\n  \"enlightened_tutor_casts\": "+ENLIGHTENED_TUTOR_CASTS.get()+",\n  \"draw_ledger_file\": \"game_"+String.format("%02d",gameNo)+"_draw_ledger.jsonl\",\n  \"draw_ledger_events\": "+DRAW_LEDGER_EVENTS.get()+",\n  \"draw_ledger_cards_accessed\": "+DRAW_LEDGER_CARDS.get()+",\n  \"draw_ledger_true_draw_cards\": "+DRAW_LEDGER_TRUE_DRAWS.get()+",\n  \"draw_ledger_top_access_cards\": "+DRAW_LEDGER_TOP_ACCESS.get()+"\n}\n";
    Files.writeString(tmp,json,StandardCharsets.UTF_8); try{Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
  }
  private static Game runOne(List<String> deckPaths,int gameNo,Path outdir)throws Exception{
    resetGameCounters(); CURRENT_DRAW_GAME=gameNo; CURRENT_DRAW_LEDGER=outdir.resolve(String.format("game_%02d_draw_ledger.jsonl",gameNo)); try{Files.deleteIfExists(CURRENT_DRAW_LEDGER);}catch(Exception ignored){} List<Deck> decks=new ArrayList<>(); for(String p:deckPaths)decks.add(load(p));
    GameRules rules=new GameRules(GameType.Commander); rules.setAppliedVariants(EnumSet.of(GameType.Commander)); rules.setSimTimeout(100);
    List<RegisteredPlayer> pp=new ArrayList<>();
    for(int i=0;i<4;i++){ RegisteredPlayer rp=RegisteredPlayer.forCommander(decks.get(i)); String n=(i==0?"Farmer-Pilot":"Ai("+(i+1)+")-"+decks.get(i).getName()); LobbyPlayer lp=new AggroLobby(n,i==0); rp.setPlayer(lp); pp.add(rp); }
    Match mc=new Match(rules,pp,"FarmerPersistent-"+gameNo); Game g=mc.createGame(); g.AI_TIMEOUT=1; g.AI_CAN_USE_TIMEOUT=true;
    System.out.println("FARMER_GAME_START game="+gameNo+" pilot="+MOWU_PILOT_VERSION); System.out.flush();
    ExecutorService ex=Executors.newSingleThreadExecutor(); Future<?> fut=ex.submit(()->mc.startGame(g));
    long gameStartedNanos=System.nanoTime();
    long lastDecision=DECISION_PROGRESS.get(),lastProgress=System.nanoTime(),lastPhaseAdvance=System.nanoTime(); int lastTurn=-1,lastLogSize=-1,phaseTurn=-1; String lastPhase="",phaseName="";
    final long GAME_WALL_NANOS=TimeUnit.SECONDS.toNanos(180),STALL_NANOS=TimeUnit.SECONDS.toNanos(180),PHASE_STALL_NANOS=TimeUnit.SECONDS.toNanos(240);
    try{
      while(true){
        try{fut.get(5,TimeUnit.SECONDS);break;}catch(ExecutionException ee){try{Files.writeString(outdir.resolve("game_"+String.format("%02d",gameNo)+"_exception.txt"),String.valueOf(ee.getCause())+"\n"+java.util.Arrays.toString(ee.getCause()==null?new StackTraceElement[0]:ee.getCause().getStackTrace()),StandardCharsets.UTF_8);}catch(Exception ignored){} throw ee;}catch(TimeoutException poll){
          long d=DECISION_PROGRESS.get(); int turnNow=g.getPhaseHandler().getTurn(); String phaseNow=String.valueOf(g.getPhaseHandler().getPhase()); int logSize=g.getGameLog().getLogEntries(null).size();
          if(turnNow!=phaseTurn||!phaseNow.equals(phaseName)){phaseTurn=turnNow;phaseName=phaseNow;lastPhaseAdvance=System.nanoTime();}
          if(d!=lastDecision||turnNow!=lastTurn||!phaseNow.equals(lastPhase)||logSize!=lastLogSize){lastDecision=d;lastTurn=turnNow;lastPhase=phaseNow;lastLogSize=logSize;lastProgress=System.nanoTime();System.out.println("FARMER_WATCHDOG game="+gameNo+" turn="+turnNow+" phase="+phaseNow+" decisions="+d+" log="+logSize);System.out.flush();System.err.flush();}
          // Raph/Mikey protocol: long duration or high turn/decision counts are not VOID reasons by themselves.
          // Keep the JVM alive while Forge continues advancing; only a true progress/phase stall is voided below.
          if(System.nanoTime()-lastPhaseAdvance>PHASE_STALL_NANOS||System.nanoTime()-lastProgress>STALL_NANOS){fut.cancel(true);ex.shutdownNow();throw new TimeoutException("game "+gameNo+" stalled at turn "+turnNow+" "+phaseNow);}
        }
      }
    }finally{ex.shutdownNow();}
    List<GameLogEntry> log=g.getGameLog().getLogEntries(null); Collections.reverse(log); for(GameLogEntry e:log)System.out.println("GAMELOG game="+gameNo+" "+e);
    String winner=g.getOutcome().isDraw()?"Draw":g.getOutcome().getWinningLobbyPlayer().getName(); int turn=outcomeTurn(g);
    int farmerLife=0; for(Player p:g.getPlayers()){ if("Farmer-Pilot".equals(p.getName())){ farmerLife=p.getLife(); break; } }
    atomicResult(outdir.resolve(String.format("game_%02d_result.json",gameNo)),winner,turn,gameNo,farmerLife);
    System.out.println("FARMER_GAME_COMPLETE game="+gameNo+" winner="+winner+" ending_turn="+turn+" peak_creature_tokens="+PEAK_THOPTERS.get()+" total_creature_tokens_seen="+THOPTER_TOKENS_SEEN.get()+" total_p1_counters_added="+TOTAL_P1_COUNTERS_ADDED.get()+" peak_p1_counters="+PEAK_P1_COUNTERS.get()+" peak_countered_creatures="+PEAK_COUNTERED_CREATURES.get()+" peak_creature_power="+PEAK_CREATURE_POWER.get()); System.out.flush();
    return g;
  }
  public static void main(String[] args)throws Exception{
    if(args.length<7){System.err.println("Usage: FarmerPersistentRunner <farmer.dck> <opp1.dck> <opp2.dck> <opp3.dck> <outdir> <games> <child.log>");System.exit(2);}
    Path outdir=Paths.get(args[4]).toAbsolutePath(); Files.createDirectories(outdir); int games=Integer.parseInt(args[5]); installLog(args[6]);
    GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
    List<String> decks=Arrays.asList(args[0],args[1],args[2],args[3]);
    for(int gameNo=1;gameNo<=games;gameNo++){
      Path checkpoint=outdir.resolve(String.format("game_%02d_result.json",gameNo));
      if(Files.exists(checkpoint)){try{String x=Files.readString(checkpoint);if(x.contains("\"valid\": true")){System.out.println("FARMER_GAME_PRESERVED game="+gameNo);continue;}}catch(Exception ignored){}}
      try{
        runOne(decks,gameNo,outdir);
      }catch(TimeoutException te){
        // A cancelled Forge game can leave internal engine threads alive. Never retry a VOID
        // inside the same JVM. Exit and let the external supervisor relaunch a fresh JVM;
        // completed atomic checkpoints are preserved and skipped on restart.
        System.out.println("FARMER_VOID_FRESH_JVM game="+gameNo+" reason="+te.getMessage());
        System.out.flush(); System.err.flush();
        System.exit(75);
      }
      System.gc();
    }
    System.out.println("FARMER_SERIES_COMPLETE games="+games); System.out.flush(); System.err.flush(); System.exit(0);
  }

}
