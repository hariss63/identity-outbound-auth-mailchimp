/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.identity.authenticator.mailChimp;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.utils.JSONUtils;
import org.wso2.carbon.identity.application.authentication.framework.FederatedApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authenticator.oidc.OIDCAuthenticatorConstants;
import org.wso2.carbon.identity.application.authenticator.oidc.OpenIDConnectAuthenticator;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Authenticator of MailChimp
 *
 * @since 1.0.0
 */
public class MailChimpAuthenticator extends OpenIDConnectAuthenticator implements FederatedApplicationAuthenticator {

    private static Log log = LogFactory.getLog(MailChimpAuthenticator.class);
    private static final long serialVersionUID = -1636321794842883885L;

    /**
     * Get MailChimp authorization endpoint.
     */
    @Override
    protected String getAuthorizationServerEndpoint(Map<String, String> authenticatorProperties) {
        return MailChimpAuthenticatorConstants.MailChimp_OAUTH_ENDPOINT;
    }

    /**
     * Get MailChimp token endpoint.
     */
    @Override
    protected String getTokenEndpoint(Map<String, String> authenticatorProperties) {
        return MailChimpAuthenticatorConstants.MailChimp_TOKEN_ENDPOINT;
    }

    /**
     * Check ID token in MailChimp OAuth.
     */
    @Override
    protected boolean requiredIDToken(Map<String, String> authenticatorProperties) {
        return false;
    }

    /**
     * Get the friendly name of the Authenticator
     */
    @Override
    public String getFriendlyName() {
        return MailChimpAuthenticatorConstants.AUTHENTICATOR_FRIENDLY_NAME;
    }

    /**
     * Get OAuth2 Scope
     *
     * @param scope                   Scope
     * @param authenticatorProperties Authentication properties.
     * @return OAuth2 Scope
     */
    @Override
    protected String getScope(String scope, Map<String, String> authenticatorProperties) {
        return "";
    }

    /**
     * Get the name of the Authenticator
     */
    @Override
    public String getName() {
        return MailChimpAuthenticatorConstants.AUTHENTICATOR_NAME;
    }

    /**
     * Get Configuration Properties
     */
    @Override
    public List<Property> getConfigurationProperties() {
        List<Property> configProperties = new ArrayList<Property>();

        Property clientId = new Property();
        clientId.setName(OIDCAuthenticatorConstants.CLIENT_ID);
        clientId.setDisplayName(MailChimpAuthenticatorConstants.CLIENT_ID);
        clientId.setRequired(true);
        clientId.setDescription("Enter mailChimp client identifier value");
        clientId.setDisplayOrder(0);
        configProperties.add(clientId);

        Property clientSecret = new Property();
        clientSecret.setName(OIDCAuthenticatorConstants.CLIENT_SECRET);
        clientSecret.setDisplayName(MailChimpAuthenticatorConstants.CLIENT_SECRET);
        clientSecret.setRequired(true);
        clientSecret.setConfidential(true);
        clientSecret.setDescription("Enter mailChimp client secret value");
        clientSecret.setDisplayOrder(1);
        configProperties.add(clientSecret);

        Property callbackUrl = new Property();
        callbackUrl.setDisplayName(MailChimpAuthenticatorConstants.CALLBACK_URL);
        callbackUrl.setName(IdentityApplicationConstants.OAuth2.CALLBACK_URL);
        callbackUrl.setDescription("Enter the callback url");
        callbackUrl.setDisplayOrder(2);
        configProperties.add(callbackUrl);

        Property userInfoEndpoint = new Property();
        userInfoEndpoint.setDisplayName(MailChimpAuthenticatorConstants.MailChimp_USERINFO_ENDPOINT);
        userInfoEndpoint.setName(OIDCAuthenticatorConstants.IdPConfParams.USER_INFO_EP);
        userInfoEndpoint.setDescription("Enter the userInfo url");
        userInfoEndpoint.setDisplayOrder(3);
        configProperties.add(userInfoEndpoint);

        return configProperties;
    }

    /**
     * Process the authentication response.
     *
     * @param request  the HttpServletRequest.
     * @param response the HttpServletResponse.
     * @param context  the AuthenticationContext.
     * @throws AuthenticationFailedException
     */
    @Override
    protected void processAuthenticationResponse(HttpServletRequest request, HttpServletResponse response,
                                                 AuthenticationContext context) throws AuthenticationFailedException {
        try {
            Map<String, String> authenticatorProperties = context.getAuthenticatorProperties();
            String clientId = authenticatorProperties.get(OIDCAuthenticatorConstants.CLIENT_ID);
            String clientSecret = authenticatorProperties.get(OIDCAuthenticatorConstants.CLIENT_SECRET);
            String tokenEndPoint = getTokenEndpoint(authenticatorProperties);
            String callbackUrl = getCallbackUrl(authenticatorProperties);
            OAuthAuthzResponse authorizationResponse = OAuthAuthzResponse.oauthCodeAuthzResponse(request);
            String code = authorizationResponse.getCode();
            OAuthClientRequest accessRequest =
                    getAccessRequest(tokenEndPoint, clientId, code, clientSecret, callbackUrl);
            OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
            OAuthClientResponse oAuthResponse = getOauthResponse(oAuthClient, accessRequest);
            String accessToken = oAuthResponse.getParam(OIDCAuthenticatorConstants.ACCESS_TOKEN);
            if (StringUtils.isBlank(accessToken)) {
                throw new AuthenticationFailedException("Access token is empty or null");
            }
            context.setProperty(OIDCAuthenticatorConstants.ACCESS_TOKEN, accessToken);
            Map<ClaimMapping, String> claims = getSubjectAttributes(oAuthResponse, authenticatorProperties);
            String email = claims.get(ClaimMapping.build(MailChimpAuthenticatorConstants.MailChimp_EMAIL, MailChimpAuthenticatorConstants.MailChimp_EMAIL, (String) null, false));
            AuthenticatedUser authenticatedUserObj =
                    AuthenticatedUser.createFederateAuthenticatedUserFromSubjectIdentifier(email);
            authenticatedUserObj.setAuthenticatedSubjectIdentifier(email);
            authenticatedUserObj.setUserAttributes(claims);
            context.setSubject(authenticatedUserObj);
        } catch (OAuthProblemException e) {
            throw new AuthenticationFailedException("Authentication process failed", e);
        }
    }

