package com.kaankaplan.movieService.kafka;

import com.kaankaplan.movieService.entity.dto.EmailMessageKafkaDto;
import org.apache.kafka.clients.admin.NewTopic;
import com.kaankaplan.movieService.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {

    private final NewTopic newTopic;
    private final KafkaTemplate<String, EmailMessageKafkaDto> kafkaTemplate;
    private final ServiceTokenProvider tokenProvider;

    @Autowired
    public KafkaProducer(NewTopic newTopic, KafkaTemplate<String, EmailMessageKafkaDto> kafkaTemplate,
                         ServiceTokenProvider tokenProvider) {
        this.newTopic = newTopic;
        this.kafkaTemplate = kafkaTemplate;
        this.tokenProvider = tokenProvider;
    }

    public void sendMessage(EmailMessageKafkaDto emailMessage) {
        Message<EmailMessageKafkaDto> message = MessageBuilder
                .withPayload(emailMessage)
                .setHeader(KafkaHeaders.TOPIC, newTopic.name())
                // Jeton de service : emailService le valide avant d'envoyer l'email
                .setHeader("Authorization", "Bearer " + tokenProvider.getToken())
                .build();

        kafkaTemplate.send(message);
    }
}
