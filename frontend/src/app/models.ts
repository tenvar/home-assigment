export interface BonusProgress {
  status: 'ACTIVE' | 'COMPLETED' | 'EXPIRED';
  grantedAmount: string;
  wageredAmount: string;
  targetAmount: string;
  expiresAt: string;
}

export interface Wallet {
  userId: string;
  currency: string;
  cash: string;
  bonus: string;
  bonusProgress: BonusProgress | null;
}

export interface Transaction {
  id?: string;
  depositId?: string;
  transactionId?: string;
  amount: string;
  status: string;
  createdAt: string;
  completedAt: string | null;
  type?: string;
  cashDelta?: string;
  bonusDelta?: string;
}

export interface CallbackResponse {
  result: 'Completed' | 'Duplicated';
  callback: Transaction;
  deposit: Transaction;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Round {
  roundId: string;
  stake: string;
  payout: string;
  payoutPercent: number;
  wallet: Wallet;
}

export interface MoneyOperation {
  key: string;
  userId: string;
  kind: 'deposit' | 'bet';
  amount: string;
}

export function emptyPage<T>(): Page<T> {
  return { items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };
}
