package com.example.starwars.data.common


import com.example.starwars.MyApplication
import com.example.starwars.R
import com.example.starwars.utils.json.JSONParser
import com.example.starwars.data.common.objects.ErrorResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

// https://medium.com/@douglas.iacovelli/how-to-handle-errors-with-retrofit-and-coroutines-33e7492a912

class ResultWrapper<T>(var result: T? = null, var error: Error? = null) {

    // Error classes
    abstract class Error(open val httpCode: Int? = null, open val message: String? = null)
    data class HttpError(override val httpCode: Int?, override val message: String?, val validationErrors: Map<String, ArrayList<String>>?): Error(httpCode, message)
    data class GenericError(override val message: String?): Error(null, message)
    class SessionExpired(): Error(401, MyApplication.getAppContext()?.getString(R.string.error_session_timed_out))
    class NetworkError: Error()
    class NetworkTimeOutError: Error()

    companion object {
        suspend fun <T> safeApiCall(dispatcher: CoroutineDispatcher, apiCall: suspend () -> T): ResultWrapper<T> {

            val resultWrapper = ResultWrapper<T>()

            withContext(dispatcher) {
                try {
                    resultWrapper.result = apiCall.invoke()
                } catch (throwable: Throwable) {
                    when (throwable) {
                        is HttpException -> {
                            val code = throwable.code()
                            val errorResponse = convertErrorBody(throwable)
                            // How to get result from 422 Validation Error from Laravel
//                            if (code == 422) {
//                                val firstError =
//                                    errorResponse?.errors?.get("username")?.firstOrNull()
//                                Log.i("ResultWrapper", "safeApiCall: $firstError")
//                                var test = 1
//                                test++
//                            }
                            if (code in 500..599) {
                                val error500message =
                                    MyApplication.getAppContext().getString(R.string.error_server_internal)
                                resultWrapper.error = HttpError(500, error500message, null)
                                //} else if (code == 401 && errorResponse?.tokenExpired == true) {
                            } else if (code == 401 && errorResponse?.tokenExpired == true) {
                                resultWrapper.error = SessionExpired()
                            } else {
                                resultWrapper.error = HttpError(code, errorResponse?.message, errorResponse?.errors)
                            }
                        }
                        is IOException -> resultWrapper.error = NetworkError()
                        is SocketTimeoutException -> resultWrapper.error = NetworkTimeOutError()
                        else -> {
                            if (throwable.message != null) {
                                resultWrapper.error = GenericError(throwable.message)
                            } else {
                                val errorGeneric = MyApplication.getAppContext().getString(
                                    R.string.dialog_error_message_unknown)
                                resultWrapper.error = GenericError(errorGeneric)
                            }
                        }
                    }
                }
            }
            return resultWrapper
        }

        private fun convertErrorBody(throwable: HttpException): ErrorResponse? {
            return try {
                throwable.response()?.errorBody()?.let {
                    JSONParser<ErrorResponse>().deserialize(it.string(), ErrorResponse::class.java)
                }
            } catch (exception: Exception) {
                ErrorResponse(exception.message)
            }
        }
    }
}

