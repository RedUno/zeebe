/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.record.Record;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;

public class DebugLogExporter implements Exporter {
  private static final Map<LogLevel, LogFunctionSupplier> LOGGERS = new EnumMap<>(LogLevel.class);

  static {
    LOGGERS.put(LogLevel.TRACE, logger -> logger::trace);
    LOGGERS.put(LogLevel.DEBUG, logger -> logger::debug);
    LOGGERS.put(LogLevel.INFO, logger -> logger::info);
    LOGGERS.put(LogLevel.WARN, logger -> logger::warn);
    LOGGERS.put(LogLevel.ERROR, logger -> logger::error);
  }

  private DebugExporterConfiguration configuration;
  private ObjectMapper objectMapper;
  private LogFunction logger;

  @Override
  public void configure(Context context) {
    configuration = context.getConfiguration().instantiate(DebugExporterConfiguration.class);
    final LogLevel logLevel = configuration.getLogLevel();
    final LogFunctionSupplier supplier = LOGGERS.get(logLevel);

    if (supplier == null) {
      final LogLevel[] expectedLogLevels = LOGGERS.keySet().toArray(new LogLevel[0]);
      throw new IllegalStateException(
          String.format(
              "Expected log level to be one of %s, but instead got %s",
              Arrays.toString(expectedLogLevels), logLevel));
    }

    logger = supplier.supply(context.getLogger());
  }

  @Override
  public void open(Controller controller) {
    logger.log("Debug exporter opened");
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    if (configuration.prettyPrint) {
      objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
  }

  @Override
  public void close() {
    logger.log("Debug exporter closed");
  }

  @Override
  public void export(Record record) {
    try {
      logger.log("{}", objectMapper.writeValueAsString(record));
    } catch (JsonProcessingException e) {
      logger.log("Failed to serialize object '{}' to JSON", record, e);
    }
  }

  public static ExporterCfg defaultConfig(final boolean prettyPrint) {
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setId("debug");
    exporterCfg.setClassName(DebugLogExporter.class.getName());
    exporterCfg.setArgs(Collections.singletonMap("prettyPrint", prettyPrint));
    return exporterCfg;
  }

  public static class DebugExporterConfiguration {
    String logLevel = "debug";
    boolean prettyPrint = false;

    LogLevel getLogLevel() {
      return LogLevel.valueOf(logLevel.trim().toUpperCase());
    }
  }

  private interface LogFunctionSupplier {
    LogFunction supply(Logger logger);
  }

  private interface LogFunction {
    void log(String message, Object... args);
  }

  private enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
  }
}
