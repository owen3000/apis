/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.surfnet.oaaas.consent;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.surfnet.oaaas.auth.AbstractAuthenticator;
import org.surfnet.oaaas.auth.AbstractUserConsentHandler;
import org.surfnet.oaaas.auth.principal.AuthenticatedPrincipal;
import org.surfnet.oaaas.model.AccessToken;
import org.surfnet.oaaas.model.AuthorizationRequest;
import org.surfnet.oaaas.model.Client;
import org.surfnet.oaaas.repository.AccessTokenRepository;
import org.surfnet.oaaas.repository.AuthorizationRequestRepository;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Example {@link AbstractUserConsentHandler} that forwards to a form.
 * 
 */
public class FormUserConsentHandler extends AbstractUserConsentHandler {

  private static final String USER_OAUTH_APPROVAL = "user_oauth_approval";

  private final AccessTokenRepository accessTokenRepository;

  private final AuthorizationRequestRepository authorizationRequestRepository;

  public FormUserConsentHandler(AccessTokenRepository accessTokenRepository, AuthorizationRequestRepository authorizationRequestRepository) {
    this.accessTokenRepository = accessTokenRepository;
    this.authorizationRequestRepository = authorizationRequestRepository;
  }

  @Override
  public void handleUserConsent(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
      String authStateValue, String returnUri, Client client) throws IOException, ServletException {
    if (isUserConsentPost(request)) {
      if (processForm(request, response)) {
        chain.doFilter(request, response);
      }
    } else {
      processInitial(request, response, chain, returnUri, authStateValue, client);
    }
  }

  private boolean isUserConsentPost(HttpServletRequest request) {
    String oauthApproval = request.getParameter(USER_OAUTH_APPROVAL);
    return request.getMethod().equals(HttpMethod.POST.toString()) && StringUtils.isNotBlank(oauthApproval);
  }

  private void processInitial(HttpServletRequest request, ServletResponse response, FilterChain chain,
      String returnUri, String authStateValue, Client client) throws IOException, ServletException {
    AuthenticatedPrincipal principal = (AuthenticatedPrincipal) request.getAttribute(AbstractAuthenticator.PRINCIPAL);
    List<AccessToken> tokens = accessTokenRepository.findByResourceOwnerIdAndClient(principal.getName(), client);
    if (!CollectionUtils.isEmpty(tokens)) {
      // If another token is already present for this resource owner and client, no new consent should be requested
      List<String> grantedScopes = tokens.get(0).getScopes(); // take the scopes of the first access token found.
      setGrantedScopes(request, grantedScopes.toArray(new String[grantedScopes.size()]));
      chain.doFilter(request, response);
    } else {
      AuthorizationRequest authorizationRequest = authorizationRequestRepository.findByAuthState(authStateValue);
      request.setAttribute("requestedScopes", authorizationRequest.getRequestedScopes());
      request.setAttribute("client", client);
      request.setAttribute(AUTH_STATE, authStateValue);
      request.setAttribute("actionUri", returnUri);
      ((HttpServletResponse) response).setHeader("X-Frame-Options", "SAMEORIGIN");
      request.getRequestDispatcher(getUserConsentUrl()).forward(request, response);
    }

  }

  protected String getUserConsentUrl() {
    return "/userconsent.html";
  }

  private boolean processForm(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    if (Boolean.valueOf(request.getParameter(USER_OAUTH_APPROVAL))) {
      setAuthStateValue(request, request.getParameter(AUTH_STATE));
      String[] scopes = request.getParameterValues(GRANTED_SCOPES);
      setGrantedScopes(request, scopes);
      return true;
    } else {
      request.getRequestDispatcher(getUserConsentDeniedUrl()).forward(request, response);
      return false;
    }
  }

  protected String getUserConsentDeniedUrl() {
    return "/userconsent-denied.html";
  }

}
