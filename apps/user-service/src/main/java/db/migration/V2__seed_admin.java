package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Seeds the first ADMIN user from environment variables on a fresh database.
 * Reads ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_FULL_NAME; falls back to dev defaults.
 * Idempotent: skips if the admin email already exists.
 */
public class V2__seed_admin extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String email    = env("ADMIN_EMAIL",     "admin@learnpulse.dev");
        String password = env("ADMIN_PASSWORD",  "Admin@1234!");
        String fullName = env("ADMIN_FULL_NAME", "Platform Admin");

        try (PreparedStatement check = context.getConnection()
                .prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?")) {
            check.setString(1, email.toLowerCase());
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }

        String hash = new BCryptPasswordEncoder(12).encode(password);
        byte[] uuidBytes = toBytes(UUID.randomUUID());

        try (PreparedStatement ins = context.getConnection().prepareStatement(
                "INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(6), NOW(6))")) {
            ins.setBytes(1, uuidBytes);
            ins.setString(2, email.toLowerCase());
            ins.setString(3, hash);
            ins.setString(4, fullName);
            ins.executeUpdate();
        }

        for (String role : new String[]{"LEARNER", "ADMIN"}) {
            try (PreparedStatement roleIns = context.getConnection()
                    .prepareStatement("INSERT INTO user_roles (user_id, role) VALUES (?, ?)")) {
                roleIns.setBytes(1, uuidBytes);
                roleIns.setString(2, role);
                roleIns.executeUpdate();
            }
        }
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
