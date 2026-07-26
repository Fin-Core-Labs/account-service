package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;

    private static SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request){
        log.info("Create account for: {}", request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account already exists");
        }
        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialBalance());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS
                        ? new BigDecimal("10000")
                        : new BigDecimal("50000"));
        Account saveAccount = accountRepository.save(account);
        log.info("Save account for: {}", saveAccount.getAccountNumber());
        return mapToResponse(saveAccount);
    }
    // Get Account by Account Number
    public AccountResponse getAccount(String accountNumber){
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        return mapToResponse(account);
    }
    // Get Account Balance
    public BigDecimal getBalance(String accountNumber){
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        return account.getBalance();
    }
    // Block Account called by Fraud detection service via Kafka
    public void blockAccount(String accountNumber){
        log.info("Block account for: {}", accountNumber);
        Account account =  accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Block account for: {}", accountNumber);
    }
    // Deduct Balance from sender account
    public void deductBalance(String accountNumber, BigDecimal amount){
        log.info("Deduct account {} from account: {}",amount, accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        //
        if(account.getStatus() != (AccountStatus.ACTIVE)){
            throw new RuntimeException("Account is not active");
        }
        //
        if(account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Insufficient funds");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance updated. New Balance: {} , account.getBalance()");
    }
    // Credit Balance - Called by transaction service via Kafka
    public void creditBalance(String accountNumber, BigDecimal amount){
        log.info("Crediting {} to account: {}",amount, accountNumber);
        Account account =  accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Credit account updated. New Balance: {} , account.getBalance()");
    }

    //Generate unique 12-digit account number

    private String generateAccountNumber(){
        String accountNumber;
        do{
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        }while(accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account){
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setAccountStatus(account.getStatus());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        return response;
    }
}
