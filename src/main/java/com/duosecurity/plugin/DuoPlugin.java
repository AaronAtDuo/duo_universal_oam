package com.duosecurity.plugin;

import java.util.Map;
import java.util.logging.Level;
import javax.security.auth.Subject;

import oracle.security.am.plugin.ExecutionStatus;
import oracle.security.am.plugin.MonitoringData;
import oracle.security.am.plugin.PluginAttributeContextType;
import oracle.security.am.plugin.PluginConfig;
import oracle.security.am.plugin.PluginResponse;
import oracle.security.am.plugin.authn.AbstractAuthenticationPlugIn;
import oracle.security.am.plugin.authn.AuthenticationContext;
import oracle.security.am.plugin.authn.AuthenticationException;
import oracle.security.am.plugin.authn.CredentialParam;
import oracle.security.am.plugin.authn.PluginConstants;
import oracle.security.am.plugin.impl.CredentialMetaData;
import oracle.security.am.plugin.impl.UserAction;
import oracle.security.am.plugin.impl.UserActionContext;
import oracle.security.am.plugin.impl.UserActionMetaData;
import oracle.security.am.plugin.impl.UserContextData;
import oracle.security.am.engines.common.identity.provider.UserIdentityProvider;
import oracle.security.am.engines.common.identity.provider.UserInfo;
import oracle.security.am.engines.common.identity.provider.exceptions.IdentityProviderException;
import oracle.security.am.common.utilities.principal.OAMGUIDPrincipal;
import oracle.security.am.common.utilities.principal.OAMUserDNPrincipal;
import oracle.security.am.common.utilities.principal.OAMUserPrincipal;
import oracle.security.am.engines.common.identity.provider.UserIdentityProviderFactory;

import com.duosecurity.Client;
import com.duosecurity.model.Token;

public final class DuoPlugin extends AbstractAuthenticationPlugIn {

    private static final String JAR_VERSION = "0.1.0";
    private static final String IKEY_PARAM = "ikey";
    private static final String SKEY_PARAM = "skey";
    private static final String HOST_PARAM = "host";
    // private static final String STORE_PARAM = "User Store";
    // private static final String FAILMODE = "Fail mode";
    private static final String CREDENTIAL_NAME_CODE = "duo_code";
    private static final String CREDENTIAL_NAME_STATE = "state";

    // number of tries to contact Duo
    private static final int MAX_TRIES = 3;
    // duration of time in seconds until a retry is requested to Duo
    private static final int MAX_TIMEOUT = 10;

    // Regex-syntax string, indicating the things to remove during sanitization of a string
    private static final String SANITIZING_PATTERN = "[^A-Za-z0-9_@.]";

    String ikey = null;
    String skey = null;
    String host = null;
    String username = null;
    // String failmode = null;
    // String userStore = null;
    private Client duoClient;
    private String duoState;

    @Override
    public ExecutionStatus initialize(final PluginConfig config) throws IllegalArgumentException {

        super.initialize(config);

        LOGGER.log(Level.INFO, this.getClass().getName() + " initializing Duo Plugin");
        try {
            this.ikey = (String) config.getParameter(IKEY_PARAM);
            this.skey = (String) config.getParameter(SKEY_PARAM);
            this.host = (String) config.getParameter(HOST_PARAM);
            // this.failmode = config.getParameter(FAILMODE).toString().toLowerCase();
            // String configuredStore = (String) config.getParameter(STORE_PARAM);
            // if (configuredStore != null && !configuredStore.equals("")) {
            //     this.userStore = configuredStore;
            // }
            this.duoClient = new Client(this.ikey, this.skey, this.host, "TODO");
            this.duoClient.appendUserAgentInfo("TODO");
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE,
                       "Null value not allowed for required parameter",
                       error);
            throw new IllegalArgumentException("Null value not allowed for "
                                               + "required parameter");
        }

        // LOGGER.log(Level.INFO, "Fail mode is set to: " + sanitizeForLogging(this.failmode));

