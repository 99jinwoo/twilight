package com.jinwoo.twilightandyou.data

import com.jinwoo.twilightandyou.data.remote.*
import com.jinwoo.twilightandyou.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

class HttpTransportTest {
    private class Response(url: URL, private val code: Int, private val body: String, private val error: Exception? = null) : HttpsURLConnection(url) {
        var disconnected = false
        override fun getResponseCode(): Int { error?.let { throw it }; return code }
        override fun getInputStream(): InputStream {
            check(code in 200..299) { "Error responses must use errorStream" }
            return ByteArrayInputStream(body.toByteArray())
        }
        override fun getErrorStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    private fun failure(code: Int, body: String, cause: Exception? = null): ApiFailure = runBlocking {
        lateinit var connection: Response
        val transport = HttpsApiTransport { url -> Response(url, code, body, cause).also { connection = it } }
        try {
            transport.get(PublicWeatherApi.AIR, "synthetic-key", emptyMap())
            error("Expected a failure")
        } catch (e: ApiFailure) {
            assertTrue(connection.disconnected)
            assertFalse(connection.instanceFollowRedirects)
            e
        }
    }

    @Test fun errorBodyPreservesAuthenticationReasonEvenWithHttp500() {
        val result = failure(500, """{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"returnReasonCode":"20","returnAuthMsg":"serviceKey=synthetic-private"}}}""")
        assertEquals(DataProblem.AUTH, result.problem)
        assertEquals(500, result.httpStatus)
        assertEquals("20", result.providerCode)
        assertFalse(result.toString().contains("synthetic-private"))
    }

    @Test fun xmlQuotaAndJsonGatewayErrorsAreRecognized() {
        val quota = failure(429, "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>22</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>")
        assertEquals(DataProblem.QUOTA, quota.problem)
        assertEquals("22", quota.providerCode)
        val auth = failure(200, """{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"returnReasonCode":"30"}}}""")
        assertEquals(DataProblem.AUTH, auth.problem)
        assertEquals(DataProblem.AUTH, assertThrows(ApiFailure::class.java) {
            ApiParsers.page("""{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"returnReasonCode":"30"}}}""")
        }.problem)
    }

    @Test fun serverErrorsAndRedirectsAreNotReportedAsNetworkFailures() {
        val server = failure(503, "<html>temporary service unavailable</html>")
        assertEquals(DataProblem.SERVER, server.problem)
        assertEquals(503, server.httpStatus)
        assertNull(server.providerCode)
        assertEquals(DataProblem.REQUEST, failure(302, "").problem)
        assertEquals(DataProblem.TIMEOUT, failure(504, "").problem)
    }

    @Test fun timeoutAndTlsFailuresRemainDistinct() {
        assertEquals(DataProblem.TIMEOUT, failure(200, "", SocketTimeoutException("secret URL")).problem)
        val secure = failure(200, "", SSLException("secret URL"))
        assertEquals(DataProblem.SECURE_CONNECTION, secure.problem)
        assertFalse(secure.toString().contains("secret URL"))
    }

    @Test fun credentialsAndStationAreEncodedOnceAndCancellationPropagates() = runBlocking {
        val transport = HttpsApiTransport { url ->
            assertEquals("apis.data.go.kr", url.host)
            assertTrue(url.query.contains("serviceKey=abc%2B%2F%3D"))
            assertTrue(url.query.contains("stationName=%EC%A2%85%EB%A1%9C%EA%B5%AC"))
            Response(url, 200, "{}")
        }
        assertEquals("{}", transport.get(PublicWeatherApi.AIR, "abc%2B%2F%3D", mapOf("stationName" to "종로구")))
        val cancelled = HttpsApiTransport { Response(it, 200, "", CancellationException()) }
        try { cancelled.get(PublicWeatherApi.AIR, "test", emptyMap()); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }

    @Test fun diagnosticMessagesOnlyKeepNumericCodesAndDistinguishNeverFetched() {
        val failure = ApiFailure(DataProblem.AUTH, 999, "serviceKey=secret")
        assertNull(failure.httpStatus)
        assertNull(failure.providerCode)
        assertTrue(SourceStatus("air").description().contains("새로고침"))
        val status = SourceStatus("air", issue = DataProblem.SERVER, httpStatus = 503)
        assertTrue(status.description().contains("HTTP 503"))
        assertFalse(status.description().contains("저장 자료"))
    }
}
