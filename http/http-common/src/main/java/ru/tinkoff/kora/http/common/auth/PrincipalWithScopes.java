package ru.tinkoff.kora.http.common.auth;

import ru.tinkoff.kora.common.Principal;

import java.util.List;

public interface PrincipalWithScopes extends Principal {
    List<String> scopes();
}
