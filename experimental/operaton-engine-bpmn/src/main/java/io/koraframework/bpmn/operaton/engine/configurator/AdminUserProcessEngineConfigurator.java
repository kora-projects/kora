package io.koraframework.bpmn.operaton.engine.configurator;

import io.koraframework.bpmn.operaton.engine.OperatonEngineBpmnConfig;
import io.koraframework.bpmn.operaton.engine.OperatonEngineDataSource;
import io.koraframework.common.util.TimeUtils;
import org.operaton.bpm.engine.AuthorizationService;
import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.authorization.Resource;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.operaton.bpm.engine.authorization.Authorization.ANY;
import static org.operaton.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.operaton.bpm.engine.authorization.Groups.GROUP_TYPE_SYSTEM;
import static org.operaton.bpm.engine.authorization.Groups.OPERATON_ADMIN;
import static org.operaton.bpm.engine.authorization.Permissions.ALL;

public final class AdminUserProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserProcessEngineConfigurator.class);

    private final OperatonEngineBpmnConfig.AdminConfig adminConfig;
    private final OperatonEngineDataSource engineDataSource;

    public AdminUserProcessEngineConfigurator(OperatonEngineBpmnConfig engineConfig,
                                              OperatonEngineDataSource engineDataSource) {
        this.adminConfig = engineConfig.admin();
        this.engineDataSource = engineDataSource;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (adminConfig != null) {
            logger.debug("Operaton Configurator Admin user creating...");
            final long started = TimeUtils.started();

            IdentityService identityService = engine.getIdentityService();
            AuthorizationService authorizationService = engine.getAuthorizationService();
            engineDataSource.transactionManager().inNewTx(() -> {
                if (!userAlreadyExists(identityService, adminConfig.id())) {
                    createUser(identityService);

                    if (!adminGroupAlreadyExists(identityService)) {
                        createAdminGroup(identityService);
                    }

                    createAdminGroupAuthorizations(authorizationService);
                    identityService.createMembership(adminConfig.id(), OPERATON_ADMIN);
                    logger.info("Operaton Configurator Admin user created in {}", TimeUtils.tookForLogging(started));
                } else {
                    logger.debug("Operaton Configurator Admin user already exist");
                }
            });
        }
    }

    private boolean userAlreadyExists(IdentityService identityService, String userId) {
        return identityService.createUserQuery().userId(userId).singleResult() != null;
    }

    private boolean adminGroupAlreadyExists(IdentityService identityService) {
        return identityService.createGroupQuery().groupId(OPERATON_ADMIN).count() > 0;
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
        Group adminGroup = identityService.newGroup(OPERATON_ADMIN);
        adminGroup.setName("Operaton Administrators");
        adminGroup.setType(GROUP_TYPE_SYSTEM);
        identityService.saveGroup(adminGroup);
    }

    private void createAdminGroupAuthorizations(AuthorizationService authorizationService) {
        for (Resource resource : Resources.values()) {
            if (authorizationService.createAuthorizationQuery().groupIdIn(OPERATON_ADMIN).resourceType(resource).resourceId(ANY).count() == 0) {
                AuthorizationEntity groupAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
                groupAuth.setGroupId(OPERATON_ADMIN);
                groupAuth.setResource(resource);
                groupAuth.setResourceId(ANY);
                groupAuth.addPermission(ALL);
                authorizationService.saveAuthorization(groupAuth);
            }
        }
    }
}