        return ExecutionStatus.SUCCESS;
    }

    @Override
    public ExecutionStatus process(final AuthenticationContext context) throws AuthenticationException {

        LOGGER.log(Level.INFO, "Duo plugin starting");
        UserActionMetaData userAction = null;
        ExecutionStatus status = ExecutionStatus.FAILURE;
        this.username = getUserName(context);

        // attempts to get the duo code value that is sent back to the plugin URL after finishing with the prompt
        CredentialParam param = context.getCredential().getParam(CREDENTIAL_NAME_CODE);

        if ((param == null) || (param.getValue() == null) || (param.getValue().toString().length() == 0)) {
            LOGGER.log(Level.INFO, "Duo phase 1 starting"); // TODO fixup
            // We didn't have a duo code, this is probably the first time through the plugin
            // TODO log things

            // TODO health check and failmode considerations will go here later

            status = ExecutionStatus.PAUSE;

            this.duoState = duoClient.generateState();

            String authUrl;

            try {
                authUrl = duoClient.createAuthUrl(this.username, this.duoState);
            } catch (Exception error) {
                LOGGER.log(Level.SEVERE,
                        "An exception occurred while "
                                + sanitizeForLogging(this.username)
                                + " attempted Duo two-factor authentication.",
                        error);
                this.updatePluginResponse(context);
                return ExecutionStatus.FAILURE;
            }
            LOGGER.log(Level.INFO, "Generated auth url " + authUrl);

            UserContextData codeResponseContext = new UserContextData(CREDENTIAL_NAME_CODE, CREDENTIAL_NAME_CODE, new CredentialMetaData((PluginConstants.PASSWORD)));
            UserContextData stateResponseContext = new UserContextData(CREDENTIAL_NAME_STATE, CREDENTIAL_NAME_STATE, new CredentialMetaData((PluginConstants.PASSWORD)));
            UserContextData urlContext = new UserContextData(authUrl, new CredentialMetaData("URL"));
            UserActionContext actionContext = new UserActionContext();
            actionContext.getContextData().add(codeResponseContext);
            actionContext.getContextData().add(stateResponseContext);
            actionContext.getContextData().add(urlContext);

            userAction = UserActionMetaData.REDIRECT_GET;

            LOGGER.log(Level.INFO, "Duo phase 1 complete, redirecting");
            UserAction action = new UserAction(actionContext, userAction);
            context.setAction(action);
            this.updatePluginResponse(context);

        } else {
            // We got a duo code, so we need to validate it
            LOGGER.log(Level.INFO, "Duo phase 2 starting");

            CredentialParam codeParam = context.getCredential().getParam(CREDENTIAL_NAME_CODE);
            String duoCode = codeParam.getValue().toString();
            LOGGER.log(Level.INFO, "Got duo code " + duoCode); // TODO remove
            CredentialParam stateParam = context.getCredential().getParam(CREDENTIAL_NAME_STATE);
            String duoState = stateParam.getValue().toString();

            // TODO check that state matches

            try {
              Token duoToken = duoClient.exchangeAuthorizationCodeFor2FAResult(duoCode, this.username);
              LOGGER.log(Level.INFO, "Got and validated duo token successfully");
              // This will raise if the username doesn't match but is there anything we want to check?
            } catch (Exception error) {
                LOGGER.log(Level.SEVERE,
                           "An exception occurred while "
                           + sanitizeForLogging(this.username)
                           + " attempted Duo two-factor authentication.",
                           error);
                this.updatePluginResponse(context);
                return ExecutionStatus.FAILURE;
            }

            status = ExecutionStatus.SUCCESS;
            this.updatePluginResponse(context);
        }

        return status;

    }

    /**  TODO will need to do mostly the same, but for the health check
    private Response sendPreAuthRequest() throws Exception {
        Http request = new Http("POST", this.host, "/auth/v2/preauth",
                MAX_TIMEOUT);
        request.addParam("username", this.username);
        String userAgent = getUserAgent();
        request.addHeader("User-Agent", userAgent);
        request.signRequest(this.ikey, this.skey);
        return request.executeHttpRequest();
    }

    String performPreAuth() throws Exception {

        if (this.failmode.equals("secure")) {
            return "auth";
        } else if (!this.failmode.equals("safe")) {
            throw new IllegalArgumentException("Fail mode must be either "
                                               + "safe or secure");
        }

        // check if Duo authentication is even necessary by calling preauth
        for (int i = 0; ; ++i) {
            try {
                Response preAuthResponse = sendPreAuthRequest();
                int statusCode = preAuthResponse.code();
                if (statusCode / 100 == 5) {
                    LOGGER.log(Level.WARNING,
                               "Duo 500 error. Fail open for user: "
                               + sanitizeForLogging(this.username));
                    return "allow";
                }

                // parse response
                JSONObject json = new JSONObject(preAuthResponse.body().string());
                if (!json.getString("stat").equals("OK")) {
                    throw new Exception(
                            "Duo error code (" + json.getInt("code") + "): "
                            + json.getString("message"));
                }

                String result = json.getJSONObject("response").getString("result");
                if (result.equals("allow")) {
                    LOGGER.log(Level.INFO, "Duo 2FA bypass for user: "
                               + sanitizeForLogging(this.username));
                    return "allow";
                }
                break;

            } catch (java.io.IOException error) {
                if (i >= this.MAX_TRIES - 1) {
                    LOGGER.log(Level.WARNING,
                               "Duo server unreachable. Fail open for user: "
                               + sanitizeForLogging(this.username), error);
                    return "allow";
                }
            }
        }
        return "auth";
    } **/

    @Override
    public String getDescription() {
        return "Duo Security's Plugin to allow users to 2FA with Duo";
    }

    @Override
    public Map<String, MonitoringData> getMonitoringData() {
        // Plugins can log DMS data which will be picked by the Auth framework
        // and logged.
        return null;
    }

    @Override
    public boolean getMonitoringStatus() {
        // Indicates if logging DMS data is enabled for the plugins.
        return false;
    }

    @Override
    public void setMonitoringStatus(final boolean status) {

    }

    @Override
    public String getPluginName() {
        return "DuoPlugin";
    }


    @Override
    public int getRevision() {
        return 0;
    }

    private void updatePluginResponse(final AuthenticationContext context) {
        String retAttrs[] = (String[]) null;

        String userName = getUserName(context);
        UserIdentityProvider provider = null;
        UserInfo user = null;
        try {
            provider = getUserIdentityProvider();
            user = provider.locateUser(userName);
            retAttrs = provider.getReturnAttributes();

        } catch (Exception error) {
            LOGGER.log(Level.SEVERE,
                       "OAM error retrieving user profile from configured "
                       + "identity store during Duo two-factor", error);

        }

        String userIdentity = user.getUserId();
        String userDN = user.getDN();
        Subject subject = new Subject();
        subject.getPrincipals().add(new OAMUserPrincipal(userIdentity));
        subject.getPrincipals().add(new OAMUserDNPrincipal(userDN));

        if (user.getGUID() != null) {
            subject.getPrincipals().add(new OAMGUIDPrincipal(user.getGUID()));
        } else {
            subject.getPrincipals().add(new OAMGUIDPrincipal(userIdentity));
        }
        context.setSubject(subject);

        CredentialParam param = new CredentialParam();
        param.setName(PluginConstants.KEY_USERNAME_DN);
        param.setType("string");
        param.setValue(userDN);
        context.getCredential().addCredentialParam(PluginConstants.KEY_USERNAME_DN, param);

        PluginResponse rsp = new PluginResponse();
        rsp = new PluginResponse();
        rsp.setName(PluginConstants.KEY_AUTHENTICATED_USER_NAME);
        rsp.setType(PluginAttributeContextType.LITERAL);
        rsp.setValue(userIdentity);
        context.addResponse(rsp);

        rsp = new PluginResponse();
        rsp.setName(PluginConstants.KEY_RETURN_ATTRIBUTE);
        rsp.setType(PluginAttributeContextType.LITERAL);
        rsp.setValue(retAttrs);
        context.addResponse(rsp);

        rsp = new PluginResponse();
        rsp.setName("authn_policy_id");
        rsp.setType(PluginAttributeContextType.REQUEST);
        rsp.setValue(context.getAuthnScheme().getName());
        context.addResponse(rsp);

    }

    private String getUserName(final AuthenticationContext context) {
        String userName = null;

        CredentialParam param = context.getCredential().getParam("KEY_USERNAME");

        if (param != null) {
            userName = (String) param.getValue();
        }

        if ((userName == null) || (userName.length() == 0)) {
            userName = context.getStringAttribute("KEY_USERNAME");
        }

        return userName;
    }

    private UserIdentityProvider getUserIdentityProvider() throws IdentityProviderException {

        return UserIdentityProviderFactory.getProvider();
        /**
        if (this.userStore == null) {
            return UserIdentityProviderFactory.getProvider();
        } else {
            return UserIdentityProviderFactory.getProvider(this.userStore);
        } **/
    }

    // TODO for later
    static String getUserAgent() {
        String userAgent = "duo_oam/jar " + JAR_VERSION  + " (";

        userAgent = addKeyValueToUserAgent(userAgent, "java.version") + "; ";
        userAgent = addKeyValueToUserAgent(userAgent, "os.name") + "; ";
        userAgent = addKeyValueToUserAgent(userAgent, "os.arch") + "; ";
        userAgent = addKeyValueToUserAgent(userAgent, "os.version");

        userAgent += ")";

        return userAgent;
    }

    private static String addKeyValueToUserAgent(String userAgent, String key) {
        return userAgent += (key + "=" + System.getProperty(key));
    }

    static String sanitizeForLogging(String stringToSanitize) {
      if (stringToSanitize == null) {
        return "";
      }

      return stringToSanitize.replaceAll(SANITIZING_PATTERN, "");        
    }
}