package ru.tinkoff.kora.http.common;

public enum HttpResultCode {
    /**
     * success - всё завершилось успешно
     */
    SUCCESS,
    
    /**
     * limit_error - ошибки лимитов, обычно код 429
     */
    LIMIT_ERROR,

    /**
     * auth_error - ошибка авторизации, обычно коды 401 и 403
     */
    AUTH_ERROR,

    /**
     * client_error - ошибки клиента, когда клиент не смог обработать ответ (4xx ответы)
     */
    CLIENT_ERROR,

    /**
     * server_error - когда клиент не смог обработать запрос по вине сервера (5xx ответы)
     */
    SERVER_ERROR,

    /**
     * connection_error - ошибки возникающие, когда ответ вообще не получен: переполнения пулов, таймауты на сети, etc.
     */
    CONNECTION_ERROR,
    ;

    private final String string = name().toLowerCase();

    public static HttpResultCode fromStatusCode(int code) {
        if (code >= 200 && code < 300) {
            return SUCCESS;
        } else if (code == 429) {
            return LIMIT_ERROR;
        } else if (code == 403 || code == 401) {
            return AUTH_ERROR;
        } else if (code >= 400 && code < 500) {
            return CLIENT_ERROR;
        } else {
            return SERVER_ERROR;
        }
    }

    public String string() {
        return this.string;
    }
}
