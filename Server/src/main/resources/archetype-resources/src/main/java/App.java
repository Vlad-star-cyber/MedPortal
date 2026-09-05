package $org.example;

import DAO.ScheduleSlotDAO;

import java.net.*;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5555)) {
            LOG.info("Сервер запущен на порту 5555");

            DataBase.DBHelper.getInstance();

            ScheduleSlotDAO slotDAO = new ScheduleSlotDAO();
            slotDAO.ensureSlotsExist(30);

            ReminderScheduler scheduler = new ReminderScheduler();
            scheduler.start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                LOG.info("Новый клиент: " + clientSocket.getInetAddress().getHostAddress());
                new Thread(new ClientThread(clientSocket)).start();
            }
        } catch (Exception e) {
            LOG.severe("Ошибка сервера: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
