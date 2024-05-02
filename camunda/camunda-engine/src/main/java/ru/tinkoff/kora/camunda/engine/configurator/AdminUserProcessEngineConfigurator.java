package ru.tinkoff.kora.camunda.engine.configurator;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

import java.time.Duration;

import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Groups.CAMUNDA_ADMIN;
import static org.camunda.bpm.engine.authorization.Groups.GROUP_TYPE_SYSTEM;
import static org.camunda.bpm.engine.authorization.Permissions.ALL;

public final class AdminUserProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserProcessEngineConfigurator.class);

    private final CamundaEngineConfig.AdminConfig adminConfig;
    private final JdbcConnectionFactory connectionFactory;

    public AdminUserProcessEngineConfigurator(CamundaEngineConfig camundaEngineConfig, JdbcConnectionFactory connectionFactory) {
        this.adminConfig = camundaEngineConfig.admin();
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (adminConfig != null) {
            logger.debug("Camunda7 Configurator Admin user creating...");
            final long started = System.nanoTime();

            IdentityService identityService = engine.getIdentityService();
            AuthorizationService authorizationService = engine.getAuthorizationService();
            connectionFactory.inTx(connection -> {
                if (!userAlreadyExists(identityService, adminConfig.id())) {
                    createUser(identityService);

                    if (!adminGroupAlreadyExists(identityService)) {
                        createAdminGroup(identityService);
                    }

                    createAdminGroupAuthorizations(authorizationService);
                    identityService.createMembership(adminConfig.id(), CAMUNDA_ADMIN);
                    logger.info("Camunda7 Configurator Admin user created in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                } else {
                    logger.debug("Camunda7 Configurator Admin user already exist");
                }
            });
        }
    }

    private boolean userAlreadyExists(IdentityService identityService, String userId) {
        return identityService.createUserQuery().userId(userId).singleResult() != null;
    }

    private boolean adminGroupAlreadyExists(IdentityService identityService) {
        return identityService.createGroupQuery().groupId(CAMUNDA_ADMIN).count() > 0;
    }

    private void createUser(IdentityService identityService) {
        User newUser = identityService.newUser(adminConfig.id());
        newUser.setPassword(adminConfig.password());
        newUser.setFirstName(adminConfig.firstname() == null ? adminConfig.id().toUpperCase() : adminConfig.firstname());
        newUser.setLastName(adminConfig.lastname() == null ? adminConfig.id().toUpperCase() : adminConfig.lastname());
        newUser.setEmail(adminConfig.email() == null ? adminConfig.id() + "@localhost" : adminConfig.email());

        identityService.saveUser(newUser);
    }

    private void createAdminGroup(IdentityService identityService) {
        Group camundaAdminGroup = identityService.newGroup(CAMUNDA_ADMIN);
        camundaAdminGroup.setName("Camunda7 Administrators");
        camundaAdminGroup.setType(GROUP_TYPE_SYSTEM);
        identityService.saveGroup(camundaAdminGroup);
    }

    private void createAdminGroupAuthorizations(AuthorizationService authorizationService) {
        for (Resource resource : Resources.values()) {
            if (authorizationService.createAuthorizationQuery().groupIdIn(CAMUNDA_ADMIN).resourceType(resource).resourceId(ANY).count() == 0) {
                AuthorizationEntity groupAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
                groupAuth.setGroupId(CAMUNDA_ADMIN);
                groupAuth.setResource(resource);
                groupAuth.setResourceId(ANY);
                groupAuth.addPermission(ALL);
                authorizationService.saveAuthorization(groupAuth);
            }
        }
    }
}
