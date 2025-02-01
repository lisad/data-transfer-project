/*
 * Copyright 2023 The Data Transfer Project Authors.
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
 *
 */

package org.datatransferproject.datatransfer.google.photos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableMap;
import com.google.photos.types.proto.MediaItem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.datatransfer.google.common.GoogleCredentialFactory;
import org.datatransferproject.datatransfer.google.mediaModels.AlbumListResponse;
import org.datatransferproject.datatransfer.google.mediaModels.BatchMediaItemResponse;
import org.datatransferproject.datatransfer.google.mediaModels.GoogleAlbum;
import org.datatransferproject.datatransfer.google.mediaModels.GoogleMediaItem;
import org.datatransferproject.datatransfer.google.mediaModels.MediaItemSearchResponse;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItem;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItemResult;
import org.datatransferproject.datatransfer.google.mediaModels.NewMediaItemUpload;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.spi.transfer.types.PermissionDeniedException;
import org.datatransferproject.spi.transfer.types.UploadErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GooglePhotosInterfaceTest {

  private static final String ALBUM_ID = "RANDOM_ALBUM_ID";
  private static final String MEDIA_ID = "RANDOM_MEDIA_ID";
  private static final String PAGE_TOKEN = "token1";
  private static final String PAGE_SIZE_KEY = "pageSize";
  private static final int ALBUM_PAGE_SIZE = 20;
  private static final int MEDIA_PAGE_SIZE = 50;
  private static final String BASE_URL = "https://photoslibrary.googleapis.com/v1/";
  private static final String FILTERS_KEY = "filters";
  private static final String INCLUDE_ARCHIVED_KEY = "includeArchivedMedia";
  private static final String ALBUM_ID_KEY = "albumId";
  private static final String TOKEN_KEY = "pageToken";
  private static final Map<String, String> PHOTO_UPLOAD_PARAMS =
      ImmutableMap.of(
          "Content-type", "application/octet-stream",
          "X-Goog-Upload-Protocol", "raw");
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private JsonFactory jsonFactory;
  private Monitor monitor;
  private GooglePhotosInterface googlePhotosInterface;
  private Credential credential;

  @BeforeEach
  public void setUp() throws Exception {
    monitor = Mockito.mock(Monitor.class);
    jsonFactory = Mockito.mock(JsonFactory.class);
    GoogleCredentialFactory credentialFactory = mock(GoogleCredentialFactory.class);
    credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod()).build();
    credential.setAccessToken("acc");
    credential.setExpirationTimeMilliseconds(null);
    when(credentialFactory.createCredential(any())).thenReturn(credential);
    googlePhotosInterface =
        new GooglePhotosInterface(credentialFactory, credential, jsonFactory, monitor, 1.0);
  }


  @Test
  void listAlbums() throws Exception {
    //Setup
    AlbumListResponse albumListResponse = new AlbumListResponse();
    Map<String, String> params = new LinkedHashMap<>();
    params.put(PAGE_SIZE_KEY, String.valueOf(ALBUM_PAGE_SIZE));

    //Declare mocks
    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn(albumListResponse).when(photosInterfaceSpy)
        .makeGetRequest(anyString(), any(), any());

    // Run test
    AlbumListResponse apiResponse = photosInterfaceSpy.listAlbums(Optional.empty());

    // Check results
    ArgumentCaptor<Optional<Map<String, String>>> paramsCaptor = ArgumentCaptor.forClass(
        Optional.class);
    Mockito.verify(photosInterfaceSpy).makeGetRequest(anyString(), paramsCaptor.capture(), any());
    assertEquals(albumListResponse, apiResponse);
    assertEquals(params, paramsCaptor.getValue().get());
  }

  @Test
  void getAlbum() throws Exception {
    GoogleAlbum googleAlbum = new GoogleAlbum();
    Map<String, String> params = new LinkedHashMap<>();

    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn(googleAlbum).when(photosInterfaceSpy)
        .makeGetRequest(anyString(), any(), any());

    GoogleAlbum apiResponse = photosInterfaceSpy.getAlbum(ALBUM_ID);

    ArgumentCaptor<String> urlStringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Optional<Map<String, String>>> paramsCaptor = ArgumentCaptor.forClass(
        Optional.class);
    Mockito.verify(photosInterfaceSpy)
        .makeGetRequest(urlStringCaptor.capture(), paramsCaptor.capture(), any());
    assertEquals(googleAlbum, apiResponse);
    assertEquals(BASE_URL + "albums/" + ALBUM_ID, urlStringCaptor.getValue());
    assertEquals(params, paramsCaptor.getValue().get());
  }

  @Test
  void getMediaItem() throws Exception {
    GoogleMediaItem googleMediaItem = new GoogleMediaItem();
    Map<String, String> params = new LinkedHashMap<>();

    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn(googleMediaItem).when(photosInterfaceSpy)
        .makeGetRequest(anyString(), any(), any());

    GoogleMediaItem apiResponse = photosInterfaceSpy.getMediaItem(MEDIA_ID);

    ArgumentCaptor<String> urlStringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Optional<Map<String, String>>> paramsCaptor = ArgumentCaptor.forClass(
        Optional.class);
    Mockito.verify(photosInterfaceSpy)
        .makeGetRequest(urlStringCaptor.capture(), paramsCaptor.capture(), any());
    assertEquals(googleMediaItem, apiResponse);
    assertEquals(BASE_URL + "mediaItems/" + MEDIA_ID, urlStringCaptor.getValue());
    assertEquals(params, paramsCaptor.getValue().get());

  }

  @Test
  void listMediaItems() throws Exception {
    MediaItemSearchResponse mediaItemSearchResponse = new MediaItemSearchResponse();
    Map<String, String> params = new LinkedHashMap<>();
    params.put(PAGE_SIZE_KEY, String.valueOf(MEDIA_PAGE_SIZE));
    params.put(ALBUM_ID_KEY, ALBUM_ID);
    params.put(TOKEN_KEY, PAGE_TOKEN);

    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn(mediaItemSearchResponse).when(photosInterfaceSpy)
        .makePostRequest(anyString(), any(), any(), any(), any());

    MediaItemSearchResponse apiResponse = photosInterfaceSpy.listMediaItems(Optional.of(ALBUM_ID),
        Optional.of(PAGE_TOKEN));

    ArgumentCaptor<String> urlStringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<JsonHttpContent> contentCaptor = ArgumentCaptor.forClass(JsonHttpContent.class);
    Mockito.verify(photosInterfaceSpy)
        .makePostRequest(urlStringCaptor.capture(), any(), any(), contentCaptor.capture(), any());
    assertEquals(mediaItemSearchResponse, apiResponse);
    assertEquals(BASE_URL + "mediaItems:search", urlStringCaptor.getValue());
    assertEquals(params, contentCaptor.getValue().getData());
  }

  @Test
  void listMediaItemsEmptyOptionals() throws Exception {
    MediaItemSearchResponse mediaItemSearchResponse = new MediaItemSearchResponse();
    Map<String, Object> params = new LinkedHashMap<>();
    params.put(PAGE_SIZE_KEY, String.valueOf(MEDIA_PAGE_SIZE));
    params.put(FILTERS_KEY, ImmutableMap.of(INCLUDE_ARCHIVED_KEY, String.valueOf(true)));

    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn(mediaItemSearchResponse).when(photosInterfaceSpy)
        .makePostRequest(anyString(), any(), any(), any(), any());

    MediaItemSearchResponse apiResponse = photosInterfaceSpy.listMediaItems(Optional.empty(),
        Optional.empty());

    ArgumentCaptor<JsonHttpContent> contentCaptor = ArgumentCaptor.forClass(JsonHttpContent.class);
    Mockito.verify(photosInterfaceSpy)
        .makePostRequest(anyString(), any(), any(), contentCaptor.capture(), any());
    assertEquals(mediaItemSearchResponse, apiResponse);
    assertEquals(params, contentCaptor.getValue().getData());
  }

  @Test
  void createAlbum() throws Exception {
    GoogleAlbum googleAlbum = new GoogleAlbum();
    googleAlbum.setId(ALBUM_ID);
    TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
    };
    Map<String, Object> albumMap = objectMapper.readValue(
        objectMapper.writeValueAsString(googleAlbum), typeRef);
    Map<String, Object> params = ImmutableMap.of("album", albumMap);

    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn(googleAlbum).when(photosInterfaceSpy)
        .makePostRequest(anyString(), any(), any(), any(), any());

    GoogleAlbum apiResponse = photosInterfaceSpy.createAlbum(googleAlbum);

    ArgumentCaptor<String> urlStringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<JsonHttpContent> contentCaptor = ArgumentCaptor.forClass(JsonHttpContent.class);
    Mockito.verify(photosInterfaceSpy)
        .makePostRequest(urlStringCaptor.capture(), any(), any(), contentCaptor.capture(), any());
    assertEquals(googleAlbum, apiResponse);
    assertEquals(BASE_URL + "albums", urlStringCaptor.getValue());
    assertEquals(params, contentCaptor.getValue().getData());
  }

  @Test
  void uploadMediaContent() throws Exception {
    byte[] bytes = "MEDIA_ID".getBytes();
    InputStream inputStream = new ByteArrayInputStream(bytes);

    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn("PHOTO").when(photosInterfaceSpy)
        .makePostRequest(anyString(), any(), any(), any(), any());

    String apiResponse = photosInterfaceSpy.uploadMediaContent(inputStream, null);

    ArgumentCaptor<String> urlStringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Optional<Map<String, String>>> paramsCaptor = ArgumentCaptor.forClass(
        Optional.class);
    ArgumentCaptor<Optional<Map<String, String>>> extraHeaders = ArgumentCaptor.forClass(
        Optional.class);
    ArgumentCaptor<ByteArrayContent> contentCaptor = ArgumentCaptor.forClass(
        ByteArrayContent.class);
    Mockito.verify(photosInterfaceSpy)
        .makePostRequest(urlStringCaptor.capture(), paramsCaptor.capture(), extraHeaders.capture(),
            contentCaptor.capture(), any());
    assertEquals("PHOTO", apiResponse);
    assertEquals(BASE_URL + "uploads/", urlStringCaptor.getValue());
    assertEquals(Optional.of(PHOTO_UPLOAD_PARAMS), paramsCaptor.getValue());
  }

  @Test
  void uploadEmptyMediaContent()
      throws InvalidTokenException, UploadErrorException, PermissionDeniedException, IOException {
    byte[] bytes = new byte[0];
    InputStream inputStream = new ByteArrayInputStream(bytes);
    String apiResponse = googlePhotosInterface.uploadMediaContent(inputStream, null);
    assertEquals("EMPTY_PHOTO", apiResponse);


  }

  @Test
  void createPhotos() throws Exception {
    BatchMediaItemResponse batchMediaItemResponse = new BatchMediaItemResponse(
        new NewMediaItemResult[]{});
    List<NewMediaItem> newMediaItem = new ArrayList<>();
    NewMediaItemUpload newMediaItemUpload = new NewMediaItemUpload(ALBUM_ID,newMediaItem);
    TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
    };
    Map<String, Object> params = objectMapper.readValue(
        objectMapper.writeValueAsString(newMediaItemUpload), typeRef);

    GooglePhotosInterface photosInterfaceSpy = Mockito.spy(this.googlePhotosInterface);
    Mockito.doReturn(batchMediaItemResponse).when(photosInterfaceSpy)
        .makePostRequest(anyString(), any(), any(), any(), any());

    BatchMediaItemResponse apiResponse = photosInterfaceSpy.createPhotos(
        newMediaItemUpload);

    ArgumentCaptor<String> urlStringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<JsonHttpContent> contentCaptor = ArgumentCaptor.forClass(JsonHttpContent.class);
    Mockito.verify(photosInterfaceSpy)
        .makePostRequest(urlStringCaptor.capture(), any(), any(), contentCaptor.capture(), any());
    assertEquals(batchMediaItemResponse, apiResponse);
    assertEquals(BASE_URL + "mediaItems:batchCreate", urlStringCaptor.getValue());
    assertEquals(params, contentCaptor.getValue().getData());
  }

//  @Test
  void makePostRequest() {
    // Discussion: I Believe this method should be kept private for the interface to make sense,
    // Writing a test would result in breaking it everytime implementation changes
  }
}