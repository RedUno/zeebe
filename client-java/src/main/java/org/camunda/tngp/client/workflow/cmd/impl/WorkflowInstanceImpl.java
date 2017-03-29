package org.camunda.tngp.client.workflow.cmd.impl;

import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;

/**
 *
 */
public class WorkflowInstanceImpl implements WorkflowInstance
{
    protected String bpmnProcessId;
    protected long workflowInstanceKey;
    protected int version;

    public WorkflowInstanceImpl(WorkflowInstanceEvent event)
    {
        this.bpmnProcessId = event.getBpmnProcessId();
        this.version = event.getVersion();
        this.workflowInstanceKey = event.getWorkflowInstanceKey();
    }

    @Override
    public String getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
    }

    @Override
    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKey;
    }

    public void setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKey = workflowInstanceKey;
    }

    @Override
    public int getVersion()
    {
        return version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

}