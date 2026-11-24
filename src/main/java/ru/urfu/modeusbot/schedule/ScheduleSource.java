package ru.urfu.modeusbot.schedule;

import java.time.LocalDate;
import java.util.List;

import ru.urfu.modeusbot.modeus.Lesson;

public interface ScheduleSource {

    List<Lesson> getSchedule(LocalDate firstDay, LocalDate lastDay);
}
