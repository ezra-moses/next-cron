package com.byteland;

import com.byteland.model.Cron;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CronExecute {

    private static final LogManager logManager = LogManager.getLogManager();
    private static final Logger LOGGER = Logger.getLogger("confLogger");

    private static List<Cron> executeList = new ArrayList<>();

    public static void main(String[] args) {

        try {
            logManager.readConfiguration(CronExecute.class.getResourceAsStream("/conf/logger.properties"));
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Error in loading configuration",exception);
        }

        ScheduledExecutorService builder = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> handle= builder.scheduleWithFixedDelay(CronExecute::scanAndBuild
                , (60 - LocalTime.now().getSecond()), 60, TimeUnit.SECONDS);
        try {
            handle.get();
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        }
    }

    private static void executeCron() {
//        CronLists cronLists = new CronLists();
//        executeList = cronLists.cronObservableList;
        executeList.parallelStream()
                .forEach(cron -> {
                    ProcessBuilder builder = new ProcessBuilder();
                    List<String> cParam = new ArrayList<>();
                    cParam.addAll(cron.getParameter());

                    if (cron.getScript().endsWith("bat")) {
                            cParam.add(0, "cmd.exe");
                            cParam.add(1, "/c");
                            cParam.add(2, cron.getScript());
                            builder.command(cParam);
                    }

                    else if(cron.getScript().endsWith("ps1")) {
                            cParam.add(0, "powershell.exe");
                            cParam.add(1, "-Command");
                            cParam.add(2, cron.getScript());
                            builder.command(cParam);
                    }

                    else if(cron.getScript().endsWith("sh")) {
                            cParam.add(0, cron.getScript());
                            builder.command(cParam);
                    }

                    else {
                        LOGGER.log(Level.WARNING,"nothing to execute");
                        builder = null;
                    }

                    try {
                        if(builder!=null) {
                            builder.directory(new File(CronConstants.CMD_PATH));
                            cron.setStatus(CronConstants.CRON_RUNNING);
                            CronUtils cronUtils = new CronUtils(false);
                            cronUtils.writeCronToDB(cron);

                            LOGGER.log(Level.INFO, "executing " + cParam);
                            Process process = builder.start();
                            final int i = process.waitFor();
                            if(i==0) {
                                LOGGER.log(Level.INFO, cron.getName() + " ran " + CronConstants.CRON_SUCCESS);
                                cron.setStatus(CronConstants.CRON_ENABLED);
                            }
                            else {
                                LOGGER.log(Level.SEVERE, cron.getName() + " " + CronConstants.CRON_FAILED);
                                cron.setStatus(CronConstants.CRON_FAILED);
                            }
                            cronUtils.writeCronToDB(cron);
                        }
                    } catch (IOException | InterruptedException e) {
                        LOGGER.log(Level.SEVERE, "cron execution " + e.getMessage());
                    }
                });
    }

    private static void scanAndBuild() {
        final ZoneId localZoneId = ZoneId.systemDefault();
        ZonedDateTime zonedLocalDateTime = ZonedDateTime.now(localZoneId);

        LOGGER.log(Level.FINEST, "scanAndBuild " + zonedLocalDateTime.getDayOfMonth()
                + "," + zonedLocalDateTime.getMonthValue()
                + "," + zonedLocalDateTime.getMonth()
                + "," + zonedLocalDateTime.getDayOfWeek()
                + "," + zonedLocalDateTime.getHour()
                + "," + zonedLocalDateTime.getMinute()
                + "," + localZoneId);

        CronLists cronLists = new CronLists();
        CronUtils cronUtils = new CronUtils(false);

        final List<Cron> collect = cronLists.cronObservableList.parallelStream()
                .filter(cron -> cron.getStatus().equalsIgnoreCase(CronConstants.CRON_ENABLED))
                .filter(cron -> {
                    ZonedDateTime cronZonedDateTime = ZonedDateTime.parse(cron.getStartDate())
                            .withZoneSameInstant(localZoneId);
                    LOGGER.log(Level.FINEST, cron.getName()
                            + " start year : " + cronZonedDateTime.getYear());
                            return cronZonedDateTime.getYear() <= zonedLocalDateTime.getYear();
                })
                .filter(cron -> {
                    ZonedDateTime cronZonedDateTime = ZonedDateTime.parse(cron.getStartDate())
                            .withZoneSameInstant(localZoneId);
                    LOGGER.log(Level.FINEST, cron.getName()
                            + " start getMonthValue : " + cronZonedDateTime.getMonthValue());
                    return cronZonedDateTime.getMonthValue() <= zonedLocalDateTime.getMonthValue();
                })
                .filter(cron -> {
                    ZonedDateTime cronZonedDateTime = ZonedDateTime.parse(cron.getStartDate())
                            .withZoneSameInstant(localZoneId);
                    LOGGER.log(Level.FINEST, cron.getName()
                            + " start getDayOfMonth : " + cronZonedDateTime.getDayOfMonth()
                            + " server's getDayOfMonth() : " + zonedLocalDateTime.getDayOfMonth());

                    if( cronZonedDateTime.getMonthValue() < zonedLocalDateTime.getMonthValue()) {
                        LOGGER.log(Level.FINEST, cron.getName()
                                + " start getMonth() : " + cronZonedDateTime.getMonth()
                                + " server's getMonth() : " + zonedLocalDateTime.getMonth());
                        return true;
                    } else
                        return cronZonedDateTime.getDayOfMonth() <= zonedLocalDateTime.getDayOfMonth();
                })
                .filter(cron -> {
                    if (cronUtils.isParamValid(cron.getEndDate())) {
                        ZonedDateTime cronZonedDateTime = ZonedDateTime.parse(cron.getEndDate())
                                .withZoneSameInstant(localZoneId);
                        LOGGER.log(Level.FINEST, cron.getName()
                                + " End getYear() : " + cronZonedDateTime.getYear());
                        return cronZonedDateTime.getYear() >= zonedLocalDateTime.getYear();
                    }
                    else
                        return true;
                })
                .filter(cron -> {
                    if (cronUtils.isParamValid(cron.getEndDate())) {
                        ZonedDateTime cronZonedDateTime = ZonedDateTime.parse(cron.getEndDate())
                                .withZoneSameInstant(localZoneId);
                        LOGGER.log(Level.FINEST, cron.getName()
                                + " End getMonthValue() : " + cronZonedDateTime.getMonthValue());
                        return cronZonedDateTime.getMonthValue() >= zonedLocalDateTime.getMonthValue();
                    }
                    else
                        return true;
                })
                .filter(cron -> {
                    if (cronUtils.isParamValid(cron.getEndDate())) {
                        ZonedDateTime cronZonedDateTime = ZonedDateTime.parse(cron.getEndDate())
                                .withZoneSameInstant(localZoneId);
                        LOGGER.log(Level.FINEST, cron.getName()
                                + " End getDayOfMonth() : " + cronZonedDateTime.getDayOfMonth()
                                + " server's getDayOfMonth() : " + zonedLocalDateTime.getDayOfMonth());
                        if( cronZonedDateTime.getMonthValue() > zonedLocalDateTime.getMonthValue()) {
                            LOGGER.log(Level.FINEST, cron.getName()
                                    + " End getMonth() : " + cronZonedDateTime.getMonth()
                                    + " server's getMonth() : " + zonedLocalDateTime.getMonth());
                            return true;
                        }
                        return cronZonedDateTime.getDayOfMonth() >= zonedLocalDateTime.getDayOfMonth();
                    }
                    else
                        return true;
                })
                .filter(cron -> {
                    ZonedDateTime startDate = ZonedDateTime.parse(cron.getStartDate());
                    ZonedDateTime startTime = ZonedDateTime
                            .parse(startDate.toLocalDate() + "T" + cron.getStartTime())
                            .withZoneSameInstant(localZoneId);

                    LOGGER.log(Level.FINEST, cron.getName()
                            + " startTime.toEpochSecond() :" + startTime.toEpochSecond()
                            + " server's .toEpochSecond(): " + zonedLocalDateTime.toEpochSecond()
                            + " startTime.toEpochSecond() <= server's .toEpochSecond() "
                            + (startTime.toEpochSecond() <= zonedLocalDateTime.toEpochSecond()));
                    return startTime.toEpochSecond() <= zonedLocalDateTime.toEpochSecond();
                })
                .filter(cron -> {
                    if (! cronUtils.isParamValid(cron.getEndTime()))
                        return true;
                    else {
                        LocalDate endDate = zonedLocalDateTime.toLocalDate();
                        if(cronUtils.isParamValid(cron.getEndDate()))
                            endDate = ZonedDateTime.parse(cron.getEndDate()).toLocalDate();

                        ZonedDateTime endTime = ZonedDateTime.parse(endDate + "T" + cron.getEndTime())
                                .withZoneSameInstant(localZoneId);

                        LOGGER.log(Level.FINEST, cron.getName()
                                + " endTime.toEpochSecond() : " + endTime.toEpochSecond()
                                + " server's .toEpochSecond() " + zonedLocalDateTime.toEpochSecond()
                                + " endTime.toEpochSecond() >= server's .toEpochSecond() "
                                + (endTime.toEpochSecond() >= zonedLocalDateTime.toEpochSecond()));
                        return endTime.toEpochSecond() >= zonedLocalDateTime.toEpochSecond();
                    }
                })
                .filter(cron -> cron.getMonths().getOn().contains(zonedLocalDateTime.getMonth().toString()))
                .filter(cron -> cron.getWeekdays().getOn().contains(zonedLocalDateTime.getDayOfWeek().toString()))
                .filter(cron -> {
                    boolean retVal = false;
                    for (int hour : cron.getHours().getOn()) {

                        ZoneId cronZoneId = cronUtils.getCronZoneId(cron.getTimeZone());
                        ZonedDateTime cronLocalTime = ZonedDateTime.now(cronZoneId);
                        String llHr = StringUtils.leftPad(Integer.toString(hour), 2, "0");
                        String llMin = StringUtils.leftPad(Integer.toString(cronLocalTime.getMinute())
                                , 2, "0");

                        ZonedDateTime zonedHour = ZonedDateTime.of(cronLocalTime.toLocalDate()
                                , LocalTime.parse(llHr + ":" + llMin, DateTimeFormatter.ISO_TIME)
                                , cronZoneId)
                                .withZoneSameInstant(localZoneId);

                        if (zonedHour.getHour() == zonedLocalDateTime.getHour()) {
                            LOGGER.log(Level.FINEST, cron.getName()
                                    + " zonedHour : " + zonedHour
                                    + " server's getHour() : " + zonedLocalDateTime.getHour());
                            retVal = true;
                            break;
                        }
                    }
                    return retVal;
                })
                .filter(cron -> {
                    boolean retVal = false;
                    for (int minute : cron.getMinutes().getOn()) {
                        ZoneId cronZoneId = cronUtils.getCronZoneId(cron.getTimeZone());
                        ZonedDateTime cronLocalTime = ZonedDateTime.now(cronZoneId);
                        String llHr = StringUtils.leftPad(Integer.toString(cronLocalTime.getHour())
                                , 2, "0");
                        String llMin = StringUtils.leftPad(Integer.toString(minute)
                                , 2, "0");

                        ZonedDateTime zonedMin = ZonedDateTime.of(cronLocalTime.toLocalDate()
                                , LocalTime.parse(llHr + ":" + llMin, DateTimeFormatter.ISO_TIME)
                                , cronZoneId)
                                .withZoneSameInstant(localZoneId);

                        if (zonedMin.getMinute() == zonedLocalDateTime.getMinute()) {
                            LOGGER.log(Level.FINEST, cron.getName()
                                    + " zonedMin : " + zonedMin
                                    + " server's getMinute() : " + zonedLocalDateTime.getMinute());
                            retVal = true;
                            break;
                        }
                    }
                    return retVal;
                })
                .collect(Collectors.toList());

        executeList.clear();
        if (collect.size() > 0) {
            executeList.addAll(collect);
            LOGGER.log(Level.FINEST, "executeList updated : " + executeList);
            executeCron();
        } else
            LOGGER.log(Level.INFO, "no crons to execute");
    }
}
