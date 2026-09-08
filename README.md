# Wallet

A single-page wallet application with deposits, bonuses, and game rounds. The interface is available in English and Belarusian.

## Requirements

The only requirement is an installed and running Docker environment with Docker Compose. Java, Gradle, Node.js, npm, Nginx, and PostgreSQL are built or provided inside the containers.

## Start the application

1. Clone or unpack the repository.
2. Open a terminal in the repository root, where `docker-compose.yml` is located.
3. Build and start the application:

```sh
docker compose up --build
```

The first build can take several minutes. Once all three services are running, open [http://localhost:8080](http://localhost:8080).

## Using the application

1. Select `Create Player`. The player ID is stored in the browser's `localStorage`.
2. Create a deposit of 10, 20, or 50 EUR. The default amount is 20 EUR. A new deposit remains `PENDING` until a manual callback is sent.
3. The first completed deposit of at least 20 EUR grants a matching bonus, capped at 100 EUR. A 10 EUR deposit does not consume bonus eligibility.
4. Enter a stake and select `Play` to run a game round.
5. Transaction history shows deposits and game rounds, with 10 records per page. Deposit rows show the deposit status; game rows show the round result.
6. To complete a pending deposit, enter its transaction ID and amount in the callback form. A matching ID and amount complete the deposit. Repeating a completed callback creates a `DUPLICATED` callback record without crediting the wallet again. An unknown ID returns `NOT_FOUND`; a mismatched amount returns `WRONG_TRANSACTION`.

The `English / Беларуская` language selector reloads the localized page without losing the current player. If the response to a money operation is interrupted, `Retry` repeats the original request with the same idempotency key, including after a page reload. A new stake remains disabled while the result of the previous stake is unknown.

## API

The frontend server exposes the backend under the `/api` prefix. For example, the backend route `POST /register` is available to the browser as `POST http://localhost:8080/api/register`.

| Method | Path without `/api` | Purpose |
| --- | --- | --- |
| POST | `/register` | Create a player |
| GET | `/cashier/wallet?userId=...` | Get balances and bonus wagering progress |
| POST | `/cashier/deposits` | Create a pending deposit |
| GET | `/cashier/deposits?userId=...&page=0&size=10` | List deposits; accepts an optional `status` filter |
| GET | `/cashier/deposits/{id}?userId=...` | Get one deposit |
| POST | `/cashier/deposit-callbacks` | Process a callback containing a transaction ID and amount |
| GET | `/cashier/deposit-callbacks?userId=...&page=0&size=10` | List callback attempts |
| GET | `/cashier/ledger?userId=...&page=0&size=10` | List deposits and game rounds |
| POST | `/bet` | Place a stake and receive the game result |
| GET | `/health` | Check backend availability |

Deposit creation and game requests accept `{"userId":"<uuid>","amount":"20.00"}` and require an `Idempotency-Key: <uuid>` header. A callback accepts `{"userId":"<uuid>","transactionId":"<uuid>","amount":"20.00"}`. Registration has no request body. Monetary values are strings with two decimal places, and timestamps use ISO 8601 in UTC.
