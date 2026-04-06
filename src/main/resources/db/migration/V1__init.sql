CREATE TABLE pay_users (
                           id BIGSERIAL PRIMARY KEY,
                           external_user_id BIGINT NOT NULL,
                           user_name VARCHAR(50) NOT NULL,
                           email VARCHAR(100) NOT NULL,
                           status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                           created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                           updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE pay_accounts (
                              id BIGSERIAL PRIMARY KEY,
                              pay_user_id BIGINT NOT NULL,
                              account_number VARCHAR(30) NOT NULL,
                              bank_code VARCHAR(20),
                              owner_name VARCHAR(50) NOT NULL,
                              balance INTEGER NOT NULL DEFAULT 0, -- ledger 기반 캐시
                              status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                              created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

                              CONSTRAINT fk_pay_accounts_user
                                  FOREIGN KEY (pay_user_id) REFERENCES pay_users(id)
);

CREATE TABLE pay_products (
                              id BIGSERIAL PRIMARY KEY,
                              category VARCHAR(30) NOT NULL,
                              name VARCHAR(100) NOT NULL,
                              description VARCHAR(255),
                              image_url VARCHAR(255),
                              original_price INTEGER NOT NULL,
                              sale_price INTEGER NOT NULL,
                              discount_rate NUMERIC(5,2),
                              status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                              created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE pay_payments (
                              id BIGSERIAL PRIMARY KEY,
                              pay_user_id BIGINT NOT NULL,
                              pay_account_id BIGINT NOT NULL,
                              external_order_id VARCHAR(50) NOT NULL UNIQUE,
                              payment_type VARCHAR(20) NOT NULL,
                              pay_product_id BIGINT,
                              title VARCHAR(100) NOT NULL,
                              amount INTEGER NOT NULL,
                              point_amount INTEGER NOT NULL DEFAULT 0,
                              coupon_discount_amount INTEGER NOT NULL DEFAULT 0,
                              status VARCHAR(20) NOT NULL DEFAULT 'READY',
                              approved_at TIMESTAMP,
                              created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

                              CONSTRAINT fk_payments_user
                                  FOREIGN KEY (pay_user_id) REFERENCES pay_users(id),

                              CONSTRAINT fk_payments_account
                                  FOREIGN KEY (pay_account_id) REFERENCES pay_accounts(id)
);

CREATE TABLE pay_transactions (
                                  id BIGSERIAL PRIMARY KEY,
                                  pay_account_id BIGINT NOT NULL,
                                  pay_payment_id BIGINT,
                                  transaction_type VARCHAR(20) NOT NULL,
                                  direction VARCHAR(10) NOT NULL,
                                  amount INTEGER NOT NULL,
                                  category VARCHAR(30),
                                  occurred_at TIMESTAMP NOT NULL,
                                  created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                                  CONSTRAINT fk_transactions_account
                                      FOREIGN KEY (pay_account_id) REFERENCES pay_accounts(id),

                                  CONSTRAINT fk_transactions_payment
                                      FOREIGN KEY (pay_payment_id) REFERENCES pay_payments(id)
);


CREATE TABLE pay_ledger (
                            id BIGSERIAL PRIMARY KEY,
                            transaction_id BIGINT NOT NULL,
                            pay_account_id BIGINT NOT NULL,
                            type VARCHAR(10) NOT NULL, -- DEBIT / CREDIT
                            amount INTEGER NOT NULL,
                            balance_after INTEGER NOT NULL,
                            reference_type VARCHAR(20) NOT NULL,
                            reference_id BIGINT NOT NULL,
                            created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                            CONSTRAINT fk_ledger_transaction
                                FOREIGN KEY (transaction_id) REFERENCES pay_transactions(id),

                            CONSTRAINT fk_ledger_account
                                FOREIGN KEY (pay_account_id) REFERENCES pay_accounts(id)
);

CREATE TABLE pay_refunds (
                             id BIGSERIAL PRIMARY KEY,
                             pay_payment_id BIGINT NOT NULL,
                             refund_amount INTEGER NOT NULL,
                             reason VARCHAR(255),
                             status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
                             refunded_at TIMESTAMP,
                             created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                             CONSTRAINT fk_refunds_payment
                                 FOREIGN KEY (pay_payment_id) REFERENCES pay_payments(id)
);

CREATE TABLE pay_idempotency (
                                 id BIGSERIAL PRIMARY KEY,
                                 pay_payment_id BIGINT,
                                 idempotency_key VARCHAR(100) NOT NULL UNIQUE,
                                 external_order_id VARCHAR(50) NOT NULL,
                                 payload JSONB,
                                 delivered_at TIMESTAMP,
                                 response_payload JSONB,
                                 status VARCHAR(20) NOT NULL,
                                 created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE pay_webhook_logs (
                                  id BIGSERIAL PRIMARY KEY,
                                  event_type VARCHAR(50) NOT NULL,
                                  external_order_id VARCHAR(50),
                                  payload JSONB NOT NULL,
                                  delivered_at TIMESTAMP,
                                  response_code INTEGER,
                                  status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
                                  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pay_accounts_user ON pay_accounts(pay_user_id);
CREATE INDEX idx_pay_payments_user ON pay_payments(pay_user_id);
CREATE INDEX idx_pay_payments_account ON pay_payments(pay_account_id);
CREATE INDEX idx_pay_transactions_account ON pay_transactions(pay_account_id);
CREATE INDEX idx_pay_ledger_account ON pay_ledger(pay_account_id);
CREATE INDEX idx_pay_ledger_transaction ON pay_ledger(transaction_id);
CREATE INDEX idx_pay_idempotency_key ON pay_idempotency(idempotency_key);