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
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

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

  @Test
  public void shouldSetInputElementVariable() {
    ENGINE.deployment().withXmlResource(WORKFLOW_MI_PARALLEL_SERVICE_TASK).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", Arrays.asList("a", "b", "c"))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    ENGINE.jobs().withType("test").activate();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.ACTIVATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .extracting(r -> r.getValue().getVariables().get("item"))
        .hasSize(3)
        .contains("a", "b", "c");

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withName("item")
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(r -> r.getValue().getValue())
        .contains("\"a\"", "\"b\"", "\"c\"");
  }

  @Test
  public void shouldCompleteActivityWhenAllInstancesAreCompleted() {
    ENGINE.deployment().withXmlResource(WORKFLOW_MI_PARALLEL_SERVICE_TASK).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", Arrays.asList("a", "b", "c"))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    final Record<JobBatchRecordValue> activatedRecord = ENGINE.jobs().withType("test").activate();

    activatedRecord
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("task")
                .limit(4))
        .hasSize(4);

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withRecordKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .exists())
        .isTrue();
  }

  @Test
  public void shouldSkipIfCollectionIsEmpty() {
    ENGINE.deployment().withXmlResource(WORKFLOW_MI_PARALLEL_SERVICE_TASK).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", Collections.emptyList())
            .create();

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("task")
                .limit(4))
        .hasSize(4)
        .extracting(Record::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withRecordKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .exists())
        .isTrue();
  }
}
