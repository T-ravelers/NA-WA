-- Add EVENT and PLACE schedules for date-only planning and appointment confirmation.
-- Exact activity time remains owned by appointments.activity_start_at/end_at.

SET NAMES utf8mb4;

ALTER TABLE appointments
    ADD CONSTRAINT uq_appointments_appointment_item
        UNIQUE (appointment_id, item_id);

CREATE TABLE trip_items (
    trip_item_id BIGINT NOT NULL AUTO_INCREMENT,
    trip_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    appointment_id BIGINT NULL,
    visit_date DATE NOT NULL,
    trip_item_status ENUM('ADDED','CONFIRMED') NOT NULL DEFAULT 'ADDED',
    display_order SMALLINT NOT NULL DEFAULT 0,
    note VARCHAR(500) NULL,
    confirmed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_trip_items PRIMARY KEY (trip_item_id),
    CONSTRAINT uq_trip_items_trip_item_date
        UNIQUE (trip_id, item_id, visit_date),
    CONSTRAINT uq_trip_items_trip_appointment
        UNIQUE (trip_id, appointment_id),
    INDEX idx_trip_items_trip_timeline
        (trip_id, visit_date, display_order, trip_item_id),
    INDEX idx_trip_items_item_status
        (item_id, trip_item_status),
    INDEX idx_trip_items_appointment_item
        (appointment_id, item_id),
    CONSTRAINT chk_trip_items_display_order CHECK (display_order >= 0),
    CONSTRAINT chk_trip_items_status CHECK (
        (trip_item_status = 'ADDED'
            AND appointment_id IS NULL
            AND confirmed_at IS NULL)
        OR (trip_item_status = 'CONFIRMED'
            AND appointment_id IS NOT NULL
            AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT fk_trip_items_trip
        FOREIGN KEY (trip_id) REFERENCES trips (trip_id),
    CONSTRAINT fk_trip_items_item
        FOREIGN KEY (item_id) REFERENCES explore_items (item_id),
    CONSTRAINT fk_trip_items_appointment_item
        FOREIGN KEY (appointment_id, item_id)
        REFERENCES appointments (appointment_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
