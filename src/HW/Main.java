package HW;

import java.sql.*;

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
