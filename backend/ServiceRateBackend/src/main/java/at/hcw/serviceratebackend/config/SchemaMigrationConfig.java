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
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url TEXT");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS payout_iban VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_merchant_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_email VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_onboarding_status VARCHAR(255) DEFAULT 'NOT_CONNECTED'");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_permissions_granted BOOLEAN");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_email_confirmed BOOLEAN");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_referral_self_url VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_onboarding_state_hash VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS paypal_onboarding_state_expires_at TIMESTAMP WITH TIME ZONE");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_default_payment_method_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_connected_account_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_onboarding_status VARCHAR(255) DEFAULT 'NOT_CONNECTED'");
        jdbcTemplate.execute("UPDATE users SET paypal_onboarding_status = 'NOT_CONNECTED' WHERE paypal_onboarding_status IS NULL");
        jdbcTemplate.execute("UPDATE users SET stripe_onboarding_status = 'NOT_CONNECTED' WHERE stripe_onboarding_status IS NULL");
        tryExecute("ALTER TABLE users ALTER COLUMN profile_image_url TYPE TEXT");
        tryExecute("ALTER TABLE users ALTER COLUMN profile_image_url SET DATA TYPE TEXT");

        jdbcTemplate.execute("ALTER TABLE service_offerings ADD COLUMN IF NOT EXISTS estimated_hours DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE service_offerings ADD COLUMN IF NOT EXISTS image_url TEXT");
        jdbcTemplate.execute("ALTER TABLE service_offerings ADD COLUMN IF NOT EXISTS image_urls TEXT");
        tryExecute("ALTER TABLE service_offerings ALTER COLUMN image_url TYPE TEXT");
        tryExecute("ALTER TABLE service_offerings ALTER COLUMN image_url SET DATA TYPE TEXT");
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
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paypal_order_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paypal_capture_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS stripe_checkout_session_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS stripe_payment_method_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS stripe_connected_account_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS gross_amount DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS platform_fee_amount DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS provider_receivable_amount DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS settlement_status VARCHAR(255) DEFAULT 'NOT_READY'");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS settlement_note TEXT");
        jdbcTemplate.execute("UPDATE bookings SET payment_status = 'UNPAID' WHERE payment_status IS NULL");
        jdbcTemplate.execute("UPDATE bookings SET payment_provider = 'MANUAL' WHERE payment_provider IS NULL");
        jdbcTemplate.execute("UPDATE bookings SET settlement_status = 'NOT_READY' WHERE settlement_status IS NULL");

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
                CREATE TABLE IF NOT EXISTS reports (
                    id UUID PRIMARY KEY,
                    reporter_id UUID NOT NULL REFERENCES users(id),
                    target_type VARCHAR(255) NOT NULL,
                    target_id UUID NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    details TEXT,
                    status VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE
                )
                """);

        jdbcTemplate.execute("ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS image_data_url TEXT");
        jdbcTemplate.execute("ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS image_name VARCHAR(255)");
        tryExecute("ALTER TABLE chat_messages ALTER COLUMN content DROP NOT NULL");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS stripe_webhook_events (
                    id UUID PRIMARY KEY,
                    event_id VARCHAR(255) NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    CONSTRAINT ux_stripe_webhook_events_event_id UNIQUE (event_id)
                )
                """);

        // Exactly-once-Garantie für Bewertungen; vorhandene Duplikate stoppen den Start fail-closed.
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_reviews_booking_id ON reviews (booking_id)");
    }

    private void tryExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
            // H2 and PostgreSQL use different ALTER COLUMN syntax. One successful variant is enough.
        }
    }
}
