package io.lucky.orchestrator.api

data class CreateWorkflowRequest(
    val name: String,
    val tasks: List<WorkflowNodeRequest>,
    val edges: List<WorkflowEdgeRequest>,
)

data class WorkflowNodeRequest(
    val taskId: Long,
)

data class WorkflowEdgeRequest(
    val parentTaskId: Long,
    val childTaskId: Long,
)

data class WorkflowResponse(
    val id: Long,
    val name: String,
    val status: String,
    val tasks: List<WorkflowTaskResponse>,
)

data class WorkflowTaskResponse(
    val taskId: Long,
    val taskName: String,
    val status: String,
    val expectedCount: Int,
    val completedCount: Int,
)

data class StartWorkflowResponse(
    val workflowId: Long,
    val status: String,
    val runningTasks: List<String>,
)
