package com.msspoker.walletservice.service;

import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.dto.request.DebitRequest;
import com.msspoker.walletservice.dto.response.BalanceResponse;
import com.msspoker.walletservice.dto.response.PageResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.dto.response.WalletResponse;
import com.msspoker.walletservice.entity.Wallet;
import com.msspoker.walletservice.entity.WalletTransaction;
import com.msspoker.walletservice.enums.TransactionReason;
import com.msspoker.walletservice.exception.AppException;
import com.msspoker.walletservice.mapper.WalletMapper;
import com.msspoker.walletservice.repository.WalletRepository;
import com.msspoker.walletservice.repository.WalletTransactionRepository;
import com.msspoker.walletservice.service.impl.WalletServiceImpl;
import com.msspoker.walletservice.validator.DebitValidator;
import com.msspoker.walletservice.validator.impl.SufficientBalanceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Spy
    private WalletMapper walletMapper = Mappers.getMapper(WalletMapper.class);

    private WalletServiceImpl walletService;

    private UUID accountId;
    private Wallet existingWallet;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        existingWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .balance(500L)
                .version(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Inject real SufficientBalanceValidator into the validator chain
        List<DebitValidator> debitValidators = List.of(new SufficientBalanceValidator());
        walletService = new WalletServiceImpl(walletRepository, transactionRepository, walletMapper, debitValidators);
    }

    @Nested
    @DisplayName("Credit Tests")
    class CreditTests {

        @Test
        @DisplayName("Credit success - adds balance and records transaction")
        void creditSuccess() {
            CreditRequest request = CreditRequest.builder()
                    .accountId(accountId)
                    .amount(200L)
                    .reason(TransactionReason.DAILY_CHECKIN)
                    .refId("checkin-today")
                    .idempotencyKey("checkin:2026-10-01:" + accountId)
                    .build();

            TransactionResponse expectedResponse = TransactionResponse.builder()
                    .accountId(accountId)
                    .amount(200L)
                    .balanceAfter(700L)
                    .reason(TransactionReason.DAILY_CHECKIN)
                    .idempotencyKey(request.idempotencyKey())
                    .build();

            when(transactionRepository.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
            when(walletRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingWallet));
            when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(transactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
                WalletTransaction tx = invocation.getArgument(0);
                tx.setId(UUID.randomUUID());
                return tx;
            });
            when(walletMapper.toTransactionResponse(any(WalletTransaction.class))).thenReturn(expectedResponse);

            TransactionResponse response = walletService.credit(request);

            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(200L);
            assertThat(response.balanceAfter()).isEqualTo(700L);
            assertThat(response.reason()).isEqualTo(TransactionReason.DAILY_CHECKIN);

            ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository).save(walletCaptor.capture());
            assertThat(walletCaptor.getValue().getBalance()).isEqualTo(700L);
        }

        @Test
        @DisplayName("Credit with lazy init - creates wallet if not exists")
        void creditWithLazyInit() {
            UUID newAccountId = UUID.randomUUID();
            CreditRequest request = CreditRequest.builder()
                    .accountId(newAccountId)
                    .amount(500L)
                    .reason(TransactionReason.FIRST_LOGIN_BONUS)
                    .refId("first-login")
                    .idempotencyKey("first-login:" + newAccountId)
                    .build();

            TransactionResponse expectedResponse = TransactionResponse.builder()
                    .accountId(newAccountId)
                    .amount(500L)
                    .balanceAfter(500L)
                    .build();

            when(transactionRepository.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
            when(walletRepository.findByAccountId(newAccountId)).thenReturn(Optional.empty());
            when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(transactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
                WalletTransaction tx = invocation.getArgument(0);
                tx.setId(UUID.randomUUID());
                return tx;
            });
            when(walletMapper.toTransactionResponse(any(WalletTransaction.class))).thenReturn(expectedResponse);

            TransactionResponse response = walletService.credit(request);

            assertThat(response.balanceAfter()).isEqualTo(500L);
            assertThat(response.amount()).isEqualTo(500L);
        }

        @Test
        @DisplayName("Credit idempotency - returns existing transaction without duplicate credit")
        void creditIdempotency() {
            String key = "rank-reward:match1:" + accountId;
            CreditRequest request = CreditRequest.builder()
                    .accountId(accountId)
                    .amount(300L)
                    .reason(TransactionReason.RANK_REWARD)
                    .idempotencyKey(key)
                    .build();

            WalletTransaction existingTx = WalletTransaction.builder()
                    .id(UUID.randomUUID())
                    .accountId(accountId)
                    .amount(300L)
                    .reason(TransactionReason.RANK_REWARD)
                    .idempotencyKey(key)
                    .balanceAfter(800L)
                    .createdAt(Instant.now())
                    .build();

            TransactionResponse expectedResponse = TransactionResponse.builder()
                    .id(existingTx.getId())
                    .balanceAfter(800L)
                    .build();

            when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existingTx));
            when(walletMapper.toTransactionResponse(existingTx)).thenReturn(expectedResponse);

            TransactionResponse response = walletService.credit(request);

            assertThat(response.id()).isEqualTo(existingTx.getId());
            assertThat(response.balanceAfter()).isEqualTo(800L);
            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Debit Tests")
    class DebitTests {

        @Test
        @DisplayName("Debit success - reduces balance and records negative amount")
        void debitSuccess() {
            DebitRequest request = DebitRequest.builder()
                    .accountId(accountId)
                    .amount(100L)
                    .reason(TransactionReason.RANK_ENTRY_FEE)
                    .refId("prop-1")
                    .idempotencyKey("rank-fee:prop-1:" + accountId)
                    .build();

            TransactionResponse expectedResponse = TransactionResponse.builder()
                    .accountId(accountId)
                    .amount(-100L)
                    .balanceAfter(400L)
                    .reason(TransactionReason.RANK_ENTRY_FEE)
                    .build();

            when(transactionRepository.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
            when(walletRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingWallet));
            when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(transactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
                WalletTransaction tx = invocation.getArgument(0);
                tx.setId(UUID.randomUUID());
                return tx;
            });
            when(walletMapper.toTransactionResponse(any(WalletTransaction.class))).thenReturn(expectedResponse);

            TransactionResponse response = walletService.debit(request);

            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualTo(-100L);
            assertThat(response.balanceAfter()).isEqualTo(400L);
            assertThat(response.reason()).isEqualTo(TransactionReason.RANK_ENTRY_FEE);

            ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository).save(walletCaptor.capture());
            assertThat(walletCaptor.getValue().getBalance()).isEqualTo(400L);
        }

        @Test
        @DisplayName("Debit failed - insufficient balance throws AppException")
        void debitInsufficientBalance() {
            DebitRequest request = DebitRequest.builder()
                    .accountId(accountId)
                    .amount(1000L)
                    .reason(TransactionReason.RANK_ENTRY_FEE)
                    .refId("prop-2")
                    .idempotencyKey("rank-fee:prop-2:" + accountId)
                    .build();

            when(transactionRepository.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
            when(walletRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingWallet));

            assertThatThrownBy(() -> walletService.debit(request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Số dư xu không đủ");

            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debit idempotency - returns existing transaction without debiting again")
        void debitIdempotency() {
            String key = "rank-fee:prop-1:" + accountId;
            DebitRequest request = DebitRequest.builder()
                    .accountId(accountId)
                    .amount(100L)
                    .reason(TransactionReason.RANK_ENTRY_FEE)
                    .idempotencyKey(key)
                    .build();

            WalletTransaction existingTx = WalletTransaction.builder()
                    .id(UUID.randomUUID())
                    .accountId(accountId)
                    .amount(-100L)
                    .reason(TransactionReason.RANK_ENTRY_FEE)
                    .idempotencyKey(key)
                    .balanceAfter(400L)
                    .createdAt(Instant.now())
                    .build();

            TransactionResponse expectedResponse = TransactionResponse.builder()
                    .id(existingTx.getId())
                    .balanceAfter(400L)
                    .build();

            when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existingTx));
            when(walletMapper.toTransactionResponse(existingTx)).thenReturn(expectedResponse);

            TransactionResponse response = walletService.debit(request);

            assertThat(response.id()).isEqualTo(existingTx.getId());
            assertThat(response.balanceAfter()).isEqualTo(400L);
            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Balance & Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Get balance of existing wallet")
        void getBalanceExisting() {
            BalanceResponse expectedResponse = BalanceResponse.builder()
                    .accountId(accountId).balance(500L).build();

            when(walletRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingWallet));
            when(walletMapper.toBalanceResponse(existingWallet)).thenReturn(expectedResponse);

            BalanceResponse response = walletService.getBalance(accountId);

            assertThat(response.accountId()).isEqualTo(accountId);
            assertThat(response.balance()).isEqualTo(500L);
        }

        @Test
        @DisplayName("Get balance of non-existing wallet returns 0")
        void getBalanceNonExisting() {
            UUID unknownId = UUID.randomUUID();
            BalanceResponse expectedResponse = BalanceResponse.builder()
                    .accountId(unknownId).balance(0L).build();

            when(walletRepository.findByAccountId(unknownId)).thenReturn(Optional.empty());
            when(walletMapper.toBalanceResponse(any(Wallet.class))).thenReturn(expectedResponse);

            BalanceResponse response = walletService.getBalance(unknownId);

            assertThat(response.accountId()).isEqualTo(unknownId);
            assertThat(response.balance()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Get my wallet - returns wallet details")
        void getMyWallet() {
            WalletResponse expectedResponse = WalletResponse.builder()
                    .accountId(accountId).balance(500L).build();

            when(walletRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingWallet));
            when(walletMapper.toWalletResponse(existingWallet)).thenReturn(expectedResponse);

            WalletResponse response = walletService.getMyWallet(accountId);

            assertThat(response.accountId()).isEqualTo(accountId);
            assertThat(response.balance()).isEqualTo(500L);
        }

        @Test
        @DisplayName("Get my transactions with pagination")
        void getMyTransactions() {
            Pageable pageable = PageRequest.of(0, 10);
            WalletTransaction tx = WalletTransaction.builder()
                    .id(UUID.randomUUID())
                    .accountId(accountId)
                    .amount(500L)
                    .reason(TransactionReason.FIRST_LOGIN_BONUS)
                    .idempotencyKey("first-login:" + accountId)
                    .balanceAfter(500L)
                    .createdAt(Instant.now())
                    .build();

            TransactionResponse txResponse = TransactionResponse.builder()
                    .amount(500L)
                    .reason(TransactionReason.FIRST_LOGIN_BONUS)
                    .build();

            when(transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable))
                    .thenReturn(new PageImpl<>(List.of(tx), pageable, 1));
            when(walletMapper.toTransactionResponse(tx)).thenReturn(txResponse);

            PageResponse<TransactionResponse> result = walletService.getMyTransactions(accountId, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).amount()).isEqualTo(500L);
            assertThat(result.getContent().get(0).reason()).isEqualTo(TransactionReason.FIRST_LOGIN_BONUS);
            assertThat(result.getTotalElements()).isEqualTo(1L);
        }
    }
}
