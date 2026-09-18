package org.example;

import DAO.ReminderDAO;
import models.Reminder;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ReminderScheduler {
    private static final Logger LOG = Logger.getLogger(ReminderScheduler.class.getName());
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                ReminderDAO dao = new ReminderDAO();
                List<Reminder> due = dao.findDueReminders();
                for (Reminder r : due) {
                    LOG.info(String.format("[НАПОМИНАНИЕ] %s -> %s: визит %s %s",
                            r.getChannel(), r.getPatientEmail(), r.getDoctorName(), r.getAppointmentDatetime()));
                    dao.markSent(r.getId());
                }
            } catch (Exception e) {
                LOG.severe("Ошибка в планировщике: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.MINUTES);
    }
}