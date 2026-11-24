package com.kaankaplan.movieService.business.concretes;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import com.kaankaplan.movieService.business.abstracts.CommentService;
import com.kaankaplan.movieService.business.abstracts.MovieService;
import com.kaankaplan.movieService.dao.CommentDao;
import com.kaankaplan.movieService.entity.Comment;
import com.kaankaplan.movieService.entity.Movie;
import com.kaankaplan.movieService.entity.dto.CommentRequestDto;
import com.kaankaplan.movieService.entity.dto.DeleteCommentRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentDao commentDao;
    private final MovieService movieService;

    @Override
    public List<Comment> getCommentsByMovieId(int movieId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo-1, pageSize);
        return commentDao.getCommentsByMovieMovieId(movieId, pageable);
    }

    @Override
    public int getNumberOfCommentsByMovieId(int movieId) {
        return commentDao.countCommentByMovieMovieId(movieId);
    }

    @Override
    public void deleteComment(DeleteCommentRequestDto deleteCommentRequestDto) {
        Comment comment = commentDao.findById(deleteCommentRequestDto.getCommentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commentaire introuvable"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // ABAC : seul l'auteur (claim sub du jeton) ou un ADMIN peut supprimer
        if (!isAdmin && !jwt.getSubject().equals(comment.getCommentByUserId())) {
            throw new AccessDeniedException("Seul l'auteur du commentaire ou un ADMIN peut le supprimer");
        }
        commentDao.deleteById(deleteCommentRequestDto.getCommentId());
    }

    @Override
    public Comment addComment(CommentRequestDto commentRequestDto) {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Movie movie = movieService.getMovieById(commentRequestDto.getMovieId());

        String author = jwt.getClaimAsString("name");
        if (author == null) {
            author = jwt.getClaimAsString("preferred_username");
        }

        Comment comment = Comment.builder()
                .commentByUserId(jwt.getSubject())   // vient du jeton, pas du body
                .commentBy(author)
                .commentText(commentRequestDto.getCommentText())
                .movie(movie)
                .build();

        return commentDao.save(comment);
    }
}
