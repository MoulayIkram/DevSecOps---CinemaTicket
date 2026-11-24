package com.kaankaplan.movieService.business.concretes;

import com.kaankaplan.movieService.business.abstracts.PaymentService;
import com.kaankaplan.movieService.client.UserServiceClient;
import com.kaankaplan.movieService.entity.dto.EmailMessageKafkaDto;
import com.kaankaplan.movieService.entity.dto.TicketInformationDto;
import com.kaankaplan.movieService.kafka.KafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final KafkaProducer kafkaProducer;
    private final UserServiceClient userServiceClient;

    @Value("${SMTP_USERNAME}")
    private String senderEmail;

    @Override
    public void sendTicketDetail(TicketInformationDto ticketInformationDto) {

        // L'identite vient du jeton valide (claim sub), jamais du body
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = jwt.getSubject();

        // Email et nom : profil recupere aupres de userService (appel avec le jeton de service).
        // Repli sur les claims du jeton si le profil n'existe pas encore.
        String email = jwt.getClaimAsString("email");
        String fullName = jwt.getClaimAsString("name");

        Optional<Map<String, Object>> profile = userServiceClient.getProfile(userId);
        if (profile.isPresent()) {
            Object profileEmail = profile.get().get("email");
            Object profileName = profile.get().get("fullName");
            if (profileEmail != null) email = profileEmail.toString();
            if (profileName != null) fullName = profileName.toString();
        }

        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun email connu pour cet utilisateur");
        }

        EmailMessageKafkaDto emailMessage = EmailMessageKafkaDto.builder()
                .sender(senderEmail)
                .recipient(email)
                .subtitle("CineVision Bilet Detay")
                .fullName(fullName)
                .movieName(ticketInformationDto.getMovieName())
                .movieDay(ticketInformationDto.getMovieDay())
                .movieStartTime(ticketInformationDto.getMovieStartTime())
                .saloonName(ticketInformationDto.getSaloonName())
                .chairNumbers(ticketInformationDto.getChairNumbers())
                .build();

        kafkaProducer.sendMessage(emailMessage);
    }
}
