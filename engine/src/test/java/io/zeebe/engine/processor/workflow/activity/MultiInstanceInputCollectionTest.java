/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.activity;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class MultiInstanceInputCollectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              "task",
              t ->
                  t.zeebeTaskType(JOB_TYPE)
                      .multiInstance(
                          b ->
                              b.zeebeInputCollection(INPUT_COLLECTION)
                                  .zeebeInputElement(INPUT_ELEMENT)))
          .endEvent()
          .done();

  @Parameterized.Parameter(0)
  public Collection<?> inputCollection;

  @Parameterized.Parameters(name = "with input collection: {0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {Arrays.asList("a")},
      {Arrays.asList(true, false)},
      {Arrays.asList(10, 20, 30)},
      {
        Arrays.asList(
            Collections.singletonMap("x", 1),
            Collections.singletonMap("x", 2),
            Collections.singletonMap("x", 3))
      },
      {Arrays.asList("x", null, true, 40)},
    };
  }

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateOneInstanceForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    final int collectionSize = inputCollection.size();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(collectionSize)
        .exists();

    // when
    ENGINE.jobs().withType(JOB_TYPE).activate();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(collectionSize))
        .hasSize(collectionSize)
        .extracting(r -> r.getValue().getVariables().get(INPUT_ELEMENT))
        .containsExactlyElementsOf(inputCollection);
  }
}
