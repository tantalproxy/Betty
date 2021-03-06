package main.java.models.games;

import main.java.models.bets.BetGroup;
import main.java.models.bets.LiveBet;
import org.eclipse.jetty.util.ConcurrentHashSet;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Entity
@Table(name="game_events")
@NamedQueries({
        @NamedQuery(name="GameEvent.getForDate",
                query="SELECT a FROM GameEvent a WHERE a.dateStart = :date AND a.game=:game"),
        @NamedQuery(name="GameEvent.getForInterval",
                query="SELECT a FROM GameEvent a WHERE a.dateStart BETWEEN :dateStart AND :dateEnd"),
        @NamedQuery(name="GameEvent.getForPreciseDate",
                query="SELECT a FROM GameEvent a WHERE a.dateStart = :dateStart AND a.dateEnd = :dateEnd")
})
public class GameEvent implements Serializable {

    @Id
    @GeneratedValue
    private Long id;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GameEvent() {
        activateBetTypes = new ConcurrentHashSet<>();
    }

    public GameEvent(Long id, Game game, BetGroup betGroup, Date dateStart, Date dateEnd,
                     String eventName, String eventTime, String eventLocation, String team1Name, String team2Name) {
        this.id = id;
        this.game = game;
        this.betGroup = betGroup;
        this.dateStart = dateStart;
        this.dateEnd = dateEnd;
        this.eventName = eventName;
        this.eventLocation = eventLocation;
        this.eventTime = eventTime;
        this.team1Name = team1Name;
        this.team2Name = team2Name;
        activateBetTypes = new ConcurrentHashSet<>();
    }

    private String eventName;
    private String eventLocation;
    private String eventTime;
    private String team1Name;
    private String team2Name;
    private String status;

    @JoinColumn(name="game_id")
    private Game game;

    @OneToOne(mappedBy="gameEvent")
    private BetGroup betGroup;

    @OneToMany(mappedBy="gameEvent", fetch = FetchType.EAGER)
    private List<LiveBet> liveBets;

    public Game getGame() {
        return game;
    }
    public void setGame(Game v) {
        game = v;
    }

    public BetGroup getBetGroup() {
        return betGroup;
    }
    public void setBetGroup(BetGroup betGroup) {
        this.betGroup = betGroup;
    }

    @Temporal(value=TemporalType.DATE)
    private Date dateStart;

    @Temporal(value=TemporalType.DATE)
    private Date dateEnd;

    @Transient
    private List<Integer> scores1;

    @Transient
    private List<Integer> scores2;

    public String getEventName() {
        return eventName;
    }

    public void setEventName (String v) {
        eventName = v;
    }

    public String getEventLocation() {
        return eventLocation;
    }

    public void setEventLocation (String v) {
        eventLocation = v;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime (String v) {
        eventTime = v;
    }

    public String getTeam1Name () {
        return team1Name;
    }

    public void setTeam1Name (String v) {
        team1Name = v;
    }

    public String getTeam2Name () {
        return team2Name;
    }

    public void setTeam2Name (String v) {
        team2Name = v;
    }

    public Date getDateStart () {
        return dateStart;
    }

    public void setDateStart (Date v) {
        dateStart = v;
    }

    public Date getDateEnd () {
        return dateEnd;
    }

    public void setDateEnd (Date v) {
        dateEnd = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Integer> getScores1() {
        return scores1;
    }
    public void setScores1(List<Integer> scores) {
        this.scores1 = scores;
    }

    public List<Integer> getScores2() {
        return scores2;
    }
    public void setScores2(List<Integer> scores) {
        this.scores2 = scores;
    }

    @Transient
    private String gameName;
    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    @Transient
    private Set<String> activateBetTypes;

    public Set<String> getActivateBetTypes() {
        return activateBetTypes;
    }

    public void setActivateBetTypes(List<LiveBet> liveBets) {
        for (LiveBet liveBet: liveBets) {
            if (!activateBetTypes.contains(liveBet.getType())) {
                activateBetTypes.add(liveBet.getType());
            }
        }
    }

    public void clearActivateBetTypes() {
        activateBetTypes = null;
    }

    public List<LiveBet> getLiveBets() {
        return liveBets;
    }

    public void setLiveBets(List<LiveBet> liveBets) {

        this.liveBets = liveBets;
        setActivateBetTypes(liveBets);
    }

    @Override
    public String toString() {
        return this.getEventName() + "<->" + getEventLocation() + "<->" + getTeam1Name() + "<->" + getTeam2Name();
    }
}
