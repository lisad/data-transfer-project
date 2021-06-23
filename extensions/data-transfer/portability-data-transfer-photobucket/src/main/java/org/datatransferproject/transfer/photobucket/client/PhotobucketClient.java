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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import okhttp3.*;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.io.IOUtils;
import org.datatransferproject.spi.cloud.storage.TemporaryPerJobDataStore;
import org.datatransferproject.transfer.photobucket.client.helper.InputStreamRequestBody;
import org.datatransferproject.transfer.photobucket.client.helper.OkHttpClientWrapper;
import org.datatransferproject.transfer.photobucket.model.PhotobucketAlbum;
import org.datatransferproject.transfer.photobucket.model.response.gql.PhotobucketGQLResponse;
import org.datatransferproject.transfer.photobucket.model.ProcessingResult;
import org.datatransferproject.transfer.photobucket.model.response.rest.UploadMediaResponse;
import org.datatransferproject.transfer.photobucket.model.response.rest.UserStatsResponse;
import org.datatransferproject.types.common.models.photos.PhotoAlbum;
import org.datatransferproject.types.common.models.photos.PhotoModel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.function.Function;

import static org.datatransferproject.transfer.photobucket.data.PhotobucketConstants.*;

public class PhotobucketClient {
  private final TemporaryPerJobDataStore jobStore;
  private final UUID jobId;
  private String pbRootAlbumId;
  private final ObjectMapper objectMapper;
  private final SimpleDateFormat simpleDateFormat =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
  private final OkHttpClientWrapper okHttpClientWrapper;

  public PhotobucketClient(
      UUID jobId,
      Credential credential,
      OkHttpClient httpClient,
      TemporaryPerJobDataStore jobStore,
      ObjectMapper objectMapper) {
    this.jobId = jobId;
    this.jobStore = jobStore;
    this.objectMapper = objectMapper;
    this.okHttpClientWrapper = new OkHttpClientWrapper(jobId, credential, httpClient);
  }

  public void createTopLevelAlbum(String name) throws Exception {
    // in case if albumId was not found while migrating photos,
    // we will migrate them into this top level album to avoid data loss
    PhotoAlbum photoAlbum = new PhotoAlbum(jobId.toString(), name, "");
    createAlbum(photoAlbum, "");
  }

  public void createAlbum(PhotoAlbum photoAlbum, String namePrefix) throws Exception {
    try {
      // check if album was not created before
      getPBAlbumId(photoAlbum.getId());
    } catch (Exception exception) {
      // if album was not created
      // generate gql query for getting pb album id via rest call
      RequestBody requestBody = createAlbumGQLViaRestMutation(photoAlbum, namePrefix);
      // get pbAlbumId and save it into job store
      Function<Response, ProcessingResult> bodyTransformF =
          response -> {
            try {
              // get photobucket albumId from response
              String pbAlbumId = parseGQLResponse(response).getCreatedAlbumId();
              // add photobucket albumId to the internal store, to match photos with proper albums
              jobStore.create(jobId, photoAlbum.getId(), new PhotobucketAlbum(pbAlbumId));
              return new ProcessingResult(pbAlbumId);
            } catch (IOException e) {
              return new ProcessingResult(e);
            }
          };

      // throw exception only if failed for top level album
      Function<Void, Boolean> isTopLevelAlbumF = v -> photoAlbum.getId().equals(jobId.toString());

      // fallback result in case if query execution/result parsing failed. In case if it's top level
      // album, we need to provide null to throw an exception. If it's usual album we can proceed.
      ProcessingResult fallbackResult =
          isTopLevelAlbumF.apply(null) ? null : new ProcessingResult("");

      System.out.println("Creating album " + photoAlbum.getName());
      ProcessingResult result =
          okHttpClientWrapper.performGQLRequest(
              requestBody, bodyTransformF, isTopLevelAlbumF, fallbackResult);
      result.extractOrThrow();
    }
  }

