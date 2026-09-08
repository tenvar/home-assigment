import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { emptyPage, MoneyOperation, Round, Transaction, Wallet } from './models';
import { messages } from './messages';
import { TransactionsComponent } from './transactions.component';
import { WalletApi } from './wallet-api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, TransactionsComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly api = inject(WalletApi);
  readonly text = messages;
  readonly language = document.documentElement.lang;
  readonly depositAmounts = ['10.00', '20.00', '50.00'];
  userId = localStorage.getItem('wallet.userId') ?? '';
  wallet: Wallet | null = null;
  deposits = emptyPage<Transaction>();
  callbacks = emptyPage<Transaction>();
  ledger = emptyPage<Transaction>();
  depositAmount = '20.00';
  stake = '2.00';
  depositId = '';
  callbackAmount = '20.00';
  round: Round | null = null;
  registering = false;
  loading = false;
  completing = false;
  notice = '';
  error = '';
  operations: MoneyOperation[] = this.restoreOperations();
  readonly inFlight = new Set<string>();
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private revision = 0;
  private destroyed = false;

  ngOnInit() {
    if (this.userId) void this.refresh();
  }

  ngOnDestroy() {
    this.destroyed = true;
    this.revision++;
    clearTimeout(this.expiryTimer);
  }

  get playing() {
    return this.operations.some(operation => operation.kind === 'bet');
  }

  get progress() {
    const bonus = this.wallet?.bonusProgress;
    return bonus ? Math.min(100, Number(bonus.wageredAmount) / Number(bonus.targetAmount) * 100) : 0;
  }

  get resultLabel() {
    if (!this.round) return this.text.resultEmpty;
    if (this.round.payoutPercent > 100) return this.text.won;
    if (this.round.payoutPercent === 100) return this.text.returned;
    return this.round.payoutPercent > 0 ? this.text.partial : this.text.lost;
  }

  async createPlayer() {
    if (this.registering || this.operations.length) return;
    this.registering = true;
    this.error = '';
    try {
      const wallet = await this.api.register();
      this.revision++;
      clearTimeout(this.expiryTimer);
      this.userId = wallet.userId;
      localStorage.setItem('wallet.userId', wallet.userId);
      this.wallet = wallet;
      this.round = null;
      this.deposits = emptyPage();
      this.callbacks = emptyPage();
      this.ledger = emptyPage();
      this.depositId = '';
      this.notice = this.text.created;
      await this.refresh();
    } catch (error) {
      this.showError(error);
    } finally {
      this.registering = false;
    }
  }

  async refresh() {
    if (!this.userId || this.destroyed) return;
    clearTimeout(this.expiryTimer);
    const revision = ++this.revision;
    this.loading = true;
    try {
      const [deposits, callbacks] = await Promise.all([
        this.api.deposits(this.userId, this.deposits.page), this.api.callbacks(this.userId, this.callbacks.page)
      ]);
      const wallet = await this.api.wallet(this.userId);
      const ledger = await this.api.ledger(this.userId, this.ledger.page);
      if (revision !== this.revision) return;
      this.wallet = wallet;
      this.deposits = deposits;
      this.callbacks = callbacks;
      this.ledger = ledger;
      this.scheduleExpiry();
    } catch (error) {
      if (revision !== this.revision) return;
      this.showError(error);
    } finally {
      if (revision === this.revision) {
        this.loading = false;
      }
    }
  }

  async deposit() {
    if (!this.wallet || this.registering) return;
    await this.startOperation('deposit', this.depositAmount);
  }

  async play() {
    if (!this.wallet || this.playing || this.registering) return;
    const stake = this.stake.trim().replace(',', '.');
    if (!/^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/.test(stake) || Number(stake) <= 0) {
      this.error = this.text.invalid;
      return;
    }
    const [whole, fraction = ''] = stake.split('.');
    await this.startOperation('bet', `${whole}.${fraction.padEnd(2, '0')}`);
  }

  async execute(operation: MoneyOperation) {
    if (this.inFlight.has(operation.key)) return;
    this.inFlight.add(operation.key);
    this.error = '';
    try {
      const response = await this.api.execute(operation);
      this.removeOperation(operation.key);
      if (operation.kind === 'bet') {
        this.round = response as Round;
      } else {
        this.depositId = (response as Transaction).depositId ?? '';
        this.callbackAmount = (response as Transaction).amount;
        this.deposits.page = 0;
        this.notice = this.text.depositPending;
      }
      this.ledger.page = 0;
      await this.refresh();
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500) {
        this.removeOperation(operation.key);
        this.showError(error);
        if (error.error?.code === 'WALLET_CHANGED') await this.refresh();
      } else {
        this.error = this.text.uncertain;
      }
    } finally {
      this.inFlight.delete(operation.key);
    }
  }

  async sendCallback() {
    if (!this.wallet || this.completing) return;
    const amount = this.normalizeAmount(this.callbackAmount);
    if (!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(this.depositId.trim()) || !amount) {
      this.error = this.text.invalid;
      return;
    }
    this.completing = true;
    this.error = '';
    try {
      const response = await this.api.callback(this.userId, this.depositId.trim(), amount);
      await this.refresh();
      this.notice = response.result === 'Completed' ? this.text.processed : this.text.duplicate;
    } catch (error) {
      this.showError(error);
      await this.refresh();
    } finally {
      this.completing = false;
    }
  }

  changePage(kind: 'deposits' | 'callbacks' | 'ledger', page: number) {
    this[kind].page = page;
    void this.refresh();
  }

  bonusStatus(status: string) {
    return status === 'ACTIVE' ? this.text.active : status === 'COMPLETED' ? this.text.completed : this.text.expired;
  }

  private async startOperation(kind: 'deposit' | 'bet', amount: string) {
    const operation: MoneyOperation = { key: crypto.randomUUID(), userId: this.userId, kind, amount };
    this.operations = [...this.operations, operation];
    this.persistOperations();
    await this.execute(operation);
  }

  private removeOperation(key: string) {
    this.operations = this.operations.filter(operation => operation.key !== key);
    this.persistOperations();
  }

  private persistOperations() {
    localStorage.setItem('wallet.operations', JSON.stringify(this.operations));
  }

  private restoreOperations(): MoneyOperation[] {
    try {
      const saved: unknown = JSON.parse(localStorage.getItem('wallet.operations') ?? '[]');
      if (!Array.isArray(saved)) return [];
      return saved.filter((item): item is MoneyOperation => item && item.userId === this.userId
        && typeof item.key === 'string' && typeof item.amount === 'string'
        && (item.kind === 'deposit' || item.kind === 'bet'));
    } catch {
      return [];
    }
  }

  private scheduleExpiry() {
    const bonus = this.wallet?.bonusProgress;
    if (bonus?.status !== 'ACTIVE') return;
    const delay = Math.max(1000, Date.parse(bonus.expiresAt) - Date.now() + 100);
    this.expiryTimer = setTimeout(() => void this.refresh(), Math.min(delay, 2147483647));
  }

  private normalizeAmount(value: string): string | null {
    const amount = value.trim().replace(',', '.');
    if (!/^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) return null;
    const [whole, fraction = ''] = amount.split('.');
    return `${whole}.${fraction.padEnd(2, '0')}`;
  }

  private showError(error: unknown) {
    if (!(error instanceof HttpErrorResponse) || error.status === 0) {
      this.error = this.text.network;
      return;
    }
    const code: string = error.error?.code ?? '';
    const errors: Record<string, string> = {
      INSUFFICIENT_FUNDS: this.text.insufficient, BONUS_BET_LIMIT_EXCEEDED: this.text.bonusLimit,
      VALIDATION_ERROR: this.text.invalid, DEPOSIT_NOT_FOUND: this.text.notFound,
      USER_NOT_FOUND: this.text.playerMissing, IDEMPOTENCY_CONFLICT: this.text.conflict,
      WALLET_CHANGED: this.text.walletChanged, WRONG_TRANSACTION: this.text.wrongTransaction
    };
    this.error = errors[code] ?? this.text.internal;
    if (code === 'USER_NOT_FOUND') {
      this.userId = '';
      this.wallet = null;
      this.operations = [];
      this.persistOperations();
      localStorage.removeItem('wallet.userId');
    }
  }
}
