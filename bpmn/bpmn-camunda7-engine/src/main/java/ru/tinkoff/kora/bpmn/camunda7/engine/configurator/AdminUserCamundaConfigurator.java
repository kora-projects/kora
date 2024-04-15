package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

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
import ru.tinkoff.kora.bpmn.camunda7.engine.CamundaEngineConfig;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Groups.CAMUNDA_ADMIN;
import static org.camunda.bpm.engine.authorization.Groups.GROUP_TYPE_SYSTEM;
import static org.camunda.bpm.engine.authorization.Permissions.ALL;

public final class AdminUserCamundaConfigurator implements CamundaConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserCamundaConfigurator.class);

    private final CamundaEngineConfig.CamundaAdminUser adminUser;
    private final JdbcConnectionFactory connectionFactory;

    public AdminUserCamundaConfigurator(CamundaEngineConfig camundaEngineConfig, JdbcConnectionFactory connectionFactory) {
        this.adminUser = camundaEngineConfig.admin();
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void setup(ProcessEngine processEngine) {
        if (adminUser != null) {
            IdentityService identityService = processEngine.getIdentityService();
            AuthorizationService authorizationService = processEngine.getAuthorizationService();
            connectionFactory.inTx(connection -> {
                if (!userAlreadyExists(identityService, adminUser.id())) {
                    createUser(identityService);

                    if (!adminGroupAlreadyExists(identityService)) {
                        createAdminGroup(identityService);
                    }

                    createAdminGroupAuthorizations(authorizationService);
                    identityService.createMembership(adminUser.id(), CAMUNDA_ADMIN);
                    logger.info("Admin user created: {}", adminUser.id());
                } else {
                    logger.info("Admin user already exist: {}", adminUser.id());
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
        User newUser = identityService.newUser(adminUser.id());
        newUser.setPassword(adminUser.password());
        newUser.setFirstName(adminUser.firstname() == null ? adminUser.id().toUpperCase() : adminUser.firstname());
        newUser.setLastName(adminUser.lastname() == null ? adminUser.id().toUpperCase() : adminUser.lastname());
        newUser.setEmail(adminUser.email() == null ? adminUser.id() + "@localhost" : adminUser.email());

        identityService.saveUser(newUser);
    }

    private void createAdminGroup(IdentityService identityService) {
        Group camundaAdminGroup = identityService.newGroup(CAMUNDA_ADMIN);
        camundaAdminGroup.setName("Camunda Administrators");
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