  public void uploadPhoto(PhotoModel photoModel) throws Exception {
    RequestBody uploadRequestBody;
    String url;
    // get pbAlbumId based on provided albumId
    String pbAlbumId = getPBAlbumId(photoModel.getAlbumId());
    if (photoModel.isInTempStore()) {
      // stream file
      BufferedInputStream inputStream =
          new BufferedInputStream(
              jobStore.getStream(jobId, photoModel.getFetchableUrl()).getStream());
      uploadRequestBody =
          new InputStreamRequestBody(MediaType.parse(photoModel.getMediaType()), inputStream);
      url = String.format("%s?albumId=%s&name=%s", UPLOAD_URL, pbAlbumId, photoModel.getTitle());
      String maybeUploadDate = extractUploadDate(photoModel);
      // extract upload date either from provided metadata or from exif
      if (maybeUploadDate != null) {
        url = url + String.format("&uploadDate=%s", simpleDateFormat.format(maybeUploadDate));
      }
    } else if (photoModel.getFetchableUrl() != null) {
      // upload media file via url
      // add query parameters
      url =
          String.format(
              "%s?url=%s&albumId=%s", UPLOAD_BY_URL_URL, photoModel.getFetchableUrl(), pbAlbumId);
      // set upload date based on provided metadata
      if (photoModel.getUploadedTime() != null) {
        url =
            url
                + String.format(
                    "&uploadDate=%s", simpleDateFormat.format(photoModel.getUploadedTime()));
      }
      uploadRequestBody = new FormBody.Builder().build();
    } else {
      throw new IllegalStateException(
          "Unable to get input stream for image " + photoModel.getTitle());
    }

    if (isUserOveStorage(uploadRequestBody.contentLength())) {
      throw new IllegalStateException("User reached his storage limits");
    }

    System.out.println("Upload url: " + url);

    Function<Response, ProcessingResult> uploadResponseTransformationF =
        uploadImageResponse -> {
          // note: if 201 code was provided, but response value is empty, do not fail upload, just
          // skip
          // title/description update
          if (uploadImageResponse.body() != null
              && photoModel.getDescription() != null
              && !photoModel.getDescription().isEmpty()) {
            String description = photoModel.getDescription().replace("\"", "").replace("\n", " ");
            // get imageId from provided response
            String imageId;
            try {
              imageId =
                  objectMapper.readValue(
                          uploadImageResponse.body().string(), UploadMediaResponse.class)
                      .id;
            } catch (IOException ioException) {
              return new ProcessingResult(
                  "Partial success: image was uploaded, but metadata wasn't updated - body parsing exception");
            }
            // update metadata gql query
            RequestBody updateMetadataRequestBody =
                RequestBody.create(
                    MediaType.parse("application/json"),
                    String.format(
                        "{\"query\": \"mutation updateImageDTP($imageId: String!, $title: String!, $description: String){ updateImage(imageId: $imageId, title: $title, description: $description)}\", \"variables\": {\"imageId\": \"%s\", \"title\": \"%s\", \"description\": \"%s\"}}",
                        imageId, photoModel.getTitle(), description));

            // do not verify update metadata response
            Function<Response, ProcessingResult> updateMetadataTransformationF =
                response -> new ProcessingResult(imageId);

            // newer fail in case of error
            Function<Void, Boolean> conditionalExceptionF = v -> true;

            try {
              // add metadata via gql
              return okHttpClientWrapper.performGQLRequest(
                  updateMetadataRequestBody,
                  updateMetadataTransformationF,
                  conditionalExceptionF,
                  null);
            } catch (Exception ignored) {
              System.out.println(
                  "Photo update wasn't successful: " + photoModel.getTitle() + " " + description);
              return new ProcessingResult(
                  "Partial success: image was uploaded, but metadata wasn't updated - gql call failed");
            }
          } else {
            return new ProcessingResult(
                "Partial success: image was uploaded, but metadata wasn't updated - body was empty");
          }
        };

    okHttpClientWrapper.performRESTPostRequest(
        url, uploadRequestBody, uploadResponseTransformationF);
  }

  /**
   * Create album either under pbRoot album (in case if we create top album) or under top album
   * TODO: add description while album creation, not supported for now within the same call
   */
  private RequestBody createAlbumGQLViaRestMutation(PhotoAlbum photoAlbum, String prefix)
      throws Exception {
    String pbParentId = getParentPBAlbumId(photoAlbum.getId());

    String jsonString =
        String.format(
            "{\"query\": \"mutation createAlbumDTP($title: String!, $parentAlbumId: String!){ createAlbum(title: $title, parentAlbumId: $parentAlbumId){ id }}\", \"variables\": {\"title\": \"%s\", \"parentAlbumId\": \"%s\"}}",
            prefix + photoAlbum.getName(), pbParentId);
    return RequestBody.create(MediaType.parse("application/json"), jsonString);
  }