    /**
     * Get subject attributes.
     *
     * @param authenticatorProperties Map<String, String>.
     * @param token                   The token to request.
     * @return attributes
     */
    @Override
    protected Map<ClaimMapping, String> getSubjectAttributes(OAuthClientResponse token, Map<String, String> authenticatorProperties) {
        HashMap claims = new HashMap();
        try {
            String access_token = token.getParam(MailChimpAuthenticatorConstants.ACCESS_TOKEN);
            String url = authenticatorProperties.get(OIDCAuthenticatorConstants.IdPConfParams.USER_INFO_EP);
            String json = sendRequest(url, access_token);
            if (StringUtils.isBlank(json)) {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to fetch user claims. Proceeding without user claims");
                }
                return claims;
            }

            Map jsonObject = JSONUtils.parseJSON(json);
            Iterator i$ = jsonObject.entrySet().iterator();

            while (i$.hasNext()) {
                Map.Entry data = (Map.Entry) i$.next();
                String key = (String) data.getKey();
                claims.put(ClaimMapping.build(key, key, null, false), jsonObject.get(key).toString());
                if (log.isDebugEnabled() && IdentityUtil.isTokenLoggable(MailChimpAuthenticatorConstants.USER_CLAIMS)) {
                    log.debug("Adding claims from end-point data mapping : " + key + " - " + jsonObject.get(key).toString());
                }
            }
        } catch (Exception var11) {
            log.error("Error occurred while accessing user info endpoint", var11);
        }
        return claims;
    }

    /**
     * Process the UserClaim response.
     *
     * @param url         the url to send Request.
     * @param accessToken the accessToken to request.
     * @throws IOException
     */
    protected String sendRequest(String url, String accessToken) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Claim URL: " + url);
        }

        if (url == null) {
            return "";
        } else {
            URL obj = new URL(url);
            HttpURLConnection urlConnection = (HttpURLConnection) obj.openConnection();
            urlConnection.setRequestMethod(MailChimpAuthenticatorConstants.REQUEST_METHOD);
            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity("{\"apikey\":\"" + accessToken + "\"" + "}"));
            HttpResponse response = httpClient.execute(post);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuilder builder = new StringBuilder();

            for (String inputLine = reader.readLine(); inputLine != null; inputLine = reader.readLine()) {
                builder.append(inputLine).append("\n");
            }

            reader.close();
            if (log.isDebugEnabled() && IdentityUtil.isTokenLoggable(MailChimpAuthenticatorConstants.USER_ID_TOKEN)) {
                log.debug(MailChimpAuthenticatorConstants.RESPONSE + builder.toString());
            }
            return builder.toString();
        }
    }

    /**
     * Get the OAuth response for access token.
     *
     * @param oAuthClient   the OAuthClient.
     * @param accessRequest the AccessRequest.
     * @return Response for access token from service provider.
     * @throws AuthenticationFailedException
     */
    private OAuthClientResponse getOauthResponse(OAuthClient oAuthClient, OAuthClientRequest accessRequest)
            throws AuthenticationFailedException {
        OAuthClientResponse oAuthResponse = null;
        try {
            oAuthResponse = oAuthClient.accessToken(accessRequest);
        } catch (OAuthSystemException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        } catch (OAuthProblemException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        }
        return oAuthResponse;
    }

    /**
     * Get the access token from endpoint from code.
     *
     * @param tokenEndPoint the Access_token endpoint.
     * @param clientId      the Client ID.
     * @param code          the Code.
     * @param clientSecret  the Client Secret.
     * @param callbackurl   the CallBack URL.
     * @return access token
     * @throws AuthenticationFailedException
     */
    private OAuthClientRequest getAccessRequest(String tokenEndPoint, String clientId, String code, String clientSecret,
                                                String callbackurl) throws AuthenticationFailedException {
        OAuthClientRequest accessRequest;
        try {
            accessRequest = OAuthClientRequest.tokenLocation(tokenEndPoint)
                    .setGrantType(GrantType.AUTHORIZATION_CODE)
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setCode(code)
                    .setRedirectURI(callbackurl)
                    .buildBodyMessage();
        } catch (OAuthSystemException e) {
            if (log.isDebugEnabled()) {
                log.debug("Exception while building request for request access token", e);
            }
            throw new AuthenticationFailedException(e.getMessage(), e);
        }
        return accessRequest;
    }
}