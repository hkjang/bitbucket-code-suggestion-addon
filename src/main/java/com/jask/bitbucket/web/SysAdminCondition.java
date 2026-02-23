package com.jask.bitbucket.web;

import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Bitbucket 6.x compatible web-item condition.
 *
 * HasGlobalPermissionCondition is Bitbucket-internal and requires
 * PermissionService which cannot be reliably injected into a plugin's
 * Spring context.  This condition uses SAL UserManager instead, which
 * is always available.
 */
public class SysAdminCondition implements Condition {

    private final UserManager userManager;

    public SysAdminCondition(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public void init(Map<String, String> params) throws PluginParseException {
        // no initialization needed
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        try {
            Object reqObj = context.get("request");
            if (reqObj instanceof HttpServletRequest) {
                UserProfile user = userManager.getRemoteUser((HttpServletRequest) reqObj);
                if (user == null) return false;
                return userManager.isSystemAdmin(user.getUserKey());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
