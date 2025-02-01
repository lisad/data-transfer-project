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

package org.datatransferproject.transfer.photobucket.client.helper;

import com.google.api.client.auth.oauth2.Credential;
import okhttp3.*;
import org.datatransferproject.transfer.photobucket.model.ProcessingResult;
import org.datatransferproject.transfer.photobucket.model.error.WrongStatusCodeException;
import org.datatransferproject.transfer.photobucket.model.error.WrongStatusCodeRetriableException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.datatransferproject.transfer.photobucket.data.PhotobucketConstants.*;

public class OkHttpClientWrapper {
  private final String bearer;
  private final OkHttpClient httpClient;
  private final UUID jobId;
  private final String requester;

  public OkHttpClientWrapper(
      UUID jobId, Credential credential, OkHttpClient httpClient, String requester) {
    this.bearer = "Bearer " + credential.getAccessToken();
    this.httpClient =
        httpClient
            .newBuilder()
            .writeTimeout(WRITE_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(CONNECTION_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            .build();
    this.jobId = jobId;
    this.requester = requester;
  }

  private Request.Builder getBuilder(String url) {
    Request.Builder requestBuilder = new Request.Builder().url(url);
    // add authorization headers
    requestBuilder
        .header(AUTHORIZATION_HEADER, bearer)
        .header(CORRELATION_ID_HEADER, jobId.toString())
        .header(REQUESTER_HEADER, requester);

    return requestBuilder;
  }

  public ProcessingResult performGQLRequest(
      RequestBody requestBody,
      Function<Response, ProcessingResult> responseTransformF,
      Function<Void, Boolean> conditionalExceptionF,
      ProcessingResult fallbackResult)
      throws Exception {

    // create builder for graphQL request
    Request.Builder gqlRequestBuilder = getBuilder(GQL_URL);
    gqlRequestBuilder.post(requestBody);
    // post request
    Response response = httpClient.newCall(gqlRequestBuilder.build()).execute();
    // gql server always provides 200 response code. If not, interrupt job
    if (response.code() == 200) {
      try {
        // apply transformation function to the response
        return responseTransformF.apply(response);
      } catch (Exception e) {
        // throw error only for given rules
        if (conditionalExceptionF.apply(null)) {
          throw e;
        } else {
          return fallbackResult;
        }
      }
    } else if (response.code() >= 500) {
      throw new WrongStatusCodeRetriableException("GQL server provided response code >= 500.");
    } else {
      throw new WrongStatusCodeException(
          String.format(
              "Wrong status code=[%s] provided by GQL server for jobId=[%s]",
              response.code(), jobId));
    }
  }

  public ProcessingResult performRESTGetRequest(
      String url, Function<Response, ProcessingResult> responseTransformF) throws Exception {
    return performRESTRequest(url, null, "get", 200, responseTransformF);
  }

  public ProcessingResult performRESTPostRequest(
      String url, RequestBody requestBody, Function<Response, ProcessingResult> responseTransformF)
      throws Exception {
    return performRESTRequest(url, requestBody, "post", 201, responseTransformF);
  }

  private ProcessingResult performRESTRequest(
      String url,
      RequestBody requestBody,
      String method,
      int code,
      Function<Response, ProcessingResult> responseTransformF)
      throws Exception {
    Request.Builder uploadRequestBuilder = getBuilder(url);
    if (method.equals("post")) {
      uploadRequestBuilder.post(requestBody);
    } else {
      uploadRequestBuilder.get();
    }
    Response uploadImageResponse = httpClient.newCall(uploadRequestBuilder.build()).execute();
    if (uploadImageResponse.code() == code) {
      return responseTransformF.apply(uploadImageResponse);
    } else if (uploadImageResponse.code() >= 500) {
      throw new WrongStatusCodeRetriableException("REST server provided response code >= 500.");
    } else {
      // throw error in case upload was not successful
      throw new WrongStatusCodeException(
          String.format(
              "Wrong status code=[%s], message=[%s] provided by REST for jobId=[%s]",
              uploadImageResponse.code(), uploadImageResponse.message(), jobId));
    }
  }
}
