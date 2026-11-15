package com.roddy.domain.community.repository;

import com.roddy.domain.community.dto.request.CommunityPostSearchCondition;
import com.roddy.domain.community.entity.CommunityInterviewPostDetail;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.entity.CommunityRoadmapPostDetail;
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
import jakarta.persistence.criteria.Subquery;
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

        // 기술 스택 컬렉션을 조인하면 글 한 개가 여러 행이 된다. 조건은 서브쿼리에서 걸러 중복을 없애고,
        // 바깥 쿼리는 distinct 없이 정렬만 한다. MySQL 은 정렬 컬럼(like_count 등)이 select 에 없는
        // "select distinct id ... order by ..." 를 거부한다(H2 테스트에서는 드러나지 않았다).
        Subquery<Long> matchingIds = idQuery.subquery(Long.class);
        Root<CommunityPost> candidate = matchingIds.from(CommunityPost.class);
        candidate.join("author", JoinType.INNER);
        Join<CommunityPost, CommunityRoadmapPostDetail> roadmapDetail = candidate.join("roadmapDetail", JoinType.LEFT);
        Join<CommunityRoadmapPostDetail, String> roadmapSkills = roadmapDetail.join("recommendedSkills", JoinType.LEFT);
        Join<CommunityPost, CommunityInterviewPostDetail> interviewDetail = candidate.join("interviewDetail", JoinType.LEFT);
        Join<CommunityInterviewPostDetail, String> interviewSkills = interviewDetail.join("techStacks", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(condition, cb, candidate, roadmapDetail, roadmapSkills, interviewDetail, interviewSkills);
        matchingIds.select(candidate.get("id")).where(predicates.toArray(Predicate[]::new));

        idQuery.select(root.get("id"));
        idQuery.where(root.get("id").in(matchingIds));
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
                        left join fetch p.roadmapDetail
                        left join fetch p.interviewDetail
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
                        left join fetch p.roadmapDetail
                        left join fetch p.interviewDetail
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
        Join<CommunityPost, CommunityRoadmapPostDetail> roadmapDetail = root.join("roadmapDetail", JoinType.LEFT);
        Join<CommunityRoadmapPostDetail, String> roadmapSkills = roadmapDetail.join("recommendedSkills", JoinType.LEFT);
        Join<CommunityPost, CommunityInterviewPostDetail> interviewDetail = root.join("interviewDetail", JoinType.LEFT);
        Join<CommunityInterviewPostDetail, String> interviewSkills = interviewDetail.join("techStacks", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(condition, cb, root, roadmapDetail, roadmapSkills, interviewDetail, interviewSkills);
        countQuery.select(cb.countDistinct(root));
        countQuery.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CommunityPostSearchCondition condition,
            CriteriaBuilder cb,
            Root<CommunityPost> root,
            Join<CommunityPost, CommunityRoadmapPostDetail> roadmapDetail,
            Join<CommunityRoadmapPostDetail, String> roadmapSkills,
            Join<CommunityPost, CommunityInterviewPostDetail> interviewDetail,
            Join<CommunityInterviewPostDetail, String> interviewSkills
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
                    cb.like(cb.lower(root.get("content")), keyword),
                    cb.like(cb.lower(cb.coalesce(roadmapDetail.get("targetCompany"), "")), keyword),
                    cb.like(cb.lower(cb.coalesce(roadmapDetail.get("targetJob"), "")), keyword),
                    cb.like(cb.lower(roadmapSkills), keyword),
                    cb.like(cb.lower(cb.coalesce(interviewDetail.get("company"), "")), keyword),
                    cb.like(cb.lower(cb.coalesce(interviewDetail.get("jobRole"), "")), keyword),
                    cb.like(cb.lower(interviewSkills), keyword)
            ));
        }
        if (StringUtils.hasText(condition.getCompany())) {
            String company = toLikePattern(condition.getCompany());
            predicates.add(cb.or(
                    cb.like(cb.lower(cb.coalesce(roadmapDetail.get("targetCompany"), "")), company),
                    cb.like(cb.lower(cb.coalesce(interviewDetail.get("company"), "")), company)
            ));
        }
        if (StringUtils.hasText(condition.getJobRole())) {
            String jobRole = toLikePattern(condition.getJobRole());
            predicates.add(cb.or(
                    cb.like(cb.lower(cb.coalesce(roadmapDetail.get("targetJob"), "")), jobRole),
                    cb.like(cb.lower(cb.coalesce(interviewDetail.get("jobRole"), "")), jobRole)
            ));
        }
        if (StringUtils.hasText(condition.getTechStack())) {
            String techStack = toLikePattern(condition.getTechStack());
            predicates.add(cb.or(
                    cb.like(cb.lower(roadmapSkills), techStack),
                    cb.like(cb.lower(interviewSkills), techStack)
            ));
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
        // 좋아요·조회 수가 같은 글이 페이지 경계에서 빠지거나 겹치지 않도록 id 로 순서를 고정한다.
        orders.add(cb.desc(root.get("id")));
        return orders;
    }

    private String toLikePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
