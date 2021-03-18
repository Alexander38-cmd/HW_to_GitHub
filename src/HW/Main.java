package HW;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

public class Main {
    public static void main(String[] args) {
        // Добавили новый метод смены ника в инерфейс AuthService
        public interface AuthService {
            String getNicknameByLoginAndPassword(String login, String password);

            boolean registration(String login, String password, String nickname);

            boolean changeNick(String oldNick, String newNick);
        }
    }

    // В SimpleAuthService переопределили данный метод
    @Override
    public boolean changeNick(String oldNick, String newNick) {
        return false;
    }
}

// Создали новый класс DBAuthService, который реализует AuthService через класс SQLHadler
public class DBAuthService implements AuthService {
    @Override
    public String getNicknameByLoginAndPassword(String login, String password) {
        return SQLHandler.getNicknameByLoginAndPassword(login, password);
    }

    @Override
    public boolean registration(String login, String password, String nickname) {
        return SQLHandler.registration(login, password, nickname);
    }

    @Override
    public boolean changeNick(String oldNickname, String newNickname) {
        return SQLHandler.changeNick(oldNickname, newNickname);
    }
}

// Объявление класса SQLHandler для работы с БД
public class SQLHandler {
    private static Connection connection;
    private static PreparedStatement psGetNickname;
    private static PreparedStatement psRegistration;
    private static PreparedStatement psChangeNick;
    private static PreparedStatement psAddMessage;
    private static PreparedStatement psGetMessageForNick;

    public static boolean connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:BCOW.db");
            prepareAllStatements();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void prepareAllStatements() throws SQLException {
        psGetNickname = connection.prepareStatement("SELECT Nickname FROM Users WHERE Login = ? AND Password = ?;");
        psRegistration = connection.prepareStatement("INSERT INTO Users (Login, Password, Nickname) VALUES (?, ?, ?);");
        psChangeNick = connection.prepareStatement("UPDATE Users SET Nickname = ? WHERE Nickname = ?;");

        psAddMessage = connection.prepareStatement("INSERT INTO Messages (Sender, Receiver, Text, Date) VALUES (\n" +
                "(SELECT ID FROM Users WHERE Nickname = ?), \n" +
                "(SELECT ID FROM Users WHERE Nickname = ?), \n" +
                "?, ?)");

        psGetMessageForNick = connection.prepareStatement("SELECT (SELECT Nickname FROM Users WHERE ID = Sender), \n" +
                "(SELECT Nickname FROM Users WHERE ID = Receiver), \n" +
                "Text, \n" +
                "Date \n" +
                "FROM Messages \n" +
                "WHERE Sender = (SELECT ID FROM Users WHERE Nickname = ?) \n" +
                "OR Receiver = (SELECT ID FROM Users WHERE Nickname = ?) \n" +
                "OR Receiver = (SELECT ID FROM Users WHERE Nickname = 'admin')");
    }

