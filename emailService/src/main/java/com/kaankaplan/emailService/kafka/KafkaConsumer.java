package com.kaankaplan.emailService.kafka;

import com.kaankaplan.emailService.business.abstracts.EmailService;
import com.kaankaplan.emailService.dto.EmailMessageKafkaDto;
import com.kaankaplan.emailService.security.ServiceTokenValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumer {

    private final EmailService emailService;
    private final ServiceTokenValidator tokenValidator;

    @KafkaListener(topics = "${spring.kafka.topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(EmailMessageKafkaDto emailMessageKafkaDto,
                        @Header(name = "Authorization", required = false) Object authorization) {
        // Zero Trust : on verifie QUI a publie le message avant d'envoyer l'email
        String bearer = authorization instanceof byte[]
                ? new String((byte[]) authorization, StandardCharsets.UTF_8)
                : (authorization == null ? null : authorization.toString());
        if (!tokenValidator.isValid(bearer)) {
            log.warn("Message Kafka REJETE : jeton de service absent, invalide ou expire");
            return;
        }
        // send email
        Map<String, String> model = new HashMap<>();
        model.put("fullName", emailMessageKafkaDto.getFullName());
        model.put("movieName", emailMessageKafkaDto.getMovieName());
        model.put("movieDay", emailMessageKafkaDto.getMovieDay());
        model.put("saloonName", emailMessageKafkaDto.getSaloonName());
        model.put("movieStartTime", emailMessageKafkaDto.getMovieStartTime());
        model.put("chairNumbers", emailMessageKafkaDto.getChairNumbers());

        emailService.sendEmail(emailMessageKafkaDto.getSender(), emailMessageKafkaDto.getRecipient(), emailMessageKafkaDto.getSubtitle(), model);
    }
}
