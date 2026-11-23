package com.supportpilot.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserContext 角色与数据范围判定（TR-5.1）。
 */
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void setUser(String role, Long teamId, String scope) {
        UserContext.set(new UserContext.CurrentUser(1L, "u", "U", 1L, role, teamId, scope));
    }

    @Test
    void hasRoleIsCaseInsensitive() {
        setUser("admin", null, null);
        assertTrue(UserContext.hasRole(Role.ADMIN));
        assertTrue(UserContext.isAdmin());
        assertFalse(UserContext.hasRole(Role.ENGINEER));
    }

    @Test
    void dataScopeDefaultsByRoleWhenAbsent() {
        setUser(Role.ADMIN, null, null);
        assertEquals(Role.SCOPE_ALL, UserContext.getDataScope());

        setUser(Role.MANAGER, null, null);
        assertEquals(Role.SCOPE_ALL, UserContext.getDataScope());

        setUser(Role.ENGINEER, null, null);
        assertEquals(Role.SCOPE_TEAM, UserContext.getDataScope());

        setUser(Role.KB_ADMIN, null, null);
        assertEquals(Role.SCOPE_TEAM, UserContext.getDataScope());
    }

    @Test
    void explicitDataScopeOverridesDefaultAndIsUppercased() {
        setUser(Role.ADMIN, null, "self");
        assertEquals(Role.SCOPE_SELF, UserContext.getDataScope());
    }

    @Test
    void getTeamIdCarriesGroup() {
        setUser(Role.ENGINEER, 7L, Role.SCOPE_TEAM);
        assertEquals(7L, UserContext.getTeamId());
    }

    @Test
    void noUserFallsBackToStrictestScope() {
        assertEquals(Role.SCOPE_TEAM, UserContext.getDataScope());
    }
}
