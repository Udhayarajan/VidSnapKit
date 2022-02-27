package com.mugames.vidsnapkit.dataholders

/**
 * @author Udhaya
 * Created on 21-01-2022
 */

/**
 * It contains callbacks what's happening in Extraction Process
 *
 * @param formats Once Extraction completed successfully list of [Formats] will be returned, Default Value: `null`
 * @param error If something wrong it will be returned with [Error] instance. Default Value: [Error.NonFatalError]
 * @param progressState Simple way to estimate/notify users that everything going correctly
 */
sealed class Result(
    val formats: List<Formats>? = null,
    val error: Error = Error.NonFatalError("empty error don't care"),
    val progressState: ProgressState = ProgressState.PreStart,
) {
    class Success(formats: List<Formats>) : Result(formats)
    class Progress(progressState: ProgressState) : Result(progressState = progressState)
    class Failed(error: Error) : Result(error = error)
}

/**
 * Instance of [Error] will be returned when [Result.Failed]
 *
 * @param message Message what went wrong, Default Value: `null`
 * @param e It provides traceback, Default Value: `null`
 */
sealed class Error(val message: String? = null, val e: Exception? = null) {
    /**
     * Called when Internet connection is not available
     */
    object NetworkError : Error()

    /**
     * Called When Url is empty
     */
    object InvalidUrl : Error()

    /**
     * Called when cookies are null but required by website
     */
    object LoginInRequired : Error()

    /**
     * Report this kind of error to VidSnapKit developer
     */
    class InternalError(message: String, e: Exception? = null) : Error(message, e)

    /**
     * These are minor error happens when cookies is invalid or video not found etc
     */
    class NonFatalError(message: String) : Error(message)
}

/**
 * Just to show progress to UI/UX
 */
sealed class ProgressState {
    object PreStart : ProgressState()
    object Start : ProgressState()
    object Middle : ProgressState()
    object End : ProgressState()
}
