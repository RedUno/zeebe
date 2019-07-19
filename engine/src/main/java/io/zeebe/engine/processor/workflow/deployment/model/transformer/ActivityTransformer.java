/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.element.LoopCharacteristics;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.mapping.JsonPathPointer;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class ActivityTransformer implements ModelElementTransformer<Activity> {
  @Override
  public Class<Activity> getType() {
    return Activity.class;
  }

  @Override
  public void transform(Activity element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableActivity activity =
        workflow.getElementById(element.getId(), ExecutableActivity.class);

    final io.zeebe.model.bpmn.instance.LoopCharacteristics elementLoopCharacteristics =
        element.getLoopCharacteristics();
    if (elementLoopCharacteristics != null
        && elementLoopCharacteristics instanceof MultiInstanceLoopCharacteristics) {

      final boolean isSequential =
          ((MultiInstanceLoopCharacteristics) elementLoopCharacteristics).isSequential();

      final ZeebeLoopCharacteristics loopCharacteristics =
          elementLoopCharacteristics.getSingleExtensionElement(ZeebeLoopCharacteristics.class);

      // TODO (saig0): validate input collection expression
      final JsonPathQuery inputCollectionQuery =
          context.getJsonPathQueryCompiler().compile(loopCharacteristics.getInputCollection());

      // TODO (saig0): validate input element expression
      // TODO (saig0): extract JsonPath creation
      // merging algorithm expect a root object $
      final JsonPathPointer inputElementPath =
          new JsonPathPointer(("$." + loopCharacteristics.getInputElement()).split("\\."));

      final LoopCharacteristics activityLoopCharacteristics =
          new LoopCharacteristics(isSequential, inputCollectionQuery, inputElementPath);
      activity.setLoopCharacteristics(activityLoopCharacteristics);
    }

    activity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.ACTIVITY_ELEMENT_ACTIVATING);
    activity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.ACTIVITY_ELEMENT_ACTIVATED);
    activity.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.ACTIVITY_EVENT_OCCURRED);
    activity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.ACTIVITY_ELEMENT_COMPLETING);
    activity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
    activity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.ACTIVITY_ELEMENT_TERMINATING);
    activity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.ACTIVITY_ELEMENT_TERMINATED);
  }
}
