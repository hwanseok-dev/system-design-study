package io.lucky.orchestrator.domain

object LogAction {
    const val CREATE_WORKFLOW = "createWorkflow"
    const val START_WORKFLOW = "startWorkflow"
    const val COMPLETE_TASK = "completeTask"
    const val FAIL_TASK = "failTask"
    const val PUBLISH_TASK_EXECUTION = "publishTaskExecution"
    const val HANDLE_SUCCESS_RESPONSE = "handleSuccessResponse"
    const val HANDLE_SUCCESS_RESPONSE_FAILED = "handleSuccessResponseFailed"
    const val HANDLE_FAILURE_RESPONSE = "handleFailureResponse"
    const val HANDLE_FAILURE_RESPONSE_FAILED = "handleFailureResponseFailed"
    const val SKIP_SUCCESS_ALREADY_FAILED = "skipSuccessAlreadyFailed"
    const val SKIP_FAILURE_ALREADY_FAILED = "skipFailureAlreadyFailed"
    const val SKIP_DUPLICATE_MESSAGE = "skipDuplicateMessage"
    const val SYNC_COUNT = "syncCount"
}
