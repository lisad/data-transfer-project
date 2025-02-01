/*
 * Copyright 2021 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.datatransferproject.transfer.photobucket.client;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import org.datatransferproject.types.transfer.auth.AppCredentials;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

import static org.datatransferproject.transfer.photobucket.data.PhotobucketConstants.ACCESS_TOKEN_EXPIRE_TIME_IN_SECONDS;

public class PhotobucketCredentialsFactory {
  private final HttpTransport httpTransport;
  private final JsonFactory jsonFactory;
  private final AppCredentials appCredentials;

  public PhotobucketCredentialsFactory(
      HttpTransport httpTransport, JsonFactory jsonFactory, AppCredentials appCredentials) {
    this.httpTransport = httpTransport;
    this.jsonFactory = jsonFactory;
    this.appCredentials = appCredentials;
  }

  public Credential createCredential(TokensAndUrlAuthData authData) {
    return new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
        .setTransport(httpTransport)
        .setJsonFactory(jsonFactory)
        .setClientAuthentication(
            new ClientParametersAuthentication(appCredentials.getKey(), appCredentials.getSecret()))
        .setTokenServerEncodedUrl(authData.getTokenServerEncodedUrl())
        .build()
        .setAccessToken(authData.getAccessToken())
        .setRefreshToken(authData.getRefreshToken())
        .setExpiresInSeconds(ACCESS_TOKEN_EXPIRE_TIME_IN_SECONDS);
  }
}
