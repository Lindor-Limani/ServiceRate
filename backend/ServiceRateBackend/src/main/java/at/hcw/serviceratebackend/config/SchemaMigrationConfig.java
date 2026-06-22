package at.hcw.serviceratebackend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaMigrationConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_expires_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(1000)");

        jdbcTemplate.execute("ALTER TABLE service_offerings ADD COLUMN IF NOT EXISTS estimated_hours DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE service_offerings ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE service_offerings ADD COLUMN IF NOT EXISTS deliverable_type VARCHAR(255) DEFAULT 'ON_SITE'");
        jdbcTemplate.execute("UPDATE service_offerings SET deliverable_type = 'ON_SITE' WHERE deliverable_type IS NULL");

        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS actual_hours DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS provider_notes TEXT");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS customer_notes TEXT");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_url VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_label VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_expires_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_status VARCHAR(255) DEFAULT 'UNPAID'");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS checkout_url VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(255) DEFAULT 'MANUAL'");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_note TEXT");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("UPDATE bookings SET payment_status = 'UNPAID' WHERE payment_status IS NULL");
        jdbcTemplate.execute("UPDATE bookings SET payment_provider = 'MANUAL' WHERE payment_provider IS NULL");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS time_entries (
                    id UUID PRIMARY KEY,
                    booking_id UUID NOT NULL REFERENCES bookings(id),
                    provider_id UUID NOT NULL REFERENCES users(id),
                    work_date DATE NOT NULL,
                    hours DOUBLE PRECISION NOT NULL,
                    note TEXT,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS payment_methods (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL REFERENCES users(id),
                    brand VARCHAR(255) NOT NULL,
                    last4 VARCHAR(4) NOT NULL,
                    holder_name VARCHAR(255),
                    expiry_month INTEGER,
                    expiry_year INTEGER,
                    provider_token VARCHAR(255) NOT NULL,
                    default_method BOOLEAN NOT NULL DEFAULT false,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE
                )
                """);
    }
}
