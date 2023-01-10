package ru.tinkoff.kora.http.common.auth;

import ru.tinkoff.kora.common.Principal;

import java.util.Collection;

public interface PrincipalWithScopes extends Principal {
    Collection<String> scopes();
}
