package com.roddy.domain.analysis.repository;

import com.roddy.domain.analysis.entity.UserStack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserStackRepository extends JpaRepository<UserStack, Long> {

    /** 기술 이름이 함께 필요하므로 stackDetail 을 같이 읽는다. */
    @Query("""
            select us from UserStack us
            join fetch us.stackDetail
            where us.user.id = :userId
            """)
    List<UserStack> findAllWithStackDetailByUserId(@Param("userId") Long userId);

    /** 다시 분석할 때 기술스택을 통째로 갈아끼우기 위해 쓴다. */
    void deleteAllByUserId(Long userId);
}
