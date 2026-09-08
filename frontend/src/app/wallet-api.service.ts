import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, retry, throwError, timeout, timer } from 'rxjs';
import { CallbackResponse, MoneyOperation, Page, Round, Transaction, Wallet } from './models';

@Injectable({ providedIn: 'root' })
export class WalletApi {
  private readonly http = inject(HttpClient);

  register() {
    return firstValueFrom(this.http.post<Wallet>('/api/register', {}).pipe(timeout(15000)));
  }

  wallet(userId: string) {
    return firstValueFrom(this.http.get<Wallet>('/api/cashier/wallet', { params: { userId } }).pipe(timeout(15000)));
  }

  deposits(userId: string, page = 0, status?: string) {
    const params: Record<string, string | number> = { userId, page, size: 10 };
    if (status) params['status'] = status;
    return firstValueFrom(this.http.get<Page<Transaction>>('/api/cashier/deposits', { params }).pipe(timeout(15000)));
  }

  ledger(userId: string, page = 0) {
    return firstValueFrom(this.http.get<Page<Transaction>>('/api/cashier/ledger', {
      params: { userId, page, size: 10 }
    }).pipe(timeout(15000)));
  }

  callbacks(userId: string, page = 0) {
    return firstValueFrom(this.http.get<Page<Transaction>>('/api/cashier/deposit-callbacks', {
      params: { userId, page, size: 10 }
    }).pipe(timeout(15000)));
  }

  callback(userId: string, transactionId: string, amount: string) {
    return firstValueFrom(this.http.post<CallbackResponse>('/api/cashier/deposit-callbacks', {
      userId, transactionId, amount
    }).pipe(timeout(15000)));
  }

  execute(operation: MoneyOperation) {
    const path = operation.kind === 'bet' ? '/api/bet' : '/api/cashier/deposits';
    return firstValueFrom(this.http.post<Round | Transaction>(path, {
      userId: operation.userId, amount: operation.amount
    }, { headers: { 'Idempotency-Key': operation.key } }).pipe(
      timeout(15000),
      retry({
        count: 2,
        delay: (error: unknown) => error instanceof HttpErrorResponse && error.status > 0 && error.status < 500
          ? throwError(() => error) : timer(1000)
      })
    ));
  }
}