    // Получаем nickname по логину и паролю
    public static String getNicknameByLoginAndPassword(String login, String password) {
        String nick = null;
        try {
            psGetNickname.setString(1, login);
            psGetNickname.setString(2, password);
            ResultSet rs = psGetNickname.executeQuery();
            if (rs.next()) {
                nick = rs.getString(1);
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return nick;
    }

    // Проходим регистрацию
    public static boolean registration(String login, String password, String nickname) {
        try {
            psRegistration.setString(1, login);
            psRegistration.setString(2, password);
            psRegistration.setString(3, nickname);
            psRegistration.executeUpdate();
            return true
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Метод смены ника
    public static boolean changeNick(String oldNick, String newNick) {
        try {
            psChangeNick.setString(1, newNick);
            psChangeNick.setString(2, oldNick);
            psChangeNick.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // Метод добавления сообщений в БД
    public static boolean addMessage(String sender, String receiver, String text, String date) {
        try {
            psAddMessage.setString(1, sender);
            psAddMessage.setString(2, receiver);
            psAddMessage.setString(3, text);
            psAddMessage.setString(4, date);
            psAddMessage.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // Метод извлечения сообщений из БД
    public static String getMessageForNick(String nick) {
        StringBuilder sb = new StringBuilder();
        try {
            psGetMessageForNick.setString(1, nick);
            psGetMessageForNick.setString(2, nick);
            ResultSet rs = psGetMessageForNick.executeQuery();

            while (rs.next()) {
                String sender = rs.getString(1);
                String receiver = rs.getString(2);
                String text = rs.getString(3);
                String date = rs.getString(4);
                //всем сообщение
                if (receiver.equals("admin")) {
                    sb.append(String.format("[ %s ] : %s\n", sender, text));
                } else {
                    sb.append(String.format("[ %s ] to [ %s ] : %s\n", sender, receiver, text));
                }
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    // Закрываем все методы
    public static void disconnect() {
        try {
            psRegistration.close();
            psGetNickname.close();
            psChangeNick.close();
            psAddMessage.close();
            psGetMessageForNick.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

// В классе Server вместо SimpleAuthService() теперь используем DBAuthService()
    //authService = new SimpleAuthService();
    //==============//
        if (!SQLHandler.connect()) {
        throw new RuntimeException("Failed to connect to database.");
    }
    authService = new DBAuthService();
    //==============//

    // Здесь же в классе Server в блоке finally после завершения работы с БД, закрывает ее
} finally {
        SQLHandler.disconnect();
    }
}
public class History {
    private static PrintWriter out;

    private static String getHistoryFilenameByLogin(String login) {
        return "history/history_" + login + ".txt";
    }

    public static void start(String login) {
        try {
            out = new PrintWriter(new FileOutputStream(getHistoryFilenameByLogin(login), true), true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void stop() {
        if (out != null) {
            out.close();
        }
    }

    public static void writeLine(String msg) {
        out.println(msg);
    }

    public static String getLast100LinesOfHistory(String login) {
        if (!Files.exists(Paths.get(getHistoryFilenameByLogin(login)))) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try {
            List<String> historyLines = Files.readAllLines(Paths.get(getHistoryFilenameByLogin(login)));
            int startPosition = 0;
            if (historyLines.size() > 100) {
                startPosition = historyLines.size() - 100;
            }
            for (int i = startPosition; i < historyLines.size(); i++) {
                sb.append(historyLines.get(i)).append(System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
public class ABC {
    static Object mon = new Object();
    static volatile int counter = 1;
    static final int interval = 5;

    public static void main(String[] args) {
        new Thread(() -> {
            try {
                for (int i = 0; i < interval; i++) {
                    synchronized (mon) {
                        while (counter != 1) {
                            mon.wait();
                        }
                        System.out.print("A");
                        counter = 2;
                        mon.notifyAll();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                for (int i = 0; i < interval; i++) {
                    synchronized (mon) {
                        while (counter != 2) {
                            mon.wait();
                        }
                        System.out.print("B");
                        counter = 3;
                        mon.notifyAll();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                for (int i = 0; i < interval; i++) {
                    synchronized (mon) {
                        while (counter != 3) {
                            mon.wait();
                        }
                        System.out.print("C ");
                        counter = 1;
                        mon.notifyAll();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}

public class NeedForSpeed {
    public static final int CARS_COUNT = 4;
    static CountDownLatch WINNER = new CountDownLatch(CARS_COUNT);

    public static void main(String[] args) {
        CyclicBarrier cb = new CyclicBarrier(CARS_COUNT, new Action());
        Semaphore smp = new Semaphore(2);

        System.out.println("IMPORTANT ANNOUNCEMENT >>> Training!!!");
        Race race = new Race(new Road(60), new Tunnel(smp), new Road(40));
        Car[] cars = new Car[CARS_COUNT];
        for (int i = 0; i < cars.length; i++) {
            cars[i] = new Car(race, 20 + (int) (Math.random() * 10), cb);
        }
        for (int i = 0; i < cars.length; i++) {
            new Thread(cars[i]).start();
        }
        while (WINNER.getCount() > 0)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        System.out.println("IMPORTANT ANNOUNCEMENT >>> The race is over!!!");
    }
}


class Car implements Runnable {
    CyclicBarrier cb;
    private static int CARS_COUNT;
    private Race race;
    private int speed;
    private String name;


    public String getName() {
        return name;
    }

    public int getSpeed() {
        return speed;
    }

    public Car(Race race, int speed, CyclicBarrier cb) {
        this.cb = cb;
        this.race = race;
        this.speed = speed;
        CARS_COUNT++;
        this.name = "Participant №" + CARS_COUNT;
    }

    @Override
    public void run() {
        try {
            System.out.println(this.name + " preparing.");
            Thread.sleep(500 + (int) (Math.random() * 800));
            System.out.println(this.name + " ready.");
            cb.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 0; i < race.getStages().size(); i++) {
            race.getStages().get(i).go(this);
        }
    }
}

abstract class Stage {
    protected int length;
    protected String description;

    public String getDescription() {
        return description;
    }

    public abstract void go(Car c);
}

class Road extends Stage {
    public Road(int length) {
        this.length = length;
        this.description = "Road " + length + " metres.";
    }

    @Override
    public void go(Car c) {
        try {
            System.out.println(c.getName() + " started stage: " + description);
            Thread.sleep(length / c.getSpeed() * 1000);
            System.out.println(c.getName() + " finished stage: " + description);
            if (this.length == 40) {
                NeedForSpeed.WINNER.countDown();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Tunnel extends Stage {
    Semaphore smp;

    public Tunnel(Semaphore smp) {
        this.smp = smp;
        this.length = 80;
        this.description = "Tunnel " + length + " metres.";
    }

    @Override
    public void go(Car c) {
        try {
            try {
                System.out.println(c.getName() + " preparing for the stage (waiting): " + description);
                smp.acquire();
                System.out.println(c.getName() + " started stage: " + description);
                Thread.sleep(length / c.getSpeed() * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println(c.getName() + " finished stage: " + description);
                smp.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Race {
    private ArrayList<Stage> stages;

    public ArrayList<Stage> getStages() {
        return stages;
    }

    public Race(Stage... stages) {
        this.stages = new ArrayList<>(Arrays.asList(stages));
    }
}

class Action implements Runnable {
    @Override
    public void run() {
        System.out.println("IMPORTANT ANNOUNCEMENT >>> The race has begun!!!");
    }
}