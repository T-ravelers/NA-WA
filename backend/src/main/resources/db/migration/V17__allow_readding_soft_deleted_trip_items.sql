ALTER TABLE trip_items
    DROP INDEX uq_trip_items_trip_item_date,
    DROP INDEX uq_trip_items_trip_appointment;

CREATE UNIQUE INDEX uq_trip_items_trip_item_date
    ON trip_items (trip_id, item_id, visit_date, (IF(deleted_at IS NULL, 1, NULL)));

CREATE UNIQUE INDEX uq_trip_items_trip_appointment
    ON trip_items (trip_id, appointment_id, (IF(deleted_at IS NULL, 1, NULL)));
