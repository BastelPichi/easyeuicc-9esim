package net.typeblog.lpac_jni

/*
 * Should reflect euicc_http_interface in lpac/euicc/interface.h
 */
interface HttpInterface {
    @Suppress("ArrayInDataClass")
    data class HttpResponse(val rcode: Int, val data: ByteArray)

    fun transmit(url: String, tx: ByteArray, headers: List<String>): HttpResponse
    // The LPA is supposed to pass in a list of pkIds supported by the eUICC.
    // HttpInterface is responsible for providing TrustManager implementations that
    // validate based on certificates corresponding to these pkIds
    fun usePublicKeyIds(pkids: Array<String>)
}