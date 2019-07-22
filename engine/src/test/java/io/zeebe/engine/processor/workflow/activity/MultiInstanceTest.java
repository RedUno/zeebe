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
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  public static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW_MI_PARALLEL_SERVICE_TASK =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              "task",
              t ->
                  t.zeebeTaskType("test")
                      .multiInstance(
                          m -> m.zeebeInputCollection("items").zeebeInputElement("item")))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateOneElementInstanceForEachElement() {
    ENGINE.deployment().withXmlResource(WORKFLOW_MI_PARALLEL_SERVICE_TASK).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", Arrays.asList(1, 2, 3))
            .create();

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("task")
                .limit(4))
        .hasSize(4);
  }

  @Test
  public void shouldCreateOneTaskForEachElement() {
    ENGINE.deployment().withXmlResource(WORKFLOW_MI_PARALLEL_SERVICE_TASK).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", Arrays.asList(1, 2, 3))
            .create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3);
  }
}
