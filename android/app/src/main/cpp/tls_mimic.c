#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <android/log.h>
#include <openssl/ssl.h>
#include <openssl/err.h>
#include <openssl/crypto.h>

#define LOG_TAG "VeilTLS"

static const char* CHROME_CIPHERS = "TLS_AES_128_GCM_SHA256:TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305";

JNIEXPORT jstring JNICALL Java_com_syshelper_service_TlsMimic_sendRequest(JNIEnv *env, jclass clazz, jstring j_host, jint port, jstring j_path, jstring j_payload) {
    const char *host = (*env)->GetStringUTFChars(env, j_host, NULL);
    const char *path = (*env)->GetStringUTFChars(env, j_path, NULL);
    const char *payload = (*env)->GetStringUTFChars(env, j_payload, NULL);
    SSL_CTX *ctx = NULL; SSL *ssl = NULL; int sock = -1; char response[4096] = {0};
    SSL_library_init(); SSL_load_error_strings(); OpenSSL_add_all_algorithms();
    ctx = SSL_CTX_new(TLS_client_method());
    if (!ctx) goto cleanup;
    SSL_CTX_set_min_proto_version(ctx, TLS1_2_VERSION);
    SSL_CTX_set_max_proto_version(ctx, TLS1_3_VERSION);
    SSL_CTX_set_cipher_list(ctx, CHROME_CIPHERS);
    SSL_CTX_set_ciphersuites(ctx, "TLS_AES_128_GCM_SHA256:TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256");
    struct hostent *he = gethostbyname(host);
    if (!he) goto cleanup;
    sock = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in addr; addr.sin_family = AF_INET; addr.sin_port = htons(port); addr.sin_addr = *((struct in_addr *)he->h_addr);
    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) goto cleanup;
    ssl = SSL_new(ctx); SSL_set_fd(ssl, sock); SSL_set_tlsext_host_name(ssl, host);
    if (SSL_connect(ssl) != 1) goto cleanup;
    char request[8192];
    snprintf(request, sizeof(request), "POST /%s HTTP/1.1\r\nHost: %s\r\nUser-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36\r\nContent-Type: application/octet-stream\r\nContent-Length: %zu\r\nConnection: keep-alive\r\n\r\n%s", path, host, strlen(payload), payload);
    SSL_write(ssl, request, strlen(request));
    int bytes = SSL_read(ssl, response, sizeof(response) - 1);
    if (bytes > 0) response[bytes] = '\0';
cleanup:
    if (ssl) SSL_shutdown(ssl); if (ssl) SSL_free(ssl); if (sock >= 0) close(sock); if (ctx) SSL_CTX_free(ctx);
    (*env)->ReleaseStringUTFChars(env, j_host, host); (*env)->ReleaseStringUTFChars(env, j_path, path); (*env)->ReleaseStringUTFChars(env, j_payload, payload);
    return (*env)->NewStringUTF(env, response);
}
