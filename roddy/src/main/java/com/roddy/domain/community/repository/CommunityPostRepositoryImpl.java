package com.roddy.domain.community.repository;

import com.roddy.domain.community.dto.request.CommunityPostSearchCondition;
import com.roddy.domain.community.entity.CommunityPost;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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

@Repository
public class CommunityPostRepositoryImpl implements CommunityPostRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<CommunityPost> search(CommunityPostSearchCondition condition, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<CommunityPost> root = idQuery.from(CommunityPost.class);
        root.join("author", JoinType.INNER);
        Join<CommunityPost, String> techStacks = root.join("techStacks", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(condition, cb, root, techStacks);
        idQuery.select(root.get("id")).distinct(true);
        idQuery.where(predicates.toArray(Predicate[]::new));
        idQuery.orderBy(buildOrders(cb, root, pageable));

        TypedQuery<Long> idTypedQuery = entityManager.createQuery(idQuery);
        idTypedQuery.setFirstResult((int) pageable.getOffset());
        idTypedQuery.setMaxResults(pageable.getPageSize());
        List<Long> ids = idTypedQuery.getResultList();

        long total = count(condition);
        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, total);
        }

        List<CommunityPost> posts = findAllWithAuthorAndTechStacksByIds(ids);
        posts.sort(Comparator.comparingInt(post -> ids.indexOf(post.getId())));
        return new PageImpl<>(posts, pageable, total);
    }

    @Override
    public List<CommunityPost> findAllWithAuthorAndTechStacksByIds(List<Long> ids) {
        return entityManager.createQuery("""
                        select distinct p
                        from CommunityPost p
                        join fetch p.author
                        left join fetch p.techStacks
                        where p.id in :ids
                        """, CommunityPost.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public java.util.Optional<CommunityPost> findDetailById(Long id) {
        return entityManager.createQuery("""
                        select distinct p
                        from CommunityPost p
                        join fetch p.author
                        left join fetch p.images
                        left join fetch p.techStacks
                        where p.id = :id
                        """, CommunityPost.class)
                .setParameter("id", id)
                .getResultList()
                .stream()
                .findFirst();
    }

    private long count(CommunityPostSearchCondition condition) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<CommunityPost> root = countQuery.from(CommunityPost.class);
        root.join("author", JoinType.INNER);
        Join<CommunityPost, String> techStacks = root.join("techStacks", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(condition, cb, root, techStacks);
        countQuery.select(cb.countDistinct(root));
        countQuery.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CommunityPostSearchCondition condition,
            CriteriaBuilder cb,
            Root<CommunityPost> root,
            Join<CommunityPost, String> techStacks
    ) {
        List<Predicate> predicates = new ArrayList<>();

        if (condition.getPostCategory() != null) {
            predicates.add(cb.equal(root.get("postCategory"), condition.getPostCategory()));
        }
        if (condition.getJobCategory() != null) {
            predicates.add(cb.equal(root.get("jobCategory"), condition.getJobCategory()));
        }
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = toLikePattern(condition.getKeyword());
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), keyword),
                    cb.like(cb.lower(cb.coalesce(root.get("company"), "")), keyword),
                    cb.like(cb.lower(cb.coalesce(root.get("position"), "")), keyword),
                    cb.like(cb.lower(techStacks), keyword)
            ));
        }
        if (StringUtils.hasText(condition.getCompany())) {
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("company"), "")), toLikePattern(condition.getCompany())));
        }
        if (StringUtils.hasText(condition.getPosition())) {
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("position"), "")), toLikePattern(condition.getPosition())));
        }
        if (StringUtils.hasText(condition.getTechStack())) {
            predicates.add(cb.like(cb.lower(techStacks), toLikePattern(condition.getTechStack())));
        }

        return predicates;
    }

    private List<Order> buildOrders(CriteriaBuilder cb, Root<CommunityPost> root, Pageable pageable) {
        List<Order> orders = new ArrayList<>();
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(sort -> orders.add(
                    sort.isAscending() ? cb.asc(root.get(sort.getProperty())) : cb.desc(root.get(sort.getProperty()))
            ));
        } else {
            orders.add(cb.desc(root.get("createdAt")));
        }
        if (orders.stream().noneMatch(order -> order.toString().contains("createdAt"))) {
            orders.add(cb.desc(root.get("createdAt")));
        }
        return orders;
    }

    private String toLikePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
