package chat.server;

record UserRecord(String username, String saltHex, String passwordHashHex) {
    boolean matches(char[] password) {
        String candidate = PasswordUtil.hashPassword(password, PasswordUtil.fromHex(saltHex));
        return constantTimeEquals(candidate, passwordHashHex);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
