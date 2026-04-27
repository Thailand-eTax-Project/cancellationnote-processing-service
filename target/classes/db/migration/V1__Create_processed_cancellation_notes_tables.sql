CREATE TABLE processed_cancellation_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_note_id VARCHAR(100) NOT NULL,
    cancellation_note_number VARCHAR(50) NOT NULL,
    issue_date DATE NOT NULL,
    cancellation_date DATE NOT NULL,
    cancelled_invoice_number VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(15, 2),
    total_tax NUMERIC(15, 2),
    total NUMERIC(15, 2),
    original_xml TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cancellation_note_number ON processed_cancellation_notes(cancellation_note_number);
CREATE INDEX idx_cancellation_source_note_id ON processed_cancellation_notes(source_note_id);
CREATE INDEX idx_cancellation_status ON processed_cancellation_notes(status);
CREATE INDEX idx_cancellation_issue_date ON processed_cancellation_notes(issue_date);

CREATE TABLE cancellation_note_parties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cancellation_note_id UUID NOT NULL,
    party_type VARCHAR(10) NOT NULL,
    name VARCHAR(200) NOT NULL,
    tax_id VARCHAR(50),
    tax_id_scheme VARCHAR(20),
    street_address VARCHAR(500),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) NOT NULL,
    email VARCHAR(200)
);

CREATE INDEX idx_cn_party_note ON cancellation_note_parties(cancellation_note_id);
CREATE INDEX idx_cn_party_type ON cancellation_note_parties(party_type);

CREATE TABLE cancellation_note_line_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cancellation_note_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    tax_rate NUMERIC(5, 2) NOT NULL,
    line_total NUMERIC(15, 2) NOT NULL,
    tax_amount NUMERIC(15, 2) NOT NULL
);

CREATE INDEX idx_cn_line_item_note ON cancellation_note_line_items(cancellation_note_id);

ALTER TABLE cancellation_note_parties
ADD CONSTRAINT fk_cn_party_note
FOREIGN KEY (cancellation_note_id)
REFERENCES processed_cancellation_notes(id)
ON DELETE CASCADE;

ALTER TABLE cancellation_note_line_items
ADD CONSTRAINT fk_cn_line_item_note
FOREIGN KEY (cancellation_note_id)
REFERENCES processed_cancellation_notes(id)
ON DELETE CASCADE;
