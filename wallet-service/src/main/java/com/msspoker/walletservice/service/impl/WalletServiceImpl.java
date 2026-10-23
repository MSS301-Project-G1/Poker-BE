package com.msspoker.walletservice.service.impl;

import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.dto.request.DebitRequest;
import com.msspoker.walletservice.dto.response.BalanceResponse;
import com.msspoker.walletservice.dto.response.PageResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.dto.response.WalletResponse;
import com.msspoker.walletservice.entity.Wallet;
import com.msspoker.walletservice.entity.WalletTransaction;
import com.msspoker.walletservice.enums.CurrencyType;
import com.msspoker.walletservice.exception.WalletExceptions;
import com.msspoker.walletservice.mapper.WalletMapper;
import com.msspoker.walletservice.repository.WalletRepository;
import com.msspoker.walletservice.repository.WalletTransactionRepository;
import com.msspoker.walletservice.service.WalletService;
import com.msspoker.walletservice.validator.DebitValidator;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletMapper walletMapper;
    private final List<DebitValidator> debitValidators;

    @Override
    @Transactional
    @Retryable(
            retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public TransactionResponse credit(CreditRequest request) {
        CurrencyType currencyType = request.currencyType() != null ? request.currencyType() : CurrencyType.COIN;
        log.info("Processing credit: accountId={}, amount={}, currencyType={}, reason={}, idempotencyKey={}",
                request.accountId(), request.amount(), currencyType, request.reason(), request.idempotencyKey());

        Optional<WalletTransaction> existingTx = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTx.isPresent()) {
            log.info("Idempotent credit detected for key={}. Returning cached transaction.", request.idempotencyKey());
            return walletMapper.toTransactionResponse(existingTx.get());
        }

        Wallet wallet = getOrCreateWalletForUpdate(request.accountId());

        long newBalance;
        if (currencyType == CurrencyType.VIP_COIN) {
            newBalance = wallet.getVipCoinBalance() + request.amount();
            wallet.setVipCoinBalance(newBalance);
        } else {
            newBalance = wallet.getCoinBalance() + request.amount();
            wallet.setCoinBalance(newBalance);
        }
        walletRepository.save(wallet);

        WalletTransaction tx = walletMapper.toCreditTransaction(request, newBalance);
        tx.setCurrencyType(currencyType);

        tx = transactionRepository.save(tx);
        log.info("Credit success: accountId={}, currency={}, newBalance={}", request.accountId(), currencyType, newBalance);
        return walletMapper.toTransactionResponse(tx);
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public TransactionResponse debit(DebitRequest request) {
        CurrencyType currencyType = request.currencyType() != null ? request.currencyType() : CurrencyType.COIN;
        log.info("Processing debit: accountId={}, amount={}, currencyType={}, reason={}, idempotencyKey={}",
                request.accountId(), request.amount(), currencyType, request.reason(), request.idempotencyKey());

        Optional<WalletTransaction> existingTx = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTx.isPresent()) {
            log.info("Idempotent debit detected for key={}. Returning cached transaction.", request.idempotencyKey());
            return walletMapper.toTransactionResponse(existingTx.get());
        }

        Wallet wallet = getOrCreateWalletForUpdate(request.accountId());
        final long requestAmount = request.amount();

        long newBalance;
        if (currencyType == CurrencyType.VIP_COIN) {
            if (wallet.getVipCoinBalance() < requestAmount) {
                log.warn("Insufficient VIP_COIN: accountId={}, balance={}, required={}",
                        wallet.getAccountId(), wallet.getVipCoinBalance(), requestAmount);
                throw WalletExceptions.insufficientBalance();
            }
            newBalance = wallet.getVipCoinBalance() - requestAmount;
            wallet.setVipCoinBalance(newBalance);
        } else {
            debitValidators.forEach(validator -> validator.validate(wallet, requestAmount));
            newBalance = wallet.getCoinBalance() - requestAmount;
            wallet.setCoinBalance(newBalance);
        }
        walletRepository.save(wallet);

        WalletTransaction tx = walletMapper.toDebitTransaction(request, newBalance);
        tx.setCurrencyType(currencyType);

        tx = transactionRepository.save(tx);
        log.info("Debit success: accountId={}, currency={}, newBalance={}", request.accountId(), currencyType, newBalance);
        return walletMapper.toTransactionResponse(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId) {
        Wallet wallet = walletRepository.findByAccountId(accountId)
                .orElseGet(() -> walletMapper.toWallet(accountId, 0L));
        return walletMapper.toBalanceResponse(wallet);
    }

    @Override
    @Transactional
    public WalletResponse getMyWallet(UUID accountId) {
        Wallet wallet = getOrCreateWallet(accountId);
        return walletMapper.toWalletResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getMyTransactions(UUID accountId, Pageable pageable) {
        Pageable safePageable = normalizePageable(pageable);
        Page<TransactionResponse> page = transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, safePageable)
                .map(walletMapper::toTransactionResponse);
        return PageResponse.of(page);
    }

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20);
        }
        int safePage = Math.max(0, pageable.getPageNumber());
        int safeSize = Math.min(Math.max(1, pageable.getPageSize()), 100);
        return PageRequest.of(safePage, safeSize, pageable.getSort());
    }

    private Wallet getOrCreateWallet(UUID accountId) {
        return walletRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    log.info("Wallet not found for accountId={}. Initializing new wallet.", accountId);
                    Wallet newWallet = walletMapper.toWallet(accountId, 0L);
                    return walletRepository.save(newWallet);
                });
    }

    private Wallet getOrCreateWalletForUpdate(UUID accountId) {
        return walletRepository.findByAccountIdForUpdate(accountId)
                .or(() -> walletRepository.findByAccountId(accountId))
                .orElseGet(() -> {
                    log.info("Wallet not found for accountId={}. Initializing new wallet.", accountId);
                    Wallet newWallet = walletMapper.toWallet(accountId, 0L);
                    return walletRepository.save(newWallet);
                });
    }
}
