package com.roddy.domain;


import com.roddy.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "post_bookmarks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_post_bookmark_user_post",
                        columnNames = {"user_id", "post_id"}
                )
        }
)
public class PostBookmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bookmark_id")
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;// 북마크한 사용자


    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "post_id",nullable = false)
    private Post post;// 북마크된 게시글

    public static PostBookmark create(User user, Post post) {
        return PostBookmark.builder()
                .user(user)
                .post(post)
                .build();
    }

}
