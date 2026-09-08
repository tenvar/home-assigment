CREATE TABLE players (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wallets (
    user_id UUID PRIMARY KEY REFERENCES players(id),
    cash NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (cash >= 0),
    bonus NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (bonus >= 0),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE deposits (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES players(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount IN (10, 20, 50)),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CHECK ((status = 'PENDING' AND completed_at IS NULL) OR (status = 'COMPLETED' AND completed_at IS NOT NULL))
);

CREATE TABLE deposit_callbacks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES players(id),
    transaction_id UUID NOT NULL,
    deposit_id UUID REFERENCES deposits(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('COMPLETED', 'DUPLICATED', 'NOT_FOUND', 'WRONG_TRANSACTION')),
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (deposit_id IS NULL OR deposit_id = transaction_id),
    CHECK ((status = 'NOT_FOUND' AND deposit_id IS NULL) OR (status <> 'NOT_FOUND' AND deposit_id IS NOT NULL))
);

CREATE TABLE bonuses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES players(id),
    deposit_id UUID NOT NULL UNIQUE REFERENCES deposits(id),
    granted_amount NUMERIC(19,2) NOT NULL CHECK (granted_amount > 0 AND granted_amount <= 100),
    wagered_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (wagered_amount >= 0),
    target_amount NUMERIC(19,2) NOT NULL CHECK (target_amount = granted_amount * 20),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    CHECK (expires_at > created_at),
    CHECK ((status = 'ACTIVE' AND closed_at IS NULL) OR (status <> 'ACTIVE' AND closed_at IS NOT NULL))
);

CREATE TABLE rounds (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES players(id),
    stake NUMERIC(19,2) NOT NULL CHECK (stake > 0),
    stake_cash NUMERIC(19,2) NOT NULL CHECK (stake_cash >= 0),
    stake_bonus NUMERIC(19,2) NOT NULL CHECK (stake_bonus >= 0),
    payout_percent INTEGER NOT NULL CHECK (payout_percent IN (0, 50, 100, 150, 200, 400)),
    payout NUMERIC(19,2) NOT NULL CHECK (payout >= 0),
    payout_cash NUMERIC(19,2) NOT NULL CHECK (payout_cash >= 0),
    payout_bonus NUMERIC(19,2) NOT NULL CHECK (payout_bonus >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (stake = stake_cash + stake_bonus),
    CHECK (payout = payout_cash + payout_bonus)
);

CREATE TABLE ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES players(id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('DEPOSIT', 'BONUS_GRANTED', 'BET', 'PAYOUT', 'BONUS_CONVERTED', 'BONUS_EXPIRED')),
    cash_delta NUMERIC(19,2) NOT NULL,
    bonus_delta NUMERIC(19,2) NOT NULL,
    cash_after NUMERIC(19,2) NOT NULL CHECK (cash_after >= 0),
    bonus_after NUMERIC(19,2) NOT NULL CHECK (bonus_after >= 0),
    deposit_id UUID REFERENCES deposits(id),
    round_id UUID REFERENCES rounds(id),
    bonus_id UUID REFERENCES bonuses(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE idempotency_keys (
    user_id UUID NOT NULL REFERENCES players(id),
    operation VARCHAR(30) NOT NULL,
    key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, operation, key)
);

CREATE INDEX deposits_user_date ON deposits (user_id, created_at DESC, id DESC);
CREATE INDEX deposits_user_status ON deposits (user_id, status, created_at DESC, id DESC);
CREATE INDEX deposit_callbacks_user_date ON deposit_callbacks (user_id, created_at DESC, id DESC);
CREATE INDEX deposit_callbacks_transaction_date ON deposit_callbacks (transaction_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX deposit_callbacks_completed ON deposit_callbacks (deposit_id) WHERE status = 'COMPLETED';
CREATE INDEX bonuses_expiring ON bonuses (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX ledger_user_date ON ledger (user_id, created_at DESC, id DESC);
CREATE INDEX rounds_user_date ON rounds (user_id, created_at DESC, id DESC);

CREATE FUNCTION reject_ledger_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable';
END;
$$;

CREATE TRIGGER ledger_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON ledger
FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_mutation();
