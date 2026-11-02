CREATE TABLE items (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    unit TEXT NOT NULL CHECK (unit IN ('PAIR', 'UNIT', 'LITRE', 'ROLL', 'KG'))
);
