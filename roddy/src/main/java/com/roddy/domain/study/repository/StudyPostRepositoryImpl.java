package com.roddy.domain.study.repository;

import com.roddy.domain.study.dto.request.StudySearchCondition;
import com.roddy.domain.study.entity.StudyPost;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class StudyPostRepositoryImpl implements StudyPostRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<StudyPost> search(StudySearchCondition condition, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<StudyPost> root = idQuery.from(StudyPost.class);

        List<Predicate> predicates = buildPredicates(condition, cb, root);
        // 조인이 없어 distinct 가 필요 없다. MySQL 은 정렬 컬럼(created_at)이 select 에 없으면
        // "ORDER BY ... incompatible with DISTINCT" 로 쿼리를 거부한다(H2 테스트에서는 드러나지 않았다).
        idQuery.select(root.get("id"));
        idQuery.where(predicates.toArray(Predicate[]::new));
        idQuery.orderBy(buildOrders(cb, root, pageable));

        TypedQuery<Long> query = entityManager.createQuery(idQuery);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<Long> ids = query.getResultList();

        long total = count(condition);
        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, total);
        }

        List<StudyPost> posts = entityManager.createQuery("""
                        select sp
                        from StudyPost sp
                        where sp.id in :ids
                        """, StudyPost.class)
                .setParameter("ids", ids)
                .getResultList();
        posts.sort(Comparator.comparingInt(post -> ids.indexOf(post.getId())));

        return new PageImpl<>(posts, pageable, total);
    }

    @Override
    public Optional<StudyPost> findDetailById(Long studyId) {
        return entityManager.createQuery("""
                        select sp
                        from StudyPost sp
                        join fetch sp.author
                        where sp.id = :studyId
                        """, StudyPost.class)
                .setParameter("studyId", studyId)
                .getResultList()
                .stream()
                .findFirst();
    }

    private long count(StudySearchCondition condition) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<StudyPost> root = countQuery.from(StudyPost.class);

        List<Predicate> predicates = buildPredicates(condition, cb, root);
        countQuery.select(cb.countDistinct(root));
        countQuery.where(predicates.toArray(Predicate[]::new));

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(StudySearchCondition condition, CriteriaBuilder cb, Root<StudyPost> root) {
        List<Predicate> predicates = new ArrayList<>();

        if (condition.getStatus() != null) {
            predicates.add(cb.equal(root.get("status"), condition.getStatus()));
        }
        if (condition.getMode() != null) {
            predicates.add(cb.equal(root.get("mode"), condition.getMode()));
        }
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = toLikePattern(condition.getKeyword());
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), keyword),
                    cb.like(cb.lower(root.get("content")), keyword),
                    cb.like(cb.lower(cb.coalesce(root.get("location"), "")), keyword)
            ));
        }

        return predicates;
    }

    private List<Order> buildOrders(CriteriaBuilder cb, Root<StudyPost> root, Pageable pageable) {
        List<Order> orders = new ArrayList<>();
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(sort -> orders.add(
                    sort.isAscending() ? cb.asc(root.get(sort.getProperty())) : cb.desc(root.get(sort.getProperty()))
            ));
        } else {
            orders.add(cb.desc(root.get("createdAt")));
        }
        // 같은 시각에 만든 글이 페이지 경계에서 빠지거나 겹치지 않도록 id 로 순서를 고정한다.
        orders.add(cb.desc(root.get("id")));
        return orders;
    }

    private String toLikePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
