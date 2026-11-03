CREATE TABLE stock_entries (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL CHECK (isfinite(occurred_at))
);

CREATE TABLE stock_entry_lines (
    entry_id UUID NOT NULL REFERENCES stock_entries(id),
    line_number INTEGER NOT NULL CHECK (line_number >= 0),
    item_id UUID NOT NULL REFERENCES items(id),
    quantity NUMERIC NOT NULL CHECK (quantity > 0 AND quantity < 'Infinity'::NUMERIC),
    quantity_scale INTEGER NOT NULL,
    PRIMARY KEY (entry_id, line_number),
    UNIQUE (entry_id, item_id)
);
