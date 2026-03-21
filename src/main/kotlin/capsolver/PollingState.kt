package capsolver

sealed interface PollingState {
    data class Solved(val token: String) : PollingState
    data object Processing : PollingState
    data object Failed : PollingState
}