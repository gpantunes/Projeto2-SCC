package tukano.auth;

import java.util.concurrent.ConcurrentHashMap;

public class CookieStore {

    private static ConcurrentHashMap<String, String> cookies;

    private static CookieStore instance;

    private CookieStore() {
        this.cookies = new ConcurrentHashMap<>();
    }

    // Public method to access the singleton instance
    public static CookieStore getInstance() {
        if(instance == null) {
            instance = new CookieStore();
        }

        return instance;
    }

    public void set(String key, String value) {
        cookies.put(key, value);
    }

    public static String get(String key) {
        return cookies.get(key);
    }

    public boolean containsKey(String key) {
        return cookies.containsKey(key);
    }

    public void remove(String key) {
        cookies.remove(key);
    }

    public boolean validateCookie(String userId, String cookie) {
        System.out.println("Entrou no validate cookie");

        String storedCookie = this.get(userId);
        Long cookieTimeStamp = Long.parseLong(storedCookie.split(";")[6]);

        int lastIndex = storedCookie.lastIndexOf(';');
        String cleanCookie = (lastIndex != -1) ? storedCookie.substring(0, lastIndex) : storedCookie;

        if(System.currentTimeMillis() - cookieTimeStamp > 3600*1000) {
            this.remove(cookie);
            System.out.println("A cookie perdeu a validade");
            return false;
        }

        if(!cleanCookie.equals(cookie)) {
            System.out.println("A cookie do user é diferente cookie guardada: " + cleanCookie + " cookie do request "
            + cookie);
            return false;
        }

        return true;
    }
}
