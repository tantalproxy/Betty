package main.java.managers.grabbers.parsers;

import main.java.managers.games.GameManager;
import main.java.managers.games.GameSheduleManager;
import main.java.managers.grabbers.ScheduleParserFactory;
import main.java.managers.grabbers.parsers.interfaces.ParserFactory;
import main.java.managers.grabbers.parsers.qualifiers.EventSchedule;
import main.java.managers.grabbers.parsers.qualifiers.ResultCheck;
import main.java.managers.loggers.ConcreteLogger;
import main.java.managers.loggers.interfaces.SystemEventLogger;
import main.java.managers.service.RedisManager;
import main.java.models.games.Game;
import main.java.models.games.GameEvent;
import main.java.models.sys.ScheduleParser;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@Singleton
@Interceptors(ConcreteLogger.class)
public class ScheduleParserStarter {

    private Logger log = Logger.getAnonymousLogger();

    private static final String REDIS_LAST_SCHEDULE_DATE_KEY = "GameLastScheduleTime";
    private static final String REDIS_NEXT_SCHEDULE_DATE_KEY = "GameNextScheduleTimes";

    @PersistenceContext
    private EntityManager em;

    @Resource
    private SessionContext ctx;

    @Inject
    private RedisManager<ScheduleParser> redisManager;

    @Inject
    private RedisManager<GameEvent> gameEventRedisManager;

    @Inject @EventSchedule
    private ParserFactory<EventParser, ScheduleParser> eventParserFactory;

    @EJB
    private GameManager gameManager;

    @EJB
    private GameSheduleManager gameEventManager;

    private void initTodayEvents() {
        //инициализируем события на сегодня
        List<GameEvent> todayEvents = gameEventManager.getAllTodaySchedules();

        gameEventRedisManager.addList("GameEvent", todayEvents);
    }

    @PostConstruct
    public void init() {

        List<ScheduleParser> lst = em.createNamedQuery("ScheduleParser.findAll", ScheduleParser.class).getResultList();
        List<ScheduleParser> detachedList = new ArrayList<>();
        //делаем все парсеры изначально исполнеными, чтобы запустить при первом запуске
        for (ScheduleParser parser: lst) {
            parser.setLastCompleteTime(DateTime.now().minusDays(1));
        }
        //добавляем список парсеров в редис

        for (ScheduleParser l: lst) {
            detachedList.add(l.clone());
        }

        redisManager.addList("Parsers", detachedList);

        List<ScheduleParser> scheduleParsers = redisManager.getRange("Parsers", 0, -1);

        initTodayEvents();

        for (ScheduleParser p: scheduleParsers) {
            log.log(Level.INFO, "Init Parser Schedule start ->name=" + p.getName());
            try {
                executeParser(p, DateTime.now());
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        createNewSchedule();
    }

    @PreDestroy
    public void onDestroy() {

        redisManager.trimList(REDIS_NEXT_SCHEDULE_DATE_KEY, 0, -1);
        redisManager.trimList("GameEvent", 0, -1);

    }

    private void createNewSchedule() {

        TimerService timerService = ctx.getTimerService();
        //планируем расписание на 5 дней вперед
        int dateInFuture = 8;
        DateTime lastScheduleTime = DateTime.now().plusDays(dateInFuture);
        DateTime targetTime = DateTime.now();
        for (int i = 1; i < dateInFuture; i++) {

            redisManager.pushDateList(REDIS_NEXT_SCHEDULE_DATE_KEY, targetTime.plusDays(i));
            TimerConfig conf = new TimerConfig();
            conf.setPersistent(false);
            Timer timer = timerService.createSingleActionTimer(targetTime.plusSeconds(i * 5).toDate(), conf);
        }

        redisManager.setRawKey(REDIS_LAST_SCHEDULE_DATE_KEY,
                lastScheduleTime.toString(DateTimeFormat.forPattern("yyyy-MM-dd'T'hh:mm:ss")));
    }

    @Timeout
    public void getNewSchedules(Timer timer) {

        List<ScheduleParser> parsers = redisManager.getRange("Parsers", 0, 10);
        DateTime lastDate = DateTime.parse((String) redisManager.getRawKey(REDIS_LAST_SCHEDULE_DATE_KEY));
        //если последняя дата расписания меньше текущей даты
        //то нужно запланировать новое расписание
        if (lastDate.isBeforeNow()) {
            log.log(Level.INFO, "Schedule Reinit ->Last Checked Time=" + lastDate);
            createNewSchedule();
        }
        log.log(Level.INFO, "Schedule Prepare Next->");
        DateTime date = redisManager.popDate(REDIS_NEXT_SCHEDULE_DATE_KEY);
        log.log(Level.INFO, "Schedule Prepare->Last Checked Time=" + date);
        //FIXME - нужно передавать дату в функцию для того чтобы запросить расписание за конкретную дату
        if (date == null) {
            return;
        } else {
            for (ScheduleParser p : parsers) {
                try {
                    executeParser(p, date);
                    log.log(Level.INFO, "PARSE: PARSE COMPLETE!" + date);
                } catch (InstantiationException | IllegalAccessException e) {
                    log.log(Level.INFO, "PARSE ERROR: PARSE INCOMPLETE!" + e.getMessage());
                    e.printStackTrace();
                    p.setComplete(false);
                }
            }
        }
    }

    @Schedule(minute="*/2")
    public void checkParserActiveState() {

        //live checking of working parsers
        List<ScheduleParser> lst = redisManager.getRange("Parsers", 0, 10);

        //срезать список не надо пока
        //redisManager.trimList("Parsers", 0, 10);
        log.log(Level.INFO, "PARSERS HEALTH CHECK->date=" + DateTime.now());

        for (ScheduleParser parser: lst) {
            DateTime completeTime = parser.getLastCompleteTime();

            if (completeTime.plusDays(1).isBeforeNow()) {
                //TODO - парсер не исполнялся х дней со дня последнего старта
                log.log(Level.SEVERE, "Parser->" + parser.getName() + "is in not functional state.");
            }

            if (!parser.getStatus()) {
                //TODO - уведомление об плохом статусе парсеров админов
                log.log(Level.SEVERE, "Parser->" + parser.getName() + "is in abnormal state.");
            }
        }
        log.log(Level.INFO, "PARSERS HEALTH CHECK->DONE");
    }

    @Asynchronous
    public Future<List<GameEvent>> executeParser(ScheduleParser parser, DateTime date) throws InstantiationException, IllegalAccessException{

        List<Game> activeGames = gameManager.getAllActiveGames();

        if (date == null) {
            date = DateTime.now();
        }

        List<GameEvent> ls = (eventParserFactory.create(parser)).parse(
                activeGames.get(0), date.getYear(), date.getMonthOfYear(), date.getDayOfMonth());
        //FIXME - костыль для тестирования

        log.log(Level.INFO, "LIST OF PREPARED SCHEDULE->" + ls.size() + ", date=" + date);
        for (GameEvent l: ls) {
            em.persist(l);
        }
        return new AsyncResult<>(ls);
    }

}