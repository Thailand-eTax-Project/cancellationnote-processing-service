package com.wpanther.cancellationnote.processing.infrastructure.config;

import com.wpanther.cancellationnote.processing.application.service.SagaCommandHandler;
import com.wpanther.cancellationnote.processing.domain.event.CompensateCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.domain.event.ProcessCancellationNoteCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final SagaCommandHandler sagaCommandHandler;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command-cancellation-note}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation-cancellation-note}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.dlq:cancellationnote.processing.dlq}")
    private String dlqTopic;

    public SagaRouteConfig(SagaCommandHandler sagaCommandHandler) {
        this.sagaCommandHandler = sagaCommandHandler;
    }

    @Override
    public void configure() throws Exception {

        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(10000)
            .logExhausted(true)
            .logStackTrace(true));

        from("kafka:" + sagaCommandTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=cancellationnote-processing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=100"
                + "&consumersCount=3")
            .routeId("saga-command-consumer")
            .log("Received saga command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
            .unmarshal().json(JsonLibrary.Jackson, ProcessCancellationNoteCommand.class)
            .process(exchange -> {
                ProcessCancellationNoteCommand cmd = exchange.getIn().getBody(ProcessCancellationNoteCommand.class);
                log.info("Processing saga command for saga: {}, cancellation note: {}",
                    cmd.sagaId(), cmd.cancellationNoteNumber());
                sagaCommandHandler.handleProcessCommand(cmd);
            })
            .log("Successfully processed saga command");

        from("kafka:" + sagaCompensationTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=cancellationnote-processing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=100"
                + "&consumersCount=3")
            .routeId("saga-compensation-consumer")
            .log("Received compensation command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
            .unmarshal().json(JsonLibrary.Jackson, CompensateCancellationNoteCommand.class)
            .process(exchange -> {
                CompensateCancellationNoteCommand cmd = exchange.getIn().getBody(CompensateCancellationNoteCommand.class);
                log.info("Processing compensation for saga: {}, document: {}",
                    cmd.sagaId(), cmd.documentId());
                sagaCommandHandler.handleCompensation(cmd);
            })
            .log("Successfully processed compensation command");
    }
}
