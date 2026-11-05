CREATE TABLE initial_stock (
    item_id UUID PRIMARY KEY REFERENCES items(id),
    quantity NUMERIC NOT NULL CHECK (quantity >= 0 AND quantity < 'Infinity'::NUMERIC),
    quantity_scale INTEGER NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL CHECK (isfinite(occurred_at))
);
