package com.LDQuang.mini_ledger.domain.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT a.id FROM Account a WHERE a.id = :id AND a.userId = :userId")
    Optional<Long> findOwnedId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT a.id FROM Account a WHERE a.accountNumber = :accountNumber "
            + "AND a.accountNumber NOT LIKE :systemPrefix")
    Optional<Long> findPublicIdByAccountNumber(@Param("accountNumber") String accountNumber,
                                                @Param("systemPrefix") String systemPrefix);

    boolean existsByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT a.id FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    List<Account> findByUserIdOrderByIdAsc(Long userId);
}