  private String getParentPBAlbumId(String albumId) throws Exception {
    // for top level album parent is PB root album
    if (albumId.equals(jobId.toString())) {
      return getPbRootAlbumId();
    } else {
      try {
        return getPBAlbumId(albumId);
      } catch (Exception e) {
        // in case if pbAlbumId not found for current album, migrate photos to the top level album
        return getPBAlbumId(jobId.toString());
      }
    }
  }

  private String getPBAlbumId(String albumId) throws IOException, NullPointerException {
    return jobStore.findData(jobId, albumId, PhotobucketAlbum.class).getPbId();
  }

  private PhotobucketGQLResponse parseGQLResponse(Response response) throws IOException {
    if (response.body() != null) {
      return objectMapper.readValue(response.body().string(), PhotobucketGQLResponse.class);
    } else {
      throw new IOException("Empty response body was provided by GQL server");
    }
  }

  private String getPbRootAlbumId() throws Exception {
    // request if pbRootAlbumId was not requested yet
    if (pbRootAlbumId == null) {
      RequestBody requestBody =
          RequestBody.create(
              MediaType.parse("application/json"),
              "{\"query\": \"query getRootAlbumIdDTP{ getProfile{ defaultAlbum }}\"}");
      Function<Response, ProcessingResult> bodyTransformF =
          response -> {
            try {
              // get photobucket pbRootAlbumId from response
              pbRootAlbumId = parseGQLResponse(response).getRootAlbumId();
              return new ProcessingResult(pbRootAlbumId);
            } catch (IOException e) {
              return new ProcessingResult(e);
            }
          };

      // always fail in case of error, as unable to proceed without knowing pbRootId
      Function<Void, Boolean> conditionalExceptionF = v -> true;

      // fallback result is null, as unable to proceed without root
      System.out.println("Getting root album");

      ProcessingResult result =
          okHttpClientWrapper.performGQLRequest(
              requestBody, bodyTransformF, conditionalExceptionF, null);
      return result.extractOrThrow();

    } else {
      return pbRootAlbumId;
    }
  }

  private Boolean isUserOveStorage(long contentLength) throws Exception {
    // make request and extract response body string
    Function<Response, ProcessingResult> bodyTransformF =
        response -> {
          try {
            return new ProcessingResult(response.body().string());
          } catch (NullPointerException | IOException e) {
            return new ProcessingResult(e);
          }
        };

    String requestResultBodyStr =
        okHttpClientWrapper.performRESTGetRequest(USER_STATS_URL, bodyTransformF).extractOrThrow();
    UserStatsResponse stats = objectMapper.readValue(requestResultBodyStr, UserStatsResponse.class);
    return !((stats.availableSpace - contentLength >= 0) && (stats.availableImages - 1 >= 0));
  }

  /**
   * @return normalized upload date, or null, based either on uploadDate PhotoModel field or on exif
   *     data
   */
  private String extractUploadDate(PhotoModel photoModel) {
    if (photoModel.getUploadedTime() != null) {
      System.out.println("Date is provided, transforming " + photoModel.getUploadedTime());
      return simpleDateFormat.format(photoModel.getUploadedTime());
    } else {
      try {
        final byte[] bytes =
            IOUtils.toByteArray(
                new BufferedInputStream(
                    jobStore.getStream(jobId, photoModel.getFetchableUrl()).getStream()));
        final ImageMetadata metadata = Imaging.getMetadata(bytes);

        if (metadata == null) {
          System.out.println("Metadata is null");
          return null;
        }

        final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

        final TiffImageMetadata exif = jpegMetadata.getExif();

        if (exif == null) {
          System.out.println("Exif is null");
          return null;
        }

        String[] values = exif.getFieldValue(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);

        if (values == null || values.length == 0) {
          values = exif.getFieldValue(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
        }

        if (values == null || values.length == 0) {
          System.out.println("Date in exif is null");
          return null;
        }

        return simpleDateFormat.format(simpleDateFormat.parse(values[0]));
      } catch (Exception e) {
        System.out.println(e.getMessage());
        return null;
      }
    }
  }
}
