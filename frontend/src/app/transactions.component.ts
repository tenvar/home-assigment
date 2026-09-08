import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { emptyPage, Page, Transaction } from './models';
import { messages } from './messages';

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './transactions.component.html'
})
export class TransactionsComponent {
  @Input({ required: true }) title = '';
  @Input() data: Page<Transaction> = emptyPage();
  @Input() emptyText = '';
  @Input() busy = false;
  @Input() showType = false;
  @Output() pageChange = new EventEmitter<number>();
  readonly text = messages;
  copiedId = '';
  copyError = '';
  private resetTimer?: ReturnType<typeof setTimeout>;

  async copy(row: Transaction) {
    const id = this.transactionId(row);
    if (!id) return;
    try {
      await navigator.clipboard.writeText(id);
      this.copiedId = id;
      this.copyError = '';
      clearTimeout(this.resetTimer);
      this.resetTimer = setTimeout(() => this.copiedId = '', 1800);
    } catch {
      this.copyError = messages.copyFailed;
    }
  }

  details(row: Transaction): string {
    const types: Record<string, string> = {
      DEPOSIT: messages.depositType, BONUS_GRANTED: messages.grant, BET: messages.betType,
      PAYOUT: messages.payout, BONUS_CONVERTED: messages.conversion, BONUS_EXPIRED: messages.bonusExpired
    };
    if (!row.type) return '';
    return `${types[row.type] ?? row.type} · ${messages.cash}: ${row.cashDelta} EUR · ${messages.bonus}: ${row.bonusDelta} EUR`;
  }

  transactionId(row: Transaction): string {
    return row.transactionId || row.id || row.depositId || '';
  }

  status(row: Transaction): string {
    const statuses: Record<string, string> = {
      PENDING: messages.pending,
      COMPLETED: messages.completed,
      DUPLICATED: messages.duplicate,
      NOT_FOUND: messages.notFound,
      WRONG_TRANSACTION: messages.wrongTransaction,
      STAKE_RETURNED: messages.stakeReturned,
      PARTIAL_RETURN: messages.partialReturn,
      WON: messages.gameWon,
      LOST: messages.gameLost
    };
    return statuses[row.status] ?? row.status;
  }

  type(row: Transaction): string {
    return row.type === 'GAME' ? messages.game : messages.depositType;
  }
}
