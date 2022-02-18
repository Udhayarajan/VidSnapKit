package com.mugames.vidsnapkit.dataholders
/**
 * @author Udhaya
 * Created on 21-01-2022
*/
sealed class Result(
    val formats: List<Formats>? = null,
    val error: Error? = null,
    val progressState: ProgressState = ProgressState.PreStart,
) {
    class Success(formats: List<Formats>) : Result(formats)
    class Progress(progressState: ProgressState) : Result(progressState = progressState)
    class Failed(error: Error) : Result(error = error)
}

sealed class Error(val message: String? = null, val e: Exception? = null) {
    object NetworkError : Error()
    object InvalidUrl : Error()
    object LoginInRequired: Error()
    class InternalError(message: String, e: Exception? = null) : Error(message, e)
    class NonFatalError(message: String): Error(message)
}

sealed class ProgressState {
    object PreStart : ProgressState()
    object Start : ProgressState()
    object Middle : ProgressState()
    object End : ProgressState()
}
