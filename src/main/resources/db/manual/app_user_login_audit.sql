CREATE TABLE IF NOT EXISTS dms.app_user_login_audit
(
    audit_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID,
    username              VARCHAR(150) NOT NULL,
    session_id            VARCHAR(255),
    login_type            VARCHAR(30) NOT NULL,
    login_status          VARCHAR(20) NOT NULL,
    login_time            TIMESTAMP WITH TIME ZONE,
    logout_time           TIMESTAMP WITH TIME ZONE,
    last_activity_time    TIMESTAMP WITH TIME ZONE,
    session_duration      BIGINT,
    ip_address            INET,
    user_agent            TEXT,
    browser_name          VARCHAR(100),
    browser_version       VARCHAR(50),
    operating_system      VARCHAR(100),
    device_type           VARCHAR(50),
    failure_reason        VARCHAR(500),
    logout_type           VARCHAR(30),
    mfa_status            BOOLEAN DEFAULT FALSE,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_app_user_login_audit_session
    ON dms.app_user_login_audit (session_id)
    WHERE session_id IS NOT NULL;
